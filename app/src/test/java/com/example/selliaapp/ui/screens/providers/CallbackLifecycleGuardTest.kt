package com.example.selliaapp.ui.screens.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallbackLifecycleGuardTest {

    @Test
    fun isActive_isTrueBeforeDispose_andFalseAfterDispose() {
        val guard = CallbackLifecycleGuard()

        assertThat(guard.isActive()).isTrue()

        guard.dispose()

        assertThat(guard.isActive()).isFalse()
    }

    @Test
    fun dispose_isIdempotent() {
        val guard = CallbackLifecycleGuard()

        guard.dispose()
        guard.dispose()

        assertThat(guard.isActive()).isFalse()
    }
}
