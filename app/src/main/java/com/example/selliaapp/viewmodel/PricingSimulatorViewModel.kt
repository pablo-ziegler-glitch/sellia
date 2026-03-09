package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.pricing.PricingCalculator
import com.example.selliaapp.pricing.PricingResult
import com.example.selliaapp.repository.PricingConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PricingSimulatorState(
    val purchasePrice: Double = 0.0,
    val settings: PricingSettingsEntity? = null,
    val fixedCosts: List<PricingFixedCostEntity> = emptyList(),
    val mlFixedCostTiers: List<PricingMlFixedCostTierEntity> = emptyList(),
    val mlShippingTiers: List<PricingMlShippingTierEntity> = emptyList(),
    val result: PricingResult? = null,
    val loaded: Boolean = false
)

@HiltViewModel
class PricingSimulatorViewModel @Inject constructor(
    private val repository: PricingConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PricingSimulatorState())
    val state: StateFlow<PricingSimulatorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val fixedCosts = repository.getFixedCosts()
            val mlFixed = repository.getMlFixedCostTiers()
            val mlShipping = repository.getMlShippingTiers()
            _state.update {
                it.copy(
                    settings = settings,
                    fixedCosts = fixedCosts,
                    mlFixedCostTiers = mlFixed,
                    mlShippingTiers = mlShipping,
                    loaded = true
                )
            }
        }
    }

    fun setPurchasePrice(price: Double) {
        _state.update { it.copy(purchasePrice = price) }
        recalculate()
    }

    fun updateSettings(settings: PricingSettingsEntity) {
        _state.update { it.copy(settings = settings) }
        recalculate()
    }

    fun resetSettings() {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val fixedCosts = repository.getFixedCosts()
            val mlFixed = repository.getMlFixedCostTiers()
            val mlShipping = repository.getMlShippingTiers()
            _state.update {
                it.copy(
                    settings = settings,
                    fixedCosts = fixedCosts,
                    mlFixedCostTiers = mlFixed,
                    mlShippingTiers = mlShipping
                )
            }
            recalculate()
        }
    }

    private fun recalculate() {
        val s = _state.value
        val settings = s.settings ?: return
        if (s.purchasePrice <= 0) {
            _state.update { it.copy(result = null) }
            return
        }
        val result = PricingCalculator.calculate(
            purchasePrice = s.purchasePrice,
            settings = settings,
            fixedCosts = s.fixedCosts,
            mlFixedCostTiers = s.mlFixedCostTiers,
            mlShippingTiers = s.mlShippingTiers
        )
        _state.update { it.copy(result = result) }
    }
}
