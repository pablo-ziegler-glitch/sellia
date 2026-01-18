package com.example.selliaapp.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.selliaapp.repository.ProductRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PricingRecalcWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val productRepository: ProductRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val updated = productRepository.recalculateAutoPricingForAll()
            Log.i(TAG, "Recalculo de pricing completado. Productos actualizados: $updated")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error al recalcular pricing", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pricing_recalc_periodic"
        const val TAG = "PricingRecalcWorker"
    }
}
