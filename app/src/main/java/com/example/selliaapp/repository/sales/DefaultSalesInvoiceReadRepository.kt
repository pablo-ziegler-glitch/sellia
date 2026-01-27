package com.example.selliaapp.repository.sales

import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceItemRow
import com.example.selliaapp.data.model.sales.InvoiceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val invoiceDao: InvoiceDao
) : SalesInvoiceReadRepository {

    override fun observeSummaries(): Flow<List<InvoiceSummary>> =
        invoiceDao.observeInvoicesWithItems() // Flow<List<InvoiceWithItems>>
            .map { list -> list.map { it.toSummary() } }

    override suspend fun getDetail(id: Long): InvoiceDetail? =
        invoiceDao.getInvoiceWithItemsById(id)?.toDetail()

    // ---- Mappers ----

    private fun InvoiceWithItems.toSummary(): InvoiceSummary {
        val number = formatNumber(invoice.id) // si no tenés número, generamos uno legible
        return InvoiceSummary(
            id = invoice.id,
            number = number,
            customerName = invoice.customerName ?: "Consumidor Final",
            date = millisToLocalDate(invoice.dateMillis),
            total = invoice.total
        )
    }

    private fun InvoiceWithItems.toDetail(): InvoiceDetail {
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
            items = itemsUi,
            notes = invoice.paymentNotes,
            status = invoice.status,
            canceledAt = invoice.canceledAt,
            canceledReason = invoice.canceledReason
        )
    }

    private fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun formatNumber(id: Long): String =
        "F-${id.toString().padStart(8, '0')}" // cambia si luego guardás talonario/pto de venta
}
