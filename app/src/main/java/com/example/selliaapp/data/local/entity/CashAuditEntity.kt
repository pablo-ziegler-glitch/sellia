package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "cash_audits",
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
data class CashAuditEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val countedAmount: Double,
    val difference: Double,
    val note: String? = null,
    val createdAt: Instant
)
