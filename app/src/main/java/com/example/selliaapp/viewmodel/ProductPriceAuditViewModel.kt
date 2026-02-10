package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.ProductPriceAuditDao
import com.example.selliaapp.data.local.entity.ProductPriceAuditEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProductPriceAuditUiState(
    val entries: List<ProductPriceAuditEntity> = emptyList()
)

@HiltViewModel
class ProductPriceAuditViewModel @Inject constructor(
    productPriceAuditDao: ProductPriceAuditDao
) : ViewModel() {
    val state: StateFlow<ProductPriceAuditUiState> = productPriceAuditDao.observeRecent(limit = 300)
        .map { ProductPriceAuditUiState(entries = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProductPriceAuditUiState()
        )
}
