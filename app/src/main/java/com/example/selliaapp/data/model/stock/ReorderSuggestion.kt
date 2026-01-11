package com.example.selliaapp.data.model.stock

data class ProductSalesSummary(
    val productId: Int,
    val name: String,
    val quantity: Int,
    val minStock: Int,
    val providerId: Int?,
    val providerName: String?,
    val soldUnits: Int
)

data class ReorderSuggestion(
    val productId: Int,
    val name: String,
    val providerId: Int?,
    val providerName: String?,
    val currentStock: Int,
    val minStock: Int,
    val soldLastDays: Int,
    val windowDays: Int,
    val avgDailySales: Double,
    val projectedDaysOfStock: Double?,
    val suggestedOrderQty: Int
)
