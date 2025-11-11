package com.example.selliaapp.di

// DAOs básicos

// DAOs de Proveedores y Gastos (faltantes en tu módulo anterior)

// Repositorios

// Firestore

// Hilt
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.CategoryDao
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.ExpenseRecordDao
import com.example.selliaapp.data.dao.ExpenseTemplateDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.ReportDataDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.repository.ReportsRepository
import com.example.selliaapp.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * AppModule DI – versión recomendada para 'selliaAppv2.zip' (04/09/2025)
 * - Room con WAL y FK activas
 * - fallbackToDestructiveMigration() sin booleano
 * - Provee TODOS los DAOs (incl. Provider/Expense)
 * - ReportsRepository SOLO con InvoiceDao (baseline actual)
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
            .fallbackToDestructiveMigration() // Política por defecto del proyecto (sin booleano)
            .addCallback(object : RoomDatabase.Callback() {
                /**
                 * Nota: RoomDatabase.Callback no tiene onConfigure(...).
                 * Usamos onOpen para reforzar FOREIGN KEYS = ON.
                 */
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            })
            .build()

    // -----------------------------
    // DAOs BÁSICOS
    // -----------------------------
     @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()
    @Provides fun provideInvoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun provideInvoiceItemDao(db: AppDatabase): InvoiceItemDao = db.invoiceItemDao()
    @Provides fun provideSyncOutboxDao(db: AppDatabase): SyncOutboxDao = db.syncOutboxDao()
    @Provides fun provideReportDataDao(db: AppDatabase): ReportDataDao = db.reportDataDao()


    // -----------------------------
    // DAOs PROVEEDORES / GASTOS
    // -----------------------------
    @Provides fun provideProviderInvoiceDao(db: AppDatabase): ProviderInvoiceDao = db.providerInvoiceDao()
    @Provides fun provideExpenseTemplateDao(db: AppDatabase): ExpenseTemplateDao = db.expenseTemplateDao()
    @Provides fun provideExpenseRecordDao(db: AppDatabase): ExpenseRecordDao = db.expenseRecordDao()



    @Provides
    @Singleton
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideProviderDao(db: AppDatabase): ProviderDao = db.providerDao()


    // -----------------------------
    // REPOSITORIES
    // -----------------------------

    // --------- Repos ---------
    /**
     * ProductRepository actualizado: coordina Room (local) + Firestore (remoto).
     * La UI observa Room; las operaciones escriben en ambos y sincronizan.
     */
    @Provides @Singleton
    fun provideProductRepository(
        db: AppDatabase,
        productDao: ProductDao,
        categoryDao: CategoryDao,
        providerDao: ProviderDao,
        firestore: FirebaseFirestore,
        @IoDispatcher io: CoroutineDispatcher   // <-- INYECTADO

    ): ProductRepository = ProductRepository(
        db = db,
        productDao = productDao,
        categoryDao = categoryDao,
        providerDao = providerDao,
        firestore = firestore,
        io = io                                  // <-- AQUÍ
    )
    @Provides
    @Singleton
    fun provideCustomerRepository(dao: CustomerDao): CustomerRepository =
        CustomerRepository(dao)

    @Provides
    @Singleton
    fun provideUserRepository(dao: UserDao): UserRepository = UserRepository(dao)




    /**
     * ⚠️ Baseline actual (04/09/2025): ReportsRepository SOLO con InvoiceDao.
     * Mantenemos ReportDataDao disponible pero no lo inyectamos todavía.
     * Si ya migraste ReportsRepository a (invoiceDao, reportDataDao),
     * cambiá la firma y el constructor aquí.
     */
    @Provides @Singleton
    fun provideReportsRepository(invoiceDao: InvoiceDao): ReportsRepository =
        ReportsRepository(invoiceDao)

    // -----------------------------
    // REPOS PROVEEDORES / GASTOS
    // -----------------------------
    @Provides
    @Singleton
    fun provideProviderRepository(
        dao: ProviderDao
    ): ProviderRepository = ProviderRepository(dao)

    @Provides
    @Singleton
    fun provideProviderInvoiceRepository(
        dao: ProviderInvoiceDao
    ): ProviderInvoiceRepository = ProviderInvoiceRepository(dao)

    @Provides
    @Singleton
    fun provideExpenseRepository(
        templateDao: ExpenseTemplateDao,
        recordDao: ExpenseRecordDao
    ): ExpenseRepository = ExpenseRepository(
        tDao = templateDao,
        rDao = recordDao
    )

    // --- Dispatchers ---
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class IoDispatcher

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class MainDispatcher

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class DefaultDispatcher


    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

}
