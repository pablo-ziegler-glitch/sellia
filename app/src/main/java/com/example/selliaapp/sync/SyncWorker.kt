package com.example.selliaapp.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.example.selliaapp.repository.CloudServiceConfigRepository


/**
 * Worker de sincronización inyectado por Hilt.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val cloudServiceConfigRepository: CloudServiceConfigRepository
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
        ).cloudServiceConfigRepository()
    )




    override suspend fun doWork(): Result {
        Log.i(TAG, "Iniciando sincronización manual (workId=$id)")
        return try {
            if (!cloudServiceConfigRepository.isCloudEnabled()) {
                return Result.failure(
                    workDataOf(
                        OUTPUT_STATUS to "failed",
                        OUTPUT_MESSAGE to "Sincronización deshabilitada (requiere Datos en la nube activo)."
                    )
                )
            }
            val includeBackup = inputData.getBoolean(INPUT_BACKUP, false)
            syncRepository.runSync(includeBackup)
            Log.i(TAG, "Sincronización completada con éxito")
            Result.success(
                workDataOf(
                    OUTPUT_STATUS to "success",
                    OUTPUT_MESSAGE to "Sincronización completada con éxito."
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
        const val INPUT_BACKUP: String = "include_backup"

        fun inputData(includeBackup: Boolean) = workDataOf(INPUT_BACKUP to includeBackup)
    }
}
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
    fun cloudServiceConfigRepository(): CloudServiceConfigRepository
}
