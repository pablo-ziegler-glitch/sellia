package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.remote.off.OffResult
import com.example.selliaapp.data.remote.off.OpenFoodFactsRepository
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.StorageRepository
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

data class ImageUploadUiState(
    val uploading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val offRepo: OpenFoodFactsRepository,
    private val storageRepository: StorageRepository,
    private val tenantProvider: TenantProvider

) : ViewModel() {

    private val _autoFillState = MutableStateFlow(AutoFillUiState())
    val autoFillState = _autoFillState.asStateFlow()

    private val _imageUploadState = MutableStateFlow(ImageUploadUiState())
    val imageUploadState = _imageUploadState.asStateFlow()

    // Campos de tu formulario (simplificado)
    var name: String? = null
    var brand: String? = null
    var imageUrl: String? = null
    var imageUrls: List<String>? = null

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
                    if (imageUrls.isNullOrEmpty() && !r.imageUrl.isNullOrBlank()) {
                        imageUrls = listOf(r.imageUrl)
                    }
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
        purchasePrice: Double?,
        legacyPrice: Double?,
        listPrice: Double?,
        cashPrice: Double?,
        transferPrice: Double?,
        transferNetPrice: Double?,
        mlPrice: Double?,
        ml3cPrice: Double?,
        ml6cPrice: Double?,
        stock: Int,
        code: String?,
        description: String?,
        imageUrls: List<String>,
        categoryName: String?,
        providerName: String?,
        providerSku: String?,
        minStock: Int?,
        pendingImageUris: List<Uri> = emptyList(),
        onDone: (Result<Int>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _imageUploadState.value = ImageUploadUiState(uploading = false, message = null)
            val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
            val entity = ProductEntity(
                id = 0, // autogen
                code = code,
                barcode = barcode,
                name = name,
                // E4 (nuevos):
                purchasePrice = purchasePrice,
                // Legacy:
                price = legacyPrice,
                listPrice = listPrice,
                cashPrice = cashPrice,
                transferPrice = transferPrice,
                transferNetPrice = transferNetPrice,
                mlPrice = mlPrice,
                ml3cPrice = ml3cPrice,
                ml6cPrice = ml6cPrice,
                autoPricing = false,
                // stock:
                quantity = stock,
                // extras:
                description = description,
                imageUrl = normalizedImages.firstOrNull(),
                imageUrls = normalizedImages,
                // E1:
                category = categoryName,
                providerName = providerName,
                providerSku = providerSku,
                minStock = minStock,
                // timestamps si los tenés, dejá null o setéalos en DAO/DB trigger
                updatedAt = LocalDate.now()
            )
            runCatching {
                val newId = repo.insert(entity)
                val uploadedUrls = uploadPendingImagesIfAny(newId, pendingImageUris)
                if (uploadedUrls.isNotEmpty()) {
                    val merged = (normalizedImages + uploadedUrls).distinct()
                    repo.update(
                        entity.copy(
                            id = newId,
                            imageUrls = merged,
                            imageUrl = merged.firstOrNull()
                        )
                    )
                }
                newId
            }.onSuccess { newId ->
                withContext(Dispatchers.Main) {
                    onDone(Result.success(newId))
                }
            }.onFailure { error ->
                _imageUploadState.value = ImageUploadUiState(
                    uploading = false,
                    message = error.message ?: "No se pudo guardar el producto."
                )
                withContext(Dispatchers.Main) {
                    onDone(Result.failure(error))
                }
            }
        }
    }

    // --------- NUEVO: Edición con firma usada por AddProductScreen ---------
    fun updateProduct(
        id: Int,
        name: String,
        barcode: String?,
        purchasePrice: Double?,
        legacyPrice: Double?,
        listPrice: Double?,
        cashPrice: Double?,
        transferPrice: Double?,
        transferNetPrice: Double?,
        mlPrice: Double?,
        ml3cPrice: Double?,
        ml6cPrice: Double?,
        stock: Int,
        code: String?,
        description: String?,
        imageUrls: List<String>,
        categoryName: String?,
        providerName: String?,
        providerSku: String?,
        minStock: Int?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
            val entity = ProductEntity(
                id = id,
                code = code,
                barcode = barcode,
                name = name,
                purchasePrice = purchasePrice,
                price = legacyPrice,
                listPrice = listPrice,
                cashPrice = cashPrice,
                transferPrice = transferPrice,
                transferNetPrice = transferNetPrice,
                mlPrice = mlPrice,
                ml3cPrice = ml3cPrice,
                ml6cPrice = ml6cPrice,
                autoPricing = false,
                quantity = stock,
                description = description,
                imageUrl = normalizedImages.firstOrNull(),
                imageUrls = normalizedImages,
                category = categoryName,
                providerName = providerName,
                providerSku = providerSku,
                minStock = minStock,
                updatedAt = LocalDate.now()
            )
            repo.update(entity)
        }
    }

    fun uploadProductImage(
        productId: Int,
        localUri: Uri,
        contentType: String?,
        onDone: (Result<String>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _imageUploadState.value = ImageUploadUiState(uploading = true, message = null)
                val tenantId = tenantProvider.requireTenantId()
                val downloadUrl = storageRepository.uploadProductImage(
                    tenantId = tenantId,
                    productId = productId,
                    localUri = localUri,
                    contentType = contentType
                )
                val current = repo.getById(productId)
                    ?: throw IllegalStateException("Producto no encontrado.")
                val merged = (current.imageUrls + downloadUrl).distinct()
                repo.update(
                    current.copy(
                        imageUrls = merged,
                        imageUrl = merged.firstOrNull()
                    )
                )
                downloadUrl
            }.onSuccess { url ->
                _imageUploadState.value = ImageUploadUiState(uploading = false, message = null)
                withContext(Dispatchers.Main) {
                    onDone(Result.success(url))
                }
            }.onFailure { error ->
                _imageUploadState.value = ImageUploadUiState(
                    uploading = false,
                    message = error.message ?: "No se pudo subir la imagen."
                )
                withContext(Dispatchers.Main) {
                    onDone(Result.failure(error))
                }
            }
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

    suspend fun getByQrValue(rawValue: String): ProductEntity? =
        withContext(Dispatchers.IO) {
            val value = normalizeQrValue(rawValue)
            repo.getByBarcodeOrNull(value)
                ?: repo.getByCodeOrNull(value)
                ?: parseProductId(value)?.let { repo.getById(it) }
                ?: parseProductId(rawValue)?.let { repo.getById(it) }
        }

    private fun normalizeQrValue(rawValue: String): String {
        val value = rawValue.trim()
        if (value.isBlank()) return value
        val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return value
        return parsed.getQueryParameter("q")
            ?.takeIf { it.isNotBlank() }
            ?: parsed.getQueryParameter("qr")?.takeIf { it.isNotBlank() }
            ?: value
    }

    private fun parseProductId(value: String): Int? {
        val normalized = value.trim()
        if (!normalized.startsWith("PRODUCT-", ignoreCase = true)) return null
        return normalized.removePrefix("PRODUCT-").toIntOrNull()
    }


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

    private suspend fun uploadPendingImagesIfAny(
        productId: Int,
        pendingImageUris: List<Uri>
    ): List<String> {
        if (pendingImageUris.isEmpty()) return emptyList()
        _imageUploadState.value = ImageUploadUiState(uploading = true, message = null)
        val tenantId = tenantProvider.requireTenantId()
        val uploaded = mutableListOf<String>()
        pendingImageUris.forEach { uri ->
            runCatching {
                storageRepository.uploadProductImage(
                    tenantId = tenantId,
                    productId = productId,
                    localUri = uri,
                    contentType = null
                )
            }.onSuccess { url ->
                uploaded.add(url)
            }.onFailure { error ->
                _imageUploadState.value = ImageUploadUiState(
                    uploading = false,
                    message = error.message ?: "No se pudieron subir todas las imágenes."
                )
            }
        }
        _imageUploadState.value = ImageUploadUiState(uploading = false, message = _imageUploadState.value.message)
        return uploaded
    }

}
