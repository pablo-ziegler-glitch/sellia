package com.floki.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Modelo de datos para representar un punto en los reportes de ventas.
 *
 * @param label Etiqueta legible (ej. "Lunes", "Semana 32", "Agosto")
 * @param amount Monto de ventas en ese período.
 */
data class ReportPoint(
    val label: String,      // Ej: "10:00", "2025-08-26"
    val amount: Double,
    val date: LocalDate? = null,
    val dateTime: LocalDateTime? = null
)