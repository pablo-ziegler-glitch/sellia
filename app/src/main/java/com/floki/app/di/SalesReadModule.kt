package com.floki.app.di

import com.floki.app.repository.sales.DefaultSalesInvoiceReadRepository
import com.floki.app.repository.sales.SalesInvoiceReadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindea la interfaz de LECTURA al repo real basado en Room.
 * No toca tu InvoiceRepository de ventas/stock.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SalesReadModule {

    @Binds
    @Singleton
    abstract fun bindSalesInvoiceReadRepository(
        impl: DefaultSalesInvoiceReadRepository
    ): SalesInvoiceReadRepository
}
