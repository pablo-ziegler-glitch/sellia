package com.example.selliaapp.repository.impl

import android.net.Uri
import com.example.selliaapp.auth.FirebaseSessionCoordinator
import com.example.selliaapp.repository.CloudCatalogImage
import com.example.selliaapp.repository.StorageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
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
            sessionCoordinator.runWithFreshSession {
                ensureAppCheckToken(forceRefresh = false)
                uploadAndResolveDownloadUrl(
                    reference = reference,
                    localUri = localUri,
                    metadata = metadata
                )
            }
        }.recoverCatching { initialError ->
            val storageError = initialError as? StorageException
            if (
                storageError?.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ||
                storageError?.errorCode == StorageException.ERROR_NOT_AUTHORIZED
            ) {
                // Reintento único con refresh explícito de sesión + App Check.
                currentUser.getIdToken(true).await()
                ensureAppCheckToken(forceRefresh = true)
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



    override suspend fun listPublicCatalogImages(limit: Int): List<CloudCatalogImage> {
        val safeLimit = limit.coerceIn(1, 200)
        // Evita bucket hardcodeado: toma siempre el bucket configurado en FirebaseApp.
        // Esto corrige errores de permisos cuando el proyecto usa *.appspot.com o
        // un bucket distinto al que quedó fijo en builds previos.
        val folderRef = storage.reference.child(PUBLIC_CATALOG_PATH)

        ensureAppCheckToken(forceRefresh = false)
        val listed = folderRef.list(safeLimit).await()
        if (listed.items.isEmpty()) return emptyList()

        return listed.items.mapNotNull { item ->
            runCatching {
                CloudCatalogImage(
                    fullPath = item.path,
                    downloadUrl = item.downloadUrl.await().toString()
                )
            }.getOrNull()
        }
    }


    private suspend fun ensureAppCheckToken(forceRefresh: Boolean) {
        runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await()
        }.getOrElse { error ->
            throw IllegalStateException(
                "No se pudo validar App Check para Storage. Verificá token debug/Play Integrity y configuración del proyecto.",
                error
            )
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
                    "Tu usuario no tiene permisos para acceder a este archivo en Storage. " +
                        "Verificá reglas de Storage, sesión activa y App Check."
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

    private companion object {
        private const val PUBLIC_CATALOG_PATH = "Images/public/catalog"
    }
}
