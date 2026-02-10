package com.example.selliaapp.domain.security

import java.security.MessageDigest

object SecurityHashing {
    fun normalizeEmail(email: String?): String = email?.trim()?.lowercase().orEmpty()

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashEmail(email: String): String = sha256(normalizeEmail(email))
}
