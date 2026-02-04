package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.DevelopmentOptionsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DevelopmentOptionsDao {
    @Query("SELECT * FROM development_options_configs ORDER BY ownerEmail ASC")
    fun observeAll(): Flow<List<DevelopmentOptionsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DevelopmentOptionsEntity)
}
