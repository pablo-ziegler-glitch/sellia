package com.example.selliaapp.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AuthErrorMapperTest {

    @Test
    fun `maps invalid login credential to actionable message`() {
        val error = mock<FirebaseAuthInvalidCredentialsException>()
        whenever(error.errorCode).thenReturn("ERROR_INVALID_LOGIN_CREDENTIAL")
        whenever(error.message).thenReturn(
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
        val error = mock<FirebaseAuthInvalidUserException>()

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "No encontramos una cuenta activa con ese email. Creá una cuenta o ingresá con Google.",
            mapped
        )
    }

    @Test
    fun `maps duplicated email to collision message`() {
        val error = mock<FirebaseAuthUserCollisionException>()

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
        val error = mock<FirebaseFunctionsException>()
        whenever(error.code).thenReturn(FirebaseFunctionsException.Code.NOT_FOUND)
        whenever(error.message).thenReturn("No existe usuario activo con ese email")
        whenever(error.details).thenReturn(null)

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "No encontramos un usuario activo con ese email. Verificá que ya tenga cuenta en SellIA.",
            mapped
        )
    }

    @Test
    fun `maps ownership conflict with another store to specific guidance`() {
        val error = mock<FirebaseFunctionsException>()
        whenever(error.code).thenReturn(FirebaseFunctionsException.Code.FAILED_PRECONDITION)
        whenever(error.message).thenReturn("El usuario ya administra otra tienda")
        whenever(error.details).thenReturn(null)

        val mapped = AuthErrorMapper.toUserMessage(error, "fallback")

        assertEquals(
            "Ese email ya administra otra tienda. Usá otro usuario para co-dueño o delegación.",
            mapped
        )
    }
}
