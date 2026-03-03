package com.floki.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para Proveedor.
 * ESTA es la única clase que declara la tabla "providers".
 */
@Entity(
    tableName = "providers",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String? = null,
    val rubrosCsv: String? = null,
    val paymentTerm: String? = null,
    val paymentMethod: String? = null
)
