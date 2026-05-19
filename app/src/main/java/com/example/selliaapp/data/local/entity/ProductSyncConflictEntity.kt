package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_sync_conflicts",
    indices = [
        Index(value = ["localProductId"]),
        Index(value = ["localProductUuid"]),
        Index(value = ["remoteProductUuid"]),
        Index(value = ["resolutionStatus"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class ProductSyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localProductId: Int? = null,
    val localProductUuid: String? = null,
    val remoteProductUuid: String? = null,
    val remoteDocumentId: String? = null,
    val conflictType: String,
    val detailsJson: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val resolvedAtEpochMs: Long? = null,
    val resolutionStatus: String = "PENDING_REVIEW"
)
