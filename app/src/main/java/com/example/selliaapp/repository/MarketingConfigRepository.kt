package com.example.selliaapp.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


data class MarketingSettings(
    val promo3x2Enabled: Boolean = true,
    val promo3x2MinQuantity: Int = 3,
    val promo3x2MinSubtotal: Double = 0.0
)

class MarketingConfigRepository {
    private val _settings = MutableStateFlow(MarketingSettings())
    val settings: StateFlow<MarketingSettings> = _settings.asStateFlow()

    fun updateSettings(updated: MarketingSettings) {
        _settings.value = updated
    }
}
