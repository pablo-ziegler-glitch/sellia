package com.floki.app.data.mappers

import com.floki.app.data.local.entity.CustomerEntity
import com.floki.app.data.model.Customer

fun CustomerEntity.toModel(): Customer =
    Customer(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        nickname = nickname,
        rubrosCsv = rubrosCsv,
        paymentTerm = paymentTerm,
        paymentMethod = paymentMethod,
        createdAt = createdAt
    )

fun Customer.toEntity(): CustomerEntity =
    CustomerEntity(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        nickname = nickname,
        rubrosCsv = rubrosCsv,
        paymentTerm = paymentTerm,
        paymentMethod = paymentMethod,
        createdAt = createdAt
    )
