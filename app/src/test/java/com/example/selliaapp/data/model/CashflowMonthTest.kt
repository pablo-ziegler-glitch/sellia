package com.example.selliaapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CashflowMonthTest {
    @Test
    fun netTotal_subtractsExpensesAndProviders() {
        val month = CashflowMonth(
            year = 2024,
            month = 9,
            salesTotal = 1000.0,
            expenseTotal = 250.0,
            providerTotal = 100.0
        )

        assertEquals(650.0, month.netTotal, 0.0)
    }
}
