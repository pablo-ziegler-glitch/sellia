package com.example.selliaapp.repository.impl

import androidx.room.withTransaction // <-- IMPORTANTE: withTransaction suspend de Room KTX
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.StockMovementEntity
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.example.selliaapp.data.model.InvoiceStatus
import com.example.selliaapp.data.model.OrderStatus
import com.example.selliaapp.data.model.dashboard.DailySalesPoint
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceDraft
import com.example.selliaapp.data.model.sales.InvoiceItemRow
import com.example.selliaapp.data.model.sales.InvoiceResult
import com.example.selliaapp.data.model.sales.InvoiceSummary
import com.example.selliaapp.data.model.sales.SyncStatus
import com.example.selliaapp.data.remote.InvoiceFirestoreMappers
import com.example.selliaapp.data.remote.ProductFirestoreMappers
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.InvoiceRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
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
    private val productImageDao: ProductImageDao,
    private val customerDao: CustomerDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) : InvoiceRepository {

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

    override suspend fun cancelInvoice(id: Long, reason: String) = withContext(io) {
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank()) { "Motivo de anulación requerido" }
        val now = System.currentTimeMillis()
        val touchedProducts = mutableSetOf<Int>()
        var updatedRelation: InvoiceWithItems? = null
        var didUpdate = false

        db.withTransaction {
            val relation = invoiceDao.getInvoiceWithItemsById(id) ?: error("Factura no encontrada (id=$id)")
            if (relation.invoice.status != InvoiceStatus.EMITIDA) {
                updatedRelation = relation
                return@withTransaction
            }

            val updatedRows = invoiceDao.updateStatus(
                id = id,
                status = InvoiceStatus.ANULADA,
                canceledAt = now,
                canceledReason = cleanReason
            )
            require(updatedRows == 1) { "No se pudo actualizar la factura (id=$id)" }

            val movementDao = db.stockMovementDao()
            relation.items.forEach { item ->
                val affected = productDao.increaseStockIfExists(item.productId, item.quantity)
                require(affected == 1) { "Producto inexistente (id=${item.productId})" }
                movementDao.insert(
                    StockMovementEntity(
                        productId = item.productId,
                        delta = item.quantity,
                        reason = StockMovementReasons.SALE_CANCEL,
                        ts = Instant.ofEpochMilli(now),
                        user = null
                    )
                )
                touchedProducts += item.productId
            }

            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    entityType = SyncEntityType.INVOICE.storageKey,
                    entityId = id,
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

            updatedRelation = relation.copy(
                invoice = relation.invoice.copy(
                    status = InvoiceStatus.ANULADA,
                    canceledAt = now,
                    canceledReason = cleanReason
                )
            )
            didUpdate = true
        }

        if (!didUpdate) return@withContext
        val relation = updatedRelation ?: return@withContext

        val productsToSync: List<ProductEntity> = if (touchedProducts.isEmpty()) {
            emptyList()
        } else {
            productDao.getByIds(touchedProducts.toList())
        }
        val productIdsForOutbox = touchedProducts.map(Int::toLong)
        try {
            syncInvoiceWithFirestore(
                relation.invoice,
                formatNumber(relation.invoice.id),
                relation.items,
                productsToSync
            )
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.INVOICE.storageKey,
                listOf(relation.invoice.id)
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
                listOf(relation.invoice.id),
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

    override suspend fun refreshOrderStatus(orderId: String): OrderStatus? = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val snapshot = firestore.collection("tenants")
            .document(tenantId)
            .collection("orders")
            .document(orderId)
            .get()
            .await()

        if (!snapshot.exists()) return@withContext null

        OrderStatus(
            orderId = snapshot.id,
            status = snapshot.getString("status").orEmpty(),
            paymentStatus = snapshot.getString("paymentStatus"),
            statusDetail = snapshot.getString("statusDetail"),
            updatedAtMillis = snapshot.getLong("updatedAtMillis")
                ?: snapshot.getTimestamp("updatedAt")?.toDate()?.time
        )
    }

     // ----------------------------
     // Lecturas
     // ----------------------------
     override fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>> =
         invoiceDao.observeInvoicesWithItems()

     override suspend fun getInvoiceDetail(id: Long): InvoiceDetail? = withContext(io) {
         invoiceDao.getInvoiceWithItemsById(id)?.let { relation ->
             val outbox = syncOutboxDao.getByTypeAndId(SyncEntityType.INVOICE.storageKey, id)
             mapToDetail(relation, outbox)
         }
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
     private fun mapToSummary(rel: InvoiceWithItems, outbox: SyncOutboxEntity?): InvoiceSummary {
         val inv = rel.invoice
         val ld = Instant.ofEpochMilli(inv.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
         return InvoiceSummary(
             id = inv.id,
             number = formatNumber(inv.id),
             customerName = inv.customerName ?: "Consumidor Final",
             date = ld,
             total = inv.total,
             syncStatus = syncStatusFor(outbox)
         )
     }

    private fun mapToDetail(rel: InvoiceWithItems, outbox: SyncOutboxEntity?): InvoiceDetail {
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
            subtotal = inv.subtotal,
            taxes = inv.taxes,
            discountPercent = inv.discountPercent,
            discountAmount = inv.discountAmount,
            surchargePercent = inv.surchargePercent,
            surchargeAmount = inv.surchargeAmount,
            total = inv.total,
            paymentMethod = inv.paymentMethod,
            paymentNotes = inv.paymentNotes,
            status = inv.status,
            canceledReason = inv.canceledReason,
            items = itemsUi,
            notes = inv.paymentNotes,
            syncStatus = syncStatusFor(outbox)
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
        val tenantId = tenantProvider.requireTenantId()
        val invoicesCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("invoices")
        invoicesCollection
            .document(invoice.id.toString())
            .set(InvoiceFirestoreMappers.toMap(invoice, number, items, tenantId))
            .await()

        if (products.isEmpty()) return

        val imageUrlsByProductId = productImageDao.getByProductIds(products.map { it.id })
            .groupBy { it.productId }
            .mapValues { (_, items) -> items.sortedBy { it.position }.map { it.url } }
        val productsCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("products")
        val batch = firestore.batch()
        products.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = productsCollection.document(product.id.toString())
            val imageUrls = imageUrlsByProductId[product.id].orEmpty()
            batch.set(
                doc,
                ProductFirestoreMappers.toMap(product, imageUrls, tenantId),
                SetOptions.merge()
            )
        }
        batch.commit().await()
    }


     override fun observeAll(): Flow<List<InvoiceSummary>> =
         invoiceDao.observeInvoicesWithItems()
             .map { list ->
                 val outboxById = syncOutboxDao.getByType(SyncEntityType.INVOICE.storageKey)
                     .associateBy { it.entityId }
                 list.map { mapToSummary(it, outboxById[it.invoice.id]) }
             }
             .flowOn(io)

     // Búsqueda por cliente
     override fun observeInvoicesByCustomerQuery(q: String) =
         invoiceDao.observeInvoicesWithItemsByCustomerQuery(q)

    private fun syncStatusFor(outbox: SyncOutboxEntity?): SyncStatus =
        when {
            outbox == null -> SyncStatus.SYNCED
            outbox.lastError != null -> SyncStatus.ERROR
            else -> SyncStatus.PENDING
        }




 }
