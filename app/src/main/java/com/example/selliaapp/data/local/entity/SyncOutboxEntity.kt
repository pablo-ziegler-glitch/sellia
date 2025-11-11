package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entrada de outbox para sincronización pendiente.
 * Permite reintentar subidas a Firestore en background.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["entityType", "entityId"], unique = true)]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String,
    val entityId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null
)

enum class SyncEntityType(val storageKey: String) {
    PRODUCT("product"),
    INVOICE("invoice");
}
