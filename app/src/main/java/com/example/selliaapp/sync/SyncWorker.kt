package com.example.selliaapp.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.selliaapp.auth.FirebaseSessionException
import com.example.selliaapp.auth.TenantProvider
import com.google.firebase.storage.StorageException
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent


/**
 * Worker de sincronización inyectado por Hilt.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val tenantProvider: TenantProvider
) : CoroutineWorker(appContext, params) {

    /**
     * Fallback constructor para entornos donde WorkManager no usa HiltWorkerFactory.
     * Mantiene compatibilidad con el constructor por reflexión (Context, WorkerParameters).
     */
    constructor(
        appContext: Context,
        params: WorkerParameters
    ) : this(
        appContext,
        params,
        EntryPointAccessors.fromApplication(
            appContext,
            SyncWorkerEntryPoint::class.java
        ).syncRepository(),
        EntryPointAccessors.fromApplication(
            appContext,
            SyncWorkerEntryPoint::class.java
        ).tenantProvider()
    )

    override suspend fun doWork(): Result {
        Log.i(TAG, "Iniciando sincronización manual (workId=$id)")
        if (tenantProvider.currentTenantId() == null) {
            Log.i(TAG, "Sin sesión activa, omitiendo sincronización (workId=$id)")
            return Result.success(
                workDataOf(
                    OUTPUT_STATUS to "skipped",
                    OUTPUT_MESSAGE to "Sin sesión activa, sincronización omitida."
                )
            )
        }
        return try {
            syncRepository.runSync(includeBackup = true)
            Log.i(TAG, "Sincronización completada con éxito")
            Result.success(
                workDataOf(
                    OUTPUT_STATUS to "success",
                    OUTPUT_MESSAGE to "Sincronización completada con éxito."
                )
            )
        } catch (sessionError: FirebaseSessionException) {
            Log.e(TAG, "Error de sesión durante la sincronización (workId=$id)", sessionError)
            val message = sessionError.message ?: buildErrorMessage(sessionError)
            Result.failure(
                workDataOf(
                    OUTPUT_STATUS to if (isPermissionDeniedSessionError(sessionError)) "failed_permission" else "failed",
                    OUTPUT_MESSAGE to message
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error durante la sincronización", t)
            val message = buildErrorMessage(t)
            Result.failure(
                workDataOf(
                    OUTPUT_STATUS to "failed",
                    OUTPUT_MESSAGE to message
                )
            )
        }
    }

    private fun isPermissionDeniedSessionError(error: FirebaseSessionException): Boolean {
        return when (val rootCause = error.cause) {
            is FirebaseFirestoreException -> {
                rootCause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            }

            is StorageException -> {
                rootCause.errorCode == StorageException.ERROR_NOT_AUTHORIZED
            }

            else -> false
        }
    }

    private fun buildErrorMessage(t: Throwable): String {
        val base = t.message?.takeIf { it.isNotBlank() } ?: "Error desconocido"
        return if (t is FirebaseFirestoreException) {
            "Firestore ${t.code.name}: $base"
        } else {
            "${t::class.java.simpleName}: $base"
        }
    }

    companion object {
        const val UNIQUE_NAME: String = "sync_work"
        const val TAG: String = "SyncWorker"
        const val OUTPUT_STATUS: String = "status"
        const val OUTPUT_MESSAGE: String = "message"
    }
}
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
    fun tenantProvider(): TenantProvider
}
