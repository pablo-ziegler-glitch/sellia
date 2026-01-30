package com.example.selliaapp.repository

import android.net.Uri

interface StorageRepository {
    suspend fun uploadProductImage(
        tenantId: String,
        productId: Int,
        localUri: Uri,
        contentType: String? = null
    ): String
}
