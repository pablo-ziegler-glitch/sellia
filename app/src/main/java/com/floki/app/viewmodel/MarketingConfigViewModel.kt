package com.floki.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floki.app.repository.MarketingConfigRepository
import com.floki.app.repository.MarketingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MarketingConfigViewModel @Inject constructor(
    private val repository: MarketingConfigRepository
 ) : ViewModel() {
    val settings = repository.settings

    init {
        viewModelScope.launch { repository.refreshFromCloud() }
    }

    fun updateSettings(updated: MarketingSettings) {
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }
}
