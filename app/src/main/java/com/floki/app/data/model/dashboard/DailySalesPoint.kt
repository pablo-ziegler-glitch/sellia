package com.floki.app.data.model.dashboard

import java.time.LocalDate

/**
 * Punto de serie para resumir ventas diarias en el tablero.
 */
data class DailySalesPoint(
    val fecha: LocalDate,
    val total: Double
)
