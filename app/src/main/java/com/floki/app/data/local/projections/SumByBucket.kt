package com.floki.app.data.local.projections

/**
 * Proyección para reportes temporales (ej: por día).
 */
data class SumByBucket(
    val bucket: String,  // "2025-08-30"
    val amount: Double
)