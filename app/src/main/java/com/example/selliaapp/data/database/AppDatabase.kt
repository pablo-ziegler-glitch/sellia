package com.example.selliaapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.selliaapp.data.dao.CategoryDao
import com.example.selliaapp.data.dao.CashAuditDao
import com.example.selliaapp.data.dao.CashMovementDao
import com.example.selliaapp.data.dao.CashSessionDao
import com.example.selliaapp.data.dao.CloudServiceConfigDao
 import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.DevelopmentOptionsDao
import com.example.selliaapp.data.dao.ExpenseBudgetDao
import com.example.selliaapp.data.dao.ExpenseRecordDao
import com.example.selliaapp.data.dao.ExpenseTemplateDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.PricingAuditDao
import com.example.selliaapp.data.dao.PricingFixedCostDao
import com.example.selliaapp.data.dao.PricingMlFixedCostTierDao
import com.example.selliaapp.data.dao.PricingMlShippingTierDao
import com.example.selliaapp.data.dao.PricingSettingsDao
import com.example.selliaapp.data.dao.ReportDataDao
import com.example.selliaapp.data.dao.StockMovementDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.data.local.converters.Converters
import com.example.selliaapp.data.local.converters.ReportConverters
import com.example.selliaapp.data.local.entity.CategoryEntity
import com.example.selliaapp.data.local.entity.CashAuditEntity
import com.example.selliaapp.data.local.entity.CashMovementEntity
import com.example.selliaapp.data.local.entity.CashSessionEntity
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.data.local.entity.CloudServiceConfigEntity
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.ProductImageEntity
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.data.local.entity.PricingAuditEntity
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
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
import com.example.selliaapp.data.local.entity.DevelopmentOptionsEntity

/**
 * Base de datos Room principal.
 * Aumentá version si cambiás esquemas.
 */
@Database(
    entities = [
        // Persistencia principal
        ProductEntity::class,
        ProductImageEntity::class,
        CustomerEntity::class,
        ProviderEntity::class,
        ReportDataEntity::class,
        StockMovementEntity::class,
        CategoryEntity::class,
        VariantEntity::class,
        SyncOutboxEntity::class,
        PricingFixedCostEntity::class,
        PricingSettingsEntity::class,
        PricingAuditEntity::class,
        PricingMlFixedCostTierEntity::class,
        PricingMlShippingTierEntity::class,
        CashSessionEntity::class,
        CashMovementEntity::class,
        CashAuditEntity::class,
        CloudServiceConfigEntity::class,
        DevelopmentOptionsEntity::class,

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
    version = 38,
    //autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
@TypeConverters(Converters::class, ReportConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productImageDao(): ProductImageDao
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
    abstract fun pricingFixedCostDao(): PricingFixedCostDao
    abstract fun pricingSettingsDao(): PricingSettingsDao
    abstract fun pricingAuditDao(): PricingAuditDao
    abstract fun pricingMlFixedCostTierDao(): PricingMlFixedCostTierDao
    abstract fun pricingMlShippingTierDao(): PricingMlShippingTierDao
    abstract fun cashSessionDao(): CashSessionDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun cashAuditDao(): CashAuditDao
    abstract fun cloudServiceConfigDao(): CloudServiceConfigDao
    abstract fun developmentOptionsDao(): DevelopmentOptionsDao


    companion object {
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `url` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_images_productId` ON `product_images` (`productId`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_images_productId_position` ON `product_images` (`productId`, `position`)"
                )
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT INTO product_images(productId, url, position)
                    SELECT id, imageUrl, 0
                    FROM products
                    WHERE imageUrl IS NOT NULL AND TRIM(imageUrl) <> ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `code` TEXT,
                        `barcode` TEXT,
                        `name` TEXT NOT NULL,
                        `purchasePrice` REAL,
                        `price` REAL,
                        `listPrice` REAL,
                        `cashPrice` REAL,
                        `transferPrice` REAL,
                        `transferNetPrice` REAL,
                        `mlPrice` REAL,
                        `ml3cPrice` REAL,
                        `ml6cPrice` REAL,
                        `autoPricing` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `description` TEXT,
                        `categoryId` INTEGER,
                        `providerId` INTEGER,
                        `providerName` TEXT,
                        `providerSku` TEXT,
                        `category` TEXT,
                        `minStock` INTEGER,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `products_new` (
                        `id`,
                        `code`,
                        `barcode`,
                        `name`,
                        `purchasePrice`,
                        `price`,
                        `listPrice`,
                        `cashPrice`,
                        `transferPrice`,
                        `transferNetPrice`,
                        `mlPrice`,
                        `ml3cPrice`,
                        `ml6cPrice`,
                        `autoPricing`,
                        `quantity`,
                        `description`,
                        `categoryId`,
                        `providerId`,
                        `providerName`,
                        `providerSku`,
                        `category`,
                        `minStock`,
                        `updatedAt`
                    )
                    SELECT
                        `id`,
                        `code`,
                        `barcode`,
                        `name`,
                        `purchasePrice`,
                        `price`,
                        `listPrice`,
                        `cashPrice`,
                        `transferPrice`,
                        `transferNetPrice`,
                        `mlPrice`,
                        `ml3cPrice`,
                        `ml6cPrice`,
                        `autoPricing`,
                        `quantity`,
                        `description`,
                        `categoryId`,
                        `providerId`,
                        `providerName`,
                        `providerSku`,
                        `category`,
                        `minStock`,
                        `updatedAt`
                    FROM `products`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `products`")
                db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_code` ON `products` (`code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_providerId` ON `products` (`providerId`)")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cash_sessions` (
                        `id` TEXT NOT NULL,
                        `openedAt` INTEGER NOT NULL,
                        `closedAt` INTEGER,
                        `openingAmount` REAL NOT NULL,
                        `expectedAmount` REAL,
                        `status` TEXT NOT NULL,
                        `openedBy` TEXT,
                        `note` TEXT,
                        `closingAmount` REAL,
                        `closingNote` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_sessions_status` ON `cash_sessions` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_sessions_openedAt` ON `cash_sessions` (`openedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cash_movements` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `referenceId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `cash_sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_sessionId` ON `cash_movements` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_createdAt` ON `cash_movements` (`createdAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cash_audits` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `countedAmount` REAL NOT NULL,
                        `difference` REAL NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `cash_sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_audits_sessionId` ON `cash_audits` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_audits_createdAt` ON `cash_audits` (`createdAt`)")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'EMITIDA'")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `canceledAt` INTEGER")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `canceledReason` TEXT")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_service_configs` (
                        `ownerEmail` TEXT NOT NULL,
                        `cloudEnabled` INTEGER NOT NULL,
                        `firestoreBackupEnabled` INTEGER NOT NULL,
                        `authSyncEnabled` INTEGER NOT NULL,
                        `storageBackupEnabled` INTEGER NOT NULL,
                        `functionsEnabled` INTEGER NOT NULL,
                        `hostingEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerEmail`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
            }
        }
        @JvmField
        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `development_options_configs` (
                        `ownerEmail` TEXT NOT NULL,
                        `salesEnabled` INTEGER NOT NULL,
                        `stockEnabled` INTEGER NOT NULL,
                        `customersEnabled` INTEGER NOT NULL,
                        `providersEnabled` INTEGER NOT NULL,
                        `expensesEnabled` INTEGER NOT NULL,
                        `reportsEnabled` INTEGER NOT NULL,
                        `cashEnabled` INTEGER NOT NULL,
                        `usageAlertsEnabled` INTEGER NOT NULL,
                        `configEnabled` INTEGER NOT NULL,
                        `publicCatalogEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerEmail`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
