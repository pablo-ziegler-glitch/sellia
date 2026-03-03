package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.floki.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
     fun getAll(): List<CategoryEntity>

    @Query("SELECT name FROM categories")
    fun observeAllNames(): Flow<List<String>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    fun observeByName(name: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Delete
    suspend fun delete(category: CategoryEntity): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Transaction
    suspend fun upsert(category: CategoryEntity): Int {
        return if (category.id == 0) {
            insert(category).toInt()
        } else {
            update(category)
            category.id
        }
    }



}
