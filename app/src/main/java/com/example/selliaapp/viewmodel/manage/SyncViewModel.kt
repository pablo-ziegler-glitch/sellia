package com.example.selliaapp.viewmodel.manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SyncUiState(
    val syncIntervalMinutes: Int = 60
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    syncOutboxDao: SyncOutboxDao
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val pendingCount = syncOutboxDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val outboxEntries = syncOutboxDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val outboxByType = syncOutboxDao.observeAll()
        .map { entries -> entries.groupBy(SyncOutboxEntity::entityType).mapValues { it.value.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun uiState(): SyncUiState = SyncUiState(
        syncIntervalMinutes = SyncScheduler.getIntervalMinutes(appContext)
    )

    fun updateIntervalMinutes(intervalMinutes: Int) {
        SyncScheduler.enqueuePeriodic(appContext, intervalMinutes)
    }
}
