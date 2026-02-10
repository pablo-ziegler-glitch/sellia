package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.PricingAuditDao
import com.example.selliaapp.data.dao.PricingFixedCostDao
import com.example.selliaapp.data.dao.PricingMlFixedCostTierDao
import com.example.selliaapp.data.dao.PricingMlShippingTierDao
import com.example.selliaapp.data.dao.PricingSettingsDao
import com.example.selliaapp.data.local.entity.PricingAuditEntity
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.auth.TenantProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant

class PricingConfigRepository(
    private val pricingFixedCostDao: PricingFixedCostDao,
    private val pricingSettingsDao: PricingSettingsDao,
    private val pricingAuditDao: PricingAuditDao,
    private val pricingMlFixedCostTierDao: PricingMlFixedCostTierDao,
    private val pricingMlShippingTierDao: PricingMlShippingTierDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeFixedCosts(): Flow<List<PricingFixedCostEntity>> =
        pricingFixedCostDao.observeAll()

    fun observeSettings(): Flow<PricingSettingsEntity> =
        pricingSettingsDao.observe()
            .filterNotNull()
            .map { it }

    fun observeMlFixedCostTiers(): Flow<List<PricingMlFixedCostTierEntity>> =
        pricingMlFixedCostTierDao.observeAll()

    fun observeMlShippingTiers(): Flow<List<PricingMlShippingTierEntity>> =
        pricingMlShippingTierDao.observeAll()

    suspend fun getSettings(): PricingSettingsEntity = withContext(io) {
        ensureDefaultsIfNeeded()
        pricingSettingsDao.getOnce() ?: defaultSettings()
    }

    suspend fun getFixedCosts(): List<PricingFixedCostEntity> = withContext(io) {
        ensureDefaultsIfNeeded()
        pricingFixedCostDao.getAllOnce()
    }

    suspend fun getMlFixedCostTiers(): List<PricingMlFixedCostTierEntity> = withContext(io) {
        ensureDefaultsIfNeeded()
        pricingMlFixedCostTierDao.getAllOnce()
    }

    suspend fun getMlShippingTiers(): List<PricingMlShippingTierEntity> = withContext(io) {
        ensureDefaultsIfNeeded()
        pricingMlShippingTierDao.getAllOnce()
    }

    suspend fun upsertFixedCost(
        item: PricingFixedCostEntity,
        changedBy: String = "System"
    ): PricingFixedCostEntity = withContext(io) {
        ensureDefaultsIfNeeded()
        val now = Instant.now()
        val existing = pricingFixedCostDao.getAllOnce().firstOrNull { it.id == item.id }
        val id = pricingFixedCostDao.upsert(item).toInt()
        val resolved = if (item.id == 0) item.copy(id = id) else item
        if (existing == null) {
            audit(
                scope = "fixed_cost",
                itemId = resolved.id,
                field = "created",
                oldValue = null,
                newValue = "${resolved.name}:${resolved.amount}",
                changedAt = now,
                changedBy = changedBy
            )
        } else {
            if (existing.name != resolved.name) {
                audit("fixed_cost", resolved.id, "name", existing.name, resolved.name, now, changedBy)
            }
            if (existing.description != resolved.description) {
                audit("fixed_cost", resolved.id, "description", existing.description, resolved.description, now, changedBy)
            }
            if (existing.amount != resolved.amount) {
                audit("fixed_cost", resolved.id, "amount", existing.amount.toString(), resolved.amount.toString(), now, changedBy)
            }
            if (existing.applyIva != resolved.applyIva) {
                audit("fixed_cost", resolved.id, "applyIva", existing.applyIva.toString(), resolved.applyIva.toString(), now, changedBy)
            }
        }
        resolved
            .also { syncPricingConfigToCloud() }
    }

    suspend fun deleteFixedCost(id: Int, changedBy: String = "System") = withContext(io) {
        ensureDefaultsIfNeeded()
        val existing = pricingFixedCostDao.getAllOnce().firstOrNull { it.id == id }
        if (existing != null) {
            pricingFixedCostDao.deleteById(id)
            audit(
                scope = "fixed_cost",
                itemId = id,
                field = "deleted",
                oldValue = "${existing.name}:${existing.amount}",
                newValue = null,
                changedAt = Instant.now(),
                changedBy = changedBy
            )
            syncPricingConfigToCloud()
        }
    }

    suspend fun upsertMlFixedCostTier(
        item: PricingMlFixedCostTierEntity,
        changedBy: String = "System"
    ) = withContext(io) {
        ensureDefaultsIfNeeded()
        val now = Instant.now()
        val existing = pricingMlFixedCostTierDao.getAllOnce().firstOrNull { it.id == item.id }
        val id = pricingMlFixedCostTierDao.upsert(item).toInt()
        val resolved = if (item.id == 0) item.copy(id = id) else item
        if (existing == null) {
            audit("ml_fixed_cost_tier", resolved.id, "created", null, "${resolved.maxPrice}:${resolved.cost}", now, changedBy)
        } else {
            if (existing.maxPrice != resolved.maxPrice) {
                audit("ml_fixed_cost_tier", resolved.id, "maxPrice", existing.maxPrice.toString(), resolved.maxPrice.toString(), now, changedBy)
            }
            if (existing.cost != resolved.cost) {
                audit("ml_fixed_cost_tier", resolved.id, "cost", existing.cost.toString(), resolved.cost.toString(), now, changedBy)
            }
        }
        resolved
            .also { syncPricingConfigToCloud() }
    }

    suspend fun deleteMlFixedCostTier(id: Int, changedBy: String = "System") = withContext(io) {
        ensureDefaultsIfNeeded()
        val existing = pricingMlFixedCostTierDao.getAllOnce().firstOrNull { it.id == id }
        if (existing != null) {
            pricingMlFixedCostTierDao.deleteById(id)
            audit(
                scope = "ml_fixed_cost_tier",
                itemId = id,
                field = "deleted",
                oldValue = "${existing.maxPrice}:${existing.cost}",
                newValue = null,
                changedAt = Instant.now(),
                changedBy = changedBy
            )
            syncPricingConfigToCloud()
        }
    }

    suspend fun upsertMlShippingTier(
        item: PricingMlShippingTierEntity,
        changedBy: String = "System"
    ) = withContext(io) {
        ensureDefaultsIfNeeded()
        val now = Instant.now()
        val existing = pricingMlShippingTierDao.getAllOnce().firstOrNull { it.id == item.id }
        val id = pricingMlShippingTierDao.upsert(item).toInt()
        val resolved = if (item.id == 0) item.copy(id = id) else item
        if (existing == null) {
            audit("ml_shipping_tier", resolved.id, "created", null, "${resolved.maxWeightKg}:${resolved.cost}", now, changedBy)
        } else {
            if (existing.maxWeightKg != resolved.maxWeightKg) {
                audit("ml_shipping_tier", resolved.id, "maxWeightKg", existing.maxWeightKg.toString(), resolved.maxWeightKg.toString(), now, changedBy)
            }
            if (existing.cost != resolved.cost) {
                audit("ml_shipping_tier", resolved.id, "cost", existing.cost.toString(), resolved.cost.toString(), now, changedBy)
            }
        }
        resolved
            .also { syncPricingConfigToCloud() }
    }

    suspend fun deleteMlShippingTier(id: Int, changedBy: String = "System") = withContext(io) {
        ensureDefaultsIfNeeded()
        val existing = pricingMlShippingTierDao.getAllOnce().firstOrNull { it.id == id }
        if (existing != null) {
            pricingMlShippingTierDao.deleteById(id)
            audit(
                scope = "ml_shipping_tier",
                itemId = id,
                field = "deleted",
                oldValue = "${existing.maxWeightKg}:${existing.cost}",
                newValue = null,
                changedAt = Instant.now(),
                changedBy = changedBy
            )
            syncPricingConfigToCloud()
        }
    }

    suspend fun updateSettings(
        updated: PricingSettingsEntity,
        changedBy: String = "System"
    ) = withContext(io) {
        ensureDefaultsIfNeeded()
        val existing = pricingSettingsDao.getOnce()
        val now = Instant.now()
        if (existing == null) {
            pricingSettingsDao.upsert(updated.copy(updatedAt = now, updatedBy = changedBy))
            audit(
                scope = "settings",
                itemId = updated.id,
                field = "created",
                oldValue = null,
                newValue = "initial",
                changedAt = now,
                changedBy = changedBy
            )
        } else {
            val candidate = updated.copy(updatedAt = now, updatedBy = changedBy)
            pricingSettingsDao.update(candidate)
            auditField("ivaTerminalPercent", existing.ivaTerminalPercent, candidate.ivaTerminalPercent, now, changedBy)
            auditField("monthlySalesEstimate", existing.monthlySalesEstimate, candidate.monthlySalesEstimate, now, changedBy)
            auditField("operativosLocalPercent", existing.operativosLocalPercent, candidate.operativosLocalPercent, now, changedBy)
            auditField("posnet3CuotasPercent", existing.posnet3CuotasPercent, candidate.posnet3CuotasPercent, now, changedBy)
            auditField("transferenciaRetencionPercent", existing.transferenciaRetencionPercent, candidate.transferenciaRetencionPercent, now, changedBy)
            auditField("gainTargetPercent", existing.gainTargetPercent, candidate.gainTargetPercent, now, changedBy)
            auditField("mlCommissionPercent", existing.mlCommissionPercent, candidate.mlCommissionPercent, now, changedBy)
            auditField("mlCuotas3Percent", existing.mlCuotas3Percent, candidate.mlCuotas3Percent, now, changedBy)
            auditField("mlCuotas6Percent", existing.mlCuotas6Percent, candidate.mlCuotas6Percent, now, changedBy)
            auditField("mlGainMinimum", existing.mlGainMinimum, candidate.mlGainMinimum, now, changedBy)
            auditField("mlShippingThreshold", existing.mlShippingThreshold, candidate.mlShippingThreshold, now, changedBy)
            auditField("mlDefaultWeightKg", existing.mlDefaultWeightKg, candidate.mlDefaultWeightKg, now, changedBy)
            auditField("coefficient0To1500Percent", existing.coefficient0To1500Percent, candidate.coefficient0To1500Percent, now, changedBy)
            auditField("coefficient1501To3000Percent", existing.coefficient1501To3000Percent, candidate.coefficient1501To3000Percent, now, changedBy)
            auditField("coefficient3001To5000Percent", existing.coefficient3001To5000Percent, candidate.coefficient3001To5000Percent, now, changedBy)
            auditField("coefficient5001To7500Percent", existing.coefficient5001To7500Percent, candidate.coefficient5001To7500Percent, now, changedBy)
            auditField("coefficient7501To10000Percent", existing.coefficient7501To10000Percent, candidate.coefficient7501To10000Percent, now, changedBy)
            auditField("coefficient10001PlusPercent", existing.coefficient10001PlusPercent, candidate.coefficient10001PlusPercent, now, changedBy)
            auditField("recalcIntervalMinutes", existing.recalcIntervalMinutes, candidate.recalcIntervalMinutes, now, changedBy)
        }
        syncPricingConfigToCloud()
    }

    private suspend fun syncPricingConfigToCloud() {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val settings = pricingSettingsDao.getOnce() ?: return
            val fixedCosts = pricingFixedCostDao.getAllOnce()
            val mlFixedTiers = pricingMlFixedCostTierDao.getAllOnce()
            val mlShippingTiers = pricingMlShippingTierDao.getAllOnce()
            val payload = mapOf(
                "tenantId" to tenantId,
                "settings" to mapOf(
                    "ivaTerminalPercent" to settings.ivaTerminalPercent,
                    "monthlySalesEstimate" to settings.monthlySalesEstimate,
                    "operativosLocalPercent" to settings.operativosLocalPercent,
                    "posnet3CuotasPercent" to settings.posnet3CuotasPercent,
                    "transferenciaRetencionPercent" to settings.transferenciaRetencionPercent,
                    "gainTargetPercent" to settings.gainTargetPercent,
                    "mlCommissionPercent" to settings.mlCommissionPercent,
                    "mlCuotas3Percent" to settings.mlCuotas3Percent,
                    "mlCuotas6Percent" to settings.mlCuotas6Percent,
                    "mlGainMinimum" to settings.mlGainMinimum,
                    "mlShippingThreshold" to settings.mlShippingThreshold,
                    "mlDefaultWeightKg" to settings.mlDefaultWeightKg,
                    "coefficient0To1500Percent" to settings.coefficient0To1500Percent,
                    "coefficient1501To3000Percent" to settings.coefficient1501To3000Percent,
                    "coefficient3001To5000Percent" to settings.coefficient3001To5000Percent,
                    "coefficient5001To7500Percent" to settings.coefficient5001To7500Percent,
                    "coefficient7501To10000Percent" to settings.coefficient7501To10000Percent,
                    "coefficient10001PlusPercent" to settings.coefficient10001PlusPercent,
                    "recalcIntervalMinutes" to settings.recalcIntervalMinutes,
                    "updatedBy" to settings.updatedBy
                ),
                "fixedCosts" to fixedCosts.map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "description" to it.description,
                        "amount" to it.amount,
                        "applyIva" to it.applyIva
                    )
                },
                "mlFixedCostTiers" to mlFixedTiers.map {
                    mapOf("id" to it.id, "maxPrice" to it.maxPrice, "cost" to it.cost)
                },
                "mlShippingTiers" to mlShippingTiers.map {
                    mapOf("id" to it.id, "maxWeightKg" to it.maxWeightKg, "cost" to it.cost)
                },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("tenants")
                .document(tenantId)
                .collection("config")
                .document("pricing")
                .set(payload)
                .await()
        }
    }

    private suspend fun auditField(
        field: String,
        oldValue: Any,
        newValue: Any,
        changedAt: Instant,
        changedBy: String
    ) {
        if (oldValue != newValue) {
            audit(
                scope = "settings",
                itemId = 1,
                field = field,
                oldValue = oldValue.toString(),
                newValue = newValue.toString(),
                changedAt = changedAt,
                changedBy = changedBy
            )
        }
    }

    private suspend fun audit(
        scope: String,
        itemId: Int?,
        field: String,
        oldValue: String?,
        newValue: String?,
        changedAt: Instant,
        changedBy: String
    ) {
        pricingAuditDao.insert(
            PricingAuditEntity(
                scope = scope,
                itemId = itemId,
                field = field,
                oldValue = oldValue,
                newValue = newValue,
                changedAt = changedAt,
                changedBy = changedBy
            )
        )
    }

    private suspend fun ensureDefaultsIfNeeded() {
        val settings = pricingSettingsDao.getOnce()
        if (settings == null) {
            pricingSettingsDao.upsert(defaultSettings())
        }
        if (pricingFixedCostDao.getAllOnce().isEmpty()) {
            defaultFixedCosts().forEach { pricingFixedCostDao.upsert(it) }
        }
        if (pricingMlFixedCostTierDao.getAllOnce().isEmpty()) {
            defaultMlFixedCostTiers().forEach { pricingMlFixedCostTierDao.upsert(it) }
        }
        if (pricingMlShippingTierDao.getAllOnce().isEmpty()) {
            defaultMlShippingTiers().forEach { pricingMlShippingTierDao.upsert(it) }
        }
    }

    private fun defaultSettings(): PricingSettingsEntity = PricingSettingsEntity(
        ivaTerminalPercent = 21.0,
        monthlySalesEstimate = 400,
        operativosLocalPercent = 3.0,
        posnet3CuotasPercent = 12.22,
        transferenciaRetencionPercent = 5.0,
        gainTargetPercent = 50.0,
        mlCommissionPercent = 15.5,
        mlCuotas3Percent = 8.2,
        mlCuotas6Percent = 12.7,
        mlGainMinimum = 0.0,
        mlShippingThreshold = 33000.0,
        mlDefaultWeightKg = 0.3,
        coefficient0To1500Percent = 15.0,
        coefficient1501To3000Percent = 30.0,
        coefficient3001To5000Percent = 40.0,
        coefficient5001To7500Percent = 45.0,
        coefficient7501To10000Percent = 65.0,
        coefficient10001PlusPercent = 100.0,
        recalcIntervalMinutes = 30,
        updatedAt = Instant.now(),
        updatedBy = "System"
    )

    private fun defaultFixedCosts(): List<PricingFixedCostEntity> = listOf(
        PricingFixedCostEntity(name = "Alquiler", description = "Alquiler del local", amount = 350000.0),
        PricingFixedCostEntity(name = "Sueldos", description = "Sueldos", amount = 1500000.0),
        PricingFixedCostEntity(name = "Internet", description = "Internet", amount = 80000.0),
        PricingFixedCostEntity(name = "Luz", description = "Luz", amount = 100000.0),
        PricingFixedCostEntity(name = "Monotributo", description = "Monotributo", amount = 100000.0),
        PricingFixedCostEntity(name = "Insumos", description = "Insumos", amount = 200000.0),
        PricingFixedCostEntity(
            name = "Terminal Posnet",
            description = "Terminal Posnet",
            amount = 50000.0,
            applyIva = true
        )
    )

    private fun defaultMlFixedCostTiers(): List<PricingMlFixedCostTierEntity> = listOf(
        PricingMlFixedCostTierEntity(maxPrice = 15000.0, cost = 1155.0),
        PricingMlFixedCostTierEntity(maxPrice = 25000.0, cost = 2300.0),
        PricingMlFixedCostTierEntity(maxPrice = 33000.0, cost = 2810.0)
    )

    private fun defaultMlShippingTiers(): List<PricingMlShippingTierEntity> = listOf(
        PricingMlShippingTierEntity(maxWeightKg = 0.3, cost = 6364.79),
        PricingMlShippingTierEntity(maxWeightKg = 0.5, cost = 6883.19),
        PricingMlShippingTierEntity(maxWeightKg = 1.0, cost = 7717.19),
        PricingMlShippingTierEntity(maxWeightKg = 2.0, cost = 8234.39),
        PricingMlShippingTierEntity(maxWeightKg = 5.0, cost = 10952.39),
        PricingMlShippingTierEntity(maxWeightKg = 10.0, cost = 13007.99)
    )
}
