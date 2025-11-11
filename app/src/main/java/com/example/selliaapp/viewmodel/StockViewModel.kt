package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class StockViewModel @Inject constructor(
    private val repo: IProductRepository
) : ViewModel() {

    // ====== Listados / Búsquedas ======

    /** Listado principal para pantallas de stock (mantengo tu semántica). */
    fun getProducts(): Flow<List<ProductEntity>> = repo.getProducts()

    /** Búsqueda reactiva usada por pantallas con barra de búsqueda. */
    fun search(q: String?): Flow<List<ProductEntity>> = repo.search(q)

    /** Categorías / Proveedores para filtros o pickers. */
    fun getAllCategoryNames(): Flow<List<String>> = repo.distinctCategories()
    fun getAllProviderNames(): Flow<List<String>> = repo.distinctProviders()

    // ====== Lecturas puntuales ======

    /** Cache rápida en memoria (delegado al repo). */
    suspend fun cachedOrEmpty(): List<ProductEntity> = repo.cachedOrEmpty()

    /** Obtener un producto por código de barras (o null si no existe). */
    suspend fun getByBarcodeOrNull(barcode: String): ProductEntity? =
        repo.getByBarcodeOrNull(barcode)

    // ====== Importación desde archivo ======

    /** Simula importación sin escribir en DB (dry-run). */
    suspend fun simulateImport(context: Context, fileUri: Uri): ImportResult =
        repo.simulateImport(context, fileUri)

    /**
     * Importa con estrategia (Append/Replace) escribiendo en DB.
     * Devuelve resumen (insertados/actualizados/errores).
     */
    suspend fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy
    ): ImportResult = repo.importProductsFromFile(context, fileUri, strategy)

    /** Encola importación en background con WorkManager. */
    fun importProductsInBackground(context: Context, fileUri: Uri) =
        repo.importProductsInBackground(context, fileUri)

    // ====== Bulk desde filas parseadas (flujo avanzado) ======

    /** Inserta/actualiza en bloque una lista de filas ya parseadas. */
    suspend fun bulkUpsert(rows: List<com.example.selliaapp.data.csv.ProductCsvImporter.Row>) =
        repo.bulkUpsert(rows)

    // ====== Escaneo de stock ======

    /** Resultado simple para integración con UI de escaneo. */
    data class ScanResult(val foundId: Int?, val prefillBarcode: String, val name: String? = null)

    /**
     * Consulta si un barcode existe. Si existe → foundId != null y devolvemos nombre para UI.
     * Si no existe → devolvemos el mismo barcode para precargar en alta.
     */
    suspend fun onScanBarcode(barcode: String): ScanResult = withContext(Dispatchers.IO) {
        val p = repo.getByBarcodeOrNull(barcode)
        if (p != null) ScanResult(foundId = p.id, prefillBarcode = barcode, name = p.name)
        else ScanResult(foundId = null, prefillBarcode = barcode, name = null)
    }

    /**
     * Suma stock por código de barras.
     * - onSuccess: se actualizó la cantidad.
     * - onNotFound: no existe el producto (abrir alta).
     * - onError: error inesperado.
     */
    fun addStockByScan(
        barcode: String,
        qty: Int,
        onSuccess: () -> Unit = {},
        onNotFound: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    // Usa API dedicada si existe; si no, la genérica de update por delta.
                    repo.increaseStockByBarcode(barcode = barcode, delta = qty)
                }
                if (ok) onSuccess() else onNotFound()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}