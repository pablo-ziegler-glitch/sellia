package com.example.selliaapp.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionUiAlert {
    data class SessionExpired(val message: String) : SessionUiAlert
    data class MissingPermission(
        val message: String,
        val requiredPermission: String
    ) : SessionUiAlert
}

@Singleton
class SessionUiNotifier @Inject constructor() {
    private val _alerts = MutableSharedFlow<SessionUiAlert>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val alerts: SharedFlow<SessionUiAlert> = _alerts

    fun notifySessionExpired(message: String) {
        _alerts.tryEmit(SessionUiAlert.SessionExpired(message = message))
    }

    fun notifyMissingPermission(message: String, requiredPermission: String) {
        _alerts.tryEmit(
            SessionUiAlert.MissingPermission(
                message = message,
                requiredPermission = requiredPermission
            )
        )
    }
}
