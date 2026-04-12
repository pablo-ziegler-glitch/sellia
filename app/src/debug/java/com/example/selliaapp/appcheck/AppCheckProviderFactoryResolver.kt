package com.example.selliaapp.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckProviderFactoryResolver {
    fun debugFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
