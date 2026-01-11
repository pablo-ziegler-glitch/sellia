package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.data.local.entity.VariantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddVariantViewModel @Inject constructor(
    private val variantDao: VariantDao
) : ViewModel() {

    fun addVariant(
        productId: Int,
        sku: String?,
        option1: String?,
        option2: String?,
        quantity: Int,
        basePrice: Double?,
        taxRate: Double?,
        onDone: (Long) -> Unit = {}
    ) {
        val trimmedSku = sku?.trim().takeIf { !it.isNullOrBlank() }
        val trimmedOption1 = option1?.trim().takeIf { !it.isNullOrBlank() }
        val trimmedOption2 = option2?.trim().takeIf { !it.isNullOrBlank() }
        val normalizedTax = taxRate?.takeIf { it >= 0 }
        val normalizedBase = basePrice?.takeIf { it >= 0 }
        val finalPrice = normalizedBase?.let { base ->
            base * (1.0 + (normalizedTax ?: 0.0))
        }

        viewModelScope.launch {
            val id = variantDao.insert(
                VariantEntity(
                    productId = productId,
                    sku = trimmedSku,
                    option1 = trimmedOption1,
                    option2 = trimmedOption2,
                    quantity = quantity.coerceAtLeast(0),
                    basePrice = normalizedBase,
                    taxRate = normalizedTax,
                    finalPrice = finalPrice
                )
            )
            onDone(id)
        }
    }
}
