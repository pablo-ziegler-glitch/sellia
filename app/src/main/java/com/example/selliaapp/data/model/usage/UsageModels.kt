package com.example.selliaapp.data.model.usage

import java.time.Instant
import java.time.LocalDate

/**
 * Punto temporal de consumo (por día/hora según la granularidad almacenada).
 */
data class UsageSeriesPoint(
    val date: LocalDate,
    val value: Double
)

/**
 * Resumen por servicio/app para el período consultado.
 */
data class UsageServiceSummary(
    val serviceName: String,
    val appName: String,
    val total: Double,
    val trendPercent: Double,
    val sharePercent: Double
)

/**
 * Snapshot completo del dashboard de consumo.
 */
data class UsageDashboardSnapshot(
    val from: LocalDate,
    val to: LocalDate,
    val total: Double,
    val series: List<UsageSeriesPoint>,
    val services: List<UsageServiceSummary>,
    val lastUpdated: Instant? = null
)
