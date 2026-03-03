package com.floki.app.sync

/* [ANTERIOR]
... si ya tenías un módulo que bindea el repo, usalo ...
*/

/* [NUEVO] Bindea la implementación a la interfaz. */
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}
