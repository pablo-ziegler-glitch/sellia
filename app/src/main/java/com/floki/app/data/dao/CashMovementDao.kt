package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.floki.app.data.local.entity.CashMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashMovementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: CashMovementEntity)

    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun listBySession(sessionId: String): List<CashMovementEntity>
}
