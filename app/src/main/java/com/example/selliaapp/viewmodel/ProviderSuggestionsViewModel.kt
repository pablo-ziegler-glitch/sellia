package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.stock.ReorderSuggestion
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderSuggestionsState(
    val loading: Boolean = true,
    val suggestions: List<ReorderSuggestion> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ProviderSuggestionsViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderSuggestionsState())
    val state: StateFlow<ProviderSuggestionsState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            runCatching {
                productRepository.getReorderSuggestions()
            }.onSuccess { suggestions ->
                _state.update { it.copy(loading = false, suggestions = suggestions) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.localizedMessage
                            ?: "No fue posible cargar sugerencias"
                    )
                }
            }
        }
    }
}
