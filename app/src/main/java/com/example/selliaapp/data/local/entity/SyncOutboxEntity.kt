package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entrada de outbox para sincronización pendiente.
 * Permite reintentar subidas a Firestore en background.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        Index(value = ["entityType", "entityUuid", "operation"], unique = true)
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String,
    val entityId: Long,
    val entityUuid: String? = null,
    val operation: String = SyncOutboxOperation.UPSERT.storageKey,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "attempts") val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null
)

enum class SyncOutboxOperation(val storageKey: String) {
    UPSERT("UPSERT"),
    MARK_DELETED("MARK_DELETED");
}

enum class SyncEntityType(val storageKey: String) {
    PRODUCT("product"),
    INVOICE("invoice"),
    CUSTOMER("customer"),
    PRICING_CONFIG("pricing_config");
}
