package com.example.selliaapp.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSessionCoordinator @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionUiNotifier: SessionUiNotifier
) {

    suspend fun <T> runWithFreshSession(block: suspend () -> T): T {
        val user = firebaseAuth.currentUser
            ?: throw buildSessionExpiredException()

        preflightToken(user = user, forceRefresh = false)

        return try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!isRetryableAuthFailure(error)) {
                throw mapToFunctionalError(error)
            }

            forceRefreshSessionToken()

            try {
                block()
            } catch (retryError: Throwable) {
                if (retryError is CancellationException) throw retryError
                throw mapToFunctionalError(retryError)
            }
        }
    }

    private suspend fun preflightToken(
        user: com.google.firebase.auth.FirebaseUser,
        forceRefresh: Boolean
    ) {
        try {
            user.getIdToken(forceRefresh).await()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw mapToFunctionalError(error)
        }
    }

    private suspend fun forceRefreshSessionToken() {
        val activeUser = firebaseAuth.currentUser
            ?: throw buildSessionExpiredException()
        preflightToken(user = activeUser, forceRefresh = true)
    }

    private fun isRetryableAuthFailure(error: Throwable): Boolean = when (error) {
        is FirebaseAuthInvalidUserException -> true
        is FirebaseFirestoreException -> {
            error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }

        is StorageException -> {
            error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ||
                error.errorCode == StorageException.ERROR_NOT_AUTHORIZED
        }

        else -> false
    }

    private fun mapToFunctionalError(error: Throwable): FirebaseSessionException {
        emitUiAlertIfNeeded(error)

        val userMessage = when (error) {
            is FirebaseAuthInvalidUserException -> {
                "Tu sesión ya no es válida. Iniciá sesión nuevamente."
            }

            is FirebaseAuthInvalidCredentialsException -> {
                "No pudimos validar tu sesión. Volvé a iniciar sesión para continuar."
            }

            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    "Tu sesión expiró. Volvé a iniciar sesión para continuar."
                }

                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Tu sesión está activa, pero no tenés permisos para esta acción."
                }

                FirebaseFirestoreException.Code.UNAVAILABLE -> {
                    "No pudimos conectarnos al servidor. Verificá tu conexión e intentá de nuevo."
                }

                else -> "No se pudo sincronizar tu información. Intentá nuevamente en unos segundos."
            }

            is StorageException -> when (error.errorCode) {
                StorageException.ERROR_NOT_AUTHENTICATED -> {
                    "Tu sesión expiró. Volvé a iniciar sesión para subir archivos."
                }

                StorageException.ERROR_NOT_AUTHORIZED -> {
                    "Tu usuario está autenticado, pero no tiene permisos para subir archivos en este tenant."
                }

                StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
                StorageException.ERROR_QUOTA_EXCEEDED -> {
                    "No se pudo subir el archivo en este momento. Reintentá en unos minutos."
                }

                else -> "No se pudo procesar el archivo. Verificá la conexión e intentá otra vez."
            }

            is FirebaseNetworkException -> {
                "Sin conexión a internet. Verificá tu red e intentá nuevamente."
            }

            is FirebaseAuthException -> {
                "No pudimos validar tu sesión. Volvé a iniciar sesión para continuar."
            }

            else -> error.message?.takeIf { it.isNotBlank() }
                ?: "Ocurrió un error de sesión. Intentá nuevamente."
        }
        return FirebaseSessionException(userMessage = userMessage, cause = error)
    }

    private fun emitUiAlertIfNeeded(error: Throwable) {
        if (shouldForceSignOut(error)) {
            sessionUiNotifier.notifySessionExpired(
                message = "Tu sesión venció por seguridad. Necesitás volver a iniciar sesión."
            )
            return
        }

        if (isPermissionDenied(error)) {
            sessionUiNotifier.notifyMissingPermission(
                message = "No tenés permisos para ejecutar esta acción.",
                requiredPermission = requiredPermissionFor(error)
            )
        }
    }

    private fun isPermissionDenied(error: Throwable): Boolean = when (error) {
        is FirebaseFirestoreException -> error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        is StorageException -> error.errorCode == StorageException.ERROR_NOT_AUTHORIZED
        else -> false
    }

    private fun requiredPermissionFor(error: Throwable): String = when (error) {
        is StorageException -> {
            "Escritura en Storage para el tenant activo (rol owner/manager/cashier/admin o super admin)."
        }

        is FirebaseFirestoreException -> {
            "Permiso de escritura/lectura en Firestore según reglas del tenant y rol activo."
        }

        else -> {
            "Permiso del módulo solicitado para tu rol actual."
        }
    }

    private fun shouldForceSignOut(error: Throwable): Boolean = when (error) {
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> true

        is FirebaseAuthException -> {
            error.errorCode in FORCED_SIGN_OUT_AUTH_ERROR_CODES
        }

        is FirebaseFirestoreException -> {
            error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
        }

        is StorageException -> {
            error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED
        }

        else -> false
    }

    private fun buildSessionExpiredException(): FirebaseSessionException {
        sessionUiNotifier.notifySessionExpired(
            message = "Tu sesión expiró. Para continuar necesitás volver a iniciar sesión."
        )
        return FirebaseSessionException("Tu sesión expiró. Iniciá sesión nuevamente.")
    }

    private companion object {
        val FORCED_SIGN_OUT_AUTH_ERROR_CODES = setOf(
            "ERROR_USER_TOKEN_EXPIRED",
            "ERROR_INVALID_USER_TOKEN",
            "ERROR_USER_DISABLED"
        )
    }
}

class FirebaseSessionException(
    userMessage: String,
    cause: Throwable? = null
) : IllegalStateException(userMessage, cause)
