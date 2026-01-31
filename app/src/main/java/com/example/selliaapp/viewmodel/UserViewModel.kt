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



    fun addUser(name: String,email : String, role : String) {
        viewModelScope.launch {
            val sanitizedRole = role.trim().ifBlank { AppRole.VIEWER.raw }
            val totalUsers = repository.countUsers()
            val effectiveRole = if (totalUsers == 0) {
                AppRole.SUPER_ADMIN.raw
            } else {
                sanitizedRole
            }
            repository.insert(User(name = name.trim(), email = email.trim(), role = effectiveRole))
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch { repository.delete(user) }
    }
}
