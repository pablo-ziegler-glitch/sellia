package com.example.selliaapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.selliaapp.data.dao.CategoryDao
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.ExpenseBudgetDao
import com.example.selliaapp.data.dao.ExpenseRecordDao
import com.example.selliaapp.data.dao.ExpenseTemplateDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.ReportDataDao
import com.example.selliaapp.data.dao.StockMovementDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.data.local.converters.Converters
import com.example.selliaapp.data.local.converters.ReportConverters
import com.example.selliaapp.data.local.entity.CategoryEntity
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.data.local.entity.ReportDataEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.local.entity.VariantEntity
import com.example.selliaapp.data.model.ExpenseCategoryBudget
import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseTemplate
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.data.model.User

/**
 * Base de datos Room principal.
 * Aumentá version si cambiás esquemas.
 */
@Database(
    entities = [
        // Persistencia principal
        ProductEntity::class,
        CustomerEntity::class,
        ProviderEntity::class,
        ReportDataEntity::class,
        StockMovementEntity::class,
        CategoryEntity::class,
        VariantEntity::class,
        SyncOutboxEntity::class,

        // Tablas de negocio basadas en modelos (ya tienen @Entity)
        Invoice::class,
        InvoiceItem::class,
        ExpenseTemplate::class,
        ExpenseRecord::class,
        ExpenseCategoryBudget::class,
        ProviderInvoice::class,
        ProviderInvoiceItem::class,
        User::class
    ],
    version = 29,
    //autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
@TypeConverters(Converters::class, ReportConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceItemDao(): InvoiceItemDao
    abstract fun reportDataDao(): ReportDataDao
    abstract fun providerDao(): ProviderDao
    abstract fun providerInvoiceDao(): ProviderInvoiceDao
    abstract fun expenseTemplateDao(): ExpenseTemplateDao
    abstract fun expenseRecordDao(): ExpenseRecordDao
    abstract fun expenseBudgetDao(): ExpenseBudgetDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun categoryDao(): CategoryDao
    abstract fun variantDao(): VariantDao
    abstract fun syncOutboxDao(): SyncOutboxDao
}
