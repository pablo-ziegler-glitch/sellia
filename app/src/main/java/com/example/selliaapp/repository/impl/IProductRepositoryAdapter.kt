
package com.example.selliaapp.repository.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.paging.PagingData
import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.Product
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository // <-- TU clase concreta existente
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptador: implementa la nueva interfaz y reusa tu ProductRepository actual.
 * No rompe nada: seguimos usando tu lógica existente, solo que detrás de una interfaz.
 */
@Singleton
class IProductRepositoryAdapter @Inject constructor(
    private val legacy: ProductRepository
) : IProductRepository {

    // ---------- CRUD base ----------
    override suspend fun insert(entity: ProductEntity): Int = legacy.insert(entity)
    override suspend fun update(entity: ProductEntity): Int = legacy.update(entity)
    override suspend fun deleteById(id: Int) = legacy.deleteById(id)

    // ---------- Lecturas / streams ----------
    override fun observeAll(): Flow<List<ProductEntity>> = legacy.observeAll()
    override suspend fun getById(id: Int): ProductEntity? = legacy.getById(id)
    override suspend fun getByIdModel(id: Int): Product? = legacy.getByIdModel(id)
    override suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? =
        legacy.getByBarcodeOrNull(barcode)

    // ---------- Búsquedas / listados ----------
    override fun search(q: String?): Flow<List<ProductEntity>> = legacy.search(q)
    override fun distinctCategories(): Flow<List<String>> = legacy.distinctCategories()
    override fun distinctProviders(): Flow<List<String>> = legacy.distinctProviders()

    // ---------- Paging ----------
    override fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>> =
        legacy.pagingSearchFlow(query)

    override fun getProducts(): Flow<List<ProductEntity>> = legacy.getProducts()

    // ---------- Cache util ----------
    override suspend fun cachedOrEmpty(): List<ProductEntity> = legacy.cachedOrEmpty()

    // ---------- Stock ----------
    override suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean =
        legacy.increaseStockByBarcode(barcode, delta)

    // ---------- Archivo tabular: filas parseadas ----------
    override suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>) =
        legacy.bulkUpsert(rows)

    // ---------- Archivo tabular: desde archivo ----------
    override suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult =
        legacy.simulateImport(context, fileUri)

    override suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult = legacy.importProductsFromFile(context, fileUri, strategy)

    override suspend fun importFromFile(
        resolver: ContentResolver,
        uri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult = legacy.importFromFile(resolver, uri, strategy)

    override fun importProductsInBackground(context: Context, fileUri: Uri) =
        legacy.importProductsInBackground(context, fileUri)

    // ---------- Alias semánticos (compat) ----------
    override suspend fun addProduct(p: ProductEntity): Int = legacy.addProduct(p)
    override suspend fun updateProduct(p: ProductEntity): Int = legacy.updateProduct(p)

    // ---------- Sync (pull) ----------
    override suspend fun syncDown(): Int = legacy.syncDown()
}
