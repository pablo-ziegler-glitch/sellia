package com.example.selliaapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.ui.navigation.Routes
import com.example.selliaapp.data.model.stock.StockMovementReasons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class QuickReorderState(
    val loading: Boolean = true,
    val product: ProductEntity? = null,
    val providers: List<ProviderEntity> = emptyList(),
    val selectedProviderId: Int? = null,
    val quantityText: String = "",
    val unitPriceText: String = "",
    val autoReceive: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val createdInvoiceId: Int? = null
) {
    val totalText: String
        get() = runCatching {
            val qty = quantityText.toDouble()
            val price = unitPriceText.replace(',', '.').toDouble()
            String.format(Locale.getDefault(), "%.2f", qty * price)
        }.getOrDefault("0.00")
}

@HiltViewModel
class QuickReorderViewModel @Inject constructor(
    private val productRepository: IProductRepository,
    private val providerRepository: ProviderRepository,
    private val providerInvoiceRepository: ProviderInvoiceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Int = checkNotNull(
        savedStateHandle.get<Int>(Routes.QuickReorder.ARG_PRODUCT_ID)
    )

    private val _state = MutableStateFlow(QuickReorderState())
    val state: StateFlow<QuickReorderState> = _state.asStateFlow()

    init {
        loadProduct()
        observeProviders()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            val product = productRepository.getById(productId)
            val deficit = product?.let { p ->
                val min = p.minStock ?: 0
                val missing = (min - p.quantity).coerceAtLeast(0)
                if (missing > 0) missing.toString() else ""
            } ?: ""
            val price = product?.let { p ->
                val resolved = p.listPrice ?: p.price
                resolved?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
            } ?: ""
            _state.update {
                it.copy(
                    loading = false,
                    product = product,
                    quantityText = deficit,
                    unitPriceText = price,
                    selectedProviderId = product?.providerId,
                    autoReceive = false,
                    error = if (product == null) "Producto no encontrado" else null
                )
            }
        }
    }

    private fun observeProviders() {
        viewModelScope.launch {
            providerRepository.observeAll().collect { providers ->
                val current = state.value
                val resolvedProviderId = when {
                    current.selectedProviderId != null -> current.selectedProviderId
                    current.product?.providerId != null -> current.product.providerId
                    else -> providers.firstOrNull()?.id
                }
                _state.update {
                    it.copy(providers = providers, selectedProviderId = resolvedProviderId)
                }
            }
        }
    }

    fun onProviderSelected(id: Int) {
        _state.update { it.copy(selectedProviderId = id) }
    }

    fun onQuantityChange(text: String) {
        _state.update { it.copy(quantityText = text, error = null) }
    }

    fun onUnitPriceChange(text: String) {
        _state.update { it.copy(unitPriceText = text, error = null) }
    }

    fun onToggleAutoReceive(value: Boolean) {
        _state.update { it.copy(autoReceive = value) }
    }

    fun createOrder() {
        val snapshot = state.value
        val product = snapshot.product ?: run {
            _state.update { it.copy(error = "Producto no encontrado") }
            return
        }
        val providerId = snapshot.selectedProviderId ?: run {
            _state.update { it.copy(error = "Seleccioná un proveedor") }
            return
        }
        val quantity = snapshot.quantityText.toDoubleOrNull()?.takeIf { it > 0 } ?: run {
            _state.update { it.copy(error = "Ingresá una cantidad mayor a cero") }
            return
        }
        val unitPrice = snapshot.unitPriceText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: run {
            _state.update { it.copy(error = "Ingresá un precio válido") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = false, createdInvoiceId = null) }
            val now = System.currentTimeMillis()
            val priceUnitNet = unitPrice
            val vatAmount = 0.0
            val lineTotal = priceUnitNet * quantity
            val invoice = ProviderInvoice(
                providerId = providerId,
                number = "PO-$now",
                issueDateMillis = now,
                total = lineTotal
            )
            val item = ProviderInvoiceItem(
                invoiceId = 0,
                code = product.code,
                name = product.name,
                quantity = quantity,
                priceUnit = priceUnitNet,
                vatPercent = 0.0,
                vatAmount = vatAmount,
                total = lineTotal
            )
            try {
                val invoiceId = providerInvoiceRepository.create(invoice, listOf(item)).toInt()
                if (snapshot.autoReceive) {
                    val delta = quantity.roundToInt()
                    if (delta > 0) {
                        productRepository.adjustStock(
                            productId = product.id,
                            delta = delta,
                            reason = StockMovementReasons.QUICK_ORDER_RECEIVED,
                            note = "Orden ${invoice.number}"
                        )
                    }
                }
                _state.update {
                    it.copy(
                        isSaving = false,
                        success = true,
                        createdInvoiceId = invoiceId
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = t.message ?: "Error al crear la orden"
                    )
                }
            }
        }
    }
}
