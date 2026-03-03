package com.floki.app.data.remote.off

import javax.inject.Inject


/**
 * [NUEVO] Hilt puede construirlo gracias a @Inject y al provide de OpenFoodFactsApi.
 */
class OffRemoteDataSource @Inject constructor(
    private val api: OpenFoodFactsApi
) {
    suspend fun fetch(barcode: String): OffProductResponse = api.getProduct(barcode)
}