package com.example.selliaapp.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.InvoiceItemDao
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.auth.FirebaseSessionCoordinator
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.InvoiceFirestoreMappers
import com.example.selliaapp.data.remote.CustomerFirestoreMappers
import com.example.selliaapp.data.remote.ProductFirestoreMappers
import com.example.selliaapp.di.AppModule.IoDispatcher // [NUEVO] El qualifier real del ZIP está dentro de AppModule
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.PricingConfigRepository
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val pricingConfigRepository: PricingConfigRepository,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    private val sessionCoordinator: FirebaseSessionCoordinator,
    @ApplicationContext private val context: Context,
    /* [ANTERIOR]
    import com.example.selliaapp.di.IoDispatcher
    @IoDispatcher private val io: CoroutineDispatcher
    */
    @IoDispatcher private val io: CoroutineDispatcher
) : SyncRepository {
    private val syncPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun pushPending() = withContext(io) {
        sessionCoordinator.runWithFreshSession(notifyPermissionDenied = false) {
            val now = System.currentTimeMillis()
            pushPendingProducts(now)
            pushPendingInvoices(now)
            pushPendingCustomers(now)
            pushPendingPricingConfig(now)
        }
    }

    override suspend fun pullRemote() {
        withContext(io) {
            sessionCoordinator.runWithFreshSession(notifyPermissionDenied = false) {
                val tenantId = tenantProvider.requireTenantId()
                if (!shouldPullNow(tenantId)) {
                    Log.i(TAG, "Pull omitido por intervalo (tenant=$tenantId)")
                    return@runWithFreshSession
                }
                runIncrementalPull(tenantId)
            }
        }
    }

    override suspend fun runSync(includeBackup: Boolean) = withContext(io) {
        pushPending()
        if (includeBackup) {
            sessionCoordinator.runWithFreshSession(notifyPermissionDenied = false) {
                val tenantId = tenantProvider.requireTenantId()
                runIncrementalPull(tenantId, force = true)
            }
        } else {
            pullRemote()
        }
        if (includeBackup) {
            pushAllLocalTables()
        }
    }


    private suspend fun pushPendingPricingConfig(now: Long) {
        val entityType = SyncEntityType.PRICING_CONFIG.storageKey
        val pending = syncOutboxDao.getByType(entityType)
        if (pending.isEmpty()) return

        val entityIds = pending.map { it.entityId }
        try {
            pricingConfigRepository.pushPricingConfigToCloud()
            syncOutboxDao.deleteByTypeAndIds(entityType, entityIds)
        } catch (t: Throwable) {
            val error = extractErrorMessage(t)
            syncOutboxDao.markAttempt(
                entityType = entityType,
                entityIds = entityIds,
                timestamp = now,
                error = error
            )
            throw t
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

        try {
            entities.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { product ->
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

        try {
            relations.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { relation ->
                    val invoice = relation.invoice
                    val doc = invoicesCollection.document(invoice.id.toString())
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
                batch.commit().await()
            }
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

        if (existingCustomers.isEmpty() && deletedIds.isEmpty()) return

        try {
            val writes = buildList {
                existingCustomers.forEach { customer ->
                    add(
                        CustomerWrite.Upsert(
                            id = customer.id.toLong(),
                            payload = CustomerFirestoreMappers.toMap(customer, tenantId)
                        )
                    )
                }
                deletedIds.forEach { customerId ->
                    add(CustomerWrite.Delete(customerId))
                }
            }
            writes.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { op ->
                    when (op) {
                        is CustomerWrite.Upsert -> {
                            val docRef = customersCollection.document(op.id.toString())
                            batch.set(docRef, op.payload, SetOptions.merge())
                        }
                        is CustomerWrite.Delete -> {
                            val docRef = customersCollection.document(op.id.toString())
                            batch.delete(docRef)
                        }
                    }
                }
                batch.commit().await()
            }
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

    private suspend fun runIncrementalPull(tenantId: String, force: Boolean = false) {
        try {
            val productApplied = pullProductsIncremental(tenantId)
            val invoiceApplied = pullInvoicesIncremental(tenantId)
            val customerApplied = pullCustomersIncremental(tenantId)
            pricingConfigRepository.pullPricingConfigFromCloud()
            markPullCompleted(tenantId)
            Log.i(
                TAG,
                "Pull remoto aplicado tenant=$tenantId products=$productApplied invoices=$invoiceApplied customers=$customerApplied force=$force"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error en pull incremental tenant=$tenantId", t)
            throw t
        }
    }

    private suspend fun pullProductsIncremental(tenantId: String): Int {
        if (!isProductsBaselineDone(tenantId)) {
            val synced = productRepository.syncDown()
            val latestProductUpdatedMs = fetchMaxTimestamp(
                collection = firestore.collection("tenants").document(tenantId).collection("products"),
                field = "updatedAtEpochMs"
            )
            val latestDeletionMs = fetchMaxTimestamp(
                collection = firestore.collection("tenants").document(tenantId).collection("product_deletions"),
                field = "deletedAtEpochMs"
            )
            saveProductsCursor(tenantId, latestProductUpdatedMs, latestDeletionMs)
            setProductsBaselineDone(tenantId)
            return synced
        }

        val sinceUpdatedMs = getProductsUpdatedCursor(tenantId)
        val sinceDeletedMs = getProductsDeletedCursor(tenantId)

        val productsCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("products")
        val changedDocs = fetchPagedSince(
            baseQuery = productsCollection
                .whereGreaterThan("updatedAtEpochMs", sinceUpdatedMs)
                .orderBy("updatedAtEpochMs", Query.Direction.ASCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        )
        val remoteProducts = changedDocs.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            ProductFirestoreMappers.fromMap(doc.id, data)
        }

        val deletionsCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("product_deletions")
        val deletionDocs = fetchPagedSince(
            baseQuery = deletionsCollection
                .whereGreaterThan("deletedAtEpochMs", sinceDeletedMs)
                .orderBy("deletedAtEpochMs", Query.Direction.ASCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        )
        val deletedIds = deletionDocs
            .mapNotNull { doc -> doc.getLong("productId")?.toInt() ?: doc.id.toIntOrNull() }
            .toSet()

        val applied = productRepository.applyRemoteDelta(remoteProducts, deletedIds)
        val maxUpdatedSeen = changedDocs.maxOfOrNull { doc -> doc.getLong("updatedAtEpochMs") ?: sinceUpdatedMs }
            ?: sinceUpdatedMs
        val maxDeletedSeen = deletionDocs.maxOfOrNull { doc -> doc.getLong("deletedAtEpochMs") ?: sinceDeletedMs }
            ?: sinceDeletedMs
        saveProductsCursor(tenantId, maxUpdatedSeen, maxDeletedSeen)
        return applied
    }

    private suspend fun pullInvoicesIncremental(tenantId: String): Int {
        val invoicesCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("invoices")
        val baselineDone = isInvoicesBaselineDone(tenantId)
        val docs = if (!baselineDone) {
            val cutoffMillis = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
            fetchPaged(
                invoicesCollection
                    .whereGreaterThanOrEqualTo("dateMillis", cutoffMillis)
                    .orderBy("dateMillis", Query.Direction.ASCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            )
        } else {
            val cursor = getInvoicesCursor(tenantId)
            fetchPagedSince(
                invoicesCollection
                    .whereGreaterThan("updatedAtMillis", cursor)
                    .orderBy("updatedAtMillis", Query.Direction.ASCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            )
        }

        if (docs.isEmpty()) {
            if (!baselineDone) setInvoicesBaselineDone(tenantId)
            return 0
        }

        val remoteInvoices = docs.mapNotNull { doc -> InvoiceFirestoreMappers.fromDocument(doc) }
        if (remoteInvoices.isNotEmpty()) {
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

        val currentCursor = getInvoicesCursor(tenantId)
        val maxUpdatedSeen = docs.maxOfOrNull { it.getLong("updatedAtMillis") ?: currentCursor } ?: currentCursor
        saveInvoicesCursor(tenantId, maxUpdatedSeen)
        if (!baselineDone) setInvoicesBaselineDone(tenantId)
        return remoteInvoices.size
    }

    private suspend fun pullCustomersIncremental(tenantId: String): Int {
        val customersCollection = firestore.collection("tenants")
            .document(tenantId)
            .collection("customers")
        val baselineDone = isCustomersBaselineDone(tenantId)
        val docs = if (!baselineDone) {
            fetchPaged(
                customersCollection
                    .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            )
        } else {
            val cursor = getCustomersCursor(tenantId)
            fetchPagedSince(
                customersCollection
                    .whereGreaterThan("updatedAtMillis", cursor)
                    .orderBy("updatedAtMillis", Query.Direction.ASCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            )
        }
        if (docs.isEmpty()) {
            if (!baselineDone) setCustomersBaselineDone(tenantId)
            return 0
        }

        var applied = 0
        docs.mapNotNull { doc -> doc.toCustomerEntityOrNull() }.forEach { customer ->
            customerDao.upsert(customer)
            applied++
        }
        val currentCursor = getCustomersCursor(tenantId)
        val maxUpdatedSeen = docs.maxOfOrNull { it.getLong("updatedAtMillis") ?: currentCursor } ?: currentCursor
        saveCustomersCursor(tenantId, maxUpdatedSeen)
        if (!baselineDone) setCustomersBaselineDone(tenantId)
        return applied
    }

    private suspend fun fetchPaged(baseQuery: Query): List<DocumentSnapshot> {
        val docs = mutableListOf<DocumentSnapshot>()
        var lastDoc: DocumentSnapshot? = null
        do {
            val query = if (lastDoc == null) {
                baseQuery.limit(PAGE_SIZE)
            } else {
                baseQuery.startAfter(lastDoc).limit(PAGE_SIZE)
            }
            val page = query.get().await()
            docs.addAll(page.documents)
            lastDoc = page.documents.lastOrNull()
        } while (lastDoc != null)
        return docs
    }

    private suspend fun fetchPagedSince(baseQuery: Query): List<DocumentSnapshot> =
        fetchPaged(baseQuery)

    private suspend fun fetchMaxTimestamp(
        collection: com.google.firebase.firestore.CollectionReference,
        field: String
    ): Long {
        val doc = collection
            .orderBy(field, Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        return doc?.getLong(field) ?: 0L
    }

    private fun shouldPullNow(tenantId: String): Boolean {
        val lastPull = syncPrefs.getLong(keyFor(tenantId, KEY_LAST_PULL_MS), 0L)
        if (lastPull <= 0L) return true
        val intervalMs = SyncScheduler.getIntervalMinutes(context).toLong() * 60_000L
        return System.currentTimeMillis() - lastPull >= intervalMs
    }

    private fun markPullCompleted(tenantId: String) {
        syncPrefs.edit {
            putLong(keyFor(tenantId, KEY_LAST_PULL_MS), System.currentTimeMillis())
        }
    }

    private fun saveProductsCursor(tenantId: String, updatedMs: Long, deletedMs: Long) {
        syncPrefs.edit {
            putLong(keyFor(tenantId, KEY_PRODUCTS_UPDATED_CURSOR_MS), updatedMs.coerceAtLeast(0L))
            putLong(keyFor(tenantId, KEY_PRODUCTS_DELETED_CURSOR_MS), deletedMs.coerceAtLeast(0L))
        }
    }

    private fun getProductsUpdatedCursor(tenantId: String): Long =
        syncPrefs.getLong(keyFor(tenantId, KEY_PRODUCTS_UPDATED_CURSOR_MS), 0L)

    private fun getProductsDeletedCursor(tenantId: String): Long =
        syncPrefs.getLong(keyFor(tenantId, KEY_PRODUCTS_DELETED_CURSOR_MS), 0L)

    private fun saveInvoicesCursor(tenantId: String, updatedMs: Long) {
        syncPrefs.edit {
            putLong(keyFor(tenantId, KEY_INVOICES_UPDATED_CURSOR_MS), updatedMs.coerceAtLeast(0L))
        }
    }

    private fun getInvoicesCursor(tenantId: String): Long =
        syncPrefs.getLong(keyFor(tenantId, KEY_INVOICES_UPDATED_CURSOR_MS), 0L)

    private fun saveCustomersCursor(tenantId: String, updatedMs: Long) {
        syncPrefs.edit {
            putLong(keyFor(tenantId, KEY_CUSTOMERS_UPDATED_CURSOR_MS), updatedMs.coerceAtLeast(0L))
        }
    }

    private fun getCustomersCursor(tenantId: String): Long =
        syncPrefs.getLong(keyFor(tenantId, KEY_CUSTOMERS_UPDATED_CURSOR_MS), 0L)

    private fun isProductsBaselineDone(tenantId: String): Boolean =
        syncPrefs.getBoolean(keyFor(tenantId, KEY_PRODUCTS_BASELINE_DONE), false)

    private fun setProductsBaselineDone(tenantId: String) {
        syncPrefs.edit {
            putBoolean(keyFor(tenantId, KEY_PRODUCTS_BASELINE_DONE), true)
        }
    }

    private fun isInvoicesBaselineDone(tenantId: String): Boolean =
        syncPrefs.getBoolean(keyFor(tenantId, KEY_INVOICES_BASELINE_DONE), false)

    private fun setInvoicesBaselineDone(tenantId: String) {
        syncPrefs.edit {
            putBoolean(keyFor(tenantId, KEY_INVOICES_BASELINE_DONE), true)
        }
    }

    private fun isCustomersBaselineDone(tenantId: String): Boolean =
        syncPrefs.getBoolean(keyFor(tenantId, KEY_CUSTOMERS_BASELINE_DONE), false)

    private fun setCustomersBaselineDone(tenantId: String) {
        syncPrefs.edit {
            putBoolean(keyFor(tenantId, KEY_CUSTOMERS_BASELINE_DONE), true)
        }
    }

    private fun keyFor(tenantId: String, key: String): String = "$tenantId:$key"

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

    private fun DocumentSnapshot.toCustomerEntityOrNull() = runCatching {
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
        private const val PAGE_SIZE = 500L
        private const val TAG = "SyncRepository"
        private const val SYNC_PREFS_NAME = "sync_repository_preferences"
        private const val KEY_LAST_PULL_MS = "last_pull_ms"
        private const val KEY_PRODUCTS_UPDATED_CURSOR_MS = "products_updated_cursor_ms"
        private const val KEY_PRODUCTS_DELETED_CURSOR_MS = "products_deleted_cursor_ms"
        private const val KEY_INVOICES_UPDATED_CURSOR_MS = "invoices_updated_cursor_ms"
        private const val KEY_CUSTOMERS_UPDATED_CURSOR_MS = "customers_updated_cursor_ms"
        private const val KEY_PRODUCTS_BASELINE_DONE = "products_baseline_done"
        private const val KEY_INVOICES_BASELINE_DONE = "invoices_baseline_done"
        private const val KEY_CUSTOMERS_BASELINE_DONE = "customers_baseline_done"
        private val EXCLUDED_SYNC_TABLES = setOf(
            "android_metadata",
            "room_master_table",
            "sqlite_sequence",
            "sync_outbox"
        )
    }

    private sealed interface CustomerWrite {
        data class Upsert(val id: Long, val payload: Map<String, Any?>) : CustomerWrite
        data class Delete(val id: Long) : CustomerWrite
    }
}
