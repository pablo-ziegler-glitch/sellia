package com.floki.app.di

import com.floki.app.auth.AuthManager
import com.floki.app.auth.TenantProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindTenantProvider(authManager: AuthManager): TenantProvider
}
