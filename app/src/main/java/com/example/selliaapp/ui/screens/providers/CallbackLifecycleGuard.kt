package com.example.selliaapp.ui.screens.providers

internal class CallbackLifecycleGuard {
    private var disposed = false

    fun isActive(): Boolean = !disposed

    fun dispose() {
        disposed = true
    }
}
