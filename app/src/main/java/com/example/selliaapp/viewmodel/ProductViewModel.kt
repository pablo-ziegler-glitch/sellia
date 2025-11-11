package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.remote.off.OffResult
import com.example.selliaapp.data.remote.off.OpenFoodFactsRepository
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject


data class AutoFillUiState(
    val loading: Boolean = false,
    val message: String? = null
)


@HiltViewModel
class ProductViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val offRepo: OpenFoodFactsRepository

) : ViewModel() {

    private val _autoFillState = MutableStateFlow(AutoFillUiState())
    val autoFillState = _autoFillState.asStateFlow()

    // Campos de tu formulario (simplificado)
    var name: String? = null
    var brand: String? = null
    var imageUrl: String? = null

    // Si tus pantallas “Manage” u otras necesitan listas:
    val allProducts: Flow<List<ProductEntity>> = repo.observeAll()
    /**
     * Devuelve el producto por barcode (o null) en un hilo de IO.
     * Útil al volver del escáner para decidir si agregamos al carrito o abrimos alta.
     */
    suspend fun findByBarcodeOnce(barcode: String): ProductEntity? =
        withContext(Dispatchers.IO) {
            repo.getByBarcodeOrNull(barcode)
        }

    // 👇 Alias para compatibilidad con pantallas que usan `productVm.products`
    val products: Flow<List<ProductEntity>> get() = allProducts

    fun search(q: String?): Flow<List<ProductEntity>> = repo.search(q)

    // ------- E1: pickers -------
    fun getAllCategoryNames(): Flow<List<String>> = repo.distinctCategories()
    fun getAllProviderNames(): Flow<List<String>> = repo.distinctProviders()

    // ------- Obtener para edición -------
    suspend fun getProductById(id: Int): ProductEntity? = repo.getById(id)
    fun autocompleteFromOff(barcode: String) {
        viewModelScope.launch {
            _autoFillState.value = AutoFillUiState(loading = true)
            when (val r = offRepo.getByBarcode(barcode)) {
                is OffResult.Success -> {
                    name = name ?: r.name // sólo completa si está vacío
                    brand = brand ?: r.brand
                    imageUrl = imageUrl ?: r.imageUrl
                    _autoFillState.value = AutoFillUiState(
                        loading = false,
                        message = if (r.name.isNullOrBlank() && r.brand.isNullOrBlank())
                            "Producto encontrado, pero sin datos útiles."
                        else
                            "Producto encontrado en OFF."
                    )
                }
                OffResult.NotFound -> {
                    _autoFillState.value = AutoFillUiState(
                        loading = false,
                        message = "No hay datos en OFF para este código."
                    )
                }
                is OffResult.HttpError -> {
                    _autoFillState.value = AutoFillUiState(
                        loading = false,
                        message = "OFF devolvió HTTP ${r.code}. Verificar la URL."
                    )
                }
                is OffResult.NetworkError -> {
                    _autoFillState.value = AutoFillUiState(
                        loading = false,
                        message = "Error de red: ${r.msg}"
                    )
                }
            }
        }
    }
    // ------- Altas / Ediciones -------
    fun addProduct(
        name: String,
        barcode: String?,
        basePrice: Double?,
        taxRate: Double?,
        finalPrice: Double?,
        legacyPrice: Double?,
        stock: Int,
        code: String?,
        description: String?,
        imageUrl: String?,
        categoryName: String?,
        providerName: String?,
        minStock: Int?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ProductEntity(
                id = 0, // autogen
                code = code,
                barcode = barcode,
                name = name,
                // E4 (nuevos):
                basePrice = basePrice,
                taxRate = taxRate,
                finalPrice = finalPrice,
                // Legacy:
                price = legacyPrice,
                // stock:
                quantity = stock,
                // extras:
                description = description,
                imageUrl = imageUrl,
                // E1:
                category = categoryName,
                providerName = providerName,
                minStock = minStock,
                // timestamps si los tenés, dejá null o setéalos en DAO/DB trigger
                updatedAt = LocalDate.now()
            )
            repo.insert(entity)
        }
    }

    // --------- NUEVO: Edición con firma usada por AddProductScreen ---------
    fun updateProduct(
        id: Int,
        name: String,
        barcode: String?,
        basePrice: Double?,
        taxRate: Double?,
        finalPrice: Double?,
        legacyPrice: Double?,
        stock: Int,
        code: String?,
        description: String?,
        imageUrl: String?,
        categoryName: String?,
        providerName: String?,
        minStock: Int?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ProductEntity(
                id = id,
                code = code,
                barcode = barcode,
                name = name,
                basePrice = basePrice,
                taxRate = taxRate,
                finalPrice = finalPrice,
                price = legacyPrice,
                quantity = stock,
                description = description,
                imageUrl = imageUrl,
                category = categoryName,
                providerName = providerName,
                minStock = minStock,
                updatedAt = LocalDate.now()
            )
            repo.update(entity)
        }
    }


    // --------- (Compat) Versión antigua aceptando Entity directo ---------
    /**
     * Si aún hay llamadas viejas a addProduct(Entity), las mantenemos vivas.
     * Recomendación: migrar a la firma nueva para evitar errores de datos.
     */
    fun addProduct(p: ProductEntity, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repo.insert(p)
            onDone(id)
        }
    }


    // Nuevo: resolver producto por código de barras
    suspend fun getByBarcode(barcode: String): ProductEntity? =
        withContext(Dispatchers.IO) { repo.getByBarcodeOrNull(barcode) }


    fun importProductsFromFile(
        context: Context,
        fileUri: Uri,
        strategy: ProductRepository.ImportStrategy,
        onResult: (ImportResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repo.importProductsFromFile(context, fileUri, strategy)
            onResult(result)
        }
    }

}
