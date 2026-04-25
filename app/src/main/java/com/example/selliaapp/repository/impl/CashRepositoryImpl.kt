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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
        recordSystemSnapshot(
            sessionId = session.id,
            expectedAmount = openingAmount,
            countedAmount = openingAmount,
            note = "SYSTEM|STATE=OPEN|openingAmount=$openingAmount|openedBy=${openedBy.orEmpty()}|note=${note.orEmpty()}"
        )
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
        val session = cashSessionDao.getById(sessionId)
        val movements = cashMovementDao.listBySession(sessionId)
        val openingAmount = session?.openingAmount ?: 0.0
        val expected = CashCalculations.expectedAmount(openingAmount, movements)
        recordSystemSnapshot(
            sessionId = sessionId,
            expectedAmount = expected,
            countedAmount = expected,
            note = "SYSTEM|STATE=MOVEMENT|type=$type|amount=$amount|reference=${referenceId.orEmpty()}|note=${note.orEmpty()}"
        )
        return movement
    }

    override suspend fun registerAudit(
        sessionId: String,
        countedAmount: Double,
        note: String?
    ): CashAuditEntity {
        val movements = cashMovementDao.listBySession(sessionId)
        val session = cashSessionDao.getById(sessionId)
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
        val session = cashSessionDao.getById(sessionId)
        val openingAmount = session?.openingAmount ?: 0.0
        val expected = CashCalculations.expectedAmount(openingAmount, movements)
        val counted = closingAmount ?: expected
        recordSystemSnapshot(
            sessionId = sessionId,
            expectedAmount = expected,
            countedAmount = counted,
            note = "SYSTEM|STATE=CLOSE|expected=$expected|counted=$counted|difference=${counted - expected}|note=${note.orEmpty()}"
        )
        cashSessionDao.closeSession(
            sessionId = sessionId,
            closedAt = Instant.now().toEpochMilli(),
            status = CashSessionStatus.CLOSED,
            expectedAmount = expected,
            closingAmount = closingAmount,
            closingNote = note
        )
    }

    override suspend fun closeOpenSessionWithCurrentBalance(note: String?): Boolean {
        val openSession = cashSessionDao.getOpenSession() ?: return false
        closeSession(
            sessionId = openSession.id,
            closingAmount = null,
            note = note
        )
        return true
    }

    private suspend fun recordSystemSnapshot(
        sessionId: String,
        expectedAmount: Double,
        countedAmount: Double,
        note: String
    ) {
        cashAuditDao.insert(
            CashAuditEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                countedAmount = countedAmount,
                difference = countedAmount - expectedAmount,
                note = note,
                createdAt = Instant.now()
            )
        )
    }
}
