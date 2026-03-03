package com.floki.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "report_data",
    indices = [Index(value = ["scope"])]
)
data class ReportDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scope") val scope: ReportScope, // Día/Semana/Mes
    @ColumnInfo(name = "label") val label: String,      // Etiqueta visible (ej: "Lunes", "Semana 32", "Agosto")
    @ColumnInfo(name = "amount") val amount: Double,    // Monto total
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Scope del reporte (cómo agrupamos datos).
 */
enum class ReportScope { DIA, SEMANA, MES }
