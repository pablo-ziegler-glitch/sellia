package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.MarketingConfigRepository
import com.example.selliaapp.repository.MarketingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MarketingConfigViewModel @Inject constructor(
    private val repository: MarketingConfigRepository
) : ViewModel() {
    val settings = repository.settings

    fun updateSettings(updated: MarketingSettings) {
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }
}
