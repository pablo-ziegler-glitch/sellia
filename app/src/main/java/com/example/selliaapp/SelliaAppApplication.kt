package com.example.selliaapp

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.selliaapp.repository.AppVersionRepository
import com.example.selliaapp.sync.PricingScheduler
import com.example.selliaapp.sync.SyncScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SelliaAppApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appVersionRepository: AppVersionRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        initStrictMode()

        // Firebase primero
        val firebaseApp = FirebaseApp.initializeApp(this)
        if (firebaseApp == null) {
            Log.e(TAG, "FirebaseApp.initializeApp() devolvió null. Revisá google-services.json + plugin google-services + namespace.")
            return
        }

        // AppCheck antes de usar Firebase “en serio”
        initAppCheck(
            onReady = {
                // Evita spam mientras AppCheck está roto/no registrado
                enqueuePeriodicSync()
                PricingScheduler.enqueuePeriodic(this, 30)
                trackInstalledVersion()
            }
        )
    }


    private fun trackInstalledVersion() {
        applicationScope.launch {
            appVersionRepository.trackInstalledVersionIfNeeded()
                .onFailure { error ->
                    Log.w(TAG, "No se pudo registrar versión instalada en Firebase.", error)
                }
        }
    }

    private fun initStrictMode() {
        if (!BuildConfig.DEBUG) return

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
                //.detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    private fun initAppCheck(onReady: () -> Unit) {
        val appCheck = FirebaseAppCheck.getInstance()
        val useDebugProvider = BuildConfig.APP_CHECK_DEBUG

        Log.w(
            TAG,
            "AppCheck init: useDebugProvider=$useDebugProvider buildType=${BuildConfig.BUILD_TYPE} pkg=$packageName"
        )

        if (useDebugProvider) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.setTokenAutoRefreshEnabled(true)

            appCheck.getAppCheckToken(true)
                .addOnSuccessListener { token ->
                    Log.i(TAG, "AppCheck(debug) OK. token.len=${token.token.length}")
                    onReady()
                }
                .addOnFailureListener { e ->
                    Log.w(
                        TAG,
                        "AppCheck(debug) FAIL. Verificá la registración temporal del debug token en Firebase Console.",
                        e
                    )
                    // NO llamo onReady(): evitamos workers spameando mientras AppCheck no está configurado
                }

        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            appCheck.setTokenAutoRefreshEnabled(true)

            appCheck.getAppCheckToken(true)
                .addOnSuccessListener {
                    Log.i(TAG, "AppCheck(PlayIntegrity) OK.")
                    onReady()
                }
                .addOnFailureListener { e ->
                    Log.w(
                        TAG,
                        "AppCheck(PlayIntegrity) FAIL. 403 suele ser configuración: Link Cloud project + SHA-256 + settings AppCheck.",
                        e
                    )
                }
        }
    }

    private fun enqueuePeriodicSync() {
        SyncScheduler.enqueuePeriodic(this)
    }

    private companion object {
        private const val TAG = "SELLIA_BOOT"
    }
}
