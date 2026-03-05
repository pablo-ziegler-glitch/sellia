package com.floki.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floki.app.data.local.entity.ProductEntity
import com.floki.app.repository.IProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductLookupViewModel @Inject constructor(
    private val repo: IProductRepository
) : ViewModel() {

    private val _result = MutableStateFlow<ProductEntity?>(null)
    val result: StateFlow<ProductEntity?> = _result.asStateFlow()

    fun findByBarcode(barcode: String) {
        viewModelScope.launch {
            _result.value = runCatching { repo.getByBarcodeOrNull(barcode) }.getOrNull()
        }
    }
}
