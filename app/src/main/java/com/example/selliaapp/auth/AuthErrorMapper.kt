package com.example.selliaapp.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.functions.FirebaseFunctionsException

object AuthErrorMapper {

    fun toUserMessage(error: Throwable, fallback: String): String {
        if (error is IllegalStateException && !error.message.isNullOrBlank()) {
            return error.message.orEmpty()
        }
        val message = error.message.orEmpty().lowercase()
        return when (error) {
            is FirebaseAuthInvalidUserException -> {
                "No encontramos una cuenta activa con ese email. Creá una cuenta o ingresá con Google."
            }

            is FirebaseAuthInvalidCredentialsException -> {
                when {
                    error.errorCode == "ERROR_INVALID_EMAIL" -> "El email no tiene un formato válido."
                    error.errorCode == "ERROR_WRONG_PASSWORD" ||
                        error.errorCode == "ERROR_INVALID_LOGIN_CREDENTIAL" ||
                        error.errorCode == "ERROR_INVALID_CREDENTIAL" -> {
                        "Email o contraseña incorrectos. Si te registraste con Google, usá \"Continuar con Google\"."
                    }

                    message.contains("malformed") || message.contains("expired") -> {
                        "No se pudo validar la credencial. Reintentá o ingresá con Google."
                    }

                    else -> fallback
                }
            }

            is FirebaseAuthUserCollisionException -> {
                "Ese email ya está registrado. Iniciá sesión o recuperá tu contraseña."
            }

            is FirebaseNetworkException -> "Sin conexión. Verificá internet e intentá nuevamente."
            is FirebaseFunctionsException -> mapFunctionsError(error, fallback)
            else -> fallback
        }
    }

    private fun mapFunctionsError(
        error: FirebaseFunctionsException,
        fallback: String
    ): String {
        val message = error.message.orEmpty().lowercase()
        val details = error.details?.toString().orEmpty().lowercase()
        return when {
            message.contains("ya administra otra tienda") || details.contains("ya administra otra tienda") -> {
                "Ese email ya administra otra tienda. Usá otro usuario para co-dueño o delegación."
            }

            error.code == FirebaseFunctionsException.Code.NOT_FOUND -> {
                "No encontramos un usuario activo con ese email. Verificá que ya tenga cuenta en SellIA."
            }

            error.code == FirebaseFunctionsException.Code.PERMISSION_DENIED -> {
                "No tenés permisos para gestionar dueños o delegaciones de esta tienda."
            }

            error.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                "No se puede completar la acción por una condición de la cuenta destino."
            }

            error.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                "Los datos ingresados no son válidos. Revisá el email e intentá nuevamente."
            }

            else -> fallback
        }
    }
}
