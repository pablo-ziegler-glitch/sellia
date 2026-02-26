package com.example.selliaapp.viewmodel.checkout

import androidx.lifecycle.ViewModel
import com.example.selliaapp.auth.TenantProvider
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.CartItem
import com.example.selliaapp.data.model.sales.InvoiceDraft
import com.example.selliaapp.data.payment.PaymentItem
import com.example.selliaapp.domain.payment.CreatePaymentPreferenceUseCase
import com.example.selliaapp.repository.CartRepository
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.ui.state.CartItemUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val invoiceRepository: InvoiceRepository,
    private val createPaymentPreferenceUseCase: CreatePaymentPreferenceUseCase,
    private val tenantProvider: TenantProvider
) : ViewModel() {
    private companion object {
        const val MAX_POLL_ATTEMPTS = 8
        const val INITIAL_BACKOFF_MS = 1500L
        const val MAX_BACKOFF_MS = 10_000L
    }
    // [NUEVO] Configuración de impuestos simple (si tu lógica real difiere, ajustá)
    private val TAX_RATE = 0.0 // 0% por defecto; cambiar si aplican impuestos

    // [NUEVO] Estado de UI con items/subtotal/taxes/total
    val uiState: StateFlow<CheckoutUiState> =
        cartRepository.observeCart()
            .map { lines ->
                val subtotal = lines.sumOf { it.lineTotal }
                val taxes = subtotal * TAX_RATE
                val total = subtotal + taxes
                CheckoutUiState(
                    items = lines,
                    subtotal = subtotal,
                    taxes = taxes,
                    total = total
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CheckoutUiState.EMPTY
            )

    private val _paymentState = MutableStateFlow(PaymentUiState())
    val paymentState: StateFlow<PaymentUiState> = _paymentState.asStateFlow()

    // -----------------------
    // Acciones del carrito
    // -----------------------
    fun addItem(productId: Long, name: String, unitPrice: Double, quantity: Int = 1) {
        viewModelScope.launch {
            cartRepository.add(productId, name, unitPrice, quantity)
        }
    }

    fun removeItem(productId: Long, quantity: Int = 1) {
        viewModelScope.launch {
            cartRepository.remove(productId, quantity)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clear()
        }
    }

    // -----------------------
    // Pagos Mercado Pago
    // -----------------------
    fun createPaymentPreference(
        amount: Double,
        items: List<CartItemUi>,
        customerName: String?
    ) {
        if (_paymentState.value.isLoading) return
        if (amount <= 0 || items.isEmpty()) {
            _paymentState.update {
                it.copy(errorMessage = "El total debe ser mayor a cero para generar el pago.")
            }
            return
        }

        viewModelScope.launch {
            _paymentState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val description = buildPaymentDescription(items)
                val externalReference = "pos-${UUID.randomUUID()}"
                val metadata = mutableMapOf<String, Any>(
                    "source" to "pos_checkout",
                    "items_count" to items.size
                ).apply {
                    customerName?.let { put("customer_name", it) }
                }

                val paymentItems = items.map { item ->
                    PaymentItem(
                        id = item.productId.toString(),
                        title = item.name,
                        quantity = item.qty,
                        unitPrice = item.unitPrice
                    )
                }

                val result = createPaymentPreferenceUseCase(
                    amount = amount,
                    description = description,
                    externalReference = externalReference,
                    tenantId = tenantProvider.requireTenantId(),
                    items = paymentItems,
                    metadata = metadata
                )
                _paymentState.update {
                    it.copy(
                        isLoading = false,
                        initPoint = result.initPoint,
                        preferenceId = result.preferenceId,
                        orderId = result.orderId,
                        idempotencyKey = result.idempotencyKey,
                        paymentStatus = result.paymentStatus ?: "pending_confirmation",
                        isAwaitingConfirmation = true,
                        canRetry = false,
                        pollingAttempts = 0
                    )
                }
                startOrderStatusPolling()
            } catch (t: Throwable) {
                _paymentState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "No se pudo generar el link de pago."
                    )
                }
            }
        }
    }

    fun consumeInitPoint() {
        _paymentState.update { it.copy(initPoint = null) }
    }

    fun clearPaymentError() {
        _paymentState.update { it.copy(errorMessage = null) }
    }

    fun retryPendingConfirmation() {
        if (_paymentState.value.orderId.isNullOrBlank()) return
        _paymentState.update {
            it.copy(
                isAwaitingConfirmation = true,
                canRetry = false,
                errorMessage = null,
                pollingAttempts = 0
            )
        }
        startOrderStatusPolling()
    }

    private fun startOrderStatusPolling() {
        val orderId = _paymentState.value.orderId ?: return
        viewModelScope.launch {
            var delayMs = INITIAL_BACKOFF_MS
            repeat(MAX_POLL_ATTEMPTS) { attempt ->
                if (!isActive) return@launch
                val status = invoiceRepository.refreshOrderStatus(orderId)
                if (status != null) {
                    val normalized = (status.paymentStatus ?: status.status).lowercase()
                    val isTerminal = normalized == "approved" || normalized == "rejected" || normalized == "failed"
                    _paymentState.update {
                        it.copy(
                            paymentStatus = normalized,
                            isAwaitingConfirmation = !isTerminal,
                            canRetry = !isTerminal && attempt >= MAX_POLL_ATTEMPTS - 1,
                            pollingAttempts = attempt + 1,
                            statusDetail = status.statusDetail
                        )
                    }
                    if (isTerminal) return@launch
                }
                if (attempt < MAX_POLL_ATTEMPTS - 1) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }

            _paymentState.update {
                it.copy(
                    isAwaitingConfirmation = false,
                    canRetry = true,
                    errorMessage = it.errorMessage
                        ?: "La confirmación está demorando. Podés reintentar sin duplicar la orden desde el historial.",
                    paymentStatus = it.paymentStatus ?: "pending_confirmation"
                )
            }
        }
    }


// -----------------------
    // Confirmación de venta
    // -----------------------
    /**
     * Confirma la venta generando un InvoiceDraft a partir del estado actual del carrito
     * y delega en InvoiceRepository.confirmInvoice(draft).
     *
     * @param customerId    opcional, si ya tenés el ID del cliente (puede ser null)
     * @param customerName  opcional, nombre a mostrar (si no hay cliente seleccionado)
     */
    fun confirmSale(
        customerId: Long? = null,
        customerName: String? = null,
        onSuccess: (invoiceId: Long, invoiceNumber: String) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val state = uiState.value

                // [NUEVO] armamos el InvoiceDraft con CartItem + totales
                val draft = InvoiceDraft(
                    items = state.items.map { it.toCartItem() },
                    subtotal = state.subtotal,
                    taxes = state.taxes,
                    total = state.total,
                    customerId = customerId,
                    customerName = customerName
                )

                val result = invoiceRepository.confirmInvoice(draft)
                cartRepository.clear()
                onSuccess(result.invoiceId, result.invoiceNumber)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }


    fun cancelCheckout(onCanceled: () -> Unit) {
        viewModelScope.launch {
            // Podés decidir si limpiar o no; acá no limpiamos por si vuelve atrás
            onCanceled()
        }
    }

    private fun buildPaymentDescription(items: List<CartItemUi>): String {
        return when {
            items.isEmpty() -> "Venta en Sellia"
            items.size == 1 -> "Venta de ${items.first().name}"
            else -> "Venta de ${items.size} productos"
        }
    }
}


/**
 * [NUEVO] Estado de pantalla para el checkout, con todo lo que la UI requiere.
 */
data class CheckoutUiState(
    val items: List<CartRepository.CartLine>,
    val subtotal: Double,
    val taxes: Double,
    val total: Double
) {
    companion object {
        val EMPTY = CheckoutUiState(
            items = emptyList(),
            subtotal = 0.0,
            taxes = 0.0,
            total = 0.0
        )
    }
}

data class PaymentUiState(
    val isLoading: Boolean = false,
    val initPoint: String? = null,
    val preferenceId: String? = null,
    val errorMessage: String? = null,
    val orderId: String? = null,
    val idempotencyKey: String? = null,
    val paymentStatus: String? = null,
    val isAwaitingConfirmation: Boolean = false,
    val canRetry: Boolean = false,
    val pollingAttempts: Int = 0,
    val statusDetail: String? = null
)



private fun CartRepository.CartLine.toCartItem(): CartItem =
    CartItem(
        productId = this.productId,
        name = this.name,
        quantity = this.quantity,
        unitPrice = this.unitPrice
    )
