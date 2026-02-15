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
    private val firebaseAuth: FirebaseAuth
) {

    suspend fun <T> runWithFreshSession(block: suspend () -> T): T {
        val user = firebaseAuth.currentUser
            ?: throw FirebaseSessionException("Tu sesión expiró. Iniciá sesión nuevamente.")

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
            ?: throw FirebaseSessionException("Tu sesión expiró. Iniciá sesión nuevamente.")
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
        val userMessage = when (error) {
            is FirebaseAuthInvalidUserException -> {
                "Tu sesión ya no es válida. Cerrá sesión e iniciá nuevamente."
            }

            is FirebaseAuthInvalidCredentialsException -> {
                "No pudimos validar tu sesión. Volvé a iniciar sesión para continuar."
            }

            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Tu sesión venció o no tiene permisos. Reintentá iniciando sesión nuevamente."
                }

                FirebaseFirestoreException.Code.UNAVAILABLE -> {
                    "No pudimos conectarnos al servidor. Verificá tu conexión e intentá de nuevo."
                }

                else -> "No se pudo sincronizar tu información. Intentá nuevamente en unos segundos."
            }

            is StorageException -> when (error.errorCode) {
                StorageException.ERROR_NOT_AUTHENTICATED,
                StorageException.ERROR_NOT_AUTHORIZED -> {
                    "No tenés una sesión válida para subir archivos. Iniciá sesión nuevamente."
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
}

class FirebaseSessionException(
    userMessage: String,
    cause: Throwable? = null
) : IllegalStateException(userMessage, cause)
