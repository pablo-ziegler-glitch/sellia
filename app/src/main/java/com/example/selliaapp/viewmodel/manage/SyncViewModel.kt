package com.example.selliaapp.viewmodel.manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.selliaapp.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SyncUiState(
    val syncIntervalMinutes: Int = 60
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    fun uiState(): SyncUiState = SyncUiState(
        syncIntervalMinutes = SyncScheduler.getIntervalMinutes(appContext)
    )

    fun updateIntervalMinutes(intervalMinutes: Int) {
        SyncScheduler.enqueuePeriodic(appContext, intervalMinutes)
    }
}
