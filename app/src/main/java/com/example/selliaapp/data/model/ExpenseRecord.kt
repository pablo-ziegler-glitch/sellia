package com.example.selliaapp.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de gasto cargado (instancia mensual).
 * - templateId: referencia al tipo de gasto.
 * - month: 1..12, year: YYYY
 * - status: PAGO / IMPAGO / EN_PROCESO / EN_MORA
 */
@Entity(tableName = "expense_records")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateId: Int,
    val nameSnapshot: String,   // guardamos el nombre del template en el momento
    val categorySnapshot: String = "General",
    val amount: Double,
    val month: Int,
    val year: Int,
    val status: ExpenseStatus = ExpenseStatus.IMPAGO,
    val receiptUris: List<String> = emptyList()
)

enum class ExpenseStatus { PAGO, IMPAGO, EN_PROCESO, EN_MORA }
