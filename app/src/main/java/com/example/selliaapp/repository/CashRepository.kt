package com.example.selliaapp.repository

import com.example.selliaapp.data.local.entity.CashAuditEntity
import com.example.selliaapp.data.local.entity.CashMovementEntity
import com.example.selliaapp.data.local.entity.CashSessionEntity
import kotlinx.coroutines.flow.Flow

interface CashRepository {
    fun observeOpenSession(): Flow<CashSessionEntity?>

    fun observeOpenSessionSummary(): Flow<CashSessionSummary?>

    suspend fun getOpenSession(): CashSessionEntity?

    suspend fun openSession(openingAmount: Double, note: String? = null, openedBy: String? = null): CashSessionEntity

    suspend fun registerMovement(
        sessionId: String,
        type: String,
        amount: Double,
        note: String? = null,
        referenceId: String? = null
    ): CashMovementEntity

    suspend fun registerAudit(
        sessionId: String,
        countedAmount: Double,
        note: String? = null
    ): CashAuditEntity

    suspend fun closeSession(
        sessionId: String,
        closingAmount: Double?,
        note: String? = null
    )
}

data class CashSessionSummary(
    val session: CashSessionEntity,
    val movements: List<CashMovementEntity>,
    val audits: List<CashAuditEntity>,
    val expectedAmount: Double,
    val cashSalesTotal: Double
)

object CashCalculations {
    fun expectedAmount(openingAmount: Double, movements: List<CashMovementEntity>): Double {
        return openingAmount + movements.sumOf { it.amount }
    }
}
