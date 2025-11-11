package com.example.selliaapp.repository.impl

import androidx.room.withTransaction // <-- IMPORTANTE: withTransaction suspend de Room KTX
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.example.selliaapp.data.model.dashboard.DailySalesPoint
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceDraft
import com.example.selliaapp.data.model.sales.InvoiceItemRow
import com.example.selliaapp.data.model.sales.InvoiceResult
import com.example.selliaapp.data.model.sales.InvoiceSummary
import com.example.selliaapp.data.remote.InvoiceFirestoreMappers
import com.example.selliaapp.data.remote.ProductFirestoreMappers
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.InvoiceRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val invoiceDao: InvoiceDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher
) : InvoiceRepository {

    private val invoicesCollection = firestore.collection("invoices")
    private val productsCollection = firestore.collection("products")
    private val syncOutboxDao = db.syncOutboxDao()

     // ----------------------------
     // Escritura principal
     // ----------------------------
    override suspend fun confirmInvoice(draft: InvoiceDraft): InvoiceResult = withContext(io) {
        val now = System.currentTimeMillis()
        val resolvedCustomerName = draft.customerName
            ?: draft.customerId?.toInt()?.let { customerDao.getNameById(it) }

        var persistedInvoice: Invoice? = null
        var persistedItems: List<InvoiceItem> = emptyList()
        val touchedProducts = mutableSetOf<Int>()

        db.withTransaction {
            val baseInvoice = Invoice(
                id = 0L,
                dateMillis = now,
                customerId = draft.customerId?.toInt(),
                customerName = resolvedCustomerName,
                subtotal = draft.subtotal,
                taxes = draft.taxes,
                discountPercent = draft.discountPercent,
                discountAmount = draft.discountAmount,
                surchargePercent = draft.surchargePercent,
                surchargeAmount = draft.surchargeAmount,
                total = draft.total,
                paymentMethod = draft.paymentMethod.ifBlank { "EFECTIVO" },
                paymentNotes = draft.paymentNotes
            )
            val invId = invoiceDao.insertInvoice(baseInvoice)

            persistedItems = draft.items.map { li ->
                InvoiceItem(
                    id = 0L,
                    invoiceId = invId,
                    productId = li.productId.toInt(),
                    productName = li.name,
                    quantity = li.quantity,
                    unitPrice = li.unitPrice,
                    lineTotal = li.quantity * li.unitPrice
                )
            }
            invoiceDao.insertItems(persistedItems)

            val movementDao = db.stockMovementDao()
            for (item in persistedItems) {
                val affected = productDao.decrementStockIfEnough(
                    productId = item.productId,
                    qty = item.quantity
                )
                require(affected == 1) { "Stock insuficiente o producto inexistente (id=${item.productId})" }

                    movementDao.insert(
                        StockMovementEntity(
                            productId = item.productId,
                            delta = -item.quantity,
                            reason = StockMovementReasons.SALE,
                            ts = Instant.ofEpochMilli(now),
                            user = null
                        )
                    )
                touchedProducts += item.productId
            }

            persistedInvoice = baseInvoice.copy(id = invId)
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    entityType = SyncEntityType.INVOICE.storageKey,
                    entityId = invId,
                    createdAt = now
                )
            )
            if (touchedProducts.isNotEmpty()) {
                val entries = touchedProducts.map { productId ->
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = productId.toLong(),
                        createdAt = now
                    )
                }
                syncOutboxDao.upsertAll(entries)
            }
        }

        val invoice = requireNotNull(persistedInvoice) { "No se pudo persistir la venta" }
        val invoiceNumber = formatNumber(invoice.id)
        val productsToSync: List<ProductEntity> = if (touchedProducts.isEmpty()) {
            emptyList()
        } else {
            productDao.getByIds(touchedProducts.toList())
        }

        val productIdsForOutbox = touchedProducts.map(Int::toLong)
        try {
            syncInvoiceWithFirestore(invoice, invoiceNumber, persistedItems, productsToSync)
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.INVOICE.storageKey,
                listOf(invoice.id)
            )
            if (productIdsForOutbox.isNotEmpty()) {
                syncOutboxDao.deleteByTypeAndIds(
                    SyncEntityType.PRODUCT.storageKey,
                    productIdsForOutbox
                )
            }
        } catch (t: Throwable) {
            val errorMsg = extractErrorMessage(t)
            val timestamp = System.currentTimeMillis()
            syncOutboxDao.markAttempt(
                SyncEntityType.INVOICE.storageKey,
                listOf(invoice.id),
                timestamp,
                errorMsg
            )
            if (productIdsForOutbox.isNotEmpty()) {
                syncOutboxDao.markAttempt(
                    SyncEntityType.PRODUCT.storageKey,
                    productIdsForOutbox,
                    timestamp,
                    errorMsg
                )
            }
            throw t
        }

        InvoiceResult(invoiceId = invoice.id, invoiceNumber = invoiceNumber)
    }

     // Compat con VMs viejos: versión plana
     override suspend fun addInvoiceAndAdjustStock(invoice: Invoice, items: List<InvoiceItem>) = withContext(io) {
        val now = if (invoice.dateMillis != 0L) invoice.dateMillis else System.currentTimeMillis()
        var persistedInvoice: Invoice? = null
        var itemsWithFk: List<InvoiceItem> = emptyList()
        val touchedProducts = mutableSetOf<Int>()

        db.withTransaction {
            val invId = invoiceDao.insertInvoice(invoice.copy(id = 0L))
            itemsWithFk = items.map { it.copy(id = 0L, invoiceId = invId) }
            invoiceDao.insertItems(itemsWithFk)

            val movementDao = db.stockMovementDao()
            for (item in itemsWithFk) {
                val affected = productDao.decrementStockIfEnough(item.productId, item.quantity)
                require(affected == 1) { "Stock insuficiente o producto inexistente (id=${item.productId})" }
                    movementDao.insert(
                        StockMovementEntity(
                            productId = item.productId,
                            delta = -item.quantity,
                            reason = StockMovementReasons.SALE,
                            ts = Instant.ofEpochMilli(now),
                            user = null
                        )
                    )
                touchedProducts += item.productId
            }

            persistedInvoice = invoice.copy(id = invId)
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    entityType = SyncEntityType.INVOICE.storageKey,
                    entityId = invId,
                    createdAt = now
                )
            )
            if (touchedProducts.isNotEmpty()) {
                val entries = touchedProducts.map { productId ->
                    SyncOutboxEntity(
                        entityType = SyncEntityType.PRODUCT.storageKey,
                        entityId = productId.toLong(),
                        createdAt = now
                    )
                }
                syncOutboxDao.upsertAll(entries)
            }
        }

        val savedInvoice = requireNotNull(persistedInvoice)
        val invoiceNumber = formatNumber(savedInvoice.id)
        val productsToSync: List<ProductEntity> = if (touchedProducts.isEmpty()) {
            emptyList()
        } else {
            productDao.getByIds(touchedProducts.toList())
        }

        val productIdsForOutbox = touchedProducts.map(Int::toLong)
        try {
            syncInvoiceWithFirestore(savedInvoice, invoiceNumber, itemsWithFk, productsToSync)
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.INVOICE.storageKey,
                listOf(savedInvoice.id)
            )
            if (productIdsForOutbox.isNotEmpty()) {
                syncOutboxDao.deleteByTypeAndIds(
                    SyncEntityType.PRODUCT.storageKey,
                    productIdsForOutbox
                )
            }
        } catch (t: Throwable) {
            val errorMsg = extractErrorMessage(t)
            val timestamp = System.currentTimeMillis()
            syncOutboxDao.markAttempt(
                SyncEntityType.INVOICE.storageKey,
                listOf(savedInvoice.id),
                timestamp,
                errorMsg
            )
            if (productIdsForOutbox.isNotEmpty()) {
                syncOutboxDao.markAttempt(
                    SyncEntityType.PRODUCT.storageKey,
                    productIdsForOutbox,
                    timestamp,
                    errorMsg
                )
            }
            throw t
        }
    }

     // ----------------------------
     // Lecturas
     // ----------------------------
     override fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>> =
         invoiceDao.observeInvoicesWithItems()

     override suspend fun getInvoiceDetail(id: Long): InvoiceDetail? = withContext(io) {
         invoiceDao.getInvoiceWithItemsById(id)?.let { mapToDetail(it) }
     }

     // ----------------------------
     // Reporte simple
     // ----------------------------
    override suspend fun sumThisMonth(): Double = withContext(io) {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val end = today.plusMonths(1)
            .withDayOfMonth(1)
            .minusDays(1)
            .atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        invoiceDao.sumTotalBetween(start, end)
    }

    override suspend fun salesLastDays(dias: Int): List<DailySalesPoint> = withContext(io) {
        require(dias > 0) { "El número de días debe ser positivo" }

        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now(zona)
        val inicioSerie = hoy.minusDays((dias - 1).toLong())

        val inicioMillis = inicioSerie
            .atStartOfDay(zona)
            .toInstant()
            .toEpochMilli()

        val finMillis = hoy
            .plusDays(1)
            .atStartOfDay(zona)
            .toInstant()
            .toEpochMilli() - 1

        val registros = invoiceDao.salesGroupedByDay(inicioMillis, finMillis)
        val totalesPorDia = registros.associateBy(
            keySelector = { row ->
                Instant.ofEpochMilli(row.day).atZone(zona).toLocalDate()
            }
        )

        (0 until dias).map { offset ->
            val fecha = inicioSerie.plusDays(offset.toLong())
            val total = totalesPorDia[fecha]?.total ?: 0.0
            DailySalesPoint(fecha = fecha, total = total)
        }
    }

     // ----------------------------
     // Mappers
     // ----------------------------
     private fun mapToSummary(rel: InvoiceWithItems): InvoiceSummary {
         val inv = rel.invoice
         val ld = Instant.ofEpochMilli(inv.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
         return InvoiceSummary(
             id = inv.id,
             number = formatNumber(inv.id),
             customerName = inv.customerName ?: "Consumidor Final",
             date = ld,
             total = inv.total
         )
     }

    private fun mapToDetail(rel: InvoiceWithItems): InvoiceDetail {
        val inv = rel.invoice
        val itemsUi = rel.items.map {
            InvoiceItemRow(
                productId = (it.productId ?: 0).toLong(),    // ajustá si tu entity usa Int?
                name = it.productName ?: "(s/n)",
                quantity = it.quantity,
                unitPrice = it.unitPrice
            )
        }
        val ld = Instant.ofEpochMilli(inv.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return InvoiceDetail(
            id = inv.id,
            number = formatNumber(inv.id),
            customerName = inv.customerName ?: "Consumidor Final",
            date = ld,
            total = inv.total,
            items = itemsUi,
            notes = inv.paymentNotes
        )
    }

    private fun formatNumber(id: Long): String =
        "F-" + id.toString().padStart(8, '0')

    private fun extractErrorMessage(t: Throwable): String =
        t.message?.take(512) ?: t::class.java.simpleName

    private suspend fun syncInvoiceWithFirestore(
        invoice: Invoice,
        number: String,
        items: List<InvoiceItem>,
        products: List<ProductEntity>
    ) {
        invoicesCollection
            .document(invoice.id.toString())
            .set(InvoiceFirestoreMappers.toMap(invoice, number, items))
            .await()

        if (products.isEmpty()) return

        val batch = firestore.batch()
        products.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = productsCollection.document(product.id.toString())
            batch.set(doc, ProductFirestoreMappers.toMap(product), SetOptions.merge())
        }
        batch.commit().await()
    }


     override fun observeAll(): Flow<List<InvoiceSummary>> =
         invoiceDao.observeInvoicesWithItems().map { list -> list.map { mapToSummary(it) } }

     // Búsqueda por cliente
     override fun observeInvoicesByCustomerQuery(query: String) =
         invoiceDao.observeInvoicesWithItemsByCustomerQuery(query)




 }
