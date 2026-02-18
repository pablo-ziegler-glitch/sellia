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

    // Costo de adquisición al proveedor (para auto cálculo de precios)
    val purchasePrice: Double? = null,

    // Legacy: mantenemos por compatibilidad con flows antiguos
    val listPrice: Double? = null,
    val cashPrice: Double? = null,
    val transferPrice: Double? = null,
    val transferNetPrice: Double? = null,
    val mlPrice: Double? = null,
    val ml3cPrice: Double? = null,
    val ml6cPrice: Double? = null,
    val autoPricing: Boolean = false,

    // Stock total (cuando no hay variantes / o suma derivada)
    val quantity: Int = 0,

    // Metadatos
    val description: String? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),

    // E1: normalización
    val categoryId: Int? = null,
    val providerId: Int? = null,
    // 🔧 Ahora con valor por defecto (evita TODO() en creaciones)
    val providerName: String? = null,
    val providerSku: String? = null,
    val brand: String? = null,

    // Legacy de UI (se mantiene compatibilidad: category = subcategoría)
    val parentCategory: String? = null,
    val category: String? = null,
    val color: String? = null,
    val sizes: List<String> = emptyList(),
    val minStock: Int? = null,

    // Publicación catálogo web
    val publicStatus: String = "draft",

    // Auditoría simple
    val updatedAt: LocalDate = LocalDate.now()
)
