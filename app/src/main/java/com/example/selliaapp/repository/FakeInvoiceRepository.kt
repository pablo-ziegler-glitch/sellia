package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.example.selliaapp.data.model.OrderStatus
import com.example.selliaapp.data.model.dashboard.DailySalesPoint
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceDraft
import com.example.selliaapp.data.model.sales.InvoiceResult
import com.example.selliaapp.data.model.sales.InvoiceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake simple para tests unitarios.
 * Implementa lo mínimo para que compile y para tests de escaneo/carrito.
 */
class FakeInvoiceRepository : InvoiceRepository {

    override suspend fun confirmInvoice(draft: InvoiceDraft): InvoiceResult {
        error("FakeInvoiceRepository.confirmInvoice no implementado para este test")
    }


    override fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>> = flowOf(emptyList())

    override fun observeInvoicesByCustomerQuery(q: String): Flow<List<InvoiceWithItems>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<InvoiceSummary>> = flowOf(emptyList())

    override suspend fun getInvoiceDetail(id: Long): InvoiceDetail? = null

    override suspend fun addInvoiceAndAdjustStock(invoice: Invoice, items: List<InvoiceItem>) = Unit

    override suspend fun cancelInvoice(id: Long, reason: String) {
        error("FakeInvoiceRepository.cancelInvoice no implementado para este test")
    }

    override suspend fun sumThisMonth(): Double = 0.0

    override suspend fun salesLastDays(dias: Int): List<DailySalesPoint> = emptyList()

    override suspend fun refreshOrderStatus(orderId: String): OrderStatus? = null
}
