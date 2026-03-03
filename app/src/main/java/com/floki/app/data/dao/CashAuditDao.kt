package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.floki.app.data.local.entity.CashAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: CashAuditEntity)

    @Query("SELECT * FROM cash_audits WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<CashAuditEntity>>

    @Query("SELECT * FROM cash_audits WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun listBySession(sessionId: String): List<CashAuditEntity>
}
