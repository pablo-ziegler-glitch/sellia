// File: CustomerDao.kt
package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.selliaapp.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CustomerEntity?

    @Query("SELECT name FROM customers WHERE id = :id")
    suspend fun getNameById(id: Int): String?  // ⬅️ usado por InvoiceRepository

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :term || '%' ORDER BY name")
    fun search(term: String?): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CustomerEntity): Long

    @Update
    suspend fun update(entity: CustomerEntity): Int

    @Delete
    suspend fun delete(entity: CustomerEntity): Int

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Transaction
    suspend fun upsert(entity: CustomerEntity): Int {
        return if (entity.id == 0) {
            insert(entity).toInt()
        } else {
            update(entity)
            entity.id
        }
    }
    /**
     * Cuenta clientes cuya fecha de creación esté entre start y end (epoch millis).
     * NOTA: CustomerEntity.createdAt es LocalDateTime persistido como Long vía Converters.
     */
    @Query("SELECT COUNT(*) FROM customers WHERE strftime('%s', createdAt/1000, 'unixepoch')*1000 BETWEEN :start AND :end")
    suspend fun countBetweenMillis(start: Long, end: Long): Int
}
