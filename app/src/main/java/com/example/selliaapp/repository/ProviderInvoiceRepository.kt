package com.example.selliaapp.repository

import android.util.Log
import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.dao.ProviderInvoiceWithItems
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.data.model.ProviderInvoiceStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val MIN_PAYMENT_REF_LENGTH = 3
private const val MAX_PAYMENT_REF_LENGTH = 64
private const val PAYMENT_AUDIT_TAG = "ProviderInvoiceRepository"

class InvalidProviderPaymentException(message: String) : IllegalArgumentException(message)

@Singleton
class ProviderInvoiceRepository @Inject constructor(
    private val dao: ProviderInvoiceDao
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

        Log.i(
            PAYMENT_AUDIT_TAG,
            "provider_payment_marked invoiceId=${invoice.id} actor=${actor.orEmpty()} reason=${reason.orEmpty()}"
        )
    }
}
