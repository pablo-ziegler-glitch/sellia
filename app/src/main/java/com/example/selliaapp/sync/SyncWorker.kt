package com.example.selliaapp.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Worker de sincronización inyectado por Hilt.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Iniciando sincronización manual (workId=$id)")
        return try {
            syncRepository.pushPending()
            syncRepository.pullRemote()
            Log.i(TAG, "Sincronización completada con éxito")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error durante la sincronización", t)
            val shouldFail = t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.INVALID_ARGUMENT
            if (shouldFail) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME: String = "sync_work"
        const val TAG: String = "SyncWorker"
    }
}