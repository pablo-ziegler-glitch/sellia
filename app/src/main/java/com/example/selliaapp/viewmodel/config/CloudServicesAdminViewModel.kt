package com.example.selliaapp.viewmodel.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.User
import com.example.selliaapp.domain.config.CloudServiceConfig
import com.example.selliaapp.domain.security.AppRole
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

    private val configs = cloudServiceConfigRepository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val owners: StateFlow<List<OwnerCloudServiceConfigUi>> =
        combine(userRepository.observeUsers(), configs) { users, configList ->
            val configByEmail = configList.associateBy { it.ownerEmail }
            users.filter { AppRole.fromRaw(it.role) == AppRole.OWNER }
                .sortedBy { it.name.lowercase() }
                .map { user ->
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
        return configs.value.firstOrNull { it.ownerEmail == ownerEmail }
            ?: CloudServiceConfig.defaultFor(ownerEmail)
    }
}
