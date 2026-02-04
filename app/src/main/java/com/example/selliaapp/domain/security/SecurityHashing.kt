package com.example.selliaapp.domain.security

import java.security.MessageDigest

object SecurityHashing {
    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashEmail(email: String): String = sha256(email.trim().lowercase())
}
