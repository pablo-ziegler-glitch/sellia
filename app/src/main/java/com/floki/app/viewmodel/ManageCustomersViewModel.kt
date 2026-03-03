package com.floki.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floki.app.data.local.entity.CustomerEntity
import com.floki.app.repository.CustomerRepository
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
        repo.upsert(customer)
        onDone()
    }

    fun delete(customer: CustomerEntity) = viewModelScope.launch {
        repo.delete(customer)
    }
}
