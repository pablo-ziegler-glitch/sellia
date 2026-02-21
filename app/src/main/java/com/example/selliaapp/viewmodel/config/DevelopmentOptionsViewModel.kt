package com.example.selliaapp.viewmodel.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.domain.config.DevelopmentFeatureKey
import com.example.selliaapp.domain.config.DevelopmentOptionsConfig
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.domain.security.SecurityHashing
import com.example.selliaapp.repository.DevelopmentOptionsRepository
import com.example.selliaapp.repository.UserRepository
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerDevelopmentOptionsUi(
    val ownerName: String,
    val ownerEmail: String,
    val isActive: Boolean,
    val config: DevelopmentOptionsConfig
)

data class AppCheckUiState(
    val token: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val cooldownRemainingSeconds: Int? = null
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
            val configByEmail = configList.associateBy { SecurityHashing.normalizeEmail(it.ownerEmail) }
            val ownerUsers = users
                .asSequence()
                .filter { AppRole.fromRaw(it.role) == AppRole.OWNER }
                .map { user -> user.copy(email = SecurityHashing.normalizeEmail(user.email)) }
                .filter { it.email.isNotBlank() }
                .plus(
                    com.example.selliaapp.data.model.User(
                        name = FIXED_SUPER_ADMIN_NAME,
                        email = FIXED_SUPER_ADMIN_EMAIL,
                        role = AppRole.OWNER.raw,
                        isActive = true
                    )
                )
                .distinctBy { it.email }
                .sortedBy { it.name.lowercase() }
                .toList()

            ownerUsers.map { user ->
                val config = configByEmail[user.email] ?: DevelopmentOptionsConfig.defaultFor(user.email)
                OwnerDevelopmentOptionsUi(
                    ownerName = user.name,
                    ownerEmail = user.email,
                    isActive = user.isActive,
                    config = config
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _appCheckState = MutableStateFlow(AppCheckUiState())
    val appCheckState: StateFlow<AppCheckUiState> = _appCheckState
    private var cooldownJob: Job? = null

    init {
        viewModelScope.launch { developmentOptionsRepository.refreshFromCloud() }
        refreshAppCheckToken(forceRefresh = false)
    }

    fun setFeature(ownerEmail: String, feature: DevelopmentFeatureKey, enabled: Boolean) {
        viewModelScope.launch {
            val current = findConfig(ownerEmail)
            developmentOptionsRepository.upsert(current.withFeature(feature, enabled))
        }
    }

    fun refreshAppCheckToken(forceRefresh: Boolean) {
        if (_appCheckState.value.isLoading) return
        val cooldownRemaining = _appCheckState.value.cooldownRemainingSeconds
        if (cooldownRemaining != null && cooldownRemaining > 0) {
            _appCheckState.update {
                it.copy(error = "Demasiados intentos. Esperá ${cooldownRemaining}s para reintentar.")
            }
            return
        }
        _appCheckState.update { it.copy(isLoading = true, error = null) }
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(forceRefresh)
            .addOnSuccessListener { token ->
                _appCheckState.update {
                    it.copy(token = token.token, isLoading = false, error = null, cooldownRemainingSeconds = null)
                }
            }
            .addOnFailureListener { error ->
                val isTooManyAttempts = error.message?.contains("TOO_MANY_REQUESTS", ignoreCase = true) == true ||
                    error.message?.contains("Too many attempts", ignoreCase = true) == true ||
                    error.cause?.message?.contains("TOO_MANY_REQUESTS", ignoreCase = true) == true
                if (isTooManyAttempts) {
                    startCooldown(DEFAULT_APP_CHECK_COOLDOWN_MS)
                }
                val message = when {
                    isTooManyAttempts -> "Demasiados intentos. Esperá ${DEFAULT_APP_CHECK_COOLDOWN_MS / 1000}s para reintentar."
                    else -> error.message?.takeIf { it.isNotBlank() } ?: "Error al obtener token"
                }
                _appCheckState.update {
                    it.copy(isLoading = false, error = message)
                }
            }
    }

    private fun findConfig(ownerEmail: String): DevelopmentOptionsConfig {
        val normalizedEmail = SecurityHashing.normalizeEmail(ownerEmail)
        return configs.value.firstOrNull { SecurityHashing.normalizeEmail(it.ownerEmail) == normalizedEmail }
            ?: DevelopmentOptionsConfig.defaultFor(normalizedEmail)
    }

    private fun startCooldown(durationMs: Long) {
        val endAt = System.currentTimeMillis() + durationMs
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (isActive) {
                val remainingSeconds = ((endAt - System.currentTimeMillis()) / 1000).toInt()
                if (remainingSeconds <= 0) {
                    _appCheckState.update { it.copy(cooldownRemainingSeconds = null) }
                    break
                }
                _appCheckState.update { it.copy(cooldownRemainingSeconds = remainingSeconds) }
                delay(COOLDOWN_TICK_MS)
            }
        }
    }

    private companion object {
        private const val DEFAULT_APP_CHECK_COOLDOWN_MS = 60_000L
        private const val COOLDOWN_TICK_MS = 1_000L
        const val FIXED_SUPER_ADMIN_EMAIL = "pabloz18ezeiza@gmail.com"
        const val FIXED_SUPER_ADMIN_NAME = "Pablo (Super Admin)"
    }
}
