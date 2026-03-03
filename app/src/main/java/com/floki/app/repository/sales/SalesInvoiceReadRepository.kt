package com.floki.app.repository.sales

import com.floki.app.data.model.sales.InvoiceDetail
import com.floki.app.data.model.sales.InvoiceSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de LECTURA para facturas de venta (no toca stock ni procesa ventas).
 * Mantiene separado tu InvoiceRepository (clase) actual.
 */
interface SalesInvoiceReadRepository {
    fun observeSummaries(): Flow<List<InvoiceSummary>>
    suspend fun getDetail(id: Long): InvoiceDetail?
}
