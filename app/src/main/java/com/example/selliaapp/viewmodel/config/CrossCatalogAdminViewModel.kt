package com.example.selliaapp.viewmodel.config

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CrossCatalogAdminViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    data class UiState(
        val isImporting: Boolean = false,
        val lastResult: ImportResult? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun importFromFile(
        context: Context,
        uri: Uri,
        onCompleted: (ImportResult) -> Unit
    ) {
        viewModelScope.launch(io) {
            _uiState.value = _uiState.value.copy(isImporting = true)
            val result = productRepository.importCrossCatalogFromFile(context, uri)
            _uiState.value = _uiState.value.copy(isImporting = false, lastResult = result)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }
}
