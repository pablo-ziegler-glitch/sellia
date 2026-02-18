package com.example.selliaapp.di

// DAOs básicos

// DAOs de Proveedores y Gastos (faltantes en tu módulo anterior)

// Repositorios

// Firestore

// Hilt
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.CategoryDao
import com.example.selliaapp.data.dao.CashAuditDao
import com.example.selliaapp.data.dao.CashMovementDao
import com.example.selliaapp.data.dao.CashSessionDao
import com.example.selliaapp.data.dao.CloudServiceConfigDao
import com.example.selliaapp.data.dao.DevelopmentOptionsDao
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.ExpenseBudgetDao
import com.example.selliaapp.data.dao.ExpenseRecordDao
import com.example.selliaapp.data.dao.ExpenseTemplateDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductPriceAuditDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.PricingAuditDao
import com.example.selliaapp.data.dao.PricingFixedCostDao
import com.example.selliaapp.data.dao.PricingMlFixedCostTierDao
import com.example.selliaapp.data.dao.PricingMlShippingTierDao
import com.example.selliaapp.data.dao.PricingSettingsDao
import com.example.selliaapp.auth.FirebaseSessionCoordinator
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.dao.ReportDataDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.dao.TenantSkuConfigDao
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.repository.CloudServiceConfigRepository
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.AccessControlRepository
import com.example.selliaapp.repository.AccountRequestsRepository
import com.example.selliaapp.repository.AuthOnboardingRepository
import com.example.selliaapp.repository.CashRepository
import com.example.selliaapp.repository.DevelopmentOptionsRepository
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.repository.MarketingConfigRepository
import com.example.selliaapp.repository.PricingConfigRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.repository.ReportsRepository
import com.example.selliaapp.repository.SecurityConfigRepository
import com.example.selliaapp.repository.StorageRepository
import com.example.selliaapp.repository.TenantDirectoryRepository
import com.example.selliaapp.repository.UsageRepository
import com.example.selliaapp.repository.UserRepository
import com.example.selliaapp.repository.ViewerStoreRepository
import com.example.selliaapp.repository.impl.AccountRequestsRepositoryImpl
import com.example.selliaapp.repository.impl.AccessControlRepositoryImpl
import com.example.selliaapp.repository.impl.AuthOnboardingRepositoryImpl
import com.example.selliaapp.repository.impl.CashRepositoryImpl
import com.example.selliaapp.repository.impl.TenantDirectoryRepositoryImpl
import com.example.selliaapp.repository.impl.StorageRepositoryImpl
import com.example.selliaapp.repository.impl.UsageRepositoryImpl
import com.example.selliaapp.repository.impl.ViewerStoreRepositoryImpl
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * AppModule DI – versión recomendada para 'selliaAppv2.zip' (04/09/2025)
 * - Room con WAL y FK activas
 * - fallbackToDestructiveMigration() sin booleano
 * - Provee TODOS los DAOs (incl. Provider/Expense)
 * - ReportsRepository SOLO con InvoiceDao (baseline actual)
 *
 * /* [ANTERIOR]
 *  @Provides fun provideIProductRepository(repo: ProductRepository): IProductRepository = repo
 *  (Esto es lo que te rompía: ProductRepository NO implementa IProductRepository y además duplicaba bindings)
 * */
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -----------------------------
    // DATABASE (singleton Room)
    // -----------------------------
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext appContext: Context): AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "sellia_db_v1")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL
            // [NUEVO] Room no admite booleano acá. Si no hay Migration, esto evita que el build/runtime se rompa.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addMigrations(
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41,
                AppDatabase.MIGRATION_41_42
            )
            .addCallback(object : RoomDatabase.Callback() {
                /**
                 * Nota: RoomDatabase.Callback no tiene onConfigure(...).
                 * Usamos onOpen para reforzar FOREIGN KEYS = ON.
                 */
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            })
            .build()

    // -----------------------------
    // DAOs
    // -----------------------------
    @Provides @Singleton fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides @Singleton fun provideProductPriceAuditDao(db: AppDatabase): ProductPriceAuditDao = db.productPriceAuditDao()
    @Provides @Singleton fun provideProductImageDao(db: AppDatabase): ProductImageDao = db.productImageDao()
    @Provides @Singleton fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides @Singleton fun provideVariantDao(db: AppDatabase): VariantDao = db.variantDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()
    @Provides @Singleton fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides @Singleton fun provideProviderDao(db: AppDatabase): ProviderDao = db.providerDao()
    @Provides @Singleton fun provideProviderInvoiceDao(db: AppDatabase): ProviderInvoiceDao = db.providerInvoiceDao()
    @Provides @Singleton fun provideInvoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()
    @Provides @Singleton fun provideInvoiceItemDao(db: AppDatabase): InvoiceItemDao = db.invoiceItemDao()
    @Provides @Singleton fun provideSyncOutboxDao(db: AppDatabase): SyncOutboxDao = db.syncOutboxDao()
    @Provides @Singleton fun provideReportDataDao(db: AppDatabase): ReportDataDao = db.reportDataDao()

    // Gastos
    @Provides @Singleton fun provideExpenseBudgetDao(db: AppDatabase): ExpenseBudgetDao = db.expenseBudgetDao()
    @Provides @Singleton fun provideExpenseRecordDao(db: AppDatabase): ExpenseRecordDao = db.expenseRecordDao()
    @Provides @Singleton fun provideExpenseTemplateDao(db: AppDatabase): ExpenseTemplateDao = db.expenseTemplateDao()

    // Pricing DAOs
    @Provides @Singleton fun providePricingFixedCostDao(db: AppDatabase): PricingFixedCostDao = db.pricingFixedCostDao()
    @Provides @Singleton fun providePricingSettingsDao(db: AppDatabase): PricingSettingsDao = db.pricingSettingsDao()
    @Provides @Singleton fun providePricingAuditDao(db: AppDatabase): PricingAuditDao = db.pricingAuditDao()
    @Provides @Singleton fun providePricingMlFixedCostTierDao(db: AppDatabase): PricingMlFixedCostTierDao = db.pricingMlFixedCostTierDao()
    @Provides @Singleton fun providePricingMlShippingTierDao(db: AppDatabase): PricingMlShippingTierDao = db.pricingMlShippingTierDao()
    @Provides @Singleton fun provideCashSessionDao(db: AppDatabase): CashSessionDao = db.cashSessionDao()
    @Provides @Singleton fun provideCashMovementDao(db: AppDatabase): CashMovementDao = db.cashMovementDao()
    @Provides @Singleton fun provideCashAuditDao(db: AppDatabase): CashAuditDao = db.cashAuditDao()
    @Provides @Singleton fun provideCloudServiceConfigDao(db: AppDatabase): CloudServiceConfigDao =
        db.cloudServiceConfigDao()
    @Provides @Singleton fun provideDevelopmentOptionsDao(db: AppDatabase): DevelopmentOptionsDao =
        db.developmentOptionsDao()
    @Provides @Singleton fun provideTenantSkuConfigDao(db: AppDatabase): TenantSkuConfigDao =
        db.tenantSkuConfigDao()

    // -----------------------------
    // REPOSITORIES
    // -----------------------------

    /**
     * ProductRepository actualizado: coordina Room (local) + Firestore (remoto).
     * La UI observa Room; las operaciones escriben en ambos y sincronizan.
     */
    @Provides @Singleton
    fun provideProductRepository(
        db: AppDatabase,
        productDao: ProductDao,
        productImageDao: ProductImageDao,
        categoryDao: CategoryDao,
        providerDao: ProviderDao,
        productPriceAuditDao: ProductPriceAuditDao,
        pricingConfigRepository: PricingConfigRepository,
        firestore: FirebaseFirestore,
        tenantProvider: TenantProvider,
        tenantSkuConfigDao: TenantSkuConfigDao,
        // [NUEVO] Qualifier global (CoroutinesModule)
        @IoDispatcher io: CoroutineDispatcher
    ): ProductRepository = ProductRepository(
        db = db,
        productDao = productDao,
        productImageDao = productImageDao,
        categoryDao = categoryDao,
        providerDao = providerDao,
        productPriceAuditDao = productPriceAuditDao,
        pricingConfigRepository = pricingConfigRepository,
        firestore = firestore,
        tenantProvider = tenantProvider,
        tenantSkuConfigDao = tenantSkuConfigDao,
        io = io
    )

    @Provides
    @Singleton
    fun providePricingConfigRepository(
        pricingFixedCostDao: PricingFixedCostDao,
        pricingSettingsDao: PricingSettingsDao,
        pricingAuditDao: PricingAuditDao,
        pricingMlFixedCostTierDao: PricingMlFixedCostTierDao,
        pricingMlShippingTierDao: PricingMlShippingTierDao,
        syncOutboxDao: SyncOutboxDao,
        firestore: FirebaseFirestore,
        tenantProvider: TenantProvider,
        @IoDispatcher io: CoroutineDispatcher
    ): PricingConfigRepository = PricingConfigRepository(
        pricingFixedCostDao = pricingFixedCostDao,
        pricingSettingsDao = pricingSettingsDao,
        pricingAuditDao = pricingAuditDao,
        pricingMlFixedCostTierDao = pricingMlFixedCostTierDao,
        pricingMlShippingTierDao = pricingMlShippingTierDao,
        syncOutboxDao = syncOutboxDao,
        firestore = firestore,
        tenantProvider = tenantProvider,
        io = io
    )

    @Provides
    @Singleton
    fun provideCustomerRepository(
        customerDao: CustomerDao,
        syncOutboxDao: SyncOutboxDao,
        firestore: FirebaseFirestore,
        tenantProvider: TenantProvider
    ): CustomerRepository = CustomerRepository(
        customerDao = customerDao,
        syncOutboxDao = syncOutboxDao,
        firestore = firestore,
        tenantProvider = tenantProvider
    )
    @Provides
    @Singleton
    fun provideUserRepository(
        dao: UserDao,
        firestore: FirebaseFirestore,
        tenantProvider: TenantProvider,
        sessionCoordinator: FirebaseSessionCoordinator,
        @IoDispatcher io: CoroutineDispatcher
    ): UserRepository = UserRepository(
        userDao = dao,
        firestore = firestore,
        tenantProvider = tenantProvider,
        sessionCoordinator = sessionCoordinator,
        io = io
    )
    @Provides
    @Singleton
    fun provideCloudServiceConfigRepository(
        dao: CloudServiceConfigDao,
        @IoDispatcher io: CoroutineDispatcher
    ): CloudServiceConfigRepository = CloudServiceConfigRepository(dao, io)

    @Provides
    @Singleton
    fun provideDevelopmentOptionsRepository(
        dao: DevelopmentOptionsDao,
        @IoDispatcher io: CoroutineDispatcher
    ): DevelopmentOptionsRepository = DevelopmentOptionsRepository(dao, io)

    @Provides
    @Singleton
    fun provideAccessControlRepository(
        userDao: UserDao,
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        securityConfigRepository: SecurityConfigRepository,
        @IoDispatcher io: CoroutineDispatcher
    ): AccessControlRepository = AccessControlRepositoryImpl(
        userDao = userDao,
        auth = auth,
        firestore = firestore,
        securityConfigRepository = securityConfigRepository,
        io = io
    )

    @Provides
    @Singleton
    fun provideAuthOnboardingRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        @IoDispatcher io: CoroutineDispatcher
    ): AuthOnboardingRepository = AuthOnboardingRepositoryImpl(
        auth = auth,
        firestore = firestore,
        io = io
    )

    @Provides
    @Singleton
    fun provideAccountRequestsRepository(
        firestore: FirebaseFirestore,
        @IoDispatcher io: CoroutineDispatcher
    ): AccountRequestsRepository = AccountRequestsRepositoryImpl(
        firestore = firestore,
        io = io
    )


    @Provides
    @Singleton
    fun provideViewerStoreRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        @IoDispatcher io: CoroutineDispatcher
    ): ViewerStoreRepository = ViewerStoreRepositoryImpl(
        auth = auth,
        firestore = firestore,
        io = io
    )

    @Provides
    @Singleton
    fun provideTenantDirectoryRepository(
        firestore: FirebaseFirestore,
        @IoDispatcher io: CoroutineDispatcher
    ): TenantDirectoryRepository = TenantDirectoryRepositoryImpl(
        firestore = firestore,
        io = io
    )

    /**
     * Reportes de ventas + valorización de stock.
     */
    @Provides @Singleton
    fun provideReportsRepository(
        invoiceDao: InvoiceDao,
        productDao: ProductDao
    ): ReportsRepository = ReportsRepository(
        invoiceDao = invoiceDao,
        productDao = productDao
    )

    @Provides
    @Singleton
    fun provideUsageRepository(
        firestore: FirebaseFirestore,
        sessionCoordinator: FirebaseSessionCoordinator,
        @IoDispatcher io: CoroutineDispatcher
    ): UsageRepository = UsageRepositoryImpl(
        firestore = firestore,
        sessionCoordinator = sessionCoordinator,
        ioDispatcher = io
    )

    @Provides @Singleton fun provideProviderRepository(dao: ProviderDao): ProviderRepository = ProviderRepository(dao)
    @Provides @Singleton fun provideProviderInvoiceRepository(dao: ProviderInvoiceDao): ProviderInvoiceRepository = ProviderInvoiceRepository(dao)

    @Provides
    @Singleton
    fun provideMarketingConfigDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile("marketing_config.preferences_pb") }
    )

    @Provides
    @Singleton
    fun provideMarketingConfigRepository(
        dataStore: DataStore<Preferences>
    ): MarketingConfigRepository = MarketingConfigRepository(dataStore)

    @Provides
    @Singleton
    fun provideSecurityConfigRepository(
        dataStore: DataStore<Preferences>
    ): SecurityConfigRepository = SecurityConfigRepository(dataStore)

    @Provides
    @Singleton
    fun provideStorageRepository(
        storage: FirebaseStorage,
        sessionCoordinator: FirebaseSessionCoordinator,
        auth: FirebaseAuth
    ): StorageRepository = StorageRepositoryImpl(
        storage = storage,
        sessionCoordinator = sessionCoordinator,
        auth = auth
    )

    @Provides
    @Singleton
    fun provideCashRepository(
        cashSessionDao: CashSessionDao,
        cashMovementDao: CashMovementDao,
        cashAuditDao: CashAuditDao
    ): CashRepository = CashRepositoryImpl(
        cashSessionDao = cashSessionDao,
        cashMovementDao = cashMovementDao,
        cashAuditDao = cashAuditDao
    )

    @Provides
    @Singleton
    fun provideExpenseRepository(
        templateDao: ExpenseTemplateDao,
        recordDao: ExpenseRecordDao,
        budgetDao: ExpenseBudgetDao,
        invoiceDao: InvoiceDao,
        providerInvoiceDao: ProviderInvoiceDao
    ): ExpenseRepository =
        ExpenseRepository(
            tDao = templateDao,
            rDao = recordDao,
            bDao = budgetDao,
            invoiceDao = invoiceDao,
            providerInvoiceDao = providerInvoiceDao
        )

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class IoDispatcher

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO



}
