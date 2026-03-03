package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.floki.app.data.local.entity.ReportDataEntity
import com.floki.app.data.local.entity.ReportScope
import com.floki.app.data.model.ReportPoint

@Dao
interface ReportDataDao {

    // --- Lectura mapeando directo a DTO para la UI ---
    @Query("SELECT * FROM report_data WHERE scope = :scope")
    suspend fun getByScope(scope: String): List<ReportDataEntity>

    // --- CRUD base ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReportDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ReportDataEntity>)

    @Query("DELETE FROM report_data WHERE scope = :scope")
    suspend fun deleteByScope(scope: String)

    @Query("DELETE FROM report_data")
    suspend fun clearAll()

    // --- Reemplazo atómico de un scope ---
    @Transaction
    suspend fun replaceScopeData(scope: ReportScope, models: List<ReportPoint>) {
        deleteByScope(scope.toString())
        insertAll(models.map { ReportDataEntity(scope = scope, label = it.label, amount = it.amount) })
    }
}
