package com.example.selliaapp.data.model

data class CashflowMonth(
    val year: Int,
    val month: Int,
    val salesTotal: Double,
    val expenseTotal: Double,
    val providerTotal: Double
) {
    val netTotal: Double = salesTotal - expenseTotal - providerTotal
}

data class ExpenseCategoryComparison(
    val category: String,
    val currentTotal: Double,
    val previousTotal: Double,
    val delta: Double
)
