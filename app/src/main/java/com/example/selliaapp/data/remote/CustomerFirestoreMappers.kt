package com.example.selliaapp.data.remote

import com.example.selliaapp.data.local.entity.CustomerEntity
import java.time.ZoneId

object CustomerFirestoreMappers {
    fun toMap(customer: CustomerEntity, tenantId: String): Map<String, Any?> = mapOf(
        "id" to customer.id,
        "tenantId" to tenantId,
        "name" to customer.name,
        "phone" to customer.phone,
        "email" to customer.email,
        "address" to customer.address,
        "nickname" to customer.nickname,
        "rubrosCsv" to customer.rubrosCsv,
        "paymentTerm" to customer.paymentTerm,
        "paymentMethod" to customer.paymentMethod,
        "createdAtMillis" to customer.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
}
