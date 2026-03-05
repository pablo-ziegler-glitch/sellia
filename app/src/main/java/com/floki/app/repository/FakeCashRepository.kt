package com.floki.app.repository

import com.floki.app.data.local.entity.CashAuditEntity
import com.floki.app.data.local.entity.CashMovementEntity
import com.floki.app.data.local.entity.CashSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake simple para tests unitarios.
 * Implementa lo mínimo para que SellViewModel compile y los tests de escaneo/carrito funcionen.
 */
class FakeCashRepository : CashRepository {

    override fun observeOpenSession(): Flow<CashSessionEntity?> = flowOf(null)

    override fun observeOpenSessionSummary(): Flow<CashSessionSummary?> = flowOf(null)

    override suspend fun getOpenSession(): CashSessionEntity? = null

    override suspend fun openSession(
        openingAmount: Double,
        note: String?,
        openedBy: String?
    ): CashSessionEntity = error("FakeCashRepository.openSession no implementado para este test")

    override suspend fun registerMovement(
        sessionId: String,
        type: String,
        amount: Double,
        note: String?,
        referenceId: String?
    ): CashMovementEntity = error("FakeCashRepository.registerMovement no implementado para este test")

    override suspend fun registerAudit(
        sessionId: String,
        countedAmount: Double,
        note: String?
    ): CashAuditEntity = error("FakeCashRepository.registerAudit no implementado para este test")

    override suspend fun closeSession(
        sessionId: String,
        closingAmount: Double?,
        note: String?
    ) = error("FakeCashRepository.closeSession no implementado para este test")
}
