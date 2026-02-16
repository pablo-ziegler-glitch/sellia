package com.example.selliaapp.viewmodel

import android.net.Uri
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
import com.example.selliaapp.repository.SellDraft
import com.example.selliaapp.repository.SellDraftItem
import com.example.selliaapp.repository.SellDraftRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SellViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val invoiceRepo: InvoiceRepository,
    private val cashRepository: CashRepository,
    private val sellDraftRepository: SellDraftRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SellUiState())
    val state: StateFlow<SellUiState> = _state.asStateFlow()
    private var customerSummaryJob: Job? = null

    init {
        restoreDraft()
    }

    // ----- Escaneo: helper de resultado -----
    data class ScanResult(val foundId: Int?, val prefillBarcode: String)

    /**
     * Consulta si un valor escaneado corresponde a un producto existente.
     * Soporta barcode plano y QR público/interno (URL, código interno o PRODUCT-<id>).
     * Si no existe -> devolvemos el valor normalizado para precargar en alta.
     */
    suspend fun onScanBarcode(rawScanValue: String): ScanResult = withContext(Dispatchers.IO) {
        val normalizedValue = normalizeScanValue(rawScanValue)
        val product = resolveProductByScan(rawScanValue)
        if (product != null) ScanResult(foundId = product.id, prefillBarcode = normalizedValue)
        else ScanResult(foundId = null, prefillBarcode = normalizedValue)
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
                val p = withContext(Dispatchers.IO) { resolveProductByScan(barcode) }
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

    private suspend fun resolveProductByScan(rawValue: String): ProductEntity? {
        val normalizedValue = normalizeScanValue(rawValue)
        val candidates = linkedSetOf<String>().apply {
            add(normalizedValue)
            add(rawValue.trim())
            extractPathLastSegment(normalizedValue)?.let(::add)
            extractPathLastSegment(rawValue)?.let(::add)
        }.filter { it.isNotBlank() }

        candidates.forEach { candidate ->
            repo.getByBarcodeOrNull(candidate)?.let { return it }
            repo.getByCodeOrNull(candidate)?.let { return it }
            parseProductId(candidate)?.let { id ->
                repo.getById(id)?.let { return it }
            }
        }

        return null
    }

    private fun normalizeScanValue(rawValue: String): String {
        val value = rawValue.trim()
        if (value.isBlank()) return value
        val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return value

        val queryCandidate = listOf("q", "qr", "barcode", "code", "productId", "product_id", "id")
            .firstNotNullOfOrNull { key -> parsed.getQueryParameter(key)?.takeIf { it.isNotBlank() } }

        return queryCandidate?.trim()
            ?: extractPathLastSegment(value)
            ?: value
    }

    private fun extractPathLastSegment(rawValue: String): String? {
        val parsed = runCatching { Uri.parse(rawValue) }.getOrNull() ?: return null
        return parsed.lastPathSegment?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseProductId(value: String): Int? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        if (normalized.all(Char::isDigit)) return normalized.toIntOrNull()
        if (normalized.startsWith("PRODUCT-", ignoreCase = true)) {
            return normalized.removePrefix("PRODUCT-").toIntOrNull()
        }
        return null
    }

    /** Agrega un producto acumulando, respetando stock (clamp 1..max). */
    fun addToCart(product: ProductEntity, qty: Int = 1) {
        updateAndPersist { ui ->
            val max = (product.quantity ?: 0).coerceAtLeast(0)
            val listPrice = product.listPrice ?: 0.0
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
        updateAndPersist { ui ->
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
        updateAndPersist { ui ->
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
        updateAndPersist { ui ->
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
        updateAndPersist { ui ->
            val nuevosItems = ui.items.filterNot { it.productId == productId }
            recalc(ui, nuevosItems)
        }
    }

    /** Limpia el carrito y totales. */
    fun clear() {
        _state.value = SellUiState()
        sellDraftRepository.clear()
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
        updateAndPersist { ui ->
            val nuevoValor = percent.coerceIn(0, 100)
            recalc(ui.copy(discountPercent = nuevoValor))
        }
    }

    fun setSurchargePercent(percent: Int) {
        updateAndPersist { ui ->
            val nuevoValor = percent.coerceIn(0, 100)
            recalc(ui.copy(surchargePercent = nuevoValor))
        }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        updateAndPersist { ui ->
            val updatedItems = ui.items.map { item ->
                val unit = resolveUnitPrice(method, item.listPrice, item.cashPrice, item.transferPrice)
                item.copy(unitPrice = unit)
            }
            recalc(ui.copy(paymentMethod = method), updatedItems)
        }
    }

    fun updatePaymentNotes(notes: String) {
        updateAndPersist { ui ->
            ui.copy(paymentNotes = notes.take(280))
        }
    }

    fun updateOrderType(orderType: OrderType) {
        updateAndPersist { ui ->
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
        updateAndPersist { ui ->
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
                    _state.value = recalc(
                        _state.value.copy(
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

    fun setCustomerDiscountPercent(percent: Int) {
        updateAndPersist { ui ->
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

    private fun restoreDraft() {
        val draft = sellDraftRepository.load() ?: return
        val restoredState = SellUiState(
            items = draft.items.map { item ->
                CartItemUi(
                    productId = item.productId,
                    name = item.name,
                    barcode = item.barcode,
                    unitPrice = item.unitPrice,
                    listPrice = item.listPrice,
                    cashPrice = item.cashPrice,
                    transferPrice = item.transferPrice,
                    qty = item.qty.coerceAtLeast(1),
                    maxStock = item.maxStock.coerceAtLeast(0)
                )
            },
            discountPercent = draft.discountPercent.coerceIn(0, 100),
            customerDiscountPercent = draft.customerDiscountPercent.coerceIn(0, 100),
            surchargePercent = draft.surchargePercent.coerceIn(0, 100),
            paymentMethod = runCatching { PaymentMethod.valueOf(draft.paymentMethod) }.getOrDefault(PaymentMethod.LISTA),
            paymentNotes = draft.paymentNotes,
            orderType = runCatching { OrderType.valueOf(draft.orderType) }.getOrDefault(OrderType.INMEDIATA),
            selectedCustomerId = draft.selectedCustomerId,
            selectedCustomerName = draft.selectedCustomerName
        )
        _state.value = recalc(restoredState)
    }

    private fun updateAndPersist(transform: (SellUiState) -> SellUiState) {
        val next = transform(_state.value)
        _state.value = next
        persistDraft(next)
    }

    private fun persistDraft(state: SellUiState) {
        if (state.items.isEmpty()) {
            sellDraftRepository.clear()
            return
        }
        val draft = SellDraft(
            items = state.items.map { item ->
                SellDraftItem(
                    productId = item.productId,
                    name = item.name,
                    barcode = item.barcode,
                    unitPrice = item.unitPrice,
                    listPrice = item.listPrice,
                    cashPrice = item.cashPrice,
                    transferPrice = item.transferPrice,
                    qty = item.qty,
                    maxStock = item.maxStock
                )
            },
            discountPercent = state.discountPercent,
            customerDiscountPercent = state.customerDiscountPercent,
            surchargePercent = state.surchargePercent,
            paymentMethod = state.paymentMethod.name,
            paymentNotes = state.paymentNotes,
            orderType = state.orderType.name,
            selectedCustomerId = state.selectedCustomerId,
            selectedCustomerName = state.selectedCustomerName
        )
        sellDraftRepository.save(draft)
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
