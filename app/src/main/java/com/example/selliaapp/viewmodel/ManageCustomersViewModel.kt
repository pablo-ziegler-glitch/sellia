package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageCustomersViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> =
        query.flatMapLatest { q -> repo.search(q) }.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

    fun setQuery(q: String) { query.value = q }

    fun save(customer: CustomerEntity, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repo.upsert(customer) }.onSuccess { onDone() }
    }

    fun delete(customer: CustomerEntity) = viewModelScope.launch {
        runCatching { repo.delete(customer) }
    }
}
