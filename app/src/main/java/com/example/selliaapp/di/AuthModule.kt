package com.example.selliaapp.di

import com.example.selliaapp.auth.AuthManager
import com.example.selliaapp.auth.TenantProvider
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
