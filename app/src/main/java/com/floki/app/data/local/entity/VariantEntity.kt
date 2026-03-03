package com.floki.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Variante de producto (E2):
 * - stock por variante
 * - precio por variante (override). Si null, hereda precio del producto.
 * - opción 1/2 para atributos (talle, color, presentación).
 */
@Entity(
    tableName = "variants",
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
        Index(value = ["sku"], unique = true)
    ]
)
data class VariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val sku: String?,                 // SKU específico variante
    val option1: String?,             // ej: Talle M
    val option2: String?,             // ej: Color Rojo
    val quantity: Int = 0,            // stock de la variante
    val basePrice: Double? = null,    // override (E4)
    val taxRate: Double? = null,      // override
    val finalPrice: Double? = null,   // override
    val updatedAt: LocalDate = LocalDate.now()
)
