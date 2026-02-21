package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.MarketingConfigRepository
import com.example.selliaapp.ui.theme.ThemePalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    private val repository: MarketingConfigRepository
) : ViewModel() {

    val themePalette: StateFlow<ThemePalette> = repository.settings
        .map { settings ->
            ThemePalette(
                primaryHex = settings.tenantPalette.primary.ifBlank { settings.defaultPalette.primary },
                secondaryHex = settings.tenantPalette.secondary.ifBlank { settings.defaultPalette.secondary },
                tertiaryHex = settings.tenantPalette.tertiary.ifBlank { settings.defaultPalette.tertiary }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePalette())

    init {
        viewModelScope.launch { repository.refreshFromCloud() }
    }
}
