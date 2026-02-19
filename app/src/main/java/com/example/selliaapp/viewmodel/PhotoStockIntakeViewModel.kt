package com.example.selliaapp.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.domain.product.ProductPhotoRecognitionService
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class PhotoCandidateRow(
    val id: String,
    val imagePath: String,
    val name: String,
    val brand: String,
    val category: String,
    val quantity: String,
    val confidence: Float,
    val selected: Boolean = true
)

data class PhotoStockIntakeUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val rows: List<PhotoCandidateRow> = emptyList(),
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PhotoStockIntakeViewModel @Inject constructor(
    private val recognitionService: ProductPhotoRecognitionService,
    private val productRepository: IProductRepository,
    private val storageRepository: StorageRepository,
    private val tenantProvider: TenantProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoStockIntakeUiState())
    val uiState: StateFlow<PhotoStockIntakeUiState> = _uiState

    fun analyzeImagePaths(paths: List<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true, infoMessage = null, errorMessage = null) }
            val generated = paths.map { path ->
                val detection = recognitionService.recognizeFromImagePath(path)
                PhotoCandidateRow(
                    id = path + System.nanoTime(),
                    imagePath = path,
                    name = detection.suggestedName,
                    brand = detection.suggestedBrand.orEmpty(),
                    category = detection.suggestedCategory.orEmpty(),
                    quantity = "1",
                    confidence = detection.confidence
                )
            }

            _uiState.update { state ->
                val dedup = (state.rows + generated).distinctBy { it.imagePath }
                state.copy(
                    loading = false,
                    rows = dedup,
                    infoMessage = "Se analizaron ${generated.size} foto(s). Revisá y ajustá antes de guardar."
                )
            }
        }
    }

    fun updateRow(updated: PhotoCandidateRow) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.id == updated.id) updated else it })
        }
    }

    fun commitSelectedToStock() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val selected = state.rows.filter { it.selected }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No hay filas seleccionadas para guardar.") }
                return@launch
            }

            _uiState.update { it.copy(saving = true, errorMessage = null, infoMessage = null) }
            var created = 0
            var withImage = 0
            selected.forEach { row ->
                val quantity = row.quantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (row.name.isBlank() || quantity <= 0) return@forEach

                val entity = ProductEntity(
                    name = row.name.trim(),
                    brand = row.brand.trim().ifBlank { null },
                    category = row.category.trim().ifBlank { null },
                    quantity = quantity,
                    updatedAt = LocalDate.now(),
                    description = "Alta sugerida por IA (confianza ${(row.confidence * 100f).roundToInt()}%)"
                )
                val productId = productRepository.insert(entity)
                if (attachPhotoToProduct(productId = productId, imagePath = row.imagePath)) {
                    withImage++
                }
                created++
            }

            _uiState.update {
                it.copy(
                    saving = false,
                    rows = emptyList(),
                    infoMessage = "Se agregaron $created productos al stock ($withImage con imagen).",
                    errorMessage = null
                )
            }
        }
    }

    private suspend fun attachPhotoToProduct(productId: Int, imagePath: String): Boolean {
        if (imagePath.isBlank() || productId <= 0) return false
        return runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val uploadedUrl = storageRepository.uploadProductImage(
                tenantId = tenantId,
                productId = productId,
                localUri = Uri.parse(imagePath),
                contentType = null
            )
            val current = productRepository.getById(productId) ?: return false
            val merged = (current.imageUrls + uploadedUrl).distinct()
            productRepository.update(
                current.copy(
                    imageUrl = merged.firstOrNull(),
                    imageUrls = merged
                )
            ) > 0
        }.getOrElse { false }
    }

    fun clearMessage() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
