package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Entidad Room para productos con índices y restricciones de unicidad.
 * - UNIQUE(barcode) y UNIQUE(code) evitan duplicados.
 * - Índice por nombre para búsqueda rápida.
 * - quantity/minStock se mantienen >= 0 por lógica de DAO/Repo y (opcional) por CHECK SQL en migraciones.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["code"], unique = true),
        Index(value = ["name"]),
        Index(value = ["categoryId"]),
        Index(value = ["providerId"])
    ]
)
data class ProductEntity(
    // PK autogenerada. Para altas nuevas usá id=0 (Room la genera).
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Claves de identificación
    val code: String? = null,
    val barcode: String? = null,

    // Descriptivo
    val name: String,

    // E4: precio/impuesto (normalizado)
    val basePrice: Double? = null,   // precio neto (sin impuesto)
    val taxRate: Double? = null,     // 0..1 (ej. 0.21 para 21%)
    val finalPrice: Double? = null,  // base * (1 + tax)

    // Legacy: mantenemos por compatibilidad con flows antiguos
    val price: Double? = null,
    val listPrice: Double? = null,
    val cashPrice: Double? = null,
    val transferPrice: Double? = null,
    val mlPrice: Double? = null,
    val ml3cPrice: Double? = null,
    val ml6cPrice: Double? = null,

    // Stock total (cuando no hay variantes / o suma derivada)
    val quantity: Int = 0,

    // Metadatos
    val description: String? = null,
    val imageUrl: String? = null,

    // E1: normalización
    val categoryId: Int? = null,
    val providerId: Int? = null,
    // 🔧 Ahora con valor por defecto (evita TODO() en creaciones)
    val providerName: String? = null,
    val providerSku: String? = null,

    // Legacy de UI (puede usarse para mostrados rápidos)
    val category: String? = null,
    val minStock: Int? = null,

    // Auditoría simple
    val updatedAt: LocalDate = LocalDate.now()
)
