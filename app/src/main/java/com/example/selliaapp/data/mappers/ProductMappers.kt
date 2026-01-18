package com.example.selliaapp.data.mappers

import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.Product
import java.time.LocalDate

/**
 * Convierte un Product (modelo usado en UI/repos) a ProductEntity (persistencia Room).
 * Ajustá campos según tu schema real (nullable vs no-nullable).
 */

fun ProductEntity.toModel(): Product =
    Product(
        id = id,
        code = code,
        barcode = barcode,
        name = name,
        basePrice = basePrice,
        taxRate = taxRate,
        finalPrice = finalPrice,
        price = price,
        listPrice = listPrice,
        cashPrice = cashPrice,
        transferPrice = transferPrice,
        mlPrice = mlPrice,
        ml3cPrice = ml3cPrice,
        ml6cPrice = ml6cPrice,
        quantity = quantity ?: 0,
        description = description,
        imageUrl = imageUrl,
        category = category,
        providerName = providerName,
        providerSku = providerSku,
        minStock = minStock,
        updatedAt = updatedAt
    )

fun Product.toEntity(): ProductEntity =
    ProductEntity(
        id = id,
        code = code,
        barcode = barcode,
        name = name,
        basePrice = basePrice,
        taxRate = taxRate,
        finalPrice = finalPrice,
        price = price,
        listPrice = listPrice,
        cashPrice = cashPrice,
        transferPrice = transferPrice,
        mlPrice = mlPrice,
        ml3cPrice = ml3cPrice,
        ml6cPrice = ml6cPrice,
        quantity = quantity,
        description = description,
        imageUrl = imageUrl,
        category = category,
        providerName = providerName,
        providerSku = providerSku,
        minStock = minStock,
        updatedAt = updatedAt?: LocalDate.now()
    )
