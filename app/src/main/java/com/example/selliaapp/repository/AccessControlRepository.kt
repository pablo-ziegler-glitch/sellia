package com.example.selliaapp.repository

import com.example.selliaapp.domain.security.UserAccessState
import kotlinx.coroutines.flow.Flow

interface AccessControlRepository {
    fun observeAccessState(): Flow<UserAccessState>

    suspend fun getAccessState(): UserAccessState
}
