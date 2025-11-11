package com.example.selliaapp.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.paging.PagingData
import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake para tests de StockImportViewModel. Implementa IProductRepository.
 * Solo las funciones usadas en el VM retornan valores reales; el resto devuelven stubs.
 */
class FakeProductRepository : IProductRepository {

    // Observabilidad simple
    private val productsFlow = MutableStateFlow<List<ProductEntity>>(emptyList())

    // Señales para verificar llamadas
    var lastSimulateUri: Uri? = null
    var lastImportFromFile: Triple<Uri, ProductRepository.ImportStrategy, Boolean>? = null
    var importInBackgroundCalledWith: Uri? = null

    // Config del fake
    var simulateResult: ImportResult = ImportResult(inserted = 2, updated = 3, errors = emptyList())
    var importResult: ImportResult = ImportResult(inserted = 1, updated = 4, errors = listOf())

    // ---------- CRUD base ----------
    override suspend fun insert(entity: ProductEntity): Int = 1
    override suspend fun update(entity: ProductEntity): Int = 1
    override suspend fun deleteById(id: Int) {}

    // ---------- Lecturas / streams ----------
    override fun observeAll(): Flow<List<ProductEntity>> = productsFlow
    override suspend fun getById(id: Int): ProductEntity? = null
    override suspend fun getByIdModel(id: Int): Product? = null
    override suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? = null

    // ---------- Búsquedas / listados ----------
    override fun search(q: String?): Flow<List<ProductEntity>> = flowOf(emptyList())
    override fun distinctCategories(): Flow<List<String>> = flowOf(emptyList())
    override fun distinctProviders(): Flow<List<String>> = flowOf(emptyList())

    // ---------- Paging ----------
    override fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>> =
        flowOf(PagingData.empty())

    override fun getProducts(): Flow<List<ProductEntity>> = productsFlow

    // ---------- Cache util ----------
    override suspend fun cachedOrEmpty(): List<ProductEntity> = emptyList()

    // ---------- Stock ----------
    override suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean = false

    // ---------- Archivo tabular: filas parseadas ----------
    override suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) {}

    // ---------- Archivo tabular: desde archivo ----------
    override suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult {
        lastSimulateUri = fileUri
        return simulateResult
    }

    override suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult {
        lastImportFromFile = Triple(fileUri, strategy, true)
        return importResult
    }

    override suspend fun importFromFile(
        resolver: ContentResolver,
        uri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult {
        lastImportFromFile = Triple(uri, strategy, false)
        return importResult
    }

    override fun importProductsInBackground(context: Context, fileUri: Uri) {
        importInBackgroundCalledWith = fileUri
    }

    // ---------- Alias semánticos (compat) ----------
    override suspend fun addProduct(p: ProductEntity): Int = 1
    override suspend fun updateProduct(p: ProductEntity): Int = 1

    // ---------- Sync (pull) ----------
    override suspend fun syncDown(): Int = 0

    // ---------- Enum espejo (no usado directamente aquí) ----------
    enum class ImportStrategy { Append, Replace }
}
