package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.User
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel  @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    val user: StateFlow<List<User>> =
        repository.observeUsers().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    init {
        refreshFromCloud()
    }

    fun refreshFromCloud() {
        viewModelScope.launch {
            repository.syncFromCloud()
        }
    }

    fun addUser(name: String, email: String, role: String, isActive: Boolean = true) {
        viewModelScope.launch {
            val requestedRole = AppRole.fromRaw(role)
            val totalUsers = repository.countUsers()
            val effectiveRole = if (totalUsers == 0) {
                AppRole.ADMIN.raw
            } else {
                normalizeAssignableRole(requestedRole).raw
            }
            repository.insert(
                User(
                    name = name.trim(),
                    email = email.trim(),
                    role = effectiveRole,
                    isActive = isActive
                )
            )
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch { repository.delete(user) }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            repository.update(
                user.copy(
                    role = normalizeAssignableRole(AppRole.fromRaw(user.role)).raw
                )
            )
        }
    }

    private fun normalizeAssignableRole(role: AppRole): AppRole = when (role) {
        AppRole.MANAGER,
        AppRole.CASHIER -> role

        else -> AppRole.CASHIER
    }
}
