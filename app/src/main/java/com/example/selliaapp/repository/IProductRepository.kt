// [NUEVO]
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

/**
 * Contrato estable para Productos.
 * Los ViewModels nuevos dependerán de esta interfaz.
 * La implementación actual delega a tu ProductRepository existente.
 *
 * Nota: expongo métodos que ya existen en tu repo actual
 * (según los usos que vimos en errores/VMs).
 */
interface IProductRepository {

    // ---------- CRUD base ----------
    suspend fun insert(entity: ProductEntity): Int
    suspend fun update(entity: ProductEntity): Int
    suspend fun deleteById(id: Int)

    // ---------- Lecturas / streams ----------
    fun observeAll(): Flow<List<ProductEntity>>
    suspend fun getById(id: Int): ProductEntity?
    suspend fun getByIdModel(id: Int): Product?
    suspend fun getByBarcodeOrNull(barcode: String): ProductEntity?

    // ---------- Búsquedas / listados ----------
    fun search(q: String?): Flow<List<ProductEntity>>
    fun distinctCategories(): Flow<List<String>>
    fun distinctProviders(): Flow<List<String>>

    // ---------- Paging ----------
    fun pagingSearchFlow(query: String): Flow<PagingData<ProductEntity>>
    fun getProducts(): Flow<List<ProductEntity>>

    // ---------- Cache util ----------
    suspend fun cachedOrEmpty(): List<ProductEntity>

    // ---------- Stock ----------
    suspend fun increaseStockByBarcode(barcode: String, delta: Int): Boolean

    // ---------- Archivo tabular: filas parseadas ----------
    suspend fun bulkUpsert(rows: List<ProductCsvImporter.Row>)

    // ---------- Archivo tabular: desde archivo ----------
    enum class ImportStrategy { Append, Replace } // espejo del enum, por conveniencia
    suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult
    suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy // se permite el tipo del repo legacy
    ): ImportResult
    suspend fun importFromFile(
        resolver: ContentResolver,
        uri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult
    fun importProductsInBackground(context: Context, fileUri: Uri)

    // ---------- Alias semánticos (compat) ----------
    suspend fun addProduct(p: ProductEntity): Int
    suspend fun updateProduct(p: ProductEntity): Int

    // ---------- Sync (pull) ----------
    suspend fun syncDown(): Int
}