package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_service_configs")
data class CloudServiceConfigEntity(
    @PrimaryKey
    val ownerEmail: String,
    val cloudEnabled: Boolean,
    val firestoreBackupEnabled: Boolean,
    val authSyncEnabled: Boolean,
    val storageBackupEnabled: Boolean,
    val functionsEnabled: Boolean,
    val hostingEnabled: Boolean,
    val updatedAt: Long
)
