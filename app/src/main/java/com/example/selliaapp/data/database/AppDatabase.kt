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
import com.example.selliaapp.data.dao.ProductPriceAuditDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.ProductStateHistoryDao
import com.example.selliaapp.data.dao.ProductSyncConflictDao
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
import com.example.selliaapp.data.dao.TenantSkuConfigDao
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
import com.example.selliaapp.data.local.entity.ProductPriceAuditEntity
import com.example.selliaapp.data.local.entity.ProductStateHistoryEntity
import com.example.selliaapp.data.local.entity.ProductSyncConflictEntity
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.data.local.entity.PricingAuditEntity
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.data.local.entity.ReportDataEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.local.entity.TenantSkuConfigEntity
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
        ProductPriceAuditEntity::class,
        ProductStateHistoryEntity::class,
        ProductSyncConflictEntity::class,
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
        TenantSkuConfigEntity::class,

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
    version = 50,
    //autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
@TypeConverters(Converters::class, ReportConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productPriceAuditDao(): ProductPriceAuditDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun productStateHistoryDao(): ProductStateHistoryDao
    abstract fun productSyncConflictDao(): ProductSyncConflictDao
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
    abstract fun tenantSkuConfigDao(): TenantSkuConfigDao


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

        @JvmField
        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN parentCategory TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN color TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN sizes TEXT NOT NULL DEFAULT '[]'")
            }
        }


        @JvmField
        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `code` TEXT,
                        `barcode` TEXT,
                        `name` TEXT NOT NULL,
                        `purchasePrice` REAL,
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
                        `imageUrl` TEXT,
                        `imageUrls` TEXT NOT NULL,
                        `categoryId` INTEGER,
                        `providerId` INTEGER,
                        `providerName` TEXT,
                        `providerSku` TEXT,
                        `brand` TEXT,
                        `parentCategory` TEXT,
                        `category` TEXT,
                        `color` TEXT,
                        `sizes` TEXT NOT NULL,
                        `minStock` INTEGER,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `products_new` (
                        `id`,`code`,`barcode`,`name`,`purchasePrice`,`listPrice`,`cashPrice`,`transferPrice`,`transferNetPrice`,
                        `mlPrice`,`ml3cPrice`,`ml6cPrice`,`autoPricing`,`quantity`,`description`,`imageUrl`,`imageUrls`,`categoryId`,
                        `providerId`,`providerName`,`providerSku`,`brand`,`parentCategory`,`category`,`color`,`sizes`,`minStock`,`updatedAt`
                    )
                    SELECT
                        `id`,`code`,`barcode`,`name`,`purchasePrice`,`listPrice`,`cashPrice`,`transferPrice`,`transferNetPrice`,
                        `mlPrice`,`ml3cPrice`,`ml6cPrice`,`autoPricing`,`quantity`,`description`,`imageUrl`,COALESCE(`imageUrls`, '[]'),`categoryId`,
                        `providerId`,`providerName`,`providerSku`,`brand`,`parentCategory`,`category`,`color`,COALESCE(`sizes`, '[]'),`minStock`,`updatedAt`
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

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_price_audit_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `purchasePrice` REAL,
                        `oldListPrice` REAL,
                        `newListPrice` REAL,
                        `oldCashPrice` REAL,
                        `newCashPrice` REAL,
                        `oldTransferPrice` REAL,
                        `newTransferPrice` REAL,
                        `oldMlPrice` REAL,
                        `newMlPrice` REAL,
                        `oldMl3cPrice` REAL,
                        `newMl3cPrice` REAL,
                        `oldMl6cPrice` REAL,
                        `newMl6cPrice` REAL,
                        `reason` TEXT NOT NULL,
                        `changedBy` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `changedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_price_audit_log_productId` ON `product_price_audit_log` (`productId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_price_audit_log_changedAt` ON `product_price_audit_log` (`changedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tenant_sku_config` (
                        `tenantId` TEXT NOT NULL,
                        `storeName` TEXT NOT NULL,
                        `skuPrefix` TEXT NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`tenantId`)
                    )
                    """.trimIndent()
                )
            }
        }


        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `pricing_settings`
                    ADD COLUMN `fixedCostImputationMode` TEXT NOT NULL DEFAULT 'FULL_TO_ALL_PRODUCTS'
                    """.trimIndent()
                )
            }
        }


        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn(tableName = "products", columnName = "publicStatus")) {
                    db.execSQL(
                        """
                        ALTER TABLE `products`
                        ADD COLUMN `publicStatus` TEXT NOT NULL DEFAULT 'draft'
                        """.trimIndent()
                    )
                }
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `gainTargetPercent` REAL")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdGrossAmount` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdPosnetFee` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdPosnetFeePercent` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdPurchaseCost` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdOperativosFee` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdOperativosFeePercent` REAL")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `bdNetGain` REAL")
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pricing_settings` ADD COLUMN `posnetListaCostPercent` REAL NOT NULL DEFAULT 12.99"
                )
                db.execSQL(
                    "ALTER TABLE `pricing_settings` ADD COLUMN `cobroEnMomentoCostPercent` REAL NOT NULL DEFAULT 6.60"
                )
            }
        }

        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pricing_settings` ADD COLUMN `ivaProductPercent` REAL NOT NULL DEFAULT 21.0"
                )
            }
        }

        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `products` ADD COLUMN `manualGainPercent` REAL"
                )
                db.execSQL(
                    "ALTER TABLE `provider_invoices` ADD COLUMN `receptionStatus` TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    "ALTER TABLE `provider_invoices` ADD COLUMN `receivedAtMillis` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `provider_invoices` ADD COLUMN `discrepancyNote` TEXT"
                )
                db.execSQL(
                    "ALTER TABLE `provider_invoice_items` ADD COLUMN `receivedQuantity` REAL"
                )
            }
        }

        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("products", "productUuid")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `productUuid` TEXT NOT NULL DEFAULT ''")
                }
                if (!db.hasColumn("products", "legacyLocalId")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `legacyLocalId` INTEGER")
                }
                if (!db.hasColumn("products", "createdAtEpochMs")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `createdAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("products", "updatedAtEpochMs")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("products", "deletedAtEpochMs")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `deletedAtEpochMs` INTEGER")
                }
                if (!db.hasColumn("products", "syncVersion")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `syncVersion` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("products", "syncStatus")) {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING'")
                }

                db.execSQL(
                    """
                    UPDATE products
                    SET legacyLocalId = COALESCE(legacyLocalId, id),
                        productUuid = CASE
                            WHEN TRIM(COALESCE(productUuid, '')) = '' THEN 'legacy-' || id
                            ELSE productUuid
                        END,
                        createdAtEpochMs = CASE
                            WHEN createdAtEpochMs <= 0 THEN COALESCE(updatedAt, 0) * 86400000
                            ELSE createdAtEpochMs
                        END,
                        updatedAtEpochMs = CASE
                            WHEN updatedAtEpochMs <= 0 THEN COALESCE(updatedAt, 0) * 86400000
                            ELSE updatedAtEpochMs
                        END,
                        syncStatus = CASE
                            WHEN deletedAtEpochMs IS NULL THEN 'SYNCED'
                            ELSE 'DELETED'
                        END
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_products_productUuid` ON `products` (`productUuid`)"
                )

                if (!db.hasColumn("sync_outbox", "entityUuid")) {
                    db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `entityUuid` TEXT")
                }
                if (!db.hasColumn("sync_outbox", "operation")) {
                    db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `operation` TEXT NOT NULL DEFAULT 'UPSERT'")
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityUuid_operation` ON `sync_outbox` (`entityType`, `entityUuid`, `operation`)"
                )

                if (!db.hasColumn("invoice_items", "productUuid")) {
                    db.execSQL("ALTER TABLE `invoice_items` ADD COLUMN `productUuid` TEXT")
                }
                if (!db.hasColumn("invoice_items", "productLegacyLocalId")) {
                    db.execSQL("ALTER TABLE `invoice_items` ADD COLUMN `productLegacyLocalId` INTEGER")
                }
                if (!db.hasColumn("invoice_items", "productNameSnapshot")) {
                    db.execSQL("ALTER TABLE `invoice_items` ADD COLUMN `productNameSnapshot` TEXT")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_productUuid` ON `invoice_items` (`productUuid`)")
                db.execSQL(
                    """
                    UPDATE invoice_items
                    SET productLegacyLocalId = COALESCE(productLegacyLocalId, productId),
                        productNameSnapshot = COALESCE(productNameSnapshot, productName),
                        productUuid = (
                            SELECT p.productUuid
                            FROM products p
                            WHERE p.id = invoice_items.productId
                            LIMIT 1
                        )
                    WHERE productUuid IS NULL
                    """.trimIndent()
                )

                if (!db.hasColumn("stock_movements", "productUuid")) {
                    db.execSQL("ALTER TABLE `stock_movements` ADD COLUMN `productUuid` TEXT")
                }
                if (!db.hasColumn("stock_movements", "productLegacyLocalId")) {
                    db.execSQL("ALTER TABLE `stock_movements` ADD COLUMN `productLegacyLocalId` INTEGER")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productUuid` ON `stock_movements` (`productUuid`)")
                db.execSQL(
                    """
                    UPDATE stock_movements
                    SET productLegacyLocalId = COALESCE(productLegacyLocalId, productId),
                        productUuid = (
                            SELECT p.productUuid
                            FROM products p
                            WHERE p.id = stock_movements.productId
                            LIMIT 1
                        )
                    WHERE productUuid IS NULL
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_sync_conflicts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `localProductId` INTEGER,
                        `localProductUuid` TEXT,
                        `remoteProductUuid` TEXT,
                        `remoteDocumentId` TEXT,
                        `conflictType` TEXT NOT NULL,
                        `detailsJson` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `resolvedAtEpochMs` INTEGER,
                        `resolutionStatus` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_sync_conflicts_localProductId` ON `product_sync_conflicts` (`localProductId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_sync_conflicts_localProductUuid` ON `product_sync_conflicts` (`localProductUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_sync_conflicts_remoteProductUuid` ON `product_sync_conflicts` (`remoteProductUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_sync_conflicts_resolutionStatus` ON `product_sync_conflicts` (`resolutionStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_sync_conflicts_createdAtEpochMs` ON `product_sync_conflicts` (`createdAtEpochMs`)")
            }
        }

        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_state_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER,
                        `productUuid` TEXT NOT NULL,
                        `legacyLocalId` INTEGER,
                        `snapshotJson` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `supersededByProductUuid` TEXT,
                        `remoteDocumentId` TEXT,
                        `recordedAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_state_history_productUuid` ON `product_state_history` (`productUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_state_history_recordedAtEpochMs` ON `product_state_history` (`recordedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_state_history_source` ON `product_state_history` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_state_history_reason` ON `product_state_history` (`reason`)")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
            query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameColumnIndex = cursor.getColumnIndex("name")
                if (nameColumnIndex == -1) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumnIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

    }
}
