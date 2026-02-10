package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.DevelopmentOptionsDao
import com.example.selliaapp.data.local.entity.DevelopmentOptionsEntity
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.config.DevelopmentOptionsConfig
import com.example.selliaapp.domain.security.SecurityHashing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DevelopmentOptionsRepository @Inject constructor(
    private val dao: DevelopmentOptionsDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeConfigs(): Flow<List<DevelopmentOptionsConfig>> =
        dao.observeAll().map { configs -> configs.map { it.toDomain() } }

    suspend fun upsert(config: DevelopmentOptionsConfig) = withContext(io) {
        dao.upsert(config.toEntity())
    }
}

private fun DevelopmentOptionsEntity.toDomain(): DevelopmentOptionsConfig =
    DevelopmentOptionsConfig(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        salesEnabled = salesEnabled,
        stockEnabled = stockEnabled,
        customersEnabled = customersEnabled,
        providersEnabled = providersEnabled,
        expensesEnabled = expensesEnabled,
        reportsEnabled = reportsEnabled,
        cashEnabled = cashEnabled,
        usageAlertsEnabled = usageAlertsEnabled,
        configEnabled = configEnabled,
        publicCatalogEnabled = publicCatalogEnabled
    )

private fun DevelopmentOptionsConfig.toEntity(): DevelopmentOptionsEntity =
    DevelopmentOptionsEntity(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        salesEnabled = salesEnabled,
        stockEnabled = stockEnabled,
        customersEnabled = customersEnabled,
        providersEnabled = providersEnabled,
        expensesEnabled = expensesEnabled,
        reportsEnabled = reportsEnabled,
        cashEnabled = cashEnabled,
        usageAlertsEnabled = usageAlertsEnabled,
        configEnabled = configEnabled,
        publicCatalogEnabled = publicCatalogEnabled,
        updatedAt = System.currentTimeMillis()
    )
