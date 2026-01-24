package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "cash_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["openedAt"])
    ]
)
data class CashSessionEntity(
    @PrimaryKey val id: String,
    val openedAt: Instant,
    val closedAt: Instant? = null,
    val openingAmount: Double,
    val expectedAmount: Double? = null,
    val status: String,
    val openedBy: String? = null,
    val note: String? = null,
    val closingAmount: Double? = null,
    val closingNote: String? = null
)

object CashSessionStatus {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
}
