package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.dao.CashAuditDao
import com.example.selliaapp.data.dao.CashMovementDao
import com.example.selliaapp.data.dao.CashSessionDao
import com.example.selliaapp.data.local.entity.CashAuditEntity
import com.example.selliaapp.data.local.entity.CashMovementEntity
import com.example.selliaapp.data.local.entity.CashMovementType
import com.example.selliaapp.data.local.entity.CashSessionEntity
import com.example.selliaapp.data.local.entity.CashSessionStatus
import com.example.selliaapp.repository.CashCalculations
import com.example.selliaapp.repository.CashRepository
import com.example.selliaapp.repository.CashSessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashRepositoryImpl @Inject constructor(
    private val cashSessionDao: CashSessionDao,
    private val cashMovementDao: CashMovementDao,
    private val cashAuditDao: CashAuditDao
) : CashRepository {
    override fun observeOpenSession(): Flow<CashSessionEntity?> = cashSessionDao.observeOpenSession()

    override fun observeOpenSessionSummary(): Flow<CashSessionSummary?> {
        return cashSessionDao.observeOpenSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    cashMovementDao.observeBySession(session.id),
                    cashAuditDao.observeBySession(session.id)
                ) { movements, audits ->
                    val expected = CashCalculations.expectedAmount(session.openingAmount, movements)
                    val cashSales = movements
                        .filter { it.type == CashMovementType.SALE_CASH }
                        .sumOf { it.amount }
                    CashSessionSummary(
                        session = session,
                        movements = movements,
                        audits = audits,
                        expectedAmount = expected,
                        cashSalesTotal = cashSales
                    )
                }
            }
        }
    }

    override suspend fun getOpenSession(): CashSessionEntity? = cashSessionDao.getOpenSession()

    override suspend fun openSession(
        openingAmount: Double,
        note: String?,
        openedBy: String?
    ): CashSessionEntity {
        val session = CashSessionEntity(
            id = UUID.randomUUID().toString(),
            openedAt = Instant.now(),
            openingAmount = openingAmount,
            expectedAmount = openingAmount,
            status = CashSessionStatus.OPEN,
            openedBy = openedBy,
            note = note
        )
        cashSessionDao.insert(session)
        return session
    }

    override suspend fun registerMovement(
        sessionId: String,
        type: String,
        amount: Double,
        note: String?,
        referenceId: String?
    ): CashMovementEntity {
        val movement = CashMovementEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            type = type,
            amount = amount,
            note = note,
            createdAt = Instant.now(),
            referenceId = referenceId
        )
        cashMovementDao.insert(movement)
        return movement
    }

    override suspend fun registerAudit(
        sessionId: String,
        countedAmount: Double,
        note: String?
    ): CashAuditEntity {
        val movements = cashMovementDao.listBySession(sessionId)
        val session = cashSessionDao.getOpenSession()
        val openingAmount = session?.openingAmount ?: 0.0
        val expected = CashCalculations.expectedAmount(openingAmount, movements)
        val audit = CashAuditEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            countedAmount = countedAmount,
            difference = countedAmount - expected,
            note = note,
            createdAt = Instant.now()
        )
        cashAuditDao.insert(audit)
        return audit
    }

    override suspend fun closeSession(
        sessionId: String,
        closingAmount: Double?,
        note: String?
    ) {
        val movements = cashMovementDao.listBySession(sessionId)
        val session = cashSessionDao.getOpenSession()
        val openingAmount = session?.openingAmount ?: 0.0
        val expected = CashCalculations.expectedAmount(openingAmount, movements)
        cashSessionDao.closeSession(
            sessionId = sessionId,
            closedAt = Instant.now().toEpochMilli(),
            status = CashSessionStatus.CLOSED,
            expectedAmount = expected,
            closingAmount = closingAmount,
            closingNote = note
        )
    }
}
