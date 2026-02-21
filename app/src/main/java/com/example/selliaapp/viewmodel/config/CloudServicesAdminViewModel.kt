package com.example.selliaapp.viewmodel.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.domain.config.CloudServiceConfig
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.domain.security.SecurityHashing
import com.example.selliaapp.repository.CloudServiceConfigRepository
import com.example.selliaapp.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerCloudServiceConfigUi(
    val ownerName: String,
    val ownerEmail: String,
    val config: CloudServiceConfig
)

@HiltViewModel
class CloudServicesAdminViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val cloudServiceConfigRepository: CloudServiceConfigRepository
 ) : ViewModel() {

    init {
        viewModelScope.launch { cloudServiceConfigRepository.refreshFromCloud() }
    }

    private val configs = cloudServiceConfigRepository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val owners: StateFlow<List<OwnerCloudServiceConfigUi>> =
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
                val config = configByEmail[user.email] ?: CloudServiceConfig.defaultFor(user.email)
                OwnerCloudServiceConfigUi(
                    ownerName = user.name,
                    ownerEmail = user.email,
                    config = config
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCloudEnabled(ownerEmail: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = findConfig(ownerEmail)
            val updated = if (enabled) {
                current.copy(cloudEnabled = true)
            } else {
                current.copy(
                    cloudEnabled = false,
                    firestoreBackupEnabled = false,
                    authSyncEnabled = false,
                    storageBackupEnabled = false,
                    functionsEnabled = false,
                    hostingEnabled = false
                )
            }
            cloudServiceConfigRepository.upsert(updated)
        }
    }

    fun setFirestoreBackup(ownerEmail: String, enabled: Boolean) {
        updateConfig(ownerEmail) { it.copy(firestoreBackupEnabled = enabled, cloudEnabled = true) }
    }

    fun setAuthSync(ownerEmail: String, enabled: Boolean) {
        updateConfig(ownerEmail) { it.copy(authSyncEnabled = enabled, cloudEnabled = true) }
    }

    fun setStorageBackup(ownerEmail: String, enabled: Boolean) {
        updateConfig(ownerEmail) { it.copy(storageBackupEnabled = enabled, cloudEnabled = true) }
    }

    fun setFunctionsEnabled(ownerEmail: String, enabled: Boolean) {
        updateConfig(ownerEmail) { it.copy(functionsEnabled = enabled, cloudEnabled = true) }
    }

    fun setHostingEnabled(ownerEmail: String, enabled: Boolean) {
        updateConfig(ownerEmail) { it.copy(hostingEnabled = enabled, cloudEnabled = true) }
    }

    private fun updateConfig(
        ownerEmail: String,
        updater: (CloudServiceConfig) -> CloudServiceConfig
    ) {
        viewModelScope.launch {
            val current = findConfig(ownerEmail)
            cloudServiceConfigRepository.upsert(updater(current))
        }
    }

    private fun findConfig(ownerEmail: String): CloudServiceConfig {
        val normalizedEmail = SecurityHashing.normalizeEmail(ownerEmail)
        return configs.value.firstOrNull { SecurityHashing.normalizeEmail(it.ownerEmail) == normalizedEmail }
            ?: CloudServiceConfig.defaultFor(normalizedEmail)
    }

    private companion object {
        const val FIXED_SUPER_ADMIN_EMAIL = "pabloz18ezeiza@gmail.com"
        const val FIXED_SUPER_ADMIN_NAME = "Pablo (Super Admin)"
    }
}
