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
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.mappers.toModel
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.Product
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.data.model.stock.ReorderSuggestion
import com.example.selliaapp.data.model.stock.StockAdjustmentReason
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.model.stock.StockMovementWithProduct
import com.example.selliaapp.data.remote.ProductRemoteDataSource
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.sync.CsvImportWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.ceil


/**
 * Repository de productos.
 * - Acceso a Room.
 * - Importación de archivos tabulares (dry-run + background WorkManager).
 * - Helpers de precios (E4) y normalización de categoría/proveedor.
 */
class ProductRepository(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val providerDao: ProviderDao,
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher   // <-- igual que en el VM
) {

    // ---------- Cache simple en memoria ----------
    @Volatile private var lastCache: List<ProductEntity> = emptyList()

    private val stockMovementDao = db.stockMovementDao()
    private val syncOutboxDao = db.syncOutboxDao()
    private val remote = ProductRemoteDataSource(firestore)

    suspend fun insert(entity: ProductEntity): Int = withContext(io) {
        persistProduct(entity.copy(id = 0), StockMovementReasons.PRODUCT_CREATE)
    }

    suspend fun update(entity: ProductEntity): Int = withContext(io) {
        updateProductInternal(entity, StockMovementReasons.PRODUCT_UPDATE)
    }
    // -------- Lecturas --------

    /** Devuelve el producto mapeado a modelo de dominio (para la pantalla de edición). */
    suspend fun getByIdModel(id: Int): Product? = productDao.getById(id)?.toModel()

    /** Nombres de categorías para dropdown (si no tenés CategoryDao, podemos derivarlo desde products). */
    fun observeAllCategoryNames(): Flow<List<String>> =
        categoryDao.observeAllNames() // ideal: tabla de categorías
            .map { it.filter { name -> name.isNotBlank() }.distinct().sorted() }

    /** Nombres de proveedores para dropdown. */
    fun observeAllProviderNames(): Flow<List<String>> =
        providerDao.observeAllNames()
            .map { it.filter { name -> name.isNotBlank() }.distinct().sorted() }


    suspend fun cachedOrEmpty(): List<ProductEntity> =
        if (lastCache.isNotEmpty()) lastCache else productDao.getAllOnce()

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

    // ---------- E4: Cálculo precio/impuesto ----------
    data class PriceTriplet(val base: Double?, val tax: Double?, val final: Double?)
    private fun computePrice(
        basePrice: Double?, taxRate: Double?, finalPrice: Double?, legacyPrice: Double?
    ): PriceTriplet {
        // Reglas:
        // - Si base y tax están → final = base * (1 + tax)
        // - Si final y tax están → base = final / (1 + tax)
        // - Si sólo legacy price llega → usar como final (y base = final/(1+tax) si hay tax)
        val tax = taxRate?.takeIf { it >= 0 } ?: 0.0
        return when {
            basePrice != null -> PriceTriplet(basePrice, taxRate, basePrice * (1.0 + tax))
            finalPrice != null -> PriceTriplet(finalPrice / (1.0 + tax), taxRate, finalPrice)
            legacyPrice != null -> PriceTriplet(null, null, legacyPrice) // mantenemos legacy como final
            else -> PriceTriplet(null, null, null)
        }
    }

    // ---------- Importación tabular: bulkUpsert desde filas parseadas ----------
    suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) = withContext(io) {
        if (rows.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val touchedIds = mutableSetOf<Int>()

        db.withTransaction {
            rows.forEach { r ->
                val updated = r.updatedAt ?: LocalDate.now()
                val priceTrip = computePrice(
                    basePrice = null,
                    taxRate = null,
                    finalPrice = null,
                    legacyPrice = r.price
                )

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
                    basePrice = priceTrip.base,
                    taxRate = priceTrip.tax,
                    finalPrice = priceTrip.final,
                    price = r.price,
                    quantity = max(0, r.quantity),
                    description = r.description,
                    imageUrl = r.imageUrl,
                    categoryId = existing?.categoryId,
                    providerId = existing?.providerId,
                    providerName = existing?.providerName,
                    category = r.category ?: existing?.category,
                    minStock = r.minStock?.let { max(0, it) } ?: existing?.minStock,
                    updatedAt = updated
                )

                val id = productDao.upsertByKeys(incoming)
                touchedIds += id
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
            }
            lastCache = productDao.getAllOnce()
        }

        trySyncProductsNow(touchedIds, now)
    }

    // ---------- Flujo/consultas básicas ----------
    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()


    suspend fun getById(id: Int): ProductEntity? = productDao.getById(id)

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

        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val touchedIds = mutableSetOf<Int>()
        val now = System.currentTimeMillis()

        db.withTransaction {
            rows.forEachIndexed { idx, r ->
                try {
                    val priceTrip = computePrice(basePrice = null, taxRate = null, finalPrice = null, legacyPrice = r.price)

                    val existing = when {
                        !r.barcode.isNullOrBlank() -> productDao.getByBarcodeOnce(r.barcode!!)
                        else                       -> productDao.getByNameOnce(r.name)
                    }

                    if (existing == null) {
                        val p = ProductEntity(
                            code = r.code,
                            barcode = r.barcode,
                            name = r.name,
                            basePrice = priceTrip.base,
                            taxRate = priceTrip.tax,
                            finalPrice = priceTrip.final,
                            price = r.price, // legacy
                            quantity = max(0, r.quantity),
                            description = r.description,
                            imageUrl = r.imageUrl,
                            categoryId = null,                      // si querés, usar ensureCategoryId(r.category)
                            providerId = null,                      // si en el futuro sumamos CSV con proveedor
                            providerName = null,
                            category = r.category,
                            minStock = r.minStock?.let { max(0, it) },
                            updatedAt = r.updatedAt ?: LocalDate.now()
                        )
                        val id = productDao.insert(p).toInt()
                        touchedIds += id
                        if (p.quantity != 0) {
                            stockMovementDao.insert(
                                StockMovementEntity(
                                    productId = id,
                                    delta = p.quantity,
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
                    } else {
                        val newQty = when (strategy) {
                            ImportStrategy.Append  -> existing.quantity + max(0, r.quantity)
                            ImportStrategy.Replace -> max(0, r.quantity)
                        }
                        val merged = existing.copy(
                            code        = r.code ?: existing.code,
                            barcode     = r.barcode ?: existing.barcode,
                            name        = r.name.ifBlank { existing.name },
                            basePrice   = priceTrip.base ?: existing.basePrice,
                            taxRate     = priceTrip.tax  ?: existing.taxRate,
                            finalPrice  = priceTrip.final?: existing.finalPrice,
                            price       = r.price ?: existing.price,
                            quantity    = newQty,
                            description = r.description ?: existing.description,
                            imageUrl    = r.imageUrl ?: existing.imageUrl,
                            category    = r.category ?: existing.category,
                            minStock    = r.minStock ?: existing.minStock,
                            updatedAt   = r.updatedAt ?: LocalDate.now()
                        )
                        productDao.update(merged)
                        touchedIds += existing.id
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
                    }
                } catch (e: Exception) {
                    errors += "Línea ${idx + 2}: ${e.message}"
                }
            }
            lastCache = productDao.getAllOnce()
        }
        trySyncProductsNow(touchedIds, now)
        ImportResult(inserted, updated, errors)
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
        val importer = ProductCsvImporter(productDao)
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
            for (r in remoteList) {
                val local = when {
                    r.id != 0 -> localAll[r.id to (r.barcode ?: "")]
                    !r.barcode.isNullOrBlank() -> localAll.entries.firstOrNull { it.key.second == r.barcode }?.value
                    else -> null
                }
                if (local == null) {
                    // insertar
                    val newId = productDao.upsert(r.copy(id = 0))
                    applied++
                    // si el docId no coincide, subimos de vuelta con el id real para alinear
                    if (r.id != newId) remote.upsert(r.copy(id = newId))
                } else {
                    // resolver por updatedAt
                    if (r.updatedAt >= local.updatedAt) {
                        productDao.update(r.copy(id = local.id))
                        applied++
                    } else {
                        // Local es más nuevo → subir local para ganar en remoto
                        remote.upsert(local)
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

    /**
     * Sugerencias de reposición basadas en ventas recientes.
     */
    suspend fun getReorderSuggestions(
        windowDays: Int = 14,
        targetCoverageDays: Int = 14,
        limit: Int = 20
    ): List<ReorderSuggestion> = withContext(io) {
        val safeWindow = windowDays.coerceAtLeast(1)
        val now = Instant.now()
        val from = now.minus(safeWindow.toLong(), ChronoUnit.DAYS)
        val sales = productDao.getSalesSummary(from, now, StockMovementReasons.SALE)
        sales.mapNotNull { summary ->
            val avgDaily = summary.soldUnits.toDouble() / safeWindow.toDouble()
            val projectedDays = if (avgDaily > 0) summary.quantity / avgDaily else null
            val desiredStock = max(
                summary.minStock,
                ceil(avgDaily * targetCoverageDays).toInt()
            )
            val suggested = (desiredStock - summary.quantity).coerceAtLeast(0)
            if (suggested <= 0) return@mapNotNull null
            ReorderSuggestion(
                productId = summary.productId,
                name = summary.name,
                providerId = summary.providerId,
                providerName = summary.providerName,
                currentStock = summary.quantity,
                minStock = summary.minStock,
                soldLastDays = summary.soldUnits,
                windowDays = safeWindow,
                avgDailySales = avgDaily,
                projectedDaysOfStock = projectedDays,
                suggestedOrderQty = suggested
            )
        }.sortedByDescending { it.suggestedOrderQty }
            .take(limit)
    }

    /** Alta de producto (alias más semántico para la UI). */
    suspend fun addProduct(p: ProductEntity): Int = insert(p)

    /** Actualización de producto (alias más semántico para la UI). */
    suspend fun updateProduct(p: ProductEntity): Int = update(p)

    /** Obtener producto por código de barras. */
    suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? = productDao.getByBarcodeOnce(barcode)

    /** Obtener producto por id (alias semántico). */
    suspend fun getByIdOrNull(id: Int): ProductEntity? = productDao.getById(id)

    /** (Opcional) Obtener por nombre, por compatibilidad con flujos antiguos. */
    suspend fun getByNameOrNull(name: String): ProductEntity? = productDao.getByNameOnce(name)

    // ---------- Paging (expuesto para pantallas que lo necesiten) ----------
    fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>> = pagingSearch(query)

    fun getProducts(): Flow<List<ProductEntity>> =
        productDao.observeAll()
            .map { list ->
                lastCache = list
                list
            }

    fun observeStockMovements(productId: Int, limit: Int = 20): Flow<List<StockMovementWithProduct>> =
        stockMovementDao.observeByProductDetailed(productId, limit)

    fun observeRecentStockMovements(limit: Int = 50): Flow<List<StockMovementWithProduct>> =
        stockMovementDao.observeRecentDetailed(limit)

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
            newId = productDao.upsert(normalized)
            if (normalized.quantity != 0) {
                stockMovementDao.insert(
                    StockMovementEntity(
                        productId = newId,
                        delta = normalized.quantity,
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
        return newId
    }

    private suspend fun updateProductInternal(entity: ProductEntity, reason: String): Int {
        val now = System.currentTimeMillis()
        var rows = 0
        db.withTransaction {
            val current = productDao.getById(entity.id) ?: return@withTransaction
            val normalized = entity.copy(updatedAt = LocalDate.now())
            rows = productDao.update(normalized)
            if (rows > 0) {
                val delta = normalized.quantity - current.quantity
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
        }
        return rows
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
        }
        return success
    }

    private suspend fun trySyncProductsNow(ids: Collection<Int>, now: Long) {
        val uniqueIds = ids.mapNotNull { id -> id.takeIf { it > 0 } }.distinct()
        if (uniqueIds.isEmpty()) return
        val entities = productDao.getByIds(uniqueIds)
        if (entities.isEmpty()) return
        try {
            remote.upsertAll(entities)
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


}
