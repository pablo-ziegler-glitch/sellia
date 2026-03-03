 package com.floki.app.data.remote.off

import retrofit2.http.GET
import retrofit2.http.Path

 /**
  * Endpoint correcto OFF v0: baseUrl debe ser https://world.openfoodfacts.org/api/v0/
  * product/{barcode}.json devuelve 200 si el endpoint existe:
  *  - status=1 → hay producto
  *  - status=0 → no encontrado
  */
 interface OpenFoodFactsApi {
     @GET("product/{barcode}.json")
     suspend fun getProduct(@Path("barcode") barcode: String): OffProductResponse
 }