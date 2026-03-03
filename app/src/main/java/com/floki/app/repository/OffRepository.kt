package com.floki.app.repository

import com.floki.app.data.remote.off.OffProduct
import com.floki.app.data.remote.off.OffResult

/**
 * [NUEVO] Interfaz única para OFF.
 * - getByBarcode → flujo tipado con OffResult (para UI/errores)
 * - getProductSuggestion → devuelve DTO crudo por si querés pre-llenar formularios
 */
interface OffRepository {
    suspend fun getByBarcode(barcode: String): OffResult
    suspend fun getProductSuggestion(barcode: String): OffProduct?
}
