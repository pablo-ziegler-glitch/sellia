package com.example.selliaapp.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckProviderFactoryResolver {
    fun debugFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
