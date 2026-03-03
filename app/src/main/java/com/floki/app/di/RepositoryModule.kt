
package com.floki.app.di

 import com.floki.app.data.payment.PaymentRepository
 import com.floki.app.data.payment.PaymentRepositoryImpl
 import com.floki.app.repository.CartRepository
 import com.floki.app.repository.InvoiceRepository
 import com.floki.app.repository.UsageAlertsRepository
 import com.floki.app.repository.UsageLimitsRepository
 import com.floki.app.repository.impl.CartRepositoryImpl
 import com.floki.app.repository.impl.InvoiceRepositoryImpl
 import com.floki.app.repository.impl.UsageAlertsRepositoryImpl
 import com.floki.app.repository.impl.UsageLimitsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de bindings para que Hilt sepa cómo construir los repos.
 * Si ya tenés módulos, integrá estos @Binds ahí, respetando tus implementaciones reales.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // [NUEVO] Único binding válido para evitar "DuplicateBindings" de InvoiceRepository
    @Binds
    @Singleton
    abstract fun bindInvoiceRepository(
        impl: InvoiceRepositoryImpl
    ): InvoiceRepository

    // [NUEVO] Binding que faltaba para CartRepository (soluciona MissingBinding)
    @Binds
    @Singleton
    abstract fun bindCartRepository(
        impl: CartRepositoryImpl
    ): CartRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(
        impl: PaymentRepositoryImpl
    ): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindUsageAlertsRepository(
        impl: UsageAlertsRepositoryImpl
    ): UsageAlertsRepository

    @Binds
    @Singleton
    abstract fun bindUsageLimitsRepository(
        impl: UsageLimitsRepositoryImpl
    ): UsageLimitsRepository
}
