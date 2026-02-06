package com.example.selliaapp

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.selliaapp.sync.SyncWorker
import com.example.selliaapp.sync.PricingScheduler
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SelliaAppApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // ✅ Tu versión de WorkManager pide PROPIEDAD (no método)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.VERBOSE)
            .build()

    override fun onCreate() {
        super.onCreate()

        // StrictMode solo en debug (sin detectar sockets sin tag para evitar ruido de SDKs)
        // /* [ANTERIOR] import com.google.firebase.BuildConfig */
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
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

        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.APP_CHECK_DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            appCheck.setTokenAutoRefreshEnabled(true)
            appCheck.appCheckToken
                .addOnSuccessListener { token ->
                    Log.d("AppCheck", "Debug token activo: ${token.token}")
                }
                .addOnFailureListener { error ->
                    Log.w("AppCheck", "No se pudo obtener token debug.", error)
                }
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            appCheck.setTokenAutoRefreshEnabled(true)
            appCheck.appCheckToken
                .addOnFailureListener { error ->
                    Log.w("AppCheck", "No se pudo obtener App Check token.", error)
                }
        }

        PricingScheduler.enqueuePeriodic(this, 30)
    }
}
