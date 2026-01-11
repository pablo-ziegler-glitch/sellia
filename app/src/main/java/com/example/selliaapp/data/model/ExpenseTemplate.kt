package com.example.selliaapp.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Plantilla de gasto: define el "tipo" de gasto.
 * - defaultAmount: opcional, sugerencia para cada carga.
 * - required: si es un gasto obligatorio del negocio.
 */
@Entity(tableName = "expense_templates")
data class ExpenseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String = "General",
    val defaultAmount: Double? = null,
    val required: Boolean = false
)
