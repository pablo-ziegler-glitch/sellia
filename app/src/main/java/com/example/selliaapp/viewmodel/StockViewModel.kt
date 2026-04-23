package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Filtros de stock disponibles en la pantalla principal de inventario. */
enum class StockFilter { ALL, LOW_STOCK, OUT_OF_STOCK }

@HiltViewModel
class StockViewModel @Inject constructor(
    private val repo: IProductRepository
) : ViewModel() {

    // ====== Persistencia de filtros ======

    private val _stockFilter = MutableStateFlow(StockFilter.ALL)
    val stockFilter: StateFlow<StockFilter> = _stockFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setStockFilter(filter: StockFilter) { _stockFilter.value = filter }
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // ====== Ajuste rápido desde swipe ======

    /**
     * Suma o resta [delta] unidades al producto con [productId].
     * Registra el movimiento como MANUAL_ADJUST.
     * No permite bajar de 0.
     */
    fun quickAdjust(productId: Int, delta: Int) {
        viewModelScope.launch {
            repo.adjustStock(
                productId = productId,
                delta = delta,
                reason = StockMovementReasons.MANUAL_ADJUST
            )
        }
    }

    fun updateProductsPublicStatus(
        productIds: Set<Int>,
        makePublic: Boolean,
        onDone: (Result<Int>) -> Unit = {}
    ) {
        val uniqueIds = productIds.filter { it > 0 }.toSet()
        if (uniqueIds.isEmpty()) {
            onDone(Result.success(0))
            return
        }
        val targetStatus = if (makePublic) "published" else "draft"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repo.updatePublicStatusByIds(
                    productIds = uniqueIds,
                    publicStatus = targetStatus
                )
            }.onSuccess { updated ->
                withContext(Dispatchers.Main) {
                    onDone(Result.success(updated))
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onDone(Result.failure(error))
                }
            }
        }
    }

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
    data class ScanResult(
        val foundId: Int?,
        val prefillBarcode: String,
        val name: String? = null,
        val brand: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Consulta si un barcode existe. Si existe → foundId != null y devolvemos nombre para UI.
     * Si no existe → devolvemos el mismo barcode para precargar en alta.
     */
    suspend fun onScanBarcode(barcode: String): ScanResult = withContext(Dispatchers.IO) {
        runCatching {
            val p = repo.getByBarcodeOrNull(barcode)
            if (p != null) {
                ScanResult(
                    foundId = p.id,
                    prefillBarcode = barcode,
                    name = p.name,
                    brand = p.brand
                )
            } else {
                val global = repo.getGlobalBarcodeMatch(barcode)
                ScanResult(
                    foundId = null,
                    prefillBarcode = global?.barcode ?: barcode,
                    name = global?.name,
                    brand = global?.brand
                )
            }
        }.getOrElse { error ->
            ScanResult(
                foundId = null,
                prefillBarcode = barcode,
                errorMessage = error.message ?: "No pudimos validar el código de barras."
            )
        }
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
                val ok = repo.increaseStockByBarcode(barcode = barcode, delta = qty)
                if (ok) onSuccess() else onNotFound()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}
