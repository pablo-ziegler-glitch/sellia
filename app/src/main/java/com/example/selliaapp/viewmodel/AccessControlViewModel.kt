package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.domain.security.Permission
import com.example.selliaapp.domain.security.UserAccessState
import com.example.selliaapp.repository.AccessControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccessControlViewModel @Inject constructor(
    repository: AccessControlRepository
) : ViewModel() {

    val state: StateFlow<UserAccessState> = repository.observeAccessState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserAccessState.guest())

    fun has(permission: Permission): Boolean = state.value.permissions.contains(permission)
}
