package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.repository.PricingConfigRepository
import com.example.selliaapp.repository.ProductRepository
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
    private val repository: PricingConfigRepository,
    private val productRepository: ProductRepository
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
            productRepository.recalculateAutoPricingForAll(
                reason = "Actualización de settings de pricing",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun saveFixedCost(item: PricingFixedCostEntity) {
        viewModelScope.launch {
            repository.upsertFixedCost(item)
            productRepository.recalculateAutoPricingForAll(
                reason = "Actualización de costos fijos",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun deleteFixedCost(id: Int) {
        viewModelScope.launch {
            repository.deleteFixedCost(id)
            productRepository.recalculateAutoPricingForAll(
                reason = "Eliminación de costo fijo",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun saveMlFixedCostTier(item: PricingMlFixedCostTierEntity) {
        viewModelScope.launch {
            repository.upsertMlFixedCostTier(item)
            productRepository.recalculateAutoPricingForAll(
                reason = "Actualización de tramos ML costo fijo",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun deleteMlFixedCostTier(id: Int) {
        viewModelScope.launch {
            repository.deleteMlFixedCostTier(id)
            productRepository.recalculateAutoPricingForAll(
                reason = "Eliminación de tramo ML costo fijo",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun saveMlShippingTier(item: PricingMlShippingTierEntity) {
        viewModelScope.launch {
            repository.upsertMlShippingTier(item)
            productRepository.recalculateAutoPricingForAll(
                reason = "Actualización de tramos ML envío",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }

    fun deleteMlShippingTier(id: Int) {
        viewModelScope.launch {
            repository.deleteMlShippingTier(id)
            productRepository.recalculateAutoPricingForAll(
                reason = "Eliminación de tramo ML envío",
                changedBy = "System",
                source = "PRICING_CONFIG_SCREEN"
            )
        }
    }
}
