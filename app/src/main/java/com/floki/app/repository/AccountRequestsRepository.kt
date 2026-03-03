package com.floki.app.repository

import com.floki.app.data.model.onboarding.AccountRequest
import com.floki.app.data.model.onboarding.AccountRequestStatus

interface AccountRequestsRepository {
    suspend fun fetchRequests(): Result<List<AccountRequest>>

    suspend fun updateRequest(
        requestId: String,
        status: AccountRequestStatus,
        enabledModules: Map<String, Boolean>
    ): Result<Unit>
}
