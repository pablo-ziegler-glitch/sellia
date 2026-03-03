package com.floki.app.ui.screens.providers

internal class CallbackLifecycleGuard {
    private var disposed = false

    fun isActive(): Boolean = !disposed

    fun dispose() {
        disposed = true
    }
}
