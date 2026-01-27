package com.example.selliaapp.data.dao


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.model.ExpenseCategoryBudget
import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseStatus
import com.example.selliaapp.data.model.ExpenseTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseTemplateDao {
    @Query("SELECT * FROM expense_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExpenseTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: ExpenseTemplate): Long

    @Delete
    suspend fun delete(t: ExpenseTemplate): Int
}

@Dao
interface ExpenseRecordDao {
    @Query("""
        SELECT * FROM expense_records 
        WHERE (:name IS NULL OR nameSnapshot LIKE '%' || :name || '%')
          AND (:month IS NULL OR month = :month)
          AND (:year IS NULL OR year = :year)
          AND (:status IS NULL OR status = :status)
        ORDER BY year DESC, month DESC, id DESC
    """)
    fun observeFiltered(
        name: String?,
        month: Int?,
        year: Int?,
        status: ExpenseStatus?
    ): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records ORDER BY year DESC, month DESC, id DESC")
    suspend fun getAllOnce(): List<ExpenseRecord>

    @Query("""
        SELECT categorySnapshot AS category,
               SUM(amount) AS total
        FROM expense_records
        WHERE month = :month AND year = :year
        GROUP BY categorySnapshot
        ORDER BY categorySnapshot COLLATE NOCASE
    """)
    suspend fun sumByCategory(month: Int, year: Int): List<CategoryTotal>

    @Query("""
        SELECT year AS year,
               month AS month,
               SUM(amount) AS total
        FROM expense_records
        GROUP BY year, month
        ORDER BY year DESC, month DESC
    """)
    suspend fun sumByMonth(): List<MonthlyTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ExpenseRecord): Long

    @Delete
    suspend fun delete(r: ExpenseRecord): Int
}

@Dao
interface ExpenseBudgetDao {
    @Query("""
        SELECT * FROM expense_category_budgets
        WHERE month = :month AND year = :year
        ORDER BY category COLLATE NOCASE
    """)
    fun observeByMonth(month: Int, year: Int): Flow<List<ExpenseCategoryBudget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: ExpenseCategoryBudget): Long

    @Delete
    suspend fun delete(budget: ExpenseCategoryBudget): Int
}
