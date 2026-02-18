package com.example.selliaapp.repository.impl

import android.net.Uri
import com.example.selliaapp.auth.FirebaseSessionCoordinator
import com.example.selliaapp.repository.StorageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val storage: FirebaseStorage,
    private val sessionCoordinator: FirebaseSessionCoordinator,
    private val auth: FirebaseAuth
) : StorageRepository {

    override suspend fun uploadProductImage(
        tenantId: String,
        productId: Int,
        localUri: Uri,
        contentType: String?
    ): String {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException(
                "Necesitás iniciar sesión para subir imágenes del producto."
            )

        val extension = contentType?.substringAfter('/')?.ifBlank { null }
        val fileName = if (extension != null) {
            "${UUID.randomUUID()}.$extension"
        } else {
            UUID.randomUUID().toString()
        }

        val reference = storage.reference
            .child("tenants/$tenantId/public_products/$productId/images/$fileName")
        val metadata = contentType?.let {
            StorageMetadata.Builder()
                .setContentType(it)
                .build()
        }

        return runCatching {
            currentUser.getIdToken(false).await()
            uploadAndResolveDownloadUrl(
                reference = reference,
                localUri = localUri,
                metadata = metadata
            )
        }.recoverCatching { initialError ->
            val storageError = initialError as? StorageException
            if (storageError?.errorCode == StorageException.ERROR_NOT_AUTHENTICATED) {
                // Fuerza refresh de credenciales y reintenta una sola vez.
                currentUser.getIdToken(true).await()
                uploadAndResolveDownloadUrl(
                    reference = reference,
                    localUri = localUri,
                    metadata = metadata
                )
            } else {
                throw initialError
            }
        }.getOrElse { error ->
            throw mapStorageError(error)
        }
    }

    private suspend fun uploadAndResolveDownloadUrl(
        reference: com.google.firebase.storage.StorageReference,
        localUri: Uri,
        metadata: StorageMetadata?
    ): String {
        if (metadata != null) {
            reference.putFile(localUri, metadata).await()
        } else {
            reference.putFile(localUri).await()
        }
        return reference.downloadUrl.await().toString()
    }

    private fun mapStorageError(error: Throwable): Throwable {
        val storageError = error as? StorageException ?: return error
        return when (storageError.errorCode) {
            StorageException.ERROR_NOT_AUTHENTICATED -> {
                IllegalStateException(
                    "Tu sesión venció o no está iniciada. Cerrá sesión y volvé a ingresar para subir imágenes."
                )
            }

            StorageException.ERROR_NOT_AUTHORIZED -> {
                IllegalStateException(
                    "Tu usuario no tiene permisos para subir imágenes en este negocio."
                )
            }

            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> {
                IllegalStateException(
                    "No se pudo subir la imagen por conexión inestable. Intentá nuevamente."
                )
            }

            else -> error
        }
    }
}
