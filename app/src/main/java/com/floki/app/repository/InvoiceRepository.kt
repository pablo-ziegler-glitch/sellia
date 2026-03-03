package com.floki.app.repository

import com.floki.app.data.dao.InvoiceWithItems
import com.floki.app.data.model.Invoice
import com.floki.app.data.model.InvoiceItem
import com.floki.app.data.model.OrderStatus
import com.floki.app.data.model.sales.InvoiceDetail
import com.floki.app.data.model.dashboard.DailySalesPoint
import com.floki.app.data.model.sales.InvoiceDraft
import com.floki.app.data.model.sales.InvoiceResult
import com.floki.app.data.model.sales.InvoiceSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de ventas. Ahora incluye:
 *  - confirmInvoice(draft) [escritura/negocio]
 *  - observeInvoicesWithItems() [lectura simple para pantallas existentes]
 *  - observeInvoicesByCustomerQuery(q) [lectura por cliente]
 *  - observeAll() [resúmenes]
 *  - getInvoiceDetail(id) [detalle]
 *  - addInvoiceAndAdjustStock(invoice, items) [compat con VMs viejos]
 *  - sumThisMonth() [home]
 *
 * En próximas iteraciones podemos separar definitivamente lectura
 * en SalesInvoiceReadRepository, pero agregamos estas firmas para
 * COMPILAR hoy sin romper pantallas previas.
 */
interface InvoiceRepository {
    // Negocio principal
    suspend fun confirmInvoice(draft: InvoiceDraft): InvoiceResult

    // Lecturas (compatibilidad)
    fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    fun observeInvoicesByCustomerQuery(q: String): Flow<List<InvoiceWithItems>>

    fun observeAll(): Flow<List<InvoiceSummary>>

    suspend fun getInvoiceDetail(id: Long): InvoiceDetail?

    // Escritura (compatibilidad con VM antiguos)
    suspend fun addInvoiceAndAdjustStock(invoice: Invoice, items: List<InvoiceItem>)

    // Reportes simples para el tablero
    suspend fun sumThisMonth(): Double

    suspend fun salesLastDays(dias: Int): List<DailySalesPoint>

    // Cancelación de ventas
    suspend fun cancelInvoice(id: Long, reason: String)

    /**
     * [NUEVO] Refresco puntual del estado de una orden en Checkout Pro.
     * IMPORTANTE: debe ser un get() único para minimizar costos en Firestore.
     */
    suspend fun refreshOrderStatus(orderId: String): OrderStatus?
}
