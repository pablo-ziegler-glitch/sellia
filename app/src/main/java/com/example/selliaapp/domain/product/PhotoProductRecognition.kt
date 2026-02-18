package com.example.selliaapp.domain.product

/**
 * Resultado de reconocimiento para una foto de producto.
 * Se usa como borrador editable antes de confirmar alta de stock.
 */
data class PhotoProductRecognition(
    val suggestedName: String,
    val suggestedBrand: String?,
    val suggestedCategory: String?,
    val confidence: Float
)

interface ProductPhotoRecognitionService {
    suspend fun recognizeFromImagePath(imagePath: String): PhotoProductRecognition
}
