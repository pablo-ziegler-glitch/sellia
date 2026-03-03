// File: Customer.kt
package com.floki.app.data.model

import java.time.LocalDateTime

data class Customer(
    val id: Int = 0,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val rubrosCsv: String? = null,
    val paymentTerm: String? = null,
    val paymentMethod: String? = null,
    val nickname: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
