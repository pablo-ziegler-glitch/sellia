package com.example.selliaapp.repository

data class TenantSummary(
    val id: String,
    val name: String
)

interface TenantDirectoryRepository {
    suspend fun fetchTenants(): Result<List<TenantSummary>>
}
