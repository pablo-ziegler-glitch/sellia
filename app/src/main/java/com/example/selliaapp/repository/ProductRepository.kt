package com.example.selliaapp.repository

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.dao.CategoryDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.ProductPriceAuditDao
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.dao.TenantSkuConfigDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.ProductImageEntity
import com.example.selliaapp.data.local.entity.ProductPriceAuditEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.local.entity.TenantSkuConfigEntity
import com.example.selliaapp.data.mappers.toModel
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.ImportRowIssue
import com.example.selliaapp.data.model.Product
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.data.model.stock.StockAdjustmentReason
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.model.stock.StockMovementWithProduct
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.CrossCatalogAuditContext
import com.example.selliaapp.data.remote.CrossCatalogRemoteDataSource
import com.example.selliaapp.data.remote.InvalidCrossCatalogDataException
import com.example.selliaapp.data.remote.ProductRemoteDataSource
import com.example.selliaapp.data.remote.StockInteractionEvent
import com.example.selliaapp.data.remote.StockInteractionRemoteDataSource
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.pricing.PricingCalculator
import com.example.selliaapp.sync.CsvImportWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max


/**
 * Repository de productos.
 * - Acceso a Room.
 * - Importación de archivos tabulares (dry-run + background WorkManager).
 * - Helpers de precios (E4) y normalización de categoría/proveedor.
 */
class ProductRepository(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val productImageDao: ProductImageDao,
    private val categoryDao: CategoryDao,
    private val providerDao: ProviderDao,
    private val productPriceAuditDao: ProductPriceAuditDao,
    private val pricingConfigRepository: PricingConfigRepository,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    private val tenantSkuConfigDao: TenantSkuConfigDao,
    @IoDispatcher private val io: CoroutineDispatcher   // <-- igual que en el VM
) {
    data class ImportApprovalSummary(
        val newProducts: Int,
        val existingProducts: Int,
        val totalStockToAdd: Int,
        val duplicateNameProducts: Int
    )

    data class RegenerationResult(
        val syncedFromCloud: Int,
        val mergedGroups: Int,
        val removedDuplicates: Int,
        val generatedSkuCodes: Int
    )

    // ---------- Cache simple en memoria ----------
    @Volatile private var lastCache: List<ProductEntity> = emptyList()
    @Volatile private var cachedSkuPrefix: String? = null
    @Volatile private var crossCatalogWriteAccessCache: Pair<Long, Boolean>? = null

    private val stockMovementDao = db.stockMovementDao()
    private val syncOutboxDao = db.syncOutboxDao()
    private val remote = ProductRemoteDataSource(firestore, tenantProvider)
    private val crossCatalogRemote = CrossCatalogRemoteDataSource(firestore)
    private val stockInteractionRemote = StockInteractionRemoteDataSource(firestore, tenantProvider)

    suspend fun insert(entity: ProductEntity): Int = withContext(io) {
        persistProduct(entity.copy(id = 0), StockMovementReasons.PRODUCT_CREATE)
    }

    suspend fun update(entity: ProductEntity): Int = withContext(io) {
        updateProductInternal(entity, StockMovementReasons.PRODUCT_UPDATE)
    }
    // -------- Lecturas --------

    /** Devuelve el producto mapeado a modelo de dominio (para la pantalla de edición). */
    suspend fun getByIdModel(id: Int): Product? = withContext(io) {
        val entity = productDao.getById(id) ?: return@withContext null
        val images = loadProductImages(id)
        entity.toModel().copy(
            imageUrls = images
        )
    }

    /** Nombres de categorías para dropdown (si no tenés CategoryDao, podemos derivarlo desde products). */
    fun observeAllCategoryNames(): Flow<List<String>> =
        categoryDao.observeAllNames() // ideal: tabla de categorías
            .map { it.filter { name -> name.isNotBlank() }.distinct().sorted() }

    /** Nombres de proveedores para dropdown. */
    fun observeAllProviderNames(): Flow<List<String>> =
        providerDao.observeAllNames()
            .map { it.filter { name -> name.isNotBlank() }.distinct().sorted() }


    suspend fun cachedOrEmpty(): List<ProductEntity> =
        if (lastCache.isNotEmpty()) lastCache else attachImages(productDao.getAllOnce())

    suspend fun getAllForExport(): List<ProductEntity> = withContext(io) {
        productDao.getAllOnce()
    }

    // ---------- E1: Normalización de ids por nombre ----------
    suspend fun ensureCategoryId(name: String?): Int? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val existing = categoryDao.getByName(n)
        if (existing != null) return existing.id
        val id = categoryDao.insert(com.example.selliaapp.data.local.entity.CategoryEntity(name = n))
        return if (id > 0) id.toInt() else categoryDao.getByName(n)?.id
    }

    suspend fun ensureProviderId(name: String?): Int? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val existing = providerDao.getByName(n)
        if (existing != null) return existing.id
        val id = providerDao.insert(com.example.selliaapp.data.local.entity.ProviderEntity(name = n))
        return if (id > 0) id.toInt() else providerDao.getByName(n)?.id
    }

    private suspend fun applyAutoPricing(
        incoming: ProductEntity,
        existing: ProductEntity? = null,
        force: Boolean = false
    ): ProductEntity {
        val normalizedIncoming = normalizeUnifiedEffectiveTransferPrice(incoming, existing)
        val purchasePrice = normalizedIncoming.purchasePrice ?: existing?.purchasePrice ?: return normalizedIncoming
        val hasManualPrices = when {
            existing == null -> listOf(
                normalizedIncoming.listPrice,
                normalizedIncoming.cashPrice,
            ).any { it != null }

            existing.autoPricing -> {
                val listChangedManually = normalizedIncoming.listPrice != null && normalizedIncoming.listPrice != existing.listPrice
                val effectiveChangedManually = normalizedIncoming.cashPrice != null && normalizedIncoming.cashPrice != (existing.cashPrice ?: existing.transferPrice)
                listChangedManually || effectiveChangedManually
            }

            else -> listOf(
                normalizedIncoming.listPrice,
                normalizedIncoming.cashPrice,
            ).any { it != null }
        }
        val shouldAuto = when {
            force -> true
            hasManualPrices -> false
            normalizedIncoming.autoPricing -> true
            existing != null -> existing.autoPricing
            else -> true
        }
        if (!shouldAuto) {
            val settings = pricingConfigRepository.getSettings()
            val transferPrice = normalizedIncoming.cashPrice
            val transferNetPrice = transferPrice?.let { price ->
                price * (1 - settings.transferenciaRetencionPercent / 100.0)
            }
            return normalizedIncoming.copy(
                autoPricing = false,
                transferPrice = transferPrice,
                transferNetPrice = transferNetPrice
            )
        }
        val settings = pricingConfigRepository.getSettings()
        val fixedCosts = pricingConfigRepository.getFixedCosts()
        val mlFixedCostTiers = pricingConfigRepository.getMlFixedCostTiers()
        val mlShippingTiers = pricingConfigRepository.getMlShippingTiers()
        val result = PricingCalculator.calculate(
            purchasePrice = purchasePrice,
            settings = settings,
            fixedCosts = fixedCosts,
            mlFixedCostTiers = mlFixedCostTiers,
            mlShippingTiers = mlShippingTiers,
            gainTargetOverridePercent = incoming.gainTargetPercent
        )
        return incoming.copy(
            listPrice = result.listPrice,
            cashPrice = result.cashPrice,
            transferPrice = result.cashPrice,
            transferNetPrice = result.transferNetPrice,
            mlPrice = result.mlPrice,
            ml3cPrice = result.ml3cPrice,
            ml6cPrice = result.ml6cPrice,
            autoPricing = true
        )
    }

    private fun normalizeUnifiedEffectiveTransferPrice(
        incoming: ProductEntity,
        existing: ProductEntity? = null
    ): ProductEntity {
        val unifiedEffectivePrice = incoming.cashPrice
            ?: incoming.transferPrice
            ?: existing?.cashPrice
            ?: existing?.transferPrice
        return if (unifiedEffectivePrice != null) {
            incoming.copy(
                cashPrice = unifiedEffectivePrice,
                transferPrice = unifiedEffectivePrice
            )
        } else {
            incoming
        }
    }

    // ---------- Importación tabular: bulkUpsert desde filas parseadas ----------
    suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) = withContext(io) {
        if (rows.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val skuPrefix = resolveSkuPrefix()
        val touchedIds = mutableSetOf<Int>()
        val interactionEvents = mutableListOf<StockInteractionEvent>()
        val crossCatalogCandidates = mutableMapOf<String, Pair<String, String?>>()

        db.withTransaction {
            rows.forEach { r ->
                val updated = r.updatedAt ?: LocalDate.now()
                val normalizedCode = r.code?.trim()?.takeIf { it.isNotBlank() }
                val normalizedName = r.name.trim().takeIf { it.isNotBlank() }
                val rBarcode = r.barcode?.trim()?.takeIf { it.isNotBlank() }
                val existing = when {
                    !normalizedCode.isNullOrBlank() -> productDao.getByCodeOnce(normalizedCode)
                    !normalizedName.isNullOrBlank() -> productDao.getByNameNormalizedOnce(normalizedName)
                    !rBarcode.isNullOrBlank()       -> productDao.getByBarcodeOnce(rBarcode)
                    else                      -> null
                }
                val beforeQty = existing?.quantity ?: 0
                val incoming = ProductEntity(
                    code = normalizedCode,
                    barcode = r.barcode,
                    name = r.name,
                    purchasePrice = r.purchasePrice,
                    listPrice = r.listPrice,
                    cashPrice = r.cashPrice,
                    transferPrice = r.transferPrice,
                    transferNetPrice = r.transferNetPrice,
                    mlPrice = r.mlPrice,
                    ml3cPrice = r.ml3cPrice,
                    ml6cPrice = r.ml6cPrice,
                    autoPricing = false,
                    quantity = max(0, r.quantity ?: 0),
                    description = r.description,
                    imageUrl = r.imageUrl,
                    imageUrls = r.imageUrls,
                    categoryId = existing?.categoryId,
                    providerId = existing?.providerId,
                    providerName = existing?.providerName,
                    providerSku = existing?.providerSku,
                    brand = r.brand ?: existing?.brand,
                    parentCategory = r.parentCategory ?: existing?.parentCategory,
                    category = r.category ?: existing?.category,
                    color = r.color ?: existing?.color,
                    sizes = if (r.sizes.isNotEmpty()) r.sizes else existing?.sizes.orEmpty(),
                    minStock = r.minStock?.let { max(0, it) } ?: existing?.minStock,
                    updatedAt = updated
                )

                val priced = applyAutoPricing(incoming, existing)
                val prepared = if (existing == null) ensureAutoCodes(priced, prefix = skuPrefix) else priced
                val id = productDao.upsertByKeys(prepared)
                touchedIds += id
                if (r.imageUrls.isNotEmpty()) {
                    replaceProductImages(id, r.imageUrls)
                }
                val current = productDao.getById(id) ?: return@forEach
                val delta = current.quantity - beforeQty
                if (delta != 0) {
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = id,
                            delta = delta,
                            reason = StockMovementReasons.CSV_IMPORT,
                            ts = Instant.ofEpochMilli(now),
                            note = if (existing == null) "Importación CSV (nuevo)" else "Importación CSV (actualización)"
                        )
                    )
                }
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = id.toLong(),
                        createdAt = now
                    )
                )
                interactionEvents += StockInteractionEvent(
                    action = if (existing == null) "PRODUCT_CREATED" else "PRODUCT_UPDATED",
                    productId = id,
                    productName = current.name,
                    delta = delta,
                    reason = StockMovementReasons.CSV_IMPORT,
                    note = if (existing == null) "Importación CSV (nuevo)" else "Importación CSV (actualización)",
                    source = "CSV_IMPORT",
                    occurredAtEpochMs = now
                )
            }
            lastCache = productDao.getAllOnce()
        }

        trySyncProductsNow(touchedIds, now)
        saveStockInteractions(interactionEvents)
    }

    // ---------- Flujo/consultas básicas ----------
    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()
        .map { products -> attachImages(products) }


    suspend fun getById(id: Int): ProductEntity? = withContext(io) {
        val product = productDao.getById(id) ?: return@withContext null
        val images = loadProductImages(product.id)
        product.copy(imageUrls = images)
    }

    fun pagingSearch(query: String): Flow<PagingData<ProductEntity>> =
        Pager(PagingConfig(pageSize = 30)) { productDao.pagingSearch(query) }.flow

    // ---------- Importación tabular: desde archivo ----------
    enum class ImportStrategy { Append, Replace }

    /**
     * Importa SIN escribir en DB: útil para dry-run.
     */
    suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult = withContext(io) {
        val rows = ProductCsvImporter.parseFile(context.contentResolver, fileUri)
        val already = cachedOrEmpty()
        val plan = ProductImportPlanner.plan(rows, already)
        val errors = plan.issues.map { issueToLegacyError(it) }
        ImportResult(
            inserted = plan.totalCreated,
            updated = plan.totalStockUpdated,
            errors = errors,
            totalProcessed = plan.totalProcessed,
            totalCreated = plan.totalCreated,
            totalStockUpdated = plan.totalStockUpdated,
            totalRejected = plan.totalRejected,
            totalValidationErrors = plan.totalValidationErrors,
            rowIssues = plan.issues
        )
    }

    suspend fun previewImport(context: Context, fileUri: Uri): ImportApprovalSummary = withContext(io) {
        val rows = ProductCsvImporter.parseFile(context.contentResolver, fileUri)
        val existing = cachedOrEmpty()
        val plan = ProductImportPlanner.plan(rows, existing)
        val totalStockToAdd = plan.actions
            .filterIsInstance<ProductImportPlanner.Action.UpdateStock>()
            .sumOf { max(0, it.row.quantity ?: 0) }
        val existingProducts = plan.actions.count { it is ProductImportPlanner.Action.UpdateStock } +
            plan.issues.count { it.technicalReason.contains("existing_product_", ignoreCase = true) }
        val newProducts = plan.actions.count { it is ProductImportPlanner.Action.Create }
        val duplicateNameProducts = existing
            .groupBy { it.name.trim().lowercase() }
            .values
            .sumOf { group -> if (group.size > 1) group.size else 0 }
        ImportApprovalSummary(
            newProducts = newProducts,
            existingProducts = existingProducts,
            totalStockToAdd = totalStockToAdd,
            duplicateNameProducts = duplicateNameProducts
        )
    }

    suspend fun regenerateExistingAccountData(): RegenerationResult = withContext(io) {
        val syncedFromCloud = runCatching { syncDown() }
            .onFailure { error ->
                Log.w("ProductRepository", "No se pudo refrescar desde nube durante regeneración.", error)
            }
            .getOrDefault(0)
        val all = productDao.getAllOnce()
        val grouped = all
            .groupBy { it.name.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .values
            .filter { it.size > 1 }
        var removedDuplicates = 0
        var generatedSkuCodes = 0
        val now = System.currentTimeMillis()
        val skuPrefix = resolveSkuPrefix()
        db.withTransaction {
            grouped.forEach { duplicates ->
                val keeper = duplicates.minByOrNull { it.id } ?: return@forEach
                val others = duplicates.filter { it.id != keeper.id }
                val mergedQty = duplicates.sumOf { it.quantity }
                val merged = keeper.copy(quantity = mergedQty, updatedAt = LocalDate.now())
                productDao.update(merged)
                if (others.isNotEmpty()) {
                    productDao.deleteByIds(others.map { it.id })
                    removedDuplicates += others.size
                }
                val delta = mergedQty - keeper.quantity
                if (delta != 0) {
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = keeper.id,
                            delta = delta,
                            reason = StockMovementReasons.CSV_IMPORT,
                            ts = Instant.ofEpochMilli(now),
                            note = "Regeneración de cuenta: consolidación de duplicados por nombre"
                        )
                    )
                }
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = keeper.id.toLong(),
                        createdAt = now
                    )
                )
            }
            productDao.getAllOnce().forEach { product ->
                if (product.code.isNullOrBlank()) {
                    val regenerated = ensureAutoCodes(product, prefix = skuPrefix)
                    if (regenerated.code != product.code) {
                        productDao.update(regenerated)
                        generatedSkuCodes += 1
                        syncOutboxDao.upsert(
                            SyncOutboxEntity(
                                entityType = SyncEntityType.PRODUCT.storageKey,
                                entityId = product.id.toLong(),
                                createdAt = now
                            )
                        )
                    }
                }
            }
            lastCache = productDao.getAllOnce()
        }
        RegenerationResult(
            syncedFromCloud = syncedFromCloud,
            mergedGroups = grouped.size,
            removedDuplicates = removedDuplicates,
            generatedSkuCodes = generatedSkuCodes
        )
    }

    /**
     * Importa con escritura en DB, con estrategia de stock (Append/Replace).
     */
    suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ImportStrategy
    ): ImportResult = withContext(io) {
        val rows = ProductCsvImporter.parseFile(context.contentResolver, fileUri)
        val allowMasterCatalogSync = isCsvOrXlsxImport(context, fileUri)
        if (!allowMasterCatalogSync) {
            Log.i(
                "ProductRepository",
                "Importación sin sync CROSS: solo se permite carga maestra vía CSV/XLSX"
            )
        }
        importProducts(rows, strategy, allowMasterCatalogSync = allowMasterCatalogSync)
    }

    suspend fun importProductsFromTable(
        table: List<List<String>>,
        strategy: ImportStrategy
    ): ImportResult = withContext(io) {
        val rows = ProductCsvImporter.parseTable(table)
        importProducts(rows, strategy, allowMasterCatalogSync = false)
    }

    suspend fun importCrossCatalogFromFile(
        context: Context,
        fileUri: Uri
    ): ImportResult = withContext(io) {
        val rows = ProductCsvImporter.parseFile(context.contentResolver, fileUri)
        var synced = 0
        val errors = mutableListOf<String>()

        rows.forEachIndexed { index, row ->
            val barcode = row.barcode?.trim().orEmpty()
            val name = row.name.trim()
            val brand = row.brand?.trim()?.takeIf { it.isNotBlank() }
            when {
                barcode.isBlank() -> errors += "Línea ${index + 2}: falta código de barras"
                name.isBlank() -> errors += "Línea ${index + 2}: falta nombre"
                else -> {
                    runCatching {
                        syncToCrossCatalog(barcode = barcode, name = name, brand = brand)
                    }.onSuccess { syncedOk ->
                        if (syncedOk) {
                            synced += 1
                        } else {
                            errors += "Línea ${index + 2}: no se pudo sincronizar CROSS (revisá permisos admin)"
                        }
                    }.onFailure { error ->
                        errors += "Línea ${index + 2}: ${error.message ?: "error al sincronizar CROSS"}"
                    }
                }
            }
        }

        ImportResult(inserted = synced, updated = 0, errors = errors)
    }

    private suspend fun importProducts(
        rows: List<ProductCsvImporter.Row>,
        strategy: ImportStrategy,
        allowMasterCatalogSync: Boolean
    ): ImportResult {
        val touchedIds = mutableSetOf<Int>()
        val now = System.currentTimeMillis()
        val skuPrefix = resolveSkuPrefix()
        val interactionEvents = mutableListOf<StockInteractionEvent>()
        val crossCatalogCandidates = mutableMapOf<String, Pair<String, String?>>()
        val existing = cachedOrEmpty()
        val plan = ProductImportPlanner.plan(rows, existing)
        val issues = plan.issues.toMutableList()

        db.withTransaction {
            plan.actions.forEach { action ->
                try {
                    when (action) {
                        is ProductImportPlanner.Action.Create -> {
                            val r = action.row
                            val normalizedCode = r.code?.trim()?.takeIf { it.isNotBlank() }
                            val normalizedBarcode = r.barcode?.trim()?.takeIf { it.isNotBlank() }
                            val qty = max(0, r.quantity ?: 0)
                            val existingByCode = normalizedCode?.let { productDao.getByCodeOnce(it) }
                            val existingByBarcode = normalizedBarcode?.let { productDao.getByBarcodeOnce(it) }
                            if (existingByCode != null || existingByBarcode != null) {
                                val issue = ImportRowIssue(
                                    line = r.lineNumber,
                                    productName = r.name.ifBlank { null },
                                    skuOrBarcode = normalizedCode ?: normalizedBarcode,
                                    attemptedAction = "crear",
                                    technicalReason = "concurrency_uniqueness_conflict",
                                    userMessage = "El producto ya existe y no se pudo crear por conflicto concurrente.",
                                    suggestion = "Reintentá importación usando actualizar_stock para productos existentes."
                                )
                                issues += issue
                                return@forEach
                            }
                            if (strategy != ImportStrategy.Append && strategy != ImportStrategy.Replace) {
                                throw IllegalArgumentException("Estrategia no soportada: $strategy")
                            }
                            val p = ProductEntity(
                                code = normalizedCode,
                                barcode = normalizedBarcode,
                                name = r.name.trim(),
                                purchasePrice = r.purchasePrice,
                                listPrice = r.listPrice,
                                cashPrice = r.cashPrice,
                                transferPrice = r.transferPrice,
                                transferNetPrice = r.transferNetPrice,
                                mlPrice = r.mlPrice,
                                ml3cPrice = r.ml3cPrice,
                                ml6cPrice = r.ml6cPrice,
                                autoPricing = false,
                                quantity = qty,
                                description = r.description,
                                imageUrl = r.imageUrl,
                                imageUrls = r.imageUrls,
                                categoryId = null,
                                providerId = null,
                                providerName = r.providerName,
                                providerSku = r.providerSku,
                                brand = r.brand,
                                parentCategory = r.parentCategory,
                                category = r.category,
                                color = r.color,
                                sizes = r.sizes,
                                minStock = r.minStock?.let { max(0, it) },
                                updatedAt = r.updatedAt ?: LocalDate.now()
                            )
                            val priced = applyAutoPricing(p, existing = null)
                            val prepared = ensureAutoCodes(priced, prefix = skuPrefix)
                            assertCodeAvailable(prepared.code, currentId = null)
                            val id = productDao.insert(prepared).toInt()
                            touchedIds += id
                            if (r.imageUrls.isNotEmpty()) {
                                replaceProductImages(id, r.imageUrls)
                            }
                            if (prepared.quantity != 0) {
                                stockMovementDao.insert(
                                    StockMovementEntity(
                                        productId = id,
                                        delta = prepared.quantity,
                                        reason = StockMovementReasons.CSV_IMPORT,
                                        ts = Instant.ofEpochMilli(now),
                                        note = "Importación CSV (nuevo)"
                                    )
                                )
                            }
                            syncOutboxDao.upsert(
                                SyncOutboxEntity(
                                    entityType = SyncEntityType.PRODUCT.storageKey,
                                    entityId = id.toLong(),
                                    createdAt = now
                                )
                            )
                            interactionEvents += StockInteractionEvent(
                                action = "PRODUCT_CREATED",
                                productId = id,
                                productName = prepared.name,
                                delta = prepared.quantity,
                                reason = StockMovementReasons.CSV_IMPORT,
                                note = "Importación CSV (nuevo)",
                                source = "CSV_IMPORT",
                                occurredAtEpochMs = now
                            )
                            prepared.barcode?.trim()?.takeIf { it.isNotBlank() }?.let { barcode ->
                                crossCatalogCandidates[barcode] = prepared.name to prepared.brand
                            }
                        }
                        is ProductImportPlanner.Action.UpdateStock -> {
                            val r = action.row
                            val current = productDao.getById(action.existing.id)
                            if (current == null) {
                                issues += ImportRowIssue(
                                    line = r.lineNumber,
                                    productName = r.name.ifBlank { null },
                                    skuOrBarcode = r.code?.trim()?.ifBlank { null } ?: r.barcode?.trim()?.ifBlank { null },
                                    attemptedAction = "actualizar stock",
                                    technicalReason = "missing_target_product",
                                    userMessage = "No se encontró el producto a actualizar.",
                                    suggestion = "Revisá código/barcode y reintentá la importación."
                                )
                                return@forEach
                            }
                            val replacementQty = max(0, r.quantity ?: 0)
                            val mergedRaw = current.copy(
                                quantity = replacementQty,
                                purchasePrice = r.purchasePrice ?: current.purchasePrice,
                                listPrice = r.listPrice ?: current.listPrice,
                                cashPrice = r.cashPrice ?: current.cashPrice,
                                transferPrice = r.transferPrice ?: current.transferPrice,
                                transferNetPrice = r.transferNetPrice ?: current.transferNetPrice,
                                mlPrice = r.mlPrice ?: current.mlPrice,
                                ml3cPrice = r.ml3cPrice ?: current.ml3cPrice,
                                ml6cPrice = r.ml6cPrice ?: current.ml6cPrice,
                                description = r.description ?: current.description,
                                imageUrl = r.imageUrl ?: current.imageUrl,
                                imageUrls = if (r.imageUrls.isNotEmpty()) r.imageUrls else current.imageUrls,
                                providerName = r.providerName ?: current.providerName,
                                providerSku = r.providerSku ?: current.providerSku,
                                brand = r.brand ?: current.brand,
                                parentCategory = r.parentCategory ?: current.parentCategory,
                                category = r.category ?: current.category,
                                color = r.color ?: current.color,
                                sizes = if (r.sizes.isNotEmpty()) r.sizes else current.sizes,
                                minStock = r.minStock?.let { max(0, it) } ?: current.minStock,
                                updatedAt = r.updatedAt ?: LocalDate.now()
                            )
                            val merged = applyAutoPricing(mergedRaw, existing = current)
                            productDao.update(merged)
                            touchedIds += current.id
                            val delta = replacementQty - current.quantity
                            if (delta != 0) {
                                stockMovementDao.insert(
                                    StockMovementEntity(
                                        productId = current.id,
                                        delta = delta,
                                        reason = StockMovementReasons.CSV_IMPORT,
                                        ts = Instant.ofEpochMilli(now),
                                        note = "Importación CSV (actualización de stock)"
                                    )
                                )
                            }
                            syncOutboxDao.upsert(
                                SyncOutboxEntity(
                                    entityType = SyncEntityType.PRODUCT.storageKey,
                                    entityId = current.id.toLong(),
                                    createdAt = now
                                )
                            )
                            interactionEvents += StockInteractionEvent(
                                action = "PRODUCT_STOCK_UPDATED",
                                productId = current.id,
                                productName = current.name,
                                delta = delta,
                                reason = StockMovementReasons.CSV_IMPORT,
                                note = "Importación CSV (actualización de stock)",
                                source = "CSV_IMPORT",
                                occurredAtEpochMs = now
                            )
                            current.barcode?.trim()?.takeIf { it.isNotBlank() }?.let { barcode ->
                                crossCatalogCandidates[barcode] = current.name to current.brand
                            }
                        }
                    }
                } catch (e: Exception) {
                    val row = when (action) {
                        is ProductImportPlanner.Action.Create -> action.row
                        is ProductImportPlanner.Action.UpdateStock -> action.row
                    }
                    issues += ImportRowIssue(
                        line = row.lineNumber,
                        productName = row.name.ifBlank { null },
                        skuOrBarcode = row.code?.trim()?.ifBlank { null } ?: row.barcode?.trim()?.ifBlank { null },
                        attemptedAction = when (action) {
                            is ProductImportPlanner.Action.Create -> "crear"
                            is ProductImportPlanner.Action.UpdateStock -> "actualizar stock"
                        },
                        technicalReason = e.message ?: "import_runtime_error",
                        userMessage = "La fila no pudo procesarse por un error interno controlado.",
                        suggestion = "Corregí los datos y reintentá. Si persiste, revisá logs técnicos."
                    )
                }
            }
            lastCache = productDao.getAllOnce()
        }
        trySyncProductsNow(touchedIds, now)
        saveStockInteractions(interactionEvents)
        if (allowMasterCatalogSync) {
            crossCatalogCandidates.forEach { (barcode, data) ->
                syncToCrossCatalog(barcode = barcode, name = data.first, brand = data.second)
            }
        }
        val errors = issues.map { issueToLegacyError(it) }
        val created = interactionEvents.count { it.action == "PRODUCT_CREATED" }
        val stockUpdated = interactionEvents.count { it.action == "PRODUCT_STOCK_UPDATED" }
        val validationErrors = plan.totalValidationErrors + issues.count {
            it.technicalReason.contains("invalid_", ignoreCase = true) ||
                it.technicalReason.contains("missing_", ignoreCase = true)
        }
        return ImportResult(
            inserted = created,
            updated = stockUpdated,
            errors = errors,
            totalProcessed = rows.size,
            totalCreated = created,
            totalStockUpdated = stockUpdated,
            totalRejected = issues.size,
            totalValidationErrors = validationErrors,
            rowIssues = issues
        )
    }

    /**
     * Importa productos desde un archivo tabular (resolver + uri) con la estrategia dada.
     * Internamente delega en ProductCsvImporter para parsear y aplicar cambios.
     */
    suspend fun importFromFile(
        resolver: ContentResolver,
        uri: Uri,
        strategy: ImportStrategy
    ): ImportResult {
        val rows = ProductCsvImporter.parseFile(resolver, uri)
        return when (strategy) {
            ImportStrategy.Append, ImportStrategy.Replace -> importProducts(
                rows = rows,
                strategy = strategy,
                allowMasterCatalogSync = false
            )
        }
    }

    /**
     * Encola la importación en background con WorkManager.
     */
    fun importProductsInBackground(context: Context, fileUri: Uri) {
        val data = Data.Builder()
            .putString("csv_uri", fileUri.toString())
            .build()
        val request = OneTimeWorkRequestBuilder<CsvImportWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun deleteById(id: Int) = withContext(io) {
        val now = System.currentTimeMillis()
        val product = productDao.getById(id) ?: return@withContext
        val removedQuantity = product.quantity
        db.withTransaction {
            productDao.deleteById(id)
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.PRODUCT.storageKey,
                listOf(id.toLong())
            )
            lastCache = productDao.getAllOnce()
        }
        try {
            remote.deleteById(id)
            saveStockInteractions(
                listOf(
                    StockInteractionEvent(
                        action = "PRODUCT_DELETED",
                        productId = id,
                        productName = product.name,
                        delta = -removedQuantity,
                        reason = StockMovementReasons.MANUAL_ADJUST,
                        note = "Eliminación de producto",
                        source = "STOCK_SCREEN",
                        occurredAtEpochMs = now
                    )
                )
            )
        } catch (t: Throwable) {
            Log.w("ProductRepository", "Error eliminando producto en Firestore", t)
        }
    }

    suspend fun updatePublicStatusByIds(
        productIds: Collection<Int>,
        publicStatus: String
    ): Int = withContext(io) {
        val normalizedStatus = publicStatus.lowercase()
        require(normalizedStatus == "published" || normalizedStatus == "draft") {
            "publicStatus inválido: $publicStatus"
        }
        val uniqueIds = productIds.mapNotNull { id -> id.takeIf { it > 0 } }.distinct()
        if (uniqueIds.isEmpty()) return@withContext 0

        val now = System.currentTimeMillis()
        val changedIds = mutableListOf<Int>()
        db.withTransaction {
            val currentProducts = productDao.getByIds(uniqueIds)
            changedIds += currentProducts
                .filter { it.publicStatus != normalizedStatus }
                .map { it.id }
            if (changedIds.isEmpty()) return@withTransaction

            productDao.updatePublicStatusByIds(
                ids = changedIds,
                publicStatus = normalizedStatus,
                today = LocalDate.now()
            )
            changedIds.forEach { id ->
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = id.toLong(),
                        createdAt = now
                    )
                )
            }
            lastCache = productDao.getAllOnce()
        }
        if (changedIds.isNotEmpty()) {
            trySyncProductsNow(changedIds, now)
        }
        changedIds.size
    }


    // ---------- Sync manual (pull) ----------
    /**
     * Descarga todos los productos desde Firestore y actualiza Room.
     * Estrategia simple: last-write-wins por updatedAt (LocalDate).
     * Si el remoto no tiene id numérico, se inserta local con id autogenerado.
     */
    suspend fun syncDown(): Int = withContext(io) {
        val remoteList = remote.listAll()
        if (remoteList.isEmpty()) {
            return@withContext 0
        }
        try {
            syncDownIncremental(remoteList)
        } catch (t: Throwable) {
            val mustRestoreFromBackup = t is SQLiteConstraintException ||
                t.message?.contains("SQLITE_CONSTRAINT", ignoreCase = true) == true
            if (!mustRestoreFromBackup) {
                throw t
            }
            Log.e("ProductRepository", "Conflicto de unicidad detectado en syncDown. Se ejecuta restauración completa de stock.", t)
            restoreStockFromBackup(remoteList)
        }
    }

    suspend fun applyRemoteDelta(
        remoteList: List<com.example.selliaapp.data.remote.ProductFirestoreMappers.RemoteProduct>,
        deletedIds: Set<Int>
    ): Int = withContext(io) {
        if (deletedIds.isNotEmpty()) {
            db.withTransaction {
                productDao.deleteByIds(deletedIds.toList())
                syncOutboxDao.deleteByTypeAndIds(
                    SyncEntityType.PRODUCT.storageKey,
                    deletedIds.map(Int::toLong)
                )
                lastCache = productDao.getAllOnce()
            }
        }
        if (remoteList.isEmpty()) {
            return@withContext deletedIds.size
        }
        val applied = try {
            syncDownIncremental(remoteList)
        } catch (t: Throwable) {
            val mustRestoreFromBackup = t is SQLiteConstraintException ||
                t.message?.contains("SQLITE_CONSTRAINT", ignoreCase = true) == true
            if (!mustRestoreFromBackup) {
                throw t
            }
            Log.e(
                "ProductRepository",
                "Conflicto de unicidad detectado en delta sync. Se ejecuta restauración de stock.",
                t
            )
            restoreStockFromBackup(remoteList)
        }
        deletedIds.size + applied
    }

    private suspend fun syncDownIncremental(
        remoteList: List<com.example.selliaapp.data.remote.ProductFirestoreMappers.RemoteProduct>
    ): Int {
        var applied = 0
        db.withTransaction {
            val localById = productDao.getAllOnce().associateByTo(mutableMapOf()) { it.id }
            val localByBarcode = localById.values
                .mapNotNull { product -> product.barcode?.takeIf { it.isNotBlank() }?.let { it to product } }
                .toMap(mutableMapOf())
            val localByCode = localById.values
                .mapNotNull { product -> product.code?.takeIf { it.isNotBlank() }?.let { it to product } }
                .toMap(mutableMapOf())

            for (remoteProduct in remoteList) {
                val unifiedEffectiveTransfer = remoteProduct.entity.cashPrice ?: remoteProduct.entity.transferPrice
                val r = remoteProduct.entity.copy(
                    code = remoteProduct.entity.code?.trim()?.ifBlank { null },
                    barcode = remoteProduct.entity.barcode?.trim()?.ifBlank { null },
                    cashPrice = unifiedEffectiveTransfer,
                    transferPrice = unifiedEffectiveTransfer
                )
                val remoteImages = remoteProduct.imageUrls
                val local = localById[r.id]
                    ?: r.barcode?.let { localByBarcode[it] }
                    ?: r.code?.let { localByCode[it] }
                if (local == null) {
                    val newId = productDao.upsert(r.copy(id = 0))
                    if (remoteImages.isNotEmpty()) {
                        replaceProductImages(newId, remoteImages)
                    }
                    applied++
                    if (r.id != newId) remote.upsert(r.copy(id = newId), remoteImages)

                    productDao.getById(newId)?.also { saved ->
                        localById[saved.id] = saved
                        saved.barcode?.let { localByBarcode[it] = saved }
                        saved.code?.let { localByCode[it] = saved }
                    }
                } else {
                    if (r.updatedAt >= local.updatedAt) {
                        val conflictingCode = r.code
                            ?.let { remoteCode -> localByCode[remoteCode] }
                            ?.takeIf { candidate -> candidate.id != local.id }
                        val conflictingBarcode = r.barcode
                            ?.let { remoteBarcode -> localByBarcode[remoteBarcode] }
                            ?.takeIf { candidate -> candidate.id != local.id }

                        val merged = r.copy(
                            id = local.id,
                            code = if (conflictingCode != null) local.code else r.code,
                            barcode = if (conflictingBarcode != null) local.barcode else r.barcode
                        )

                        productDao.update(merged)
                        if (remoteImages.isNotEmpty()) {
                            replaceProductImages(local.id, remoteImages)
                        }
                        applied++

                        productDao.getById(local.id)?.also { saved ->
                            localById[saved.id] = saved
                            localByBarcode.entries.removeAll { (_, value) -> value.id == saved.id }
                            localByCode.entries.removeAll { (_, value) -> value.id == saved.id }
                            saved.barcode?.let { localByBarcode[it] = saved }
                            saved.code?.let { localByCode[it] = saved }
                        }
                    } else {
                        // Evita write-back automático cuando local es más nuevo:
                        // ya existe sync outbox y este push directo amplifica costo en Firestore.
                        syncOutboxDao.upsert(
                            SyncOutboxEntity(
                                entityType = SyncEntityType.PRODUCT.storageKey,
                                entityId = local.id.toLong(),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        return applied
    }

    private suspend fun restoreStockFromBackup(
        remoteList: List<com.example.selliaapp.data.remote.ProductFirestoreMappers.RemoteProduct>
    ): Int {
        require(remoteList.isNotEmpty()) {
            "No existe backup remoto de productos para restaurar el stock."
        }
        val uniqueBackup = remoteList
            .asSequence()
            .map { remoteProduct ->
                val unifiedEffectiveTransfer = remoteProduct.entity.cashPrice ?: remoteProduct.entity.transferPrice
                remoteProduct.copy(
                    entity = remoteProduct.entity.copy(
                        code = remoteProduct.entity.code?.trim()?.ifBlank { null },
                        barcode = remoteProduct.entity.barcode?.trim()?.ifBlank { null },
                        cashPrice = unifiedEffectiveTransfer,
                        transferPrice = unifiedEffectiveTransfer
                    )
                )
            }
            .sortedByDescending { it.entity.updatedAt }
            .distinctBy { item ->
                when {
                    !item.entity.code.isNullOrBlank() -> "code:${item.entity.code}"
                    !item.entity.barcode.isNullOrBlank() -> "barcode:${item.entity.barcode}"
                    item.entity.id != 0 -> "id:${item.entity.id}"
                    else -> "name:${item.entity.name.lowercase()}"
                }
            }
            .toList()

        db.withTransaction {
            productDao.deleteAll()
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.PRODUCT.storageKey,
                syncOutboxDao.getByType(SyncEntityType.PRODUCT.storageKey).map { it.entityId }
            )
            uniqueBackup.forEach { remoteProduct ->
                val restoredId = productDao.insert(remoteProduct.entity.copy(id = 0)).toInt()
                if (remoteProduct.imageUrls.isNotEmpty()) {
                    replaceProductImages(restoredId, remoteProduct.imageUrls)
                }
            }
            lastCache = productDao.getAllOnce()
        }
        return uniqueBackup.size
    }
    // ---------- WRAPPERS que espera la UI / ViewModel ----------

    /** Búsqueda reactiva por texto libre (nombre, código, barcode). */
    fun search(q: String?): Flow<List<ProductEntity>> = productDao.search(q)

    /** Listado reactivo de categorías distintas. */
    fun distinctCategories(): Flow<List<String>> = productDao.distinctCategories()

    /** Listado reactivo de proveedores distintos. */
    fun distinctProviders(): Flow<List<String>> = productDao.distinctProviders()

    /** Top-N de productos con stock crítico para el dashboard. */
    fun lowStockAlerts(limit: Int = 5): Flow<List<LowStockProduct>> =
        productDao.observeLowStock(limit)

    /** Alta de producto (alias más semántico para la UI). */
    suspend fun addProduct(p: ProductEntity): Int = insert(p)

    /** Actualización de producto (alias más semántico para la UI). */
    suspend fun updateProduct(p: ProductEntity): Int = update(p)

    /** Obtener producto por código de barras. */
    suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? = withContext(io) {
        val product = productDao.getByBarcodeOnce(barcode) ?: return@withContext null
        product.copy(imageUrls = loadProductImages(product.id))
    }

    /** Obtener producto por código interno. */
    suspend fun getByCodeOrNull(code: String): ProductEntity? = productDao.getByCodeOnce(code)

    suspend fun getGlobalBarcodeMatch(barcode: String): IProductRepository.GlobalBarcodeMatch? = withContext(io) {
        runCatching { crossCatalogRemote.findByBarcode(barcode) }
            .onFailure { error ->
                Log.w("ProductRepository", "Lookup CROSS falló para barcode=$barcode", error)
            }
            .getOrNull()
            ?.let { entry ->
                IProductRepository.GlobalBarcodeMatch(
                    barcode = entry.barcode,
                    name = entry.name,
                    brand = entry.brand
                )
            }
    }

    /** Obtener producto por id (alias semántico). */
    suspend fun getByIdOrNull(id: Int): ProductEntity? = getById(id)

    /** (Opcional) Obtener por nombre, por compatibilidad con flujos antiguos. */
    suspend fun getByNameOrNull(name: String): ProductEntity? = withContext(io) {
        val product = productDao.getByNameOnce(name) ?: return@withContext null
        product.copy(imageUrls = loadProductImages(product.id))
    }

    // ---------- Paging (expuesto para pantallas que lo necesiten) ----------
    fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>> = pagingSearch(query)

    fun getProducts(): Flow<List<ProductEntity>> =
        observeAll()
            .map { list ->
                lastCache = list
                list
            }

    fun observeStockMovements(productId: Int, limit: Int = 20): Flow<List<StockMovementWithProduct>> =
        stockMovementDao.observeByProductDetailed(productId, limit)

    fun observeRecentStockMovements(limit: Int = 50): Flow<List<StockMovementWithProduct>> =
        stockMovementDao.observeRecentDetailed(limit)

    suspend fun recalculateAutoPricingForAll(
        reason: String = "Pricing config updated",
        changedBy: String = "System",
        source: String = "PRICING_CONFIG"
    ): Int = withContext(io) {
        val now = System.currentTimeMillis()
        val updatedIds = mutableListOf<Int>()
        val priceAudits = mutableListOf<ProductPriceAuditEntity>()
        val interactionEvents = mutableListOf<StockInteractionEvent>()
        db.withTransaction {
            val all = productDao.getAllOnce()
            all.forEach { product ->
                if (!product.autoPricing || product.purchasePrice == null) return@forEach
                val priced = applyAutoPricing(product, product, force = true)
                if (priced != product) {
                    val listChanged = priced.listPrice != product.listPrice
                    val cashChanged = priced.cashPrice != product.cashPrice
                    val transferChanged = priced.transferPrice != product.transferPrice
                    val mlChanged = priced.mlPrice != product.mlPrice
                    val ml3Changed = priced.ml3cPrice != product.ml3cPrice
                    val ml6Changed = priced.ml6cPrice != product.ml6cPrice
                    if (listChanged || cashChanged || transferChanged || mlChanged || ml3Changed || ml6Changed) {
                        priceAudits += ProductPriceAuditEntity(
                            productId = product.id,
                            productName = product.name,
                            purchasePrice = product.purchasePrice,
                            oldListPrice = product.listPrice,
                            newListPrice = priced.listPrice,
                            oldCashPrice = product.cashPrice,
                            newCashPrice = priced.cashPrice,
                            oldTransferPrice = product.transferPrice,
                            newTransferPrice = priced.transferPrice,
                            oldMlPrice = product.mlPrice,
                            newMlPrice = priced.mlPrice,
                            oldMl3cPrice = product.ml3cPrice,
                            newMl3cPrice = priced.ml3cPrice,
                            oldMl6cPrice = product.ml6cPrice,
                            newMl6cPrice = priced.ml6cPrice,
                            reason = reason,
                            changedBy = changedBy,
                            source = source,
                            changedAt = Instant.ofEpochMilli(now)
                        )
                        interactionEvents += StockInteractionEvent(
                            action = "PRODUCT_PRICE_RECALCULATED",
                            productId = product.id,
                            productName = product.name,
                            delta = 0,
                            reason = StockMovementReasons.PRICING_RECALC,
                            note = "Lista ${product.listPrice}→${priced.listPrice}, Efectivo/Transferencia ${(product.cashPrice ?: product.transferPrice)}→${priced.cashPrice}",
                            source = source,
                            occurredAtEpochMs = now
                        )
                    }
                    productDao.update(priced.copy(updatedAt = LocalDate.now()))
                    updatedIds += product.id
                }
            }
            if (priceAudits.isNotEmpty()) {
                productPriceAuditDao.insertAll(priceAudits)
            }
            lastCache = productDao.getAllOnce()
        }
        if (updatedIds.isNotEmpty()) {
            trySyncProductsNow(updatedIds, now)
            saveStockInteractions(interactionEvents)
        }
        updatedIds.size
    }

    /**
     * Aumenta (o disminuye si delta < 0) el stock de un producto identificado por su barcode.
     *
     * @return true si se actualizó, false si no se encontró el producto.
     */
    suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean = withContext(io) {
        if (delta == 0) return@withContext true
        val product = productDao.getByBarcodeOnce(barcode) ?: return@withContext false
        adjustStockInternal(
            productId = product.id,
            delta = delta,
            reason = StockMovementReasons.SCAN_ADJUST,
            note = "Ajuste por escaneo ($barcode)"
        )
    }

    suspend fun adjustStock(
        productId: Int,
        delta: Int,
        reason: String,
        note: String? = null
    ): Boolean = withContext(io) {
        adjustStockInternal(productId, delta, reason, note)
    }

    suspend fun adjustStock(
        productId: Int,
        delta: Int,
        reason: StockAdjustmentReason,
        note: String? = null
    ): Boolean = adjustStock(productId, delta, reason.code, note)

    private suspend fun persistProduct(entity: ProductEntity, reason: String): Int {
        val normalized = entity.copy(id = 0, updatedAt = LocalDate.now())
        val now = System.currentTimeMillis()
        val skuPrefix = resolveSkuPrefix()
        var newId = 0
        db.withTransaction {
            assertCodeAvailable(normalized.code, currentId = null)
            val priced = applyAutoPricing(normalized)
            val prepared = ensureAutoCodes(priced, prefix = skuPrefix)
            assertCodeAvailable(prepared.code, currentId = null)
            newId = productDao.upsert(prepared)
            replaceProductImages(newId, prepared.imageUrls)
            if (prepared.quantity != 0) {
                stockMovementDao.insert(
                    StockMovementEntity(
                        productId = newId,
                        delta = prepared.quantity,
                        reason = reason,
                        ts = Instant.ofEpochMilli(now),
                        note = "Alta de producto"
                    )
                )
            }
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    entityType = SyncEntityType.PRODUCT.storageKey,
                    entityId = newId.toLong(),
                    createdAt = now
                )
            )
            lastCache = productDao.getAllOnce()
        }
        trySyncProductsNow(listOf(newId), now)
        saveStockInteractions(
            listOf(
                StockInteractionEvent(
                    action = "PRODUCT_CREATED",
                    productId = newId,
                    productName = normalized.name,
                    delta = normalized.quantity,
                    reason = reason,
                    note = "Alta de producto",
                    source = "STOCK_SCREEN",
                    occurredAtEpochMs = now
                )
            )
        )
        return newId
    }

    private suspend fun ensureAutoCodes(entity: ProductEntity, prefix: String): ProductEntity {
        val existingCode = entity.code?.trim()?.takeIf { it.isNotBlank() }
        val existingBarcode = entity.barcode?.trim()?.takeIf { it.isNotBlank() }
        var code = existingCode
        if (code == null) {
            val offset = prefix.length + 1
            var next = (productDao.getMaxSequenceForCode(prefix, offset) ?: 0) + 1
            while (true) {
                val candidate = "$prefix$next"
                if (productDao.getByCodeOnce(candidate) == null) {
                    code = candidate
                    break
                }
                next += 1
            }
        }
        val barcode = existingBarcode ?: code
        return entity.copy(code = code, barcode = barcode)
    }

    private suspend fun resolveSkuPrefix(): String {
        cachedSkuPrefix?.let { return it }
        val tenantId = runCatching { tenantProvider.requireTenantId() }.getOrNull()
        if (tenantId.isNullOrBlank()) {
            cachedSkuPrefix = "VLK"
            return "VLK"
        }

        tenantSkuConfigDao.getByTenantId(tenantId)?.let { cached ->
            cachedSkuPrefix = cached.skuPrefix
            return cached.skuPrefix
        }

        val now = System.currentTimeMillis()
        val tenantRef = firestore.collection("tenants").document(tenantId)
        val snapshot = runCatching { tenantRef.get().await() }.getOrNull()
        val remoteName = snapshot?.getString("name").orEmpty()
        val existingRemotePrefix = snapshot?.getString("skuPrefix")?.normalizeSkuPrefixOrNull()
        val prefix = existingRemotePrefix ?: deriveSkuPrefixFromStoreName(remoteName)

        if (existingRemotePrefix == null) {
            runCatching {
                tenantRef.set(mapOf("skuPrefix" to prefix), SetOptions.merge()).await()
            }
        }

        tenantSkuConfigDao.upsert(
            TenantSkuConfigEntity(
                tenantId = tenantId,
                storeName = remoteName.ifBlank { "Tienda" },
                skuPrefix = prefix,
                updatedAtEpochMs = now
            )
        )
        cachedSkuPrefix = prefix
        return prefix
    }

    private fun deriveSkuPrefixFromStoreName(storeName: String): String {
        val normalized = storeName
            .uppercase()
            .replace("[^A-Z0-9]".toRegex(), "")
        return normalized.take(3).padEnd(3, 'X')
    }

    private fun String.normalizeSkuPrefixOrNull(): String? {
        val normalized = uppercase().replace("[^A-Z0-9]".toRegex(), "").take(6)
        return normalized.takeIf { it.length >= 3 }
    }

    private suspend fun updateProductInternal(entity: ProductEntity, reason: String): Int {
        val now = System.currentTimeMillis()
        var rows = 0
        db.withTransaction {
            val current = productDao.getById(entity.id) ?: return@withTransaction
            assertCodeAvailable(entity.code, currentId = current.id)
            val normalized = entity.copy(updatedAt = LocalDate.now())
            val purchaseChanged = current.purchasePrice != normalized.purchasePrice
            val gainTargetChanged = current.gainTargetPercent != normalized.gainTargetPercent
            val shouldForceRecalculation = current.autoPricing && (purchaseChanged || gainTargetChanged)
            val priced = when {
                shouldForceRecalculation -> applyAutoPricing(normalized, current, force = true)
                else -> applyAutoPricing(normalized, current)
            }
            rows = productDao.update(priced)
            if (rows > 0) {
                replaceProductImages(current.id, priced.imageUrls)
                val delta = priced.quantity - current.quantity
                if (delta != 0) {
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = current.id,
                            delta = delta,
                            reason = reason,
                            ts = Instant.ofEpochMilli(now),
                            note = "Edición manual"
                        )
                    )
                }
                syncOutboxDao.upsert(
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = current.id.toLong(),
                        createdAt = now
                    )
                )
                lastCache = productDao.getAllOnce()
            }
        }
        if (rows > 0) {
            trySyncProductsNow(listOf(entity.id), now)
            saveStockInteractions(
                listOf(
                    StockInteractionEvent(
                        action = "PRODUCT_UPDATED",
                        productId = entity.id,
                        productName = entity.name,
                        delta = 0,
                        reason = reason,
                        note = "Edición manual",
                        source = "STOCK_SCREEN",
                        occurredAtEpochMs = now
                    )
                )
            )
        }
        return rows
    }

    private suspend fun assertCodeAvailable(code: String?, currentId: Int?) {
        val normalized = code?.trim()?.takeIf { it.isNotBlank() } ?: return
        val existing = productDao.getByCodeOnce(normalized) ?: return
        if (currentId == null || existing.id != currentId) {
            throw IllegalArgumentException("El código \"$normalized\" ya existe.")
        }
    }

    private suspend fun adjustStockInternal(
        productId: Int,
        delta: Int,
        reason: String,
        note: String?
    ): Boolean {
        if (delta == 0) return true
        val now = System.currentTimeMillis()
        var success = false
        db.withTransaction {
            val product = productDao.getById(productId) ?: return@withTransaction
            val newQty = (product.quantity + delta).coerceAtLeast(0)
            val affected = productDao.update(
                product.copy(quantity = newQty, updatedAt = LocalDate.now())
            )
            if (affected == 0) return@withTransaction
            if (product.imageUrls.isNotEmpty()) {
                replaceProductImages(productId, product.imageUrls)
            }
            stockMovementDao.insert(
                StockMovementEntity(
                    productId = productId,
                    delta = delta,
                    reason = reason,
                    ts = Instant.ofEpochMilli(now),
                    note = note
                )
            )
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    entityType = SyncEntityType.PRODUCT.storageKey,
                    entityId = productId.toLong(),
                    createdAt = now
                )
            )
            lastCache = productDao.getAllOnce()
            success = true
        }
        if (success) {
            trySyncProductsNow(listOf(productId), now)
            val productName = productDao.getById(productId)?.name
            saveStockInteractions(
                listOf(
                    StockInteractionEvent(
                        action = "STOCK_ADJUSTED",
                        productId = productId,
                        productName = productName,
                        delta = delta,
                        reason = reason,
                        note = note,
                        source = "STOCK_OPERATION",
                        occurredAtEpochMs = now
                    )
                )
            )
        }
        return success
    }

    private suspend fun trySyncProductsNow(ids: Collection<Int>, now: Long) {
        val uniqueIds = ids.mapNotNull { id -> id.takeIf { it > 0 } }.distinct()
        if (uniqueIds.isEmpty()) return
        val entities = productDao.getByIds(uniqueIds)
        if (entities.isEmpty()) return
        try {
            val imageUrlsByProductId = loadProductImagesByProductId(uniqueIds)
            remote.upsertAll(entities, imageUrlsByProductId)
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.PRODUCT.storageKey,
                uniqueIds.map(Int::toLong)
            )
        } catch (t: Throwable) {
            val error = t.message?.take(512) ?: t::class.java.simpleName
            syncOutboxDao.markAttempt(
                SyncEntityType.PRODUCT.storageKey,
                uniqueIds.map(Int::toLong),
                now,
                error
            )
            Log.w(
                "ProductRepository",
                "Fallo al sincronizar ${uniqueIds.joinToString()} con Firestore",
                t
            )
        }
    }

    private fun issueToLegacyError(issue: ImportRowIssue): String = buildString {
        append("Línea ${issue.line}")
        issue.productName?.let { append(" | Producto: \"$it\"") }
        issue.skuOrBarcode?.let { append(" | SKU/Barcode: $it") }
        append(" | Estado: ${issue.status}")
        append(" | Acción: ${issue.attemptedAction}")
        append(" | Motivo: ${issue.userMessage}")
        append(" | Sugerencia: ${issue.suggestion}")
        append(" | Técnico: ${issue.technicalReason}")
    }



    private fun isCsvOrXlsxImport(context: Context, uri: Uri): Boolean {
        val uriString = uri.toString().lowercase()
        if (uriString.endsWith(".csv") || uriString.endsWith(".xlsx")) return true

        val mimeType = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.lowercase()
            .orEmpty()

        return mimeType == "text/csv" ||
            mimeType == "application/csv" ||
            mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    private suspend fun syncToCrossCatalog(barcode: String?, name: String, brand: String?): Boolean {
        val normalizedBarcode = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return false

        if (!canWriteMasterCrossCatalog()) {
            Log.i(
                "ProductRepository",
                "Se omite sync de catálogo CROSS para barcode=$normalizedBarcode: usuario sin rol admin"
            )
            return false
        }

        val audit = runCatching { buildCrossCatalogAuditContext() }
            .onFailure { error ->
                Log.w("ProductRepository", "No se pudo construir metadata de auditoría CROSS", error)
            }
            .getOrNull() ?: return false

        return runCatching {
            crossCatalogRemote.upsertByBarcode(
                rawBarcode = normalizedBarcode,
                name = normalizedName,
                brand = brand,
                audit = audit
            )
            true
        }.onFailure { error ->
            val reason = when (error) {
                is InvalidCrossCatalogDataException -> StockMovementReasons.CSV_IMPORT
                else -> StockMovementReasons.MANUAL_ADJUST
            }
            Log.w(
                "ProductRepository",
                "No se pudo sincronizar el catálogo CROSS para barcode=$normalizedBarcode",
                error
            )
            saveStockInteractions(
                listOf(
                    StockInteractionEvent(
                        action = "CROSS_CATALOG_SYNC_ERROR",
                        productId = 0,
                        productName = normalizedName,
                        delta = 0,
                        reason = reason,
                        note = "barcode=$normalizedBarcode · ${error.message}",
                        source = "CROSS_CATALOG",
                        occurredAtEpochMs = System.currentTimeMillis(),
                        actorUid = audit.updatedByUid
                    )
                )
            )
        }.getOrElse { false }
    }

    private suspend fun canWriteMasterCrossCatalog(): Boolean {
        val now = System.currentTimeMillis()
        crossCatalogWriteAccessCache?.let { (cachedAt, allowed) ->
            if (now - cachedAt <= 5 * 60_000L) return allowed
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return false.also {
            crossCatalogWriteAccessCache = now to false
        }

        val tokenAllowed = runCatching {
            val claims = user.getIdToken(false).await().claims
            (claims["admin"] as? Boolean) == true ||
                (claims["isAdmin"] as? Boolean) == true ||
                (claims["isSuperAdmin"] as? Boolean) == true ||
                (claims["role"] as? String)?.equals("owner", ignoreCase = true) == true
        }.getOrDefault(false)

        if (tokenAllowed) {
            crossCatalogWriteAccessCache = now to true
            return true
        }

        val docAllowed = runCatching {
            val snapshot = firestore.collection("users").document(user.uid).get().await()
            if (!snapshot.exists()) return@runCatching false
            val role = snapshot.getString("role")?.lowercase()
            role == "owner" ||
                snapshot.getBoolean("isAdmin") == true ||
                snapshot.getBoolean("isSuperAdmin") == true
        }.getOrDefault(false)

        crossCatalogWriteAccessCache = now to docAllowed
        return docAllowed
    }

    private suspend fun buildCrossCatalogAuditContext(): CrossCatalogAuditContext {
        val tenantId = tenantProvider.requireTenantId()
        val cachedTenantConfig = tenantSkuConfigDao.getByTenantId(tenantId)
        val user = FirebaseAuth.getInstance().currentUser
        return CrossCatalogAuditContext(
            tenantId = tenantId,
            storeName = cachedTenantConfig?.storeName,
            updatedByUid = user?.uid,
            updatedByEmail = user?.email
        )
    }

    private suspend fun saveStockInteractions(events: List<StockInteractionEvent>) {
        if (events.isEmpty()) return
        runCatching { stockInteractionRemote.save(events) }
            .onFailure { error ->
                Log.w(
                    "ProductRepository",
                    "No se pudo guardar la interacción de stock en Firestore",
                    error
                )
            }
    }

    private suspend fun loadProductImages(productId: Int): List<String> {
        val images = productImageDao.getByProductId(productId)
            .sortedBy { it.position }
            .map { it.url }
        return images
    }

    private suspend fun loadProductImagesByProductId(productIds: List<Int>): Map<Int, List<String>> {
        if (productIds.isEmpty()) return emptyMap()
        val images = productImageDao.getByProductIds(productIds)
        return images.groupBy { it.productId }
            .mapValues { (_, items) -> items.sortedBy { it.position }.map { it.url } }
    }

    private suspend fun replaceProductImages(productId: Int, urls: List<String>) {
        val normalized = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        productImageDao.deleteByProductId(productId)
        if (normalized.isEmpty()) return
        val entities = normalized.mapIndexed { index, url ->
            ProductImageEntity(
                productId = productId,
                url = url,
                position = index
            )
        }
        productImageDao.insertAll(entities)
    }

    private suspend fun attachImages(products: List<ProductEntity>): List<ProductEntity> {
        if (products.isEmpty()) return products
        val imagesById = loadProductImagesByProductId(products.map { it.id })
        return products.map { product ->
            product.copy(imageUrls = imagesById[product.id].orEmpty())
        }
    }

}
