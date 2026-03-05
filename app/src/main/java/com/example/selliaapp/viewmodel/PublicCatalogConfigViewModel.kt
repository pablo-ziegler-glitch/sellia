package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.PublicCatalogConfigRepository
import com.example.selliaapp.repository.PublicCatalogSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PublicCatalogConfigViewModel @Inject constructor(
    private val repository: PublicCatalogConfigRepository
) : ViewModel() {
    val settings = repository.settings

    init {
        viewModelScope.launch { repository.refreshFromCloud() }
    }

    fun updateSettings(updated: PublicCatalogSettings) {
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }
}
