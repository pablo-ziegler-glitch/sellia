package com.example.selliaapp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


object SyncScheduler {
    private const val PREFS_NAME = "sync_scheduler_preferences"
    private const val KEY_INTERVAL_MINUTES = "sync_interval_minutes"
    private const val DEFAULT_INTERVAL_MINUTES = 60
    const val PERIODIC_UNIQUE_NAME: String = "sync_periodic_work"

    fun enqueueNow(context: Context, includeBackup: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG)
            .setInputData(SyncWorker.inputData(includeBackup))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                SyncWorker.UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
    }

    fun enqueuePeriodic(context: Context, intervalMinutes: Int = getIntervalMinutes(context)) {
        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(15)
        persistIntervalMinutes(context, safeIntervalMinutes)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(safeIntervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun getIntervalMinutes(context: Context): Int {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            .coerceAtLeast(15)
    }

    private fun persistIntervalMinutes(context: Context, intervalMinutes: Int) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
            .apply()
    }
}
