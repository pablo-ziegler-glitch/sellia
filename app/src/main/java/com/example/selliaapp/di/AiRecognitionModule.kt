package com.example.selliaapp.di

import com.example.selliaapp.data.ai.HeuristicProductPhotoRecognitionService
import com.example.selliaapp.domain.product.ProductPhotoRecognitionService
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
