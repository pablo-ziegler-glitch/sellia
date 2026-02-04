package com.example.selliaapp.viewmodel.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.domain.config.DevelopmentFeatureKey
import com.example.selliaapp.domain.config.DevelopmentOptionsConfig
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.repository.DevelopmentOptionsRepository
import com.example.selliaapp.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerDevelopmentOptionsUi(
    val ownerName: String,
    val ownerEmail: String,
    val isActive: Boolean,
    val config: DevelopmentOptionsConfig
)

@HiltViewModel
class DevelopmentOptionsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val developmentOptionsRepository: DevelopmentOptionsRepository
) : ViewModel() {

    private val configs = developmentOptionsRepository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val owners: StateFlow<List<OwnerDevelopmentOptionsUi>> =
        combine(userRepository.observeUsers(), configs) { users, configList ->
            val configByEmail = configList.associateBy { it.ownerEmail }
            users.filter { AppRole.fromRaw(it.role) == AppRole.OWNER }
                .sortedBy { it.name.lowercase() }
                .map { user ->
                    val config = configByEmail[user.email] ?: DevelopmentOptionsConfig.defaultFor(user.email)
                    OwnerDevelopmentOptionsUi(
                        ownerName = user.name,
                        ownerEmail = user.email,
                        isActive = user.isActive,
                        config = config
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFeature(ownerEmail: String, feature: DevelopmentFeatureKey, enabled: Boolean) {
        viewModelScope.launch {
            val current = findConfig(ownerEmail)
            developmentOptionsRepository.upsert(current.withFeature(feature, enabled))
        }
    }

    private fun findConfig(ownerEmail: String): DevelopmentOptionsConfig {
        return configs.value.firstOrNull { it.ownerEmail == ownerEmail }
            ?: DevelopmentOptionsConfig.defaultFor(ownerEmail)
    }
}
