package com.example.selliaapp.repository.sales

import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceItemRow
import com.example.selliaapp.data.model.sales.InvoiceSummary
import com.example.selliaapp.data.model.sales.SyncStatus
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación REAL que usa InvoiceDao.
 * No modifica tu stock ni tu clase InvoiceRepository.
 */
@Singleton
class DefaultSalesInvoiceReadRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val syncOutboxDao: SyncOutboxDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : SalesInvoiceReadRepository {

    override fun observeSummaries(): Flow<List<InvoiceSummary>> =
        invoiceDao.observeInvoicesWithItems() // Flow<List<InvoiceWithItems>>
            .map { list ->
                val outboxById = syncOutboxDao.getByType(SyncEntityType.INVOICE.storageKey)
                    .associateBy { it.entityId }
                list.map { it.toSummary(outboxById[it.invoice.id]) }
            }
            .flowOn(io)

    override suspend fun getDetail(id: Long): InvoiceDetail? =
        invoiceDao.getInvoiceWithItemsById(id)?.let { relation ->
            val outbox = syncOutboxDao.getByTypeAndId(SyncEntityType.INVOICE.storageKey, id)
            relation.toDetail(outbox)
        }

    // ---- Mappers ----

    private fun InvoiceWithItems.toSummary(outbox: SyncOutboxEntity?): InvoiceSummary {
        val number = formatNumber(invoice.id) // si no tenés número, generamos uno legible
        return InvoiceSummary(
            id = invoice.id,
            number = number,
            customerName = invoice.customerName ?: "Consumidor Final",
            date = millisToLocalDate(invoice.dateMillis),
            total = invoice.total,
            syncStatus = syncStatusFor(outbox)
        )
    }

    private fun InvoiceWithItems.toDetail(outbox: SyncOutboxEntity?): InvoiceDetail {
        val number = formatNumber(invoice.id)
        val itemsUi = items.map { entityItem ->
            InvoiceItemRow(
                productId = (entityItem.productId ?: 0).toLong(), // si en Room es Int?, convertimos
                name = entityItem.productName ?: "(s/n)",
                quantity = entityItem.quantity,
                unitPrice = entityItem.unitPrice
            )
        }
        return InvoiceDetail(
            id = invoice.id,
            number = number,
            customerName = invoice.customerName ?: "Consumidor Final",
            date = millisToLocalDate(invoice.dateMillis),
            subtotal = invoice.subtotal,
            taxes = invoice.taxes,
            discountPercent = invoice.discountPercent,
            discountAmount = invoice.discountAmount,
            surchargePercent = invoice.surchargePercent,
            surchargeAmount = invoice.surchargeAmount,
            total = invoice.total,
            paymentMethod = invoice.paymentMethod,
            paymentNotes = invoice.paymentNotes,
            status = invoice.status,
            canceledReason = invoice.canceledReason,
            items = itemsUi,
            notes = invoice.paymentNotes,
            syncStatus = syncStatusFor(outbox)
        )
    }

    private fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun formatNumber(id: Long): String =
        "F-${id.toString().padStart(8, '0')}" // cambia si luego guardás talonario/pto de venta

    private fun syncStatusFor(outbox: SyncOutboxEntity?): SyncStatus =
        when {
            outbox == null -> SyncStatus.SYNCED
            outbox.lastError != null -> SyncStatus.ERROR
            else -> SyncStatus.PENDING
        }
}
