package com.example.selliaapp.repository

import com.example.selliaapp.data.model.onboarding.AccountRequest
import com.example.selliaapp.data.model.onboarding.AccountRequestStatus

interface AccountRequestsRepository {
    suspend fun fetchRequests(): Result<List<AccountRequest>>

    suspend fun updateRequest(
        requestId: String,
        status: AccountRequestStatus,
        enabledModules: Map<String, Boolean>
    ): Result<Unit>
}
