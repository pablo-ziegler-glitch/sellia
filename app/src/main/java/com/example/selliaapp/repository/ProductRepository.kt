package com.example.selliaapp.repository

import android.content.ContentResolver
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
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.ProductImageEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.mappers.toModel
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.Product
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.data.model.stock.StockAdjustmentReason
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.model.stock.StockMovementWithProduct
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.ProductRemoteDataSource
import com.example.selliaapp.data.remote.StockInteractionEvent
import com.example.selliaapp.data.remote.StockInteractionRemoteDataSource
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.pricing.PricingCalculator
import com.example.selliaapp.sync.CsvImportWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val pricingConfigRepository: PricingConfigRepository,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @IoDispatcher private val io: CoroutineDispatcher   // <-- igual que en el VM
) {

    // ---------- Cache simple en memoria ----------
    @Volatile private var lastCache: List<ProductEntity> = emptyList()

    private val stockMovementDao = db.stockMovementDao()
    private val syncOutboxDao = db.syncOutboxDao()
    private val remote = ProductRemoteDataSource(firestore, tenantProvider)
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
        val purchasePrice = incoming.purchasePrice ?: existing?.purchasePrice ?: return incoming
        val hasManualPrices = listOf(
            incoming.listPrice,
            incoming.cashPrice,
            incoming.transferPrice,
            incoming.price
        ).any { it != null }
        val shouldAuto = when {
            force -> true
            hasManualPrices -> false
            incoming.autoPricing -> true
            existing != null -> existing.autoPricing
            else -> true
        }
        if (!shouldAuto) {
            return incoming.copy(autoPricing = false)
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
            mlShippingTiers = mlShippingTiers
        )
        return incoming.copy(
            listPrice = result.listPrice,
            cashPrice = result.cashPrice,
            transferPrice = result.transferPrice,
            transferNetPrice = result.transferNetPrice,
            mlPrice = result.mlPrice,
            ml3cPrice = result.ml3cPrice,
            ml6cPrice = result.ml6cPrice,
            price = incoming.price ?: result.listPrice,
            autoPricing = true
        )
    }

    // ---------- Importación tabular: bulkUpsert desde filas parseadas ----------
    suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) = withContext(io) {
        if (rows.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val touchedIds = mutableSetOf<Int>()
        val interactionEvents = mutableListOf<StockInteractionEvent>()

        db.withTransaction {
            rows.forEach { r ->
                val updated = r.updatedAt ?: LocalDate.now()
                val existing = when {
                    !r.barcode.isNullOrBlank() -> productDao.getByBarcodeOnce(r.barcode!!)
                    !r.name.isNullOrBlank() -> productDao.getByNameOnce(r.name)
                    else -> null
                }
                val beforeQty = existing?.quantity ?: 0
                val incoming = ProductEntity(
                    code = r.code,
                    barcode = r.barcode,
                    name = r.name,
                    purchasePrice = r.purchasePrice,
                    price = r.price,
                    listPrice = r.listPrice,
                    cashPrice = r.cashPrice,
                    transferPrice = r.transferPrice,
                    transferNetPrice = r.transferNetPrice,
                    mlPrice = r.mlPrice,
                    ml3cPrice = r.ml3cPrice,
                    ml6cPrice = r.ml6cPrice,
                    autoPricing = false,
                    quantity = max(0, r.quantity),
                    description = r.description,
                    imageUrl = r.imageUrl,
                    imageUrls = r.imageUrls,
                    categoryId = existing?.categoryId,
                    providerId = existing?.providerId,
                    providerName = existing?.providerName,
                    providerSku = existing?.providerSku,
                    category = r.category ?: existing?.category,
                    minStock = r.minStock?.let { max(0, it) } ?: existing?.minStock,
                    updatedAt = updated
                )

                val priced = applyAutoPricing(incoming, existing)
                val prepared = if (existing == null) ensureAutoCodes(priced) else priced
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
        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()

        // Simulación simple: contamos por barcode/nombre sin tocar DB
        val already = cachedOrEmpty()
        for ((idx, r) in rows.withIndex()) {
            try {
                val exists = when {
                    !r.barcode.isNullOrBlank() -> already.any { it.barcode == r.barcode }
                    else                       -> already.any { it.name.equals(r.name, ignoreCase = true) }
                }
                if (exists) updated++ else inserted++
            } catch (e: Exception) {
                errors += "Línea ${idx + 2}: ${e.message}"
            }
        }
        ImportResult(inserted, updated, errors)
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
        importProducts(rows, strategy)
    }

    suspend fun importProductsFromTable(
        table: List<List<String>>,
        strategy: ImportStrategy
    ): ImportResult = withContext(io) {
        val rows = ProductCsvImporter.parseTable(table)
        importProducts(rows, strategy)
    }

    private suspend fun importProducts(
        rows: List<ProductCsvImporter.Row>,
        strategy: ImportStrategy
    ): ImportResult {
        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val touchedIds = mutableSetOf<Int>()
        val now = System.currentTimeMillis()
        val seenCodes = mutableSetOf<String>()
        val interactionEvents = mutableListOf<StockInteractionEvent>()

        db.withTransaction {
            rows.forEachIndexed { idx, r ->
                try {
                    val lineNumber = idx + 2
                    val normalizedCode = r.code?.trim()?.takeIf { it.isNotBlank() }
                    if (normalizedCode != null && !seenCodes.add(normalizedCode)) {
                        errors += "Línea $lineNumber: el código \"$normalizedCode\" está duplicado en el archivo."
                        return@forEachIndexed
                    }
                    val existing = when {
                        !r.barcode.isNullOrBlank() -> productDao.getByBarcodeOnce(r.barcode!!)
                        else                       -> productDao.getByNameOnce(r.name)
                    }
                    if (normalizedCode != null) {
                        val codeOwner = productDao.getByCodeOnce(normalizedCode)
                        if (codeOwner != null && (existing == null || codeOwner.id != existing.id)) {
                            errors += "Línea $lineNumber: el código \"$normalizedCode\" ya existe."
                            return@forEachIndexed
                        }
                    }

                    if (existing == null) {
                        val p = ProductEntity(
                            code = normalizedCode,
                            barcode = r.barcode,
                            name = r.name,
                            purchasePrice = r.purchasePrice,
                            price = r.price, // legacy
                            listPrice = r.listPrice,
                            cashPrice = r.cashPrice,
                            transferPrice = r.transferPrice,
                            transferNetPrice = r.transferNetPrice,
                            mlPrice = r.mlPrice,
                            ml3cPrice = r.ml3cPrice,
                            ml6cPrice = r.ml6cPrice,
                            autoPricing = false,
                            quantity = max(0, r.quantity),
                            description = r.description,
                            imageUrl = r.imageUrl,
                            imageUrls = r.imageUrls,
                            categoryId = null,                      // si querés, usar ensureCategoryId(r.category)
                            providerId = null,                      // si en el futuro sumamos CSV con proveedor
                            providerName = r.providerName,
                            providerSku = r.providerSku,
                            category = r.category,
                            minStock = r.minStock?.let { max(0, it) },
                            updatedAt = r.updatedAt ?: LocalDate.now()
                        )
                        val priced = applyAutoPricing(p, existing)
                        val prepared = ensureAutoCodes(priced)
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
                        inserted++
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
                    } else {
                        val newQty = when (strategy) {
                            ImportStrategy.Append  -> existing.quantity + max(0, r.quantity)
                            ImportStrategy.Replace -> max(0, r.quantity)
                        }
                        val merged = existing.copy(
                            code        = normalizedCode ?: existing.code,
                            barcode     = r.barcode ?: existing.barcode,
                            name        = r.name.ifBlank { existing.name },
                            purchasePrice = r.purchasePrice ?: existing.purchasePrice,
                            price       = r.price ?: existing.price,
                            listPrice   = r.listPrice ?: existing.listPrice,
                            cashPrice   = r.cashPrice ?: existing.cashPrice,
                            transferPrice = r.transferPrice ?: existing.transferPrice,
                            transferNetPrice = r.transferNetPrice ?: existing.transferNetPrice,
                            mlPrice     = r.mlPrice ?: existing.mlPrice,
                            ml3cPrice   = r.ml3cPrice ?: existing.ml3cPrice,
                            ml6cPrice   = r.ml6cPrice ?: existing.ml6cPrice,
                            autoPricing = existing.autoPricing,
                            quantity    = newQty,
                            description = r.description ?: existing.description,
                            imageUrl    = r.imageUrl ?: existing.imageUrl,
                            imageUrls   = if (r.imageUrls.isEmpty()) existing.imageUrls else r.imageUrls,
                            category    = r.category ?: existing.category,
                            providerName = r.providerName ?: existing.providerName,
                            providerSku  = r.providerSku ?: existing.providerSku,
                            minStock    = r.minStock ?: existing.minStock,
                            updatedAt   = r.updatedAt ?: LocalDate.now()
                        )
                        val priced = applyAutoPricing(merged, existing)
                        assertCodeAvailable(priced.code, currentId = existing.id)
                        productDao.update(priced)
                        touchedIds += existing.id
                        if (r.imageUrls.isNotEmpty()) {
                            replaceProductImages(existing.id, r.imageUrls)
                        }
                        val delta = newQty - existing.quantity
                        if (delta != 0) {
                            stockMovementDao.insert(
                                StockMovementEntity(
                                    productId = existing.id,
                                    delta = delta,
                                    reason = StockMovementReasons.CSV_IMPORT,
                                    ts = Instant.ofEpochMilli(now),
                                    note = "Importación CSV (${strategy.name.lowercase()})"
                                )
                            )
                        }
                        syncOutboxDao.upsert(
                            SyncOutboxEntity(
                                entityType = SyncEntityType.PRODUCT.storageKey,
                                entityId = existing.id.toLong(),
                                createdAt = now
                            )
                        )
                        updated++
                        interactionEvents += StockInteractionEvent(
                            action = "PRODUCT_UPDATED",
                            productId = existing.id,
                            productName = priced.name,
                            delta = newQty - existing.quantity,
                            reason = StockMovementReasons.CSV_IMPORT,
                            note = "Importación CSV (${strategy.name.lowercase()})",
                            source = "CSV_IMPORT",
                            occurredAtEpochMs = now
                        )
                    }
                } catch (e: Exception) {
                    errors += "Línea ${idx + 2}: ${e.message}"
                }
            }
            lastCache = productDao.getAllOnce()
        }
        trySyncProductsNow(touchedIds, now)
        saveStockInteractions(interactionEvents)
        return ImportResult(inserted, updated, errors)
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
        val importer = ProductCsvImporter(productDao, productImageDao)
        return when (strategy) {
            ImportStrategy.Append -> importer.importAppend(resolver, uri)
            // Si realmente querés "UpsertByBarcode", agregalo al enum:
            // ImportStrategy.UpsertByBarcode -> importer.importUpsertByBarcode(resolver, uri)
            ImportStrategy.Replace -> {
                // Si Replace para vos significa "reemplazar stock" en existentes,
                // podés reutilizar importProductsFromFile con tu estrategia Replace:
                // (necesitarías un Context; si no lo tenés acá, dejá sólo Append/Upsert y remové Replace)
                ImportResult(0, 0, listOf("Replace no soportado en este método. Usá importProductsFromFile(...)"))
            }
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
        db.withTransaction {
            productDao.deleteById(id)
            if (product.quantity != 0) {
                stockMovementDao.insert(
                    StockMovementEntity(
                        productId = id,
                        delta = -product.quantity,
                        reason = StockMovementReasons.MANUAL_ADJUST,
                        ts = Instant.ofEpochMilli(now),
                        note = "Eliminación de producto"
                    )
                )
            }
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
                        delta = -product.quantity,
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


    // ---------- Sync manual (pull) ----------
    /**
     * Descarga todos los productos desde Firestore y actualiza Room.
     * Estrategia simple: last-write-wins por updatedAt (LocalDate).
     * Si el remoto no tiene id numérico, se inserta local con id autogenerado.
     */
    suspend fun syncDown(): Int = withContext(io) {
        val remoteList = remote.listAll()
        var applied = 0
        db.withTransaction {
            val localAll = productDao.getAllOnce().associateBy { it.id to (it.barcode ?: "") }
            for (remoteProduct in remoteList) {
                val r = remoteProduct.entity
                val remoteImages = remoteProduct.imageUrls
                val local = when {
                    r.id != 0 -> localAll[r.id to (r.barcode ?: "")]
                    !r.barcode.isNullOrBlank() -> localAll.entries.firstOrNull { it.key.second == r.barcode }?.value
                    else -> null
                }
                if (local == null) {
                    // insertar
                    val newId = productDao.upsert(r.copy(id = 0))
                    if (remoteImages.isNotEmpty()) {
                        replaceProductImages(newId, remoteImages)
                    }
                    applied++
                    // si el docId no coincide, subimos de vuelta con el id real para alinear
                    if (r.id != newId) remote.upsert(r.copy(id = newId), remoteImages)
                } else {
                    // resolver por updatedAt
                    if (r.updatedAt >= local.updatedAt) {
                        productDao.update(r.copy(id = local.id))
                        if (remoteImages.isNotEmpty()) {
                            replaceProductImages(local.id, remoteImages)
                        }
                        applied++
                    } else {
                        // Local es más nuevo → subir local para ganar en remoto
                        remote.upsert(local, loadProductImages(local.id))
                    }
                }
            }
        }
        applied
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

    suspend fun recalculateAutoPricingForAll(): Int = withContext(io) {
        val now = System.currentTimeMillis()
        val updatedIds = mutableListOf<Int>()
        db.withTransaction {
            val all = productDao.getAllOnce()
            all.forEach { product ->
                if (!product.autoPricing || product.purchasePrice == null) return@forEach
                val priced = applyAutoPricing(product, product, force = true)
                if (priced != product) {
                    productDao.update(priced.copy(updatedAt = LocalDate.now()))
                    updatedIds += product.id
                }
            }
            lastCache = productDao.getAllOnce()
        }
        if (updatedIds.isNotEmpty()) {
            trySyncProductsNow(updatedIds, now)
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
        var newId = 0
        db.withTransaction {
            assertCodeAvailable(normalized.code, currentId = null)
            val priced = applyAutoPricing(normalized)
            val prepared = ensureAutoCodes(priced)
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

    private suspend fun ensureAutoCodes(entity: ProductEntity): ProductEntity {
        val existingCode = entity.code?.trim()?.takeIf { it.isNotBlank() }
        val existingBarcode = entity.barcode?.trim()?.takeIf { it.isNotBlank() }
        var code = existingCode
        if (code == null) {
            val prefix = "VLK"
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

    private suspend fun updateProductInternal(entity: ProductEntity, reason: String): Int {
        val now = System.currentTimeMillis()
        var rows = 0
        db.withTransaction {
            val current = productDao.getById(entity.id) ?: return@withTransaction
            assertCodeAvailable(entity.code, currentId = current.id)
            val normalized = entity.copy(updatedAt = LocalDate.now())
            val purchaseChanged = current.purchasePrice != normalized.purchasePrice
            val priced = when {
                purchaseChanged && current.autoPricing -> applyAutoPricing(normalized, current, force = true)
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
