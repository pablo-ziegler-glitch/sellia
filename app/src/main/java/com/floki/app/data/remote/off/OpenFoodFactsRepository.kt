package com.floki.app.data.remote.off

import com.floki.app.repository.OffRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NUEVO] Implementación concreta. Hilt puede construirla (tiene @Inject constructor).
 * Implementa la interfaz que tus ViewModels consumen.
 */
@Singleton
class OpenFoodFactsRepository @Inject constructor(
    private val remote: OffRemoteDataSource
) : OffRepository {

    private fun normalize(barcode: String): String? {
        val cleaned = barcode.trim().filter { it.isDigit() }
        return when (cleaned.length) {
            8, 12, 13, 14 -> cleaned
            else -> null
        }
    }

    override suspend fun getByBarcode(barcode: String): OffResult {
        val normalized = normalize(barcode)
            ?: return OffResult.NetworkError("Código inválido (longitud).")

        return try {
            val resp = remote.fetch(normalized)
            if (resp.status == 1) {
                val p = resp.product
                OffResult.Success(
                    name = p?.productName,
                    brand = p?.brands?.split(",")?.firstOrNull()?.trim(),
                    imageUrl = p?.imageUrl
                )
            } else {
                OffResult.NotFound
            }
        } catch (e: HttpException) {
            OffResult.HttpError(e.code())
        } catch (e: Exception) {
            OffResult.NetworkError(e.message ?: "Error de red")
        }
    }

    override suspend fun getProductSuggestion(barcode: String): OffProduct? {
        val normalized = normalize(barcode) ?: return null
        return try {
            val resp = remote.fetch(normalized)
            if (resp.status == 1) resp.product else null
        } catch (_: Exception) {
            null
        }
    }
}