package com.floki.app.data.remote.off


import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API v2 de Open Food Facts - consulta de producto por código.
 * Ejemplo: /api/v2/product/7791234567890.json?fields=...&lc=es
 * Campos típicos útiles: product_name, brands, quantity, image_small_url, image_url,
 * categories, categories_tags, nutriscore_grade, nutrition_grades.
 */
interface OffApiService {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        // Idioma de preferencia (es = español)
        @Query("lc") lc: String = "es",
        // Podés limitar los campos para ahorrar datos:
        @Query("fields") fields: String =
            "code,product_name,brands,quantity,categories,image_url,image_small_url,nutriscore_grade,nutrition_grades"
    ): OffProductResponse


}
