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
 * Fake para tests de flujo de escaneo (Sell/Stock).
 * Implementa lo mínimo necesario y deja stubs seguros para el resto.
 */
class FakeScanProductRepository(
    initial: List<ProductEntity> = emptyList()
) : IProductRepository {

    private val products = mutableListOf<ProductEntity>().apply { addAll(initial) }
    private val productsFlow = MutableStateFlow(products.toList())

    // Señales de verificación
    var lastIncrease: Pair<String, Int>? = null

    // ---------- CRUD base ----------
    override suspend fun insert(entity: ProductEntity): Int {
        val newId = if (entity.id == 0) (products.maxOfOrNull { it.id } ?: 0) + 1 else entity.id
        products.add(entity.copy(id = newId))
        productsFlow.value = products.toList()
        return newId
    }
    override suspend fun update(entity: ProductEntity): Int {
        val idx = products.indexOfFirst { it.id == entity.id }
        if (idx >= 0) {
            products[idx] = entity
            productsFlow.value = products.toList()
            return 1
        }
        return 0
    }
    override suspend fun deleteById(id: Int) {
        products.removeAll { it.id == id }
        productsFlow.value = products.toList()
    }

    // ---------- Lecturas / streams ----------
    override fun observeAll(): Flow<List<ProductEntity>> = productsFlow
    override suspend fun getById(id: Int): ProductEntity? = products.firstOrNull { it.id == id }
    override suspend fun getByIdModel(id: Int): Product? = null
    override suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? =
        products.firstOrNull { it.barcode == barcode }

    // ---------- Búsquedas / listados ----------
    override fun search(q: String?): Flow<List<ProductEntity>> {
        val t = (q ?: "").trim().lowercase()
        return if (t.isEmpty()) productsFlow
        else flowOf(products.filter { (it.name ?: "").lowercase().contains(t) })
    }
    override fun distinctCategories(): Flow<List<String>> =
        flowOf(products.mapNotNull { it.category }.distinct())
    override fun distinctProviders(): Flow<List<String>> =
        flowOf(products.mapNotNull { it.providerName }.distinct())

    // ---------- Paging ----------
    override fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>> =
        flowOf(PagingData.from(products))

    override fun getProducts(): Flow<List<ProductEntity>> = productsFlow

    // ---------- Cache util ----------
    override suspend fun cachedOrEmpty(): List<ProductEntity> = products.toList()

    // ---------- Stock ----------
    override suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean {
        lastIncrease = barcode to delta
        val idx = products.indexOfFirst { it.barcode == barcode }
        if (idx < 0) return false
        val current = products[idx]
        val q = (current.quantity ?: 0) + delta
        val newQty = if (q < 0) 0 else q
        products[idx] = current.copy(quantity = newQty)
        productsFlow.value = products.toList()
        return true
    }

    // ---------- CSV: filas parseadas ----------
    override suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) { /* no usado acá */ }

    // ---------- CSV: desde archivo ----------
    override suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult =
        ImportResult(0, 0, emptyList())

    override suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult = ImportResult(0, 0, emptyList())

    override suspend fun importFromFile(
        resolver: ContentResolver,
        uri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult = ImportResult(0, 0, emptyList())

    override fun importProductsInBackground(context: Context, fileUri: Uri) { /* noop */ }

    // ---------- Alias semánticos ----------
    override suspend fun addProduct(p: ProductEntity): Int = insert(p)
    override suspend fun updateProduct(p: ProductEntity): Int = update(p)

    // ---------- Sync ----------
    override suspend fun syncDown(): Int = 0

    // Enum espejo (no lo referenciamos directamente)
    enum class ImportStrategy { Append, Replace }
}
