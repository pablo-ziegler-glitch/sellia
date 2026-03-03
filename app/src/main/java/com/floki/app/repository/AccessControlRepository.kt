package com.floki.app.repository

import com.floki.app.domain.security.UserAccessState
import kotlinx.coroutines.flow.Flow

interface AccessControlRepository {
    fun observeAccessState(): Flow<UserAccessState>

    suspend fun getAccessState(): UserAccessState
}
