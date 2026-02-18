package com.example.selliaapp.data.ai

import com.example.selliaapp.domain.product.PhotoProductRecognition
import com.example.selliaapp.domain.product.ProductPhotoRecognitionService
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación offline y de bajo costo.
 *
 * Nota: este servicio deja la arquitectura preparada para reemplazarlo por
 * un proveedor IA real (Gemini Vision en Cloud Function) sin tocar UI/ViewModel.
 */
@Singleton
class HeuristicProductPhotoRecognitionService @Inject constructor() : ProductPhotoRecognitionService {

    private val knownBrands = listOf(
        "nike", "adidas", "puma", "reebok", "new balance", "topper", "fila", "converse"
    )

    override suspend fun recognizeFromImagePath(imagePath: String): PhotoProductRecognition {
        val fileName = imagePath.substringAfterLast('/').substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        val lower = fileName.lowercase(Locale.getDefault())
        val brand = knownBrands.firstOrNull { lower.contains(it) }
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val category = when {
            lower.contains("zapat") || lower.contains("sneaker") -> "Calzado"
            lower.contains("remera") || lower.contains("shirt") -> "Indumentaria"
            lower.contains("campera") || lower.contains("jacket") -> "Abrigo"
            lower.contains("pantal") || lower.contains("jean") -> "Pantalones"
            else -> "Sin categoría"
        }

        val suggestedName = buildString {
            append(
                fileName
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    .ifBlank { "Producto sin nombre" }
            )
            if (brand != null && !contains(brand, ignoreCase = true)) {
                append(" ")
                append(brand)
            }
        }

        val confidence = when {
            brand != null && category != "Sin categoría" -> 0.78f
            brand != null || category != "Sin categoría" -> 0.62f
            else -> 0.45f
        }

        return PhotoProductRecognition(
            suggestedName = suggestedName,
            suggestedBrand = brand,
            suggestedCategory = category,
            confidence = confidence
        )
    }
}
