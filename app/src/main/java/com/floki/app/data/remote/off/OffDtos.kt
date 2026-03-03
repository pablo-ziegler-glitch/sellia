package com.floki.app.data.remote.off



import com.squareup.moshi.Json

/**
 * Respuesta OFF v0: si status=1 hay producto; si status=0 no lo encontró.
 */
data class OffProductResponse(
    val status: Int? = null,                       // 1 encontrado, 0 no encontrado
    @Json(name = "status_verbose") val statusVerbose: String? = null,
    val code: String? = null,
    val product: OffProduct? = null
)

/**
 * [NUEVO] Usamos nombres camelCase para Kotlin y mapeamos desde el JSON original.
 */
data class OffProduct(
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    // Podés ampliar acá si luego necesitás más campos (quantity, categories, etc.)
)
