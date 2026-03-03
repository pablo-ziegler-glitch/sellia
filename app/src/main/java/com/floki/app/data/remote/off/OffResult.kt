package com.floki.app.data.remote.off

/**
 * [NUEVO] Resultado de la consulta a OFF, listo para usar en ViewModels.
 */
sealed class OffResult {
    data class Success(
        val name: String?,
        val brand: String?,
        val imageUrl: String?
    ) : OffResult()

    object NotFound : OffResult()
    data class HttpError(val code: Int) : OffResult()
    data class NetworkError(val msg: String) : OffResult()
}
