package com.example.selliaapp.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
import com.example.selliaapp.data.mappers.toModel
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.Product
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.data.remote.ProductRemoteDataSource
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.sync.CsvImportWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val categoryDao: CategoryDao,
    private val providerDao: ProviderDao,
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher   // <-- igual que en el VM
) {

    // ---------- Cache simple en memoria ----------
    @Volatile private var lastCache: List<ProductEntity> = emptyList()


    suspend fun insert(entity: ProductEntity): Int = productDao.upsert(entity)


    suspend fun update(entity: ProductEntity): Int {
        return productDao.update(entity)
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
        for (r in rows) {
            val updated = r.updatedAt ?: LocalDate.now()

            // Convertir legacy 'price' a tripleta E4
            val priceTrip = computePrice(
                basePrice = null, taxRate = null, finalPrice = null, legacyPrice = r.price
            )

            val incoming = ProductEntity(
                // id=0 → autogenerado
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
                // Normalización diferida: si necesitás ids concretos podés llamar a ensureCategoryId/ensureProviderId
                categoryId = null,
                providerId = null,
                providerName = null,
                category = r.category,
                minStock = r.minStock?.let { max(0, it) },
                updatedAt = updated
            )
            productDao.upsertByKeys(incoming)
        }
        lastCache = productDao.getAllOnce()
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
                        productDao.insert(p)
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
                        updated++
                    }
                } catch (e: Exception) {
                    errors += "Línea ${idx + 2}: ${e.message}"
                }
            }
        }
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

    private val remote = ProductRemoteDataSource(firestore)


    // ---------- CRUD coordinado ----------
    suspend fun add(product: ProductEntity): Int = withContext(io) {
        val id = db.withTransaction { productDao.upsert(product.copy(id = 0)) }
        // subir a Firestore con docId = id
        remote.upsert(product.copy(id = id))
        id
    }



    suspend fun deleteById(id: Int) = withContext(io) {
        db.withTransaction { productDao.deleteById(id) }
        remote.deleteById(id)
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

    /** Alta de producto (alias más semántico para la UI). */
    suspend fun addProduct(p: ProductEntity): Int = add(p)

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
                // mantenemos una cache simple en memoria para filtros locales
                lastCache = list
                list
            }

    /**
     * Aumenta (o disminuye si delta < 0) el stock de un producto identificado por su barcode.
     *
     * @return true si se actualizó, false si no se encontró el producto.
     */
    suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean = withContext(io) {
        if (delta == 0) return@withContext true
        val current = productDao.getByBarcodeOnce(barcode) ?: return@withContext false

        val currentQty = current.quantity ?: 0
        val newQty = (currentQty + delta).coerceAtLeast(0) // no bajar de 0

        // Podés hacerlo con update(entity) o con un UPDATE directo; dejo ambas opciones:

        // Opción A: update con copia del entity
        val rows = productDao.update(current.copy(quantity = newQty))

        // Opción B: (si preferís SQL directo) descomenta y añade el método en el DAO:
        // val rows = productDao.updateQuantityByBarcode(barcode, newQty)

        if (rows > 0) {
            // refrescamos caché para la UI
            lastCache = productDao.getAllOnce()
            true
        } else {
            false
        }

    }


}
