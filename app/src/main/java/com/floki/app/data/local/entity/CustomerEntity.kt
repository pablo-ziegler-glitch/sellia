package com.floki.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entidad Room para clientes. Solo esta clase tiene @Entity.
 * Si ya tenías un Customer @Entity en data/model, QUITAR esa anotación allí.
 */
@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"], unique = false)
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val nickname: String? = null,

    // Campos que vi aparecer en consultas de provider; ajustá según tu schema real de customer
    val rubrosCsv: String? = null,
    val paymentTerm: String? = null,
    val paymentMethod: String? = null,

    // Timestamp de creación. Requiere TypeConverters para LocalDateTime.
    val createdAt: LocalDateTime = LocalDateTime.now()
)
