package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.repository.PricingConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PricingConfigViewModel @Inject constructor(
    private val repository: PricingConfigRepository
) : ViewModel() {

    private val _settings = MutableStateFlow<PricingSettingsEntity?>(null)
    val settings: StateFlow<PricingSettingsEntity?> = _settings.asStateFlow()

    val fixedCosts = repository.observeFixedCosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mlFixedCostTiers = repository.observeMlFixedCostTiers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mlShippingTiers = repository.observeMlShippingTiers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _settings.value = repository.getSettings()
            repository.observeSettings().collect { _settings.value = it }
        }
    }

    fun saveSettings(updated: PricingSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }

    fun saveFixedCost(item: PricingFixedCostEntity) {
        viewModelScope.launch {
            repository.upsertFixedCost(item)
        }
    }

    fun deleteFixedCost(id: Int) {
        viewModelScope.launch {
            repository.deleteFixedCost(id)
        }
    }

    fun saveMlFixedCostTier(item: PricingMlFixedCostTierEntity) {
        viewModelScope.launch {
            repository.upsertMlFixedCostTier(item)
        }
    }

    fun deleteMlFixedCostTier(id: Int) {
        viewModelScope.launch {
            repository.deleteMlFixedCostTier(id)
        }
    }

    fun saveMlShippingTier(item: PricingMlShippingTierEntity) {
        viewModelScope.launch {
            repository.upsertMlShippingTier(item)
        }
    }

    fun deleteMlShippingTier(id: Int) {
        viewModelScope.launch {
            repository.deleteMlShippingTier(id)
        }
    }
}
