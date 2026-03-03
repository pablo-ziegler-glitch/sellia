package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.floki.app.data.local.entity.DevelopmentOptionsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DevelopmentOptionsDao {
    @Query("SELECT * FROM development_options_configs ORDER BY ownerEmail ASC")
    fun observeAll(): Flow<List<DevelopmentOptionsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DevelopmentOptionsEntity)
}
