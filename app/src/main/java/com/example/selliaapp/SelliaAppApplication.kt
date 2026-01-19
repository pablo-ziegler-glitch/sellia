package com.example.selliaapp

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager    // BuildConfig del módulo app (mismo namespace)
import com.example.selliaapp.sync.SyncWorker
import com.example.selliaapp.sync.PricingScheduler
import com.example.selliaapp.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SelliaAppApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // ✅ Tu versión de WorkManager pide PROPIEDAD (no método)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)        // CLAVE: sin esto, cae a reflexión
            .setMinimumLoggingLevel(Log.VERBOSE)    // Útil para ver más logs de WM
            .build()

    override fun onCreate() {
        super.onCreate()

        // StrictMode solo en debug
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll().penaltyLog().build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll().penaltyLog().build()
            )
        }

        // Programa sync periódica (ejemplo: 1 hora)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
            .addTag("syncProducts")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "syncProductsPeriodicWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        PricingScheduler.enqueuePeriodic(this, 30)
    }
}
