package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "cash_movements",
    foreignKeys = [
        ForeignKey(
            entity = CashSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"])
    ]
)
data class CashMovementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val amount: Double,
    val note: String? = null,
    val createdAt: Instant,
    val referenceId: String? = null
)

object CashMovementType {
    const val SALE_CASH = "SALE_CASH"
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val ADJUSTMENT = "ADJUSTMENT"
}
