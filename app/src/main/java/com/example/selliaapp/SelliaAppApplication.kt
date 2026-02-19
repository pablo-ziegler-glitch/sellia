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
import com.example.selliaapp.sync.PricingScheduler
import com.example.selliaapp.sync.SyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SelliaAppApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
            }
        )
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
        val useDebugProvider = isAppCheckDebugEnabled()

        Log.w(
            TAG,
            "AppCheck init: useDebugProvider=$useDebugProvider buildType=${BuildConfig.BUILD_TYPE} pkg=$packageName"
        )

        if (useDebugProvider) {
            installDebugProvider(appCheck)

            // Durante setup: token manual para validar configuración.
            // Luego volvemos a auto-refresh para evitar expiración de App Check
            // en sesiones largas de uso (subidas de imágenes, sync, etc.).
            appCheck.setTokenAutoRefreshEnabled(false)

            // Fuerza token para validar que ya registraste el debug secret
            appCheck.getAppCheckToken(true)
                .addOnSuccessListener { token ->
                    Log.i(TAG, "AppCheck(debug) OK. token.len=${token.token.length} token.prefix=${token.token.take(16)}…")
                    appCheck.setTokenAutoRefreshEnabled(true)
                    onReady()
                }
                .addOnFailureListener { e ->
                    Log.w(
                        TAG,
                        "AppCheck(debug) FAIL (403 es normal si NO registraste el debug secret). " +
                                "Buscá el log 'DebugAppCheckProvider: Enter this debug secret…' y cargalo en Firebase Console.",
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

    /**
     * Modo “infalible”: definimos un debug secret fijo y lo registrás en Firebase Console.
     * Si reflection falla, cae al modo oficial (secret auto que se imprime en Logcat).
     */
    private fun installDebugProvider(appCheck: FirebaseAppCheck) {
        // ✅ Cambiá esto por un UUID tuyo (y REGISTRALO en Firebase Console > App Check > Debug tokens)
        val fixedDebugSecret = APP_CHECK_DEBUG_SECRET

        val installed = runCatching {
            val ctor = DebugAppCheckProviderFactory::class.java.getDeclaredConstructor(String::class.java)
            ctor.isAccessible = true
            val factory = ctor.newInstance(fixedDebugSecret)
            appCheck.installAppCheckProviderFactory(factory)
            true
        }.getOrElse { t ->
            Log.w(TAG, "No pude instalar DebugAppCheckProviderFactory(secret) por reflection. Caigo al modo oficial (Logcat).", t)
            false
        }

        if (installed) {
            Log.w(TAG, "Debug AppCheck usando SECRET FIJO. Registralo en Firebase Console: $fixedDebugSecret")
        } else {
            // Modo oficial: se genera secret y se imprime en Logcat con tag DebugAppCheckProvider
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            Log.w(TAG, "Debug AppCheck usando secret AUTO. Buscar en Logcat tag DebugAppCheckProvider.")
        }
    }

    private fun enqueuePeriodicSync() {
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
    }

    private fun isAppCheckDebugEnabled(): Boolean {
        // Si definiste APP_CHECK_DEBUG en BuildConfig, respétalo; si no, usa DEBUG.
        return try {
            val f = BuildConfig::class.java.getField("APP_CHECK_DEBUG")
            f.getBoolean(null)
        } catch (_: Throwable) {
            BuildConfig.DEBUG
        }
    }

    private companion object {
        private const val TAG = "SELLIA_BOOT"

        // ✅ Elegí uno y NO lo cambies: lo tenés que registrar tal cual en Firebase Console.
        private const val APP_CHECK_DEBUG_SECRET = "7a5c0df2-9b38-4a9a-9b1c-2db1a9a9c9b1"
    }
}
