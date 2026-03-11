package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.remote.AppNotification
import com.example.selliaapp.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val userId = MutableStateFlow(auth.currentUser?.uid.orEmpty())

    val notifications: StateFlow<List<AppNotification>> = userId.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList())
        else notificationRepository.observeNotifications(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = userId.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0)
        else notificationRepository.observeUnreadCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllAsRead() {
        val uid = userId.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            notificationRepository.markAllAsRead(uid)
        }
    }
}
