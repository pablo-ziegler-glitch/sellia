package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.ProductImageEntity

@Dao
interface ProductImageDao {

    @Query("SELECT * FROM product_images WHERE productId = :productId ORDER BY position ASC")
    suspend fun getByProductId(productId: Int): List<ProductImageEntity>

    @Query("SELECT * FROM product_images WHERE productId IN (:productIds) ORDER BY productId ASC, position ASC")
    suspend fun getByProductIds(productIds: List<Int>): List<ProductImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ProductImageEntity>)

    @Query("DELETE FROM product_images WHERE productId = :productId")
    suspend fun deleteByProductId(productId: Int): Int

    @Update
    suspend fun updateAll(images: List<ProductImageEntity>): Int

    @Query("UPDATE product_images SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Int, position: Int): Int
}
