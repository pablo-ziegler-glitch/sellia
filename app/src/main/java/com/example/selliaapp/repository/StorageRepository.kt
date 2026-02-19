package com.example.selliaapp.repository

import android.net.Uri

data class CloudCatalogImage(
    val fullPath: String,
    val downloadUrl: String
)

interface StorageRepository {
    suspend fun uploadProductImage(
        tenantId: String,
        productId: Int,
        localUri: Uri,
        contentType: String? = null
    ): String

    suspend fun listPublicCatalogImages(limit: Int = 60): List<CloudCatalogImage>
}
