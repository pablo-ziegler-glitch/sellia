package com.example.selliaapp.data.remote

import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mappers para representar facturas en Firestore.
 */
object InvoiceFirestoreMappers {
    fun toMap(invoice: Invoice, number: String, items: List<InvoiceItem>): Map<String, Any?> = mapOf(
        "id" to invoice.id,
        "number" to number,
        "dateMillis" to invoice.dateMillis,
        "customerId" to invoice.customerId,
        "customerName" to invoice.customerName,
        "subtotal" to invoice.subtotal,
        "taxes" to invoice.taxes,
        "discountPercent" to invoice.discountPercent,
        "discountAmount" to invoice.discountAmount,
        "surchargePercent" to invoice.surchargePercent,
        "surchargeAmount" to invoice.surchargeAmount,
        "total" to invoice.total,
        "paymentMethod" to invoice.paymentMethod,
        "paymentNotes" to invoice.paymentNotes,
        "items" to items.map { item ->
            mapOf(
                "id" to item.id,
                "productId" to item.productId,
                "productName" to item.productName,
                "quantity" to item.quantity,
                "unitPrice" to item.unitPrice,
                "lineTotal" to item.lineTotal
            )
        }
    )

    data class RemoteInvoice(val invoice: Invoice, val items: List<InvoiceItem>)

    fun fromDocument(doc: DocumentSnapshot): RemoteInvoice? {
        val data = doc.data ?: return null
        val invoiceId = doc.id.toLongOrNull()
            ?: (data["id"] as? Number)?.toLong()
            ?: return null

        val dateMillis = (data["dateMillis"] as? Number)?.toLong() ?: return null

        val invoice = Invoice(
            id = invoiceId,
            dateMillis = dateMillis,
            customerId = (data["customerId"] as? Number)?.toInt(),
            customerName = data["customerName"] as? String,
            subtotal = (data["subtotal"] as? Number)?.toDouble() ?: 0.0,
            taxes = (data["taxes"] as? Number)?.toDouble() ?: 0.0,
            discountPercent = (data["discountPercent"] as? Number)?.toInt() ?: 0,
            discountAmount = (data["discountAmount"] as? Number)?.toDouble() ?: 0.0,
            surchargePercent = (data["surchargePercent"] as? Number)?.toInt() ?: 0,
            surchargeAmount = (data["surchargeAmount"] as? Number)?.toDouble() ?: 0.0,
            total = (data["total"] as? Number)?.toDouble() ?: 0.0,
            paymentMethod = (data["paymentMethod"] as? String).orEmpty(),
            paymentNotes = data["paymentNotes"] as? String
        )

        val itemsRaw = data["items"] as? List<*>
        val items = itemsRaw
            ?.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val productId = (map["productId"] as? Number)?.toInt() ?: return@mapNotNull null
                val quantity = (map["quantity"] as? Number)?.toInt() ?: return@mapNotNull null
                val unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0
                InvoiceItem(
                    id = (map["id"] as? Number)?.toLong() ?: 0L,
                    invoiceId = invoiceId,
                    productId = productId,
                    productName = (map["productName"] as? String).orEmpty(),
                    quantity = quantity,
                    unitPrice = unitPrice,
                    lineTotal = (map["lineTotal"] as? Number)?.toDouble() ?: unitPrice * quantity
                )
            }
            ?: emptyList()

        return RemoteInvoice(invoice, items)
    }
}
