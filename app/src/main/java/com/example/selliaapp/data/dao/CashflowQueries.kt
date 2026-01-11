package com.example.selliaapp.data.dao

data class MonthlyTotal(
    val year: Int,
    val month: Int,
    val total: Double
)

data class CategoryTotal(
    val category: String,
    val total: Double
)
