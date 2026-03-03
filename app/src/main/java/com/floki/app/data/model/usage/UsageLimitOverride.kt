package com.floki.app.data.model.usage

data class UsageLimitOverride(
    val metric: String,
    val limitValue: Double,
    val updatedAtMillis: Long?,
    val updatedBy: String?
)
