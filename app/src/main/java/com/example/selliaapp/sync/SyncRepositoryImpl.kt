package com.example.selliaapp.sync

import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.InvoiceFirestoreMappers
import com.example.selliaapp.data.remote.CustomerFirestoreMappers
import com.example.selliaapp.data.remote.ProductFirestoreMappers
import com.example.selliaapp.di.AppModule.IoDispatcher // [NUEVO] El qualifier real del ZIP está dentro de AppModule
import com.example.selliaapp.repository.ProductRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val productImageDao: ProductImageDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val customerDao: CustomerDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val productRepository: ProductRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val tenantProvider: TenantProvider,
    /* [ANTERIOR]
    import com.example.selliaapp.di.IoDispatcher
    @IoDispatcher private val io: CoroutineDispatcher
    */
    @IoDispatcher private val io: CoroutineDispatcher
) : SyncRepository {

    override suspend fun pushPending() = withContext(io) {
        if (!hasPrivilegedSyncAccess()) {
            Log.i(TAG, "Sincronización omitida: rol sin permisos.")
            return@withContext
        }
        val now = System.currentTimeMillis()
        pushPendingProducts(now)
        pushPendingInvoices(now)
        pushPendingCustomers(now)
    }

    override suspend fun pullRemote() = withContext(io) {
        if (!hasReadSyncAccess()) {
            Log.i(TAG, "Sincronización omitida: rol sin permisos.")
            return@withContext
        }
        productRepository.syncDown()

        val invoicesCollection = firestore.collection("tenants")
            .document(tenantProvider.requireTenantId())
            .collection("invoices")
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

        syncCustomersFromRemote()
    }

    override suspend fun runSync(includeBackup: Boolean) = withContext(io) {
        pushPending()
        pullRemote()
        if (includeBackup) {
            pushAllLocalTables()
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

        val imageUrlsByProductId = productImageDao.getByProductIds(ids)
            .groupBy { it.productId }
            .mapValues { (_, items) -> items.sortedBy { it.position }.map { it.url } }
        val tenantId = tenantProvider.requireTenantId()
        val productsCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("products")
        val batch = firestore.batch()
        entities.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = productsCollection.document(product.id.toString())
            val imageUrls = imageUrlsByProductId[product.id].orEmpty()
            batch.set(
                doc,
                ProductFirestoreMappers.toMap(product, imageUrls, tenantId),
                SetOptions.merge()
            )
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

        val tenantId = tenantProvider.requireTenantId()
        val invoicesCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("invoices")
        val batch = firestore.batch()
        relations.forEach { relation ->
            val invoice = relation.invoice
            val doc = invoicesCollection.document(invoice.id.toString())

            // [NUEVO] toMap requiere (invoice, number:String, items:List<InvoiceItem>, tenantId:String)
            batch.set(
                doc,
                InvoiceFirestoreMappers.toMap(
                    invoice = invoice,
                    number = formatInvoiceNumber(invoice.id),
                    items = relation.items,
                    tenantId = tenantId
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

    private suspend fun pushPendingCustomers(now: Long) {
        val pending = syncOutboxDao.getByType(SyncEntityType.CUSTOMER.storageKey)
        if (pending.isEmpty()) return

        val ids = pending.map { it.entityId.toInt() }
        val tenantId = tenantProvider.requireTenantId()
        val customersCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("customers")

        val existingCustomers = ids.mapNotNull { id -> customerDao.getById(id) }
        val foundIds = existingCustomers.map { it.id.toLong() }.toSet()
        val deletedIds = pending.map { it.entityId }.filterNot { it in foundIds }

        val batch = firestore.batch()
        existingCustomers.forEach { customer ->
            val docRef = customersCollection.document(customer.id.toString())
            batch.set(docRef, CustomerFirestoreMappers.toMap(customer, tenantId), SetOptions.merge())
        }
        deletedIds.forEach { customerId ->
            val docRef = customersCollection.document(customerId.toString())
            batch.delete(docRef)
        }

        if (existingCustomers.isEmpty() && deletedIds.isEmpty()) return

        try {
            batch.commit().await()
            syncOutboxDao.deleteByTypeAndIds(
                SyncEntityType.CUSTOMER.storageKey,
                pending.map { it.entityId }
            )
        } catch (t: Throwable) {
            val error = extractErrorMessage(t)
            syncOutboxDao.markAttempt(
                SyncEntityType.CUSTOMER.storageKey,
                pending.map { it.entityId },
                now,
                error
            )
            throw t
        }
    }

    private suspend fun pushAllLocalTables() {
        val tenantId = tenantProvider.requireTenantId()
        val readableDb = db.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        readableDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (shouldSyncTable(name)) {
                    tables.add(name)
                }
            }
        }

        tables.forEach { table ->
            var batch = firestore.batch()
            var ops = 0
            readableDb.query("SELECT rowid, * FROM $table").use { cursor ->
                val rowIdIndex = cursor.getColumnIndex("rowid")
                while (cursor.moveToNext()) {
                    val data = mutableMapOf<String, Any?>(
                        "__table" to table
                    )
                    val columnCount = cursor.columnCount
                    for (i in 0 until columnCount) {
                        val columnName = cursor.getColumnName(i)
                        if (columnName == "rowid") continue
                        val value = when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            android.database.Cursor.FIELD_TYPE_BLOB -> {
                                val blob = cursor.getBlob(i)
                                if (blob == null) null else Base64.encodeToString(blob, Base64.NO_WRAP)
                            }
                            else -> null
                        }
                        data[columnName] = value
                    }
                    val rowId = if (rowIdIndex >= 0) cursor.getLong(rowIdIndex) else 0L
                    val docId = when {
                        data["id"] != null -> data["id"].toString()
                        data["uuid"] != null -> data["uuid"].toString()
                        else -> rowId.toString()
                    }
                    data["__rowId"] = rowId
                    val docRef = firestore.collection("tenants")
                        .document(tenantId)
                        .collection("sync_data")
                        .document(table)
                        .collection("rows")
                        .document(docId)
                    batch.set(docRef, data, SetOptions.merge())
                    ops++
                    if (ops >= MAX_BATCH_OPS) {
                        batch.commit().await()
                        batch = firestore.batch()
                        ops = 0
                    }
                }
            }
            if (ops > 0) {
                batch.commit().await()
            }
        }
    }

    // [NUEVO] Mismo formato que el ZIP (y evita inventar un campo "number" en Room)
    private fun formatInvoiceNumber(id: Long): String =
        "F-" + id.toString().padStart(8, '0')

    private fun extractErrorMessage(t: Throwable): String =
        t.message?.take(512) ?: t::class.java.simpleName

    private suspend fun hasPrivilegedSyncAccess(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val snapshot = firestore.collection("users").document(uid).get().await()
        val role = snapshot.getString("role")?.trim()?.lowercase().orEmpty()
        val isAdmin = role == "admin"
        val isPrivileged = role == "owner" || role == "manager"
        val hasAdminFlags = snapshot.getBoolean("isAdmin") == true ||
            snapshot.getBoolean("isSuperAdmin") == true
        return isAdmin || isPrivileged || hasAdminFlags
    }

    private suspend fun hasReadSyncAccess(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val snapshot = firestore.collection("users").document(uid).get().await()
        val status = snapshot.getString("status")?.trim()?.lowercase()
        if (!status.isNullOrBlank() && status != "active") {
            return false
        }
        val tenantId = snapshot.getString("tenantId") ?: snapshot.getString("storeId")
        return !tenantId.isNullOrBlank()
    }

    private suspend fun syncCustomersFromRemote() {
        val tenantId = tenantProvider.requireTenantId()
        val customersCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("customers")
        val snapshot = customersCollection.get().await()
        if (snapshot.isEmpty) return

        snapshot.documents
            .mapNotNull { doc -> doc.toCustomerEntityOrNull() }
            .forEach { customer ->
                customerDao.upsert(customer)
            }
    }

    private fun QueryDocumentSnapshot.toCustomerEntityOrNull() = runCatching {
        val idValue = getLong("id")?.toInt()
            ?: id.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
            ?: return null
        val name = getString("name")?.trim().orEmpty()
        if (name.isBlank()) {
            return null
        }
        val createdAtMillis = getLong("createdAtMillis")
        val createdAt = createdAtMillis
            ?.let { millis ->
                LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            }
            ?: LocalDateTime.now()

        com.example.selliaapp.data.local.entity.CustomerEntity(
            id = idValue,
            name = name,
            phone = getString("phone"),
            email = getString("email"),
            address = getString("address"),
            nickname = getString("nickname"),
            rubrosCsv = getString("rubrosCsv"),
            paymentTerm = getString("paymentTerm"),
            paymentMethod = getString("paymentMethod"),
            createdAt = createdAt
        )
    }.getOrNull()

    private fun shouldSyncTable(name: String): Boolean = name !in EXCLUDED_SYNC_TABLES &&
        !name.startsWith("sqlite_")

    companion object {
        private const val MAX_BATCH_OPS = 450
        private const val TAG = "SyncRepository"
        private val EXCLUDED_SYNC_TABLES = setOf(
            "android_metadata",
            "room_master_table",
            "sqlite_sequence",
            "sync_outbox"
        )
    }
}
