package com.floki.app.data.dao


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.floki.app.data.local.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    fun getAll():  List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Int): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity): Int

    @Delete
    suspend fun delete(provider: ProviderEntity): Int

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("SELECT name FROM providers")
    fun observeAllNames(): Flow<List<String>>


    @Transaction
    suspend fun upsert(provider: ProviderEntity): Int {
        return if (provider.id == 0) {
            insert(provider).toInt()
        } else {
            update(provider)
            provider.id
        }
    }




}