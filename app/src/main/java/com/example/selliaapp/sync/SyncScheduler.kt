package com.example.selliaapp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager


object SyncScheduler {
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
}
