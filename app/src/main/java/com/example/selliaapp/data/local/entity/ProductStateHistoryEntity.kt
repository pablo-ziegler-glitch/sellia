package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_state_history",
    indices = [
        Index(value = ["productUuid"]),
        Index(value = ["recordedAtEpochMs"]),
        Index(value = ["source"]),
        Index(value = ["reason"])
    ]
)
data class ProductStateHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Int? = null,
    val productUuid: String,
    val legacyLocalId: Int? = null,
    val snapshotJson: String,
    val source: String,
    val reason: String,
    val supersededByProductUuid: String? = null,
    val remoteDocumentId: String? = null,
    val recordedAtEpochMs: Long = System.currentTimeMillis()
)
