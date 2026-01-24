package com.example.selliaapp.repository

import com.example.selliaapp.data.local.entity.CashMovementEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CashCalculationsTest {
    @Test
    fun expectedAmountIncludesMovements() {
        val movements = listOf(
            CashMovementEntity(
                id = "1",
                sessionId = "S1",
                type = "SALE_CASH",
                amount = 1500.0,
                note = null,
                createdAt = Instant.now(),
                referenceId = "10"
            ),
            CashMovementEntity(
                id = "2",
                sessionId = "S1",
                type = "EXPENSE",
                amount = -200.0,
                note = "Gastos",
                createdAt = Instant.now(),
                referenceId = null
            )
        )

        val expected = CashCalculations.expectedAmount(1000.0, movements)

        assertEquals(2300.0, expected, 0.01)
    }
}
