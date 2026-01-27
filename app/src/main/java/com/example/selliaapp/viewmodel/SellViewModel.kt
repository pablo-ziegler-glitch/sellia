package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.sales.CartItem
import com.example.selliaapp.data.model.sales.InvoiceDraft
import com.example.selliaapp.data.local.entity.CashMovementType
import com.example.selliaapp.repository.CashRepository
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.ui.state.CartItemUi
import com.example.selliaapp.ui.state.CustomerSummaryUi
import com.example.selliaapp.ui.state.OrderType
import com.example.selliaapp.ui.state.PaymentMethod
import com.example.selliaapp.ui.state.SellUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SellViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val invoiceRepo: InvoiceRepository,
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SellUiState())
    val state: StateFlow<SellUiState> = _state.asStateFlow()
    private var customerSummaryJob: Job? = null

    // ----- Escaneo: helper de resultado -----
    data class ScanResult(val foundId: Int?, val prefillBarcode: String)

    /**
     * Consulta si un barcode existe. Si existe -> foundId != null.
     * Si no existe -> devolvemos el mismo barcode para precargar en alta.
     */
    suspend fun onScanBarcode(barcode: String): ScanResult = withContext(Dispatchers.IO) {
        val p = repo.getByBarcodeOrNull(barcode)
        if (p != null) ScanResult(foundId = p.id, prefillBarcode = barcode)
        else ScanResult(foundId = null, prefillBarcode = barcode)
    }


    /**
     * Agrega al carrito por barcode (usado tras el diálogo de cantidad).
     * Callbacks:
     *  - onSuccess: agregado ok
     *  - onNotFound: si el producto no existe (carrera entre escaneo y alta)
     *  - onError: error inesperado (DB/IO)
     */
    fun addToCartByScan(
        barcode: String,
        qty: Int,
        onSuccess: () -> Unit = {},
        onNotFound: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { repo.getByBarcodeOrNull(barcode) }
                if (p == null) {
                    onNotFound()
                } else {
                    addToCart(p, qty)
                    onSuccess()
                }
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    /** Agrega un producto acumulando, respetando stock (clamp 1..max). */
    fun addToCart(product: ProductEntity, qty: Int = 1) {
        _state.update { ui ->
            val max = (product.quantity ?: 0).coerceAtLeast(0)
            val listPrice = product.listPrice ?: product.price ?: 0.0
            val cashPrice = product.cashPrice ?: listPrice
            val transferPrice = product.transferPrice ?: listPrice
            val unit = resolveUnitPrice(ui.paymentMethod, listPrice, cashPrice, transferPrice)
            val current = ui.items.find { it.productId == product.id }
            val nuevosItems =
                if (current == null) {
                    val q = qty.coerceIn(1, max.coerceAtLeast(1))
                    ui.items + CartItemUi(
                        productId = product.id,
                        name = product.name,
                        barcode = product.barcode,
                        unitPrice = unit,
                        listPrice = listPrice,
                        cashPrice = cashPrice,
                        transferPrice = transferPrice,
                        qty = q,
                        maxStock = max
                    )
                } else {
                    val q = (current.qty + qty).coerceIn(1, max.coerceAtLeast(1))
                    ui.items.map { item ->
                        if (item.productId == product.id) item.copy(qty = q, maxStock = max) else item
                    }
                }
            recalc(ui, nuevosItems)
        }
    }

    /** Incrementa de a 1 (máximo: stock). */
    fun increment(productId: Int) {
        _state.update { ui ->
            val nuevosItems = ui.items.map { item ->
                if (item.productId == productId)
                    item.copy(qty = (item.qty + 1).coerceAtMost(item.maxStock.coerceAtLeast(1)))
                else item
            }
            recalc(ui, nuevosItems)
        }
    }

    /** Decrementa de a 1 (mínimo 1). */
    fun decrement(productId: Int) {
        _state.update { ui ->
            val nuevosItems = ui.items.map { item ->
                if (item.productId == productId)
                    item.copy(qty = (item.qty - 1).coerceAtLeast(1))
                else item
            }
            recalc(ui, nuevosItems)
        }
    }

    /** Fija una cantidad concreta (clamp 1..max). */
    fun updateQty(productId: Int, qty: Int) {
        _state.update { ui ->
            val nuevosItems = ui.items.map { item ->
                if (item.productId == productId) {
                    val max = item.maxStock.coerceAtLeast(1)
                    item.copy(qty = qty.coerceIn(1, max))
                } else item
            }
            recalc(ui, nuevosItems)
        }
    }

    /** Quita un ítem del carrito. */
    fun remove(productId: Int) {
        _state.update { ui ->
            val nuevosItems = ui.items.filterNot { it.productId == productId }
            recalc(ui, nuevosItems)
        }
    }

    /** Limpia el carrito y totales. */
    fun clear() {
        _state.value = SellUiState()
    }

    // --- Recalcula totales/validaciones y retorna nuevo estado (inmutable) ---
    private fun recalc(base: SellUiState, nuevosItems: List<CartItemUi>? = null): SellUiState {
        val items = nuevosItems ?: base.items
        val subtotal = items.sumOf { it.unitPrice * it.qty }
        val violations = items
            .filter { it.qty > it.maxStock }
            .associate { it.productId to it.maxStock }
        val baseAfterDiscounts = subtotal.coerceAtLeast(0.0)
        val customerDiscount = baseAfterDiscounts * base.customerDiscountPercent / 100.0
        val manualDiscount = baseAfterDiscounts * base.discountPercent / 100.0
        val totalDiscount = customerDiscount + manualDiscount
        val baseConDescuento = (subtotal - totalDiscount).coerceAtLeast(0.0)
        val recargo = baseConDescuento * base.surchargePercent / 100.0
        val total = baseConDescuento + recargo

        return base.copy(
            items = items,
            subtotal = subtotal,
            discountAmount = totalDiscount,
            manualDiscountAmount = manualDiscount,
            customerDiscountAmount = customerDiscount,
            surchargeAmount = recargo,
            total = total,
            stockViolations = violations
        )
    }

    fun setDiscountPercent(percent: Int) {
        _state.update { ui ->
            val nuevoValor = percent.coerceIn(0, 100)
            recalc(ui.copy(discountPercent = nuevoValor))
        }
    }

    fun setSurchargePercent(percent: Int) {
        _state.update { ui ->
            val nuevoValor = percent.coerceIn(0, 100)
            recalc(ui.copy(surchargePercent = nuevoValor))
        }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        _state.update { ui ->
            val updatedItems = ui.items.map { item ->
                val unit = resolveUnitPrice(method, item.listPrice, item.cashPrice, item.transferPrice)
                item.copy(unitPrice = unit)
            }
            recalc(ui.copy(paymentMethod = method), updatedItems)
        }
    }

    fun updatePaymentNotes(notes: String) {
        _state.update { ui ->
            ui.copy(paymentNotes = notes.take(280))
        }
    }

    fun updateOrderType(orderType: OrderType) {
        _state.update { ui ->
            ui.copy(orderType = orderType)
        }
    }

    private fun resolveUnitPrice(
        method: PaymentMethod,
        listPrice: Double,
        cashPrice: Double,
        transferPrice: Double
    ): Double {
        return when (method) {
            PaymentMethod.LISTA -> listPrice
            PaymentMethod.EFECTIVO -> cashPrice
            PaymentMethod.TRANSFERENCIA -> transferPrice
        }
    }

    fun setCustomer(customerId: Int?, customerName: String?) {
        customerSummaryJob?.cancel()
        _state.update { ui ->
            val next = ui.copy(
                selectedCustomerId = customerId,
                selectedCustomerName = customerName,
                customerSummary = null,
                customerDiscountPercent = 0
            )
            recalc(next)
        }
        if (customerName.isNullOrBlank()) {
            return
        }
        customerSummaryJob = viewModelScope.launch {
            invoiceRepo.observeInvoicesByCustomerQuery(customerName)
                .collect { invoices ->
                    val totalSpent = invoices.sumOf { it.invoice.total }
                    val purchaseCount = invoices.size
                    val lastPurchaseMillis = invoices.maxOfOrNull { it.invoice.dateMillis }
                    _state.update { ui ->
                        recalc(
                            ui.copy(
                                customerSummary = CustomerSummaryUi(
                                    totalSpent = totalSpent,
                                    purchaseCount = purchaseCount,
                                    lastPurchaseMillis = lastPurchaseMillis
                                )
                            )
                        )
                    }
                }
        }
    }

    fun setCustomerDiscountPercent(percent: Int) {
        _state.update { ui ->
            val sanitized = percent.coerceIn(0, 100)
            recalc(ui.copy(customerDiscountPercent = sanitized))
        }
    }

    fun placeOrder(
        customerId: Long? = null,
        customerName: String? = null,
        onSuccess: (CheckoutResult) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val current = state.value
        if (current.stockViolations.isNotEmpty()) {
            onError(IllegalStateException("Hay ítems con falta de stock."))
            return
        }

        viewModelScope.launch {
            try {
                val requiresCashSession = BuildConfig.REQUIRE_CASH_SESSION_FOR_CASH_PAYMENTS &&
                    current.paymentMethod == PaymentMethod.EFECTIVO
                val openSession = if (requiresCashSession) {
                    cashRepository.getOpenSession()
                } else {
                    null
                }
                if (requiresCashSession && openSession == null) {
                    onError(IllegalStateException("Necesitás abrir la caja para cobrar en efectivo."))
                    return@launch
                }
                val resolvedCustomerId = customerId ?: current.selectedCustomerId?.toLong()
                val resolvedCustomerName = customerName ?: current.selectedCustomerName
                val draft = InvoiceDraft(
                    items = current.items.map { item ->
                        CartItem(
                            productId = item.productId.toLong(),
                            name = item.name,
                            quantity = item.qty,
                            unitPrice = item.unitPrice
                        )
                    },
                    subtotal = current.subtotal,
                    taxes = 0.0,
                    total = current.total,
                    discountPercent = current.totalDiscountPercent,
                    discountAmount = current.discountAmount,
                    surchargePercent = current.surchargePercent,
                    surchargeAmount = current.surchargeAmount,
                    paymentMethod = current.paymentMethod.name,
                    paymentNotes = current.paymentNotes.takeIf { it.isNotBlank() },
                    customerId = resolvedCustomerId,
                    customerName = resolvedCustomerName
                )

                val result = invoiceRepo.confirmInvoice(draft)
                if (current.paymentMethod == PaymentMethod.EFECTIVO && openSession != null) {
                    cashRepository.registerMovement(
                        sessionId = openSession.id,
                        type = CashMovementType.SALE_CASH,
                        amount = current.total,
                        note = current.paymentNotes.takeIf { it.isNotBlank() },
                        referenceId = result.invoiceId.toString()
                    )
                }
                val checkoutResult = CheckoutResult(
                    invoiceId = result.invoiceId,
                    invoiceNumber = result.invoiceNumber,
                    total = current.total,
                    paymentMethod = current.paymentMethod,
                    discountPercent = current.discountPercent,
                    surchargePercent = current.surchargePercent,
                    notes = current.paymentNotes
                )
                clear()
                onSuccess(checkoutResult)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}

data class CheckoutResult(
    val invoiceId: Long,
    val invoiceNumber: String,
    val total: Double,
    val paymentMethod: PaymentMethod,
    val discountPercent: Int,
    val surchargePercent: Int,
    val notes: String
)
