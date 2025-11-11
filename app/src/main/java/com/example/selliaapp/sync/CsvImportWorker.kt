package com.example.selliaapp.sync


import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.selliaapp.repository.ProductRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker para importar archivos tabulares (CSV/Excel/Sheets) sin bloquear la UI.
 * - Recibe un Uri en Data ("csv_uri").
 * - Llama al repositorio para importar con transacción, registrando movimientos.
 */
@HiltWorker
class CsvImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: ProductRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriStr = inputData.getString("csv_uri") ?: return Result.failure()
        val uri = Uri.parse(uriStr)
        return runCatching {
            repo.importProductsFromFile(applicationContext, uri, ProductRepository.ImportStrategy.Append)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
