package com.example.selliaapp.repository

import com.example.selliaapp.data.remote.StoreRequest

interface StoreRequestRepository {
    suspend fun submitRequest(request: StoreRequest): Result<String>
    suspend fun getMyRequests(userId: String): Result<List<StoreRequest>>
    suspend fun getPendingRequests(): Result<List<StoreRequest>>
    suspend fun updateRequestStatus(requestId: String, status: String): Result<Unit>
}
