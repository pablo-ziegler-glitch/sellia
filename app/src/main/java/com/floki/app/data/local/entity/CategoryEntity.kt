package com.floki.app.data.local.entity


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catálogo de categorías normalizado.
 * - name UNIQUE para evitar duplicados.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)