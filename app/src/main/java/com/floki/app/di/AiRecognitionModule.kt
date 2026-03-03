package com.floki.app.di

import com.floki.app.data.ai.HeuristicProductPhotoRecognitionService
import com.floki.app.domain.product.ProductPhotoRecognitionService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiRecognitionModule {

    @Binds
    @Singleton
    abstract fun bindProductPhotoRecognitionService(
        impl: HeuristicProductPhotoRecognitionService
    ): ProductPhotoRecognitionService
}
