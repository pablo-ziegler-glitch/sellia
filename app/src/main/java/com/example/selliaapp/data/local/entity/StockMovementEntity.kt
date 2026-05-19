package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Auditoría de movimientos de stock (entradas/salidas/ajustes).
 * Cada cambio de stock genera un movimiento (delta puede ser + o -).
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["productUuid"]),
        Index(value = ["ts"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Int,
    val productUuid: String? = null,
    val productLegacyLocalId: Int? = null,
    val delta: Int,             // >0 entrada, <0 salida
    val reason: String,         // ejemplo: "CSV_APPEND", "CSV_REPLACE", "SALE", "ADJUST", "SCAN_ADD"
    val ts: Instant = Instant.now(),
    val user: String? = null,   // opcional: operador
    val note: String? = null    // detalle libre del ajuste
)
