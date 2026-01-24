package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.CashSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashSessionDao {
    @Query("SELECT * FROM cash_sessions WHERE status = 'OPEN' LIMIT 1")
    fun observeOpenSession(): Flow<CashSessionEntity?>

    @Query("SELECT * FROM cash_sessions WHERE status = 'OPEN' LIMIT 1")
    suspend fun getOpenSession(): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: String): Flow<CashSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CashSessionEntity)

    @Update
    suspend fun update(session: CashSessionEntity)

    @Query(
        """
        UPDATE cash_sessions
        SET closedAt = :closedAt,
            status = :status,
            expectedAmount = :expectedAmount,
            closingAmount = :closingAmount,
            closingNote = :closingNote
        WHERE id = :sessionId
        """
    )
    suspend fun closeSession(
        sessionId: String,
        closedAt: Long,
        status: String,
        expectedAmount: Double,
        closingAmount: Double?,
        closingNote: String?
    )
}
