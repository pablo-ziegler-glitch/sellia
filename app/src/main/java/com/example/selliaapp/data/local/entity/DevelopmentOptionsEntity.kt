package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "development_options_configs")
data class DevelopmentOptionsEntity(
    @PrimaryKey
    val ownerEmail: String,
    val salesEnabled: Boolean,
    val stockEnabled: Boolean,
    val customersEnabled: Boolean,
    val providersEnabled: Boolean,
    val expensesEnabled: Boolean,
    val reportsEnabled: Boolean,
    val cashEnabled: Boolean,
    val usageAlertsEnabled: Boolean,
    val configEnabled: Boolean,
    val publicCatalogEnabled: Boolean,
    val updatedAt: Long
)
