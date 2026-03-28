package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.ProviderInvoiceWithItems
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.data.model.ProviderInvoiceReceptionStatus
import com.example.selliaapp.data.model.ProviderInvoiceStatus
import com.example.selliaapp.data.model.stock.StockMovementReasons
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val MIN_PAYMENT_REF_LENGTH = 3
private const val MAX_PAYMENT_REF_LENGTH = 64
private const val PAYMENT_AUDIT_TAG = "ProviderInvoiceRepository"

class InvalidProviderPaymentException(message: String) : IllegalArgumentException(message)

@Singleton
class ProviderInvoiceRepository @Inject constructor(
    private val dao: ProviderInvoiceDao,
    private val productRepository: IProductRepository
) {
    fun observeByProvider(providerId: Int): Flow<List<ProviderInvoiceWithItems>> =
        dao.observeByProvider(providerId)

    fun observePending(): Flow<List<ProviderInvoiceWithItems>> =
        dao.observeByStatus(ProviderInvoiceStatus.IMPAGA)

    fun observeDetail(invoiceId: Int): Flow<ProviderInvoiceWithItems?> =
        dao.observeDetail(invoiceId)

    suspend fun create(
        invoice: ProviderInvoice,
        items: List<ProviderInvoiceItem>
    ): Long {
        val id = dao.insertInvoice(invoice)
        dao.insertItems(items.map { it.copy(invoiceId = id.toInt()) })
        return id
    }

    suspend fun markPaid(
        invoice: ProviderInvoice,
        ref: String,
        amount: Double,
        paymentDateMillis: Long,
        actor: String? = null,
        reason: String? = null
    ) {
        if (amount <= 0.0) {
            throw InvalidProviderPaymentException("El monto pagado debe ser mayor a 0.")
        }

        val normalizedRef = ref.trim().replace("\\s+".toRegex(), " ")
        if (normalizedRef.length !in MIN_PAYMENT_REF_LENGTH..MAX_PAYMENT_REF_LENGTH) {
            throw InvalidProviderPaymentException(
                "La referencia de pago debe tener entre $MIN_PAYMENT_REF_LENGTH y $MAX_PAYMENT_REF_LENGTH caracteres."
            )
        }

        val updated = invoice.copy(
            status = ProviderInvoiceStatus.PAGA,
            paymentRef = normalizedRef,
            paymentAmount = amount,
            paymentDateMillis = paymentDateMillis
        )
        dao.updateInvoice(updated)

        // Evitamos dependencia dura de android.util.Log en unit tests JVM.
        println(
            "$PAYMENT_AUDIT_TAG provider_payment_marked " +
                "invoiceId=${invoice.id} actor=${actor.orEmpty()} reason=${reason.orEmpty()}"
        )
    }

    suspend fun confirmReception(
        invoiceId: Int,
        receivedByItemId: Map<Int, Double>,
        discrepancyNote: String? = null,
        actor: String? = null
    ) {
        val detail = requireNotNull(dao.getDetailOnce(invoiceId)) { "Orden no encontrada." }
        val now = System.currentTimeMillis()

        val updatedItems = detail.items.map { item ->
            val resolved = receivedByItemId[item.id]
                ?.coerceAtLeast(0.0)
                ?: item.receivedQuantity
                ?: 0.0
            item.copy(receivedQuantity = resolved)
        }

        updatedItems.forEach { item ->
            val rounded = item.receivedQuantity?.toInt() ?: 0
            if (rounded <= 0) return@forEach
            val code = item.code?.trim().orEmpty()
            if (code.isNotBlank()) {
                val byBarcode = productRepository.getByBarcodeOrNull(code)
                if (byBarcode != null) {
                    productRepository.adjustStock(
                        productId = byBarcode.id,
                        delta = rounded,
                        reason = StockMovementReasons.MANUAL_RECEIVE,
                        note = "Recepción OC ${detail.invoice.number}"
                    )
                    return@forEach
                }
                val byCode = productRepository.getByCodeOrNull(code)
                if (byCode != null) {
                    productRepository.adjustStock(
                        productId = byCode.id,
                        delta = rounded,
                        reason = StockMovementReasons.MANUAL_RECEIVE,
                        note = "Recepción OC ${detail.invoice.number}"
                    )
                }
            }
        }

        val orderedTotal = updatedItems.sumOf { it.quantity }
        val receivedTotal = updatedItems.sumOf { it.receivedQuantity ?: 0.0 }
        val receptionStatus = when {
            receivedTotal <= 0.0 -> ProviderInvoiceReceptionStatus.PENDING
            receivedTotal + 0.0001 < orderedTotal -> ProviderInvoiceReceptionStatus.PARTIAL
            else -> ProviderInvoiceReceptionStatus.RECEIVED
        }
        val autoNote = updatedItems
            .filter { kotlin.math.abs((it.receivedQuantity ?: 0.0) - it.quantity) > 0.0001 }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { item ->
                "• ${item.name}: pedido=${item.quantity}, recibido=${item.receivedQuantity ?: 0.0}"
            }

        val mergedDiscrepancy = listOfNotNull(discrepancyNote?.trim()?.takeIf { it.isNotBlank() }, autoNote)
            .joinToString(separator = "\n")
            .ifBlank { null }

        dao.updateItems(updatedItems)
        dao.updateInvoice(
            detail.invoice.copy(
                receptionStatus = receptionStatus,
                receivedAtMillis = now,
                discrepancyNote = mergedDiscrepancy
            )
        )

        println(
            "$PAYMENT_AUDIT_TAG provider_reception_confirmed invoiceId=$invoiceId " +
                "status=$receptionStatus actor=${actor.orEmpty()}"
        )
    }
}
