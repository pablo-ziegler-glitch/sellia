package com.example.selliaapp.viewmodel.providers

import androidx.lifecycle.ViewModel
import com.example.selliaapp.domain.invoiceimport.ParsedProviderInvoiceDraft
import com.example.selliaapp.domain.invoiceimport.ProviderInvoiceTextParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ProviderInvoiceReaderViewModel @Inject constructor(
    private val parser: ProviderInvoiceTextParser
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderInvoiceReaderUiState())
    val state: StateFlow<ProviderInvoiceReaderUiState> = _state.asStateFlow()

    fun onRawTextChange(value: String) {
        _state.update { it.copy(rawText = value) }
    }


    fun onError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun analyze() {
        val text = _state.value.rawText.trim()
        if (text.isBlank()) {
            _state.update { it.copy(errorMessage = "Pegá el texto OCR de la factura para analizarla") }
            return
        }
        val parsed = parser.parse(text)
        _state.update {
            it.copy(
                parsed = parsed,
                errorMessage = null
            )
        }
    }
}

data class ProviderInvoiceReaderUiState(
    val rawText: String = "",
    val parsed: ParsedProviderInvoiceDraft? = null,
    val errorMessage: String? = null
)
