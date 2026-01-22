package com.example.selliaapp.di

import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.impl.IProductRepositoryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo puente:
 * - Cuando un VM pida IProductRepository, se inyecta el adaptador,
 *   que por dentro usa tu ProductRepository de siempre (provisto en AppModule).
 * - Nada de esto afecta a los VMs viejos que sigan pidiendo ProductRepository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBridgeModule {

    @Binds
    @Singleton
    abstract fun bindIProductRepository(
        impl: IProductRepositoryAdapter
    ): IProductRepository
}
