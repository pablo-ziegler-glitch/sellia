package com.floki.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Presupuesto mensual por categoría de gasto.
 */
@Entity(
    tableName = "expense_category_budgets",
    indices = [Index(value = ["category", "month", "year"], unique = true)]
)
data class ExpenseCategoryBudget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val month: Int,
    val year: Int,
    val amount: Double
)
