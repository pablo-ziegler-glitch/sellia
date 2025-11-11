package com.example.selliaapp.sync

import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.remote.InvoiceFirestoreMappers
import com.example.selliaapp.data.remote.ProductFirestoreMappers
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.repository.ProductRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val productRepository: ProductRepository,
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher
) : SyncRepository {

    private val productsCollection = firestore.collection("products")
    private val invoicesCollection = firestore.collection("invoices")

    override suspend fun pushPending() = withContext(io) {
        val now = System.currentTimeMillis()
        pushPendingProducts(now)
        pushPendingInvoices(now)
    }

    override suspend fun pullRemote() = withContext(io) {
        productRepository.syncDown()

        val snapshot = invoicesCollection.get().await()
        if (snapshot.isEmpty) return@withContext

        val remoteInvoices = snapshot.documents.mapNotNull { doc ->
            InvoiceFirestoreMappers.fromDocument(doc)
        }

        if (remoteInvoices.isEmpty()) return@withContext

        db.withTransaction {
            remoteInvoices.forEach { remote ->
                val invoice = remote.invoice
                invoiceDao.insertInvoice(invoice)
                invoiceItemDao.deleteByInvoiceId(invoice.id)
                if (remote.items.isNotEmpty()) {
                    invoiceItemDao.insertAll(remote.items)
                }
            }
        }
    }

    private suspend fun pushPendingProducts(now: Long) {
        val pending = syncOutboxDao.getByType(SyncEntityType.PRODUCT.storageKey)
        if (pending.isEmpty()) return

        val ids = pending.map { it.entityId.toInt() }
        val entities = productDao.getByIds(ids)
        val foundIds = entities.map { it.id.toLong() }.toSet()
        val missing = pending.map { it.entityId }.filterNot { it in foundIds }
        if (missing.isNotEmpty()) {
            syncOutboxDao.deleteByTypeAndIds(SyncEntityType.PRODUCT.storageKey, missing)
        }
        if (entities.isEmpty()) return

        val batch = firestore.batch()
        entities.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = productsCollection.document(product.id.toString())
            batch.set(doc, ProductFirestoreMappers.toMap(product), SetOptions.merge())
        }

        try {
            batch.commit().await()
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.PRODUCT.storageKey,
                entities.map { it.id.toLong() }
            )
        } catch (t: Throwable) {
            val error = extractErrorMessage(t)
            syncOutboxDao.markAttempt(
                SyncEntityType.PRODUCT.storageKey,
                entities.map { it.id.toLong() },
                now,
                error
            )
            throw t
        }
    }

    private suspend fun pushPendingInvoices(now: Long) {
        val pending = syncOutboxDao.getByType(SyncEntityType.INVOICE.storageKey)
        if (pending.isEmpty()) return

        val ids = pending.map { it.entityId }
        val relations = invoiceDao.getInvoicesWithItemsByIds(ids)
        val foundIds = relations.map { it.invoice.id }.toSet()
        val missing = ids.filterNot { it in foundIds }
        if (missing.isNotEmpty()) {
            syncOutboxDao.deleteByTypeAndIds(SyncEntityType.INVOICE.storageKey, missing)
        }
        if (relations.isEmpty()) return

        val batch = firestore.batch()
        relations.forEach { relation ->
            val invoice = relation.invoice
            val doc = invoicesCollection.document(invoice.id.toString())
            batch.set(
                doc,
                InvoiceFirestoreMappers.toMap(
                    invoice,
                    formatInvoiceNumber(invoice.id),
                    relation.items
                ),
                SetOptions.merge()
            )
        }

        try {
            batch.commit().await()
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.INVOICE.storageKey,
                relations.map { it.invoice.id }
            )
        } catch (t: Throwable) {
            val error = extractErrorMessage(t)
            syncOutboxDao.markAttempt(
                SyncEntityType.INVOICE.storageKey,
                relations.map { it.invoice.id },
                now,
                error
            )
            throw t
        }
    }

    private fun formatInvoiceNumber(id: Long): String =
        "F-" + id.toString().padStart(8, '0')

    private fun extractErrorMessage(t: Throwable): String =
        t.message?.take(512) ?: t::class.java.simpleName
}
