package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BulkDataViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    fun importCustomers(
        context: Context,
        uri: Uri,
        onCompleted: (ImportResult) -> Unit
    ) {
        viewModelScope.launch(io) {
            val result = customerRepository.importCustomersFromFile(context, uri)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun importUsers(
        context: Context,
        uri: Uri,
        onCompleted: (ImportResult) -> Unit
    ) {
        viewModelScope.launch(io) {
            val result = userRepository.importUsersFromFile(context, uri)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }
}
