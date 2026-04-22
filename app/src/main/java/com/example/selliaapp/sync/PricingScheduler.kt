package com.example.selliaapp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PricingScheduler {
    private const val DEFAULT_INTERVAL_MINUTES = 1440 // 24 hs
    private const val ENABLE_PERIODIC_PRICING_RECALC = false

    fun enqueuePeriodic(context: Context, intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES) {
        if (!ENABLE_PERIODIC_PRICING_RECALC) {
            cancelPeriodic(context)
            return
        }
        val safeInterval = intervalMinutes.coerceAtLeast(15)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PricingRecalcWorker>(safeInterval.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(PricingRecalcWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PricingRecalcWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PricingRecalcWorker.UNIQUE_NAME)
    }
}
