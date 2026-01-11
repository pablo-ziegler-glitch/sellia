package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.ExpenseBudgetDao
import com.example.selliaapp.data.dao.ExpenseRecordDao
import com.example.selliaapp.data.dao.ExpenseTemplateDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.model.CashflowMonth
import com.example.selliaapp.data.model.ExpenseCategoryBudget
import com.example.selliaapp.data.model.ExpenseCategoryComparison
import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseStatus
import com.example.selliaapp.data.model.ExpenseTemplate
import com.example.selliaapp.data.model.ProviderInvoiceStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val tDao: ExpenseTemplateDao,
    private val rDao: ExpenseRecordDao,
    private val bDao: ExpenseBudgetDao,
    private val invoiceDao: InvoiceDao,
    private val providerInvoiceDao: ProviderInvoiceDao
) {
    // Plantillas
    fun observeTemplates(): Flow<List<ExpenseTemplate>> = tDao.observeAll()
    suspend fun upsertTemplate(t: ExpenseTemplate) = tDao.upsert(t)
    suspend fun deleteTemplate(t: ExpenseTemplate) = tDao.delete(t)

    // Registros (instancias)
    fun observeRecords(
        name: String?,
        month: Int?,
        year: Int?,
        status: ExpenseStatus?
    ): Flow<List<ExpenseRecord>> = rDao.observeFiltered(name, month, year, status)

    suspend fun upsertRecord(r: ExpenseRecord) = rDao.upsert(r)
    suspend fun deleteRecord(r: ExpenseRecord) = rDao.delete(r)

    // Presupuestos por categoría
    fun observeBudgets(month: Int, year: Int): Flow<List<ExpenseCategoryBudget>> =
        bDao.observeByMonth(month, year)

    suspend fun upsertBudget(budget: ExpenseCategoryBudget) = bDao.upsert(budget)
    suspend fun deleteBudget(budget: ExpenseCategoryBudget) = bDao.delete(budget)

    // Comparativas mensuales por categoría
    suspend fun getMonthlyCategoryComparison(
        month: Int,
        year: Int
    ): List<ExpenseCategoryComparison> {
        val current = rDao.sumByCategory(month, year)
        val (prevMonth, prevYear) = if (month == 1) 12 to (year - 1) else (month - 1) to year
        val previous = rDao.sumByCategory(prevMonth, prevYear)
        val currentMap = current.associateBy { it.category }
        val previousMap = previous.associateBy { it.category }
        val categories = (currentMap.keys + previousMap.keys).sorted()
        return categories.map { category ->
            val currentTotal = currentMap[category]?.total ?: 0.0
            val previousTotal = previousMap[category]?.total ?: 0.0
            ExpenseCategoryComparison(
                category = category,
                currentTotal = currentTotal,
                previousTotal = previousTotal,
                delta = currentTotal - previousTotal
            )
        }
    }

    // Reporte combinado ventas + gastos + proveedores (cashflow)
    suspend fun getCashflowReport(): List<CashflowMonth> {
        val sales = invoiceDao.sumSalesByMonth().associateBy { it.year to it.month }
        val expenses = rDao.sumByMonth().associateBy { it.year to it.month }
        val providers = providerInvoiceDao
            .sumPaidByMonth(ProviderInvoiceStatus.PAGA)
            .associateBy { it.year to it.month }

        val keys = (sales.keys + expenses.keys + providers.keys).toSortedSet(compareByDescending<Pair<Int, Int>> { it.first }
            .thenByDescending { it.second })

        return keys.map { key ->
            CashflowMonth(
                year = key.first,
                month = key.second,
                salesTotal = sales[key]?.total ?: 0.0,
                expenseTotal = expenses[key]?.total ?: 0.0,
                providerTotal = providers[key]?.total ?: 0.0
            )
        }
    }
}
