package com.example.selliaapp.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Constructor

class AuthErrorMapperTest {

    @Test
    fun `maps invalid login credential to actionable message`() {
        val error = FirebaseAuthInvalidCredentialsException(
            "ERROR_INVALID_LOGIN_CREDENTIAL",
            "The supplied auth credential is incorrect, malformed or has expired."
        )

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "Email o contraseña incorrectos. Si te registraste con Google, usá \"Continuar con Google\".",
            mapped
        )
    }

    @Test
    fun `maps invalid user to account not found message`() {
        val error = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "No user")

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "No encontramos una cuenta activa con ese email. Creá una cuenta o ingresá con Google.",
            mapped
        )
    }

    @Test
    fun `maps duplicated email to collision message`() {
        val error = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "Collision")

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "Ese email ya está registrado. Iniciá sesión o recuperá tu contraseña.",
            mapped
        )
    }

    @Test
    fun `maps connectivity errors to offline message`() {
        val error = FirebaseNetworkException("A network error")

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals("Sin conexión. Verificá internet e intentá nuevamente.", mapped)
    }

    @Test
    fun `returns domain message for illegal state errors`() {
        val error = IllegalStateException("Necesitás verificar tu email antes de ingresar.")

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals("Necesitás verificar tu email antes de ingresar.", mapped)
    }

    @Test
    fun `maps ownership not found errors to clear user message`() {
        val error = firebaseFunctionsException(
            "No existe usuario activo con ese email",
            FirebaseFunctionsException.Code.NOT_FOUND,
            null
        )

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "No encontramos un usuario activo con ese email. Verificá que ya tenga cuenta en SellIA.",
            mapped
        )
    }

    @Test
    fun `maps ownership conflict with another store to specific guidance`() {
        val error = firebaseFunctionsException(
            "El usuario ya administra otra tienda",
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            null
        )

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "Ese email ya administra otra tienda. Usá otro usuario para co-dueño o delegación.",
            mapped
        )
    }

    private fun firebaseFunctionsException(
        message: String,
        code: FirebaseFunctionsException.Code,
        details: Any?
    ): FirebaseFunctionsException {
        val constructor = FirebaseFunctionsException::class.java.declaredConstructors
            .firstOrNull { candidate ->
                val parameterTypes = candidate.parameterTypes
                parameterTypes.size == 3 &&
                    parameterTypes[0] == String::class.java &&
                    parameterTypes[1] == FirebaseFunctionsException.Code::class.java &&
                    parameterTypes[2] == Any::class.java
            } as? Constructor<FirebaseFunctionsException>
            ?: error("FirebaseFunctionsException constructor not found")

        constructor.isAccessible = true
        return constructor.newInstance(message, code, details)
    }
}
