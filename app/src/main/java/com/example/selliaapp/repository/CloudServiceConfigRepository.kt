package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.CloudServiceConfigDao
import com.example.selliaapp.data.local.entity.CloudServiceConfigEntity
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.config.CloudServiceConfig
import com.example.selliaapp.domain.security.SecurityHashing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CloudServiceConfigRepository @Inject constructor(
    private val dao: CloudServiceConfigDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeConfigs(): Flow<List<CloudServiceConfig>> =
        dao.observeAll().map { configs -> configs.map { it.toDomain() } }

    suspend fun upsert(config: CloudServiceConfig) = withContext(io) {
        dao.upsert(config.toEntity())
    }

    suspend fun isCloudEnabled(): Boolean = withContext(io) {
        dao.countCloudEnabled() > 0
    }
}

private fun CloudServiceConfigEntity.toDomain(): CloudServiceConfig =
    CloudServiceConfig(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        cloudEnabled = cloudEnabled,
        firestoreBackupEnabled = firestoreBackupEnabled,
        authSyncEnabled = authSyncEnabled,
        storageBackupEnabled = storageBackupEnabled,
        functionsEnabled = functionsEnabled,
        hostingEnabled = hostingEnabled
    )

private fun CloudServiceConfig.toEntity(): CloudServiceConfigEntity =
    CloudServiceConfigEntity(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        cloudEnabled = cloudEnabled,
        firestoreBackupEnabled = firestoreBackupEnabled,
        authSyncEnabled = authSyncEnabled,
        storageBackupEnabled = storageBackupEnabled,
        functionsEnabled = functionsEnabled,
        hostingEnabled = hostingEnabled,
        updatedAt = System.currentTimeMillis()
    )
