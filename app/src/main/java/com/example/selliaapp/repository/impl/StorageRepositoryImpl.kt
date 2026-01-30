package com.example.selliaapp.repository.impl

import android.net.Uri
import com.example.selliaapp.repository.StorageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val storage: FirebaseStorage
) : StorageRepository {

    override suspend fun uploadProductImage(
        tenantId: String,
        productId: Int,
        localUri: Uri,
        contentType: String?
    ): String {
        val extension = contentType?.substringAfter('/')?.ifBlank { null }
        val fileName = if (extension != null) {
            "${UUID.randomUUID()}.$extension"
        } else {
            UUID.randomUUID().toString()
        }
        val reference = storage.reference
            .child("tenants/$tenantId/products/$productId/images/$fileName")
        val metadata = contentType?.let {
            StorageMetadata.Builder()
                .setContentType(it)
                .build()
        }
        if (metadata != null) {
            reference.putFile(localUri, metadata).await()
        } else {
            reference.putFile(localUri).await()
        }
        return reference.downloadUrl.await().toString()
    }
}
