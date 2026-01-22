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
import javax.inject.Inject

/**
 * VM simple para exponer la lista de clientes (y permitir filtrar).
 * Si después querés mover el filtro a la hoja, lo podemos simplificar aún más.
 */
@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> =
        query.flatMapLatest { q ->
            if (q.isBlank()) repo.observeAll() else repo.search(q)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(q: String) {
        query.value = q
    }
}
