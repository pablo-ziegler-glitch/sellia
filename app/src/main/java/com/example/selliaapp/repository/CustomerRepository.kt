package com.example.selliaapp.repository

import android.content.Context
import android.net.Uri
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.csv.CustomerCsvImporter
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.dao.SyncOutboxDao
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.data.local.entity.SyncEntityType
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.remote.CustomerFirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de clientes con búsqueda y borrado.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider
) {
    fun observeAll(): Flow<List<CustomerEntity>> = customerDao.observeAll()

    suspend fun getAllOnce(): List<CustomerEntity> = customerDao.getAllOnce()

    suspend fun upsert(c: CustomerEntity): Int {
        val localId = customerDao.upsert(c)
        if (localId <= 0) return localId

        val persisted = customerDao.getById(localId) ?: return localId
        val tenantId = runCatching { tenantProvider.requireTenantId() }.getOrNull()
        if (tenantId.isNullOrBlank()) {
            enqueueCustomerSync(localId.toLong())
            return localId
        }

        val docRef = firestore.collection("tenants")
            .document(tenantId)
            .collection("customers")
            .document(localId.toString())
        val payload = CustomerFirestoreMappers.toMap(persisted, tenantId)

        runCatching {
            docRef.set(payload, SetOptions.merge()).await()
        }.onFailure {
            enqueueCustomerSync(localId.toLong())
        }

        return localId
    }

    /** Búsqueda por nombre/email/teléfono/apodo. */
    fun search(q: String): Flow<List<CustomerEntity>> = customerDao.search(q)

    suspend fun importCustomersFromFile(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val rows = CustomerCsvImporter.parseFile(context.contentResolver, uri)
        importCustomers(rows)
    }

    suspend fun importCustomersFromTable(table: List<List<String>>): ImportResult =
        withContext(Dispatchers.IO) {
            val rows = CustomerCsvImporter.parseTable(table)
            importCustomers(rows)
        }

    private suspend fun importCustomers(rows: List<CustomerCsvImporter.Row>): ImportResult {
        if (rows.isEmpty()) {
            return ImportResult(0, 0, listOf("El archivo no contiene filas válidas."))
        }

        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()

        rows.forEachIndexed { idx, row ->
            val name = row.name.trim()
            if (name.isBlank()) {
                errors += "L${idx + 2}: nombre requerido."
                return@forEachIndexed
            }

            try {
                val existing = customerDao.getByName(name)
                if (existing == null) {
                    val id = upsert(
                        CustomerEntity(
                            name = name,
                            phone = row.phone,
                            email = row.email,
                            address = row.address,
                            nickname = row.nickname,
                            rubrosCsv = row.rubrosCsv,
                            paymentTerm = row.paymentTerm,
                            paymentMethod = row.paymentMethod,
                            createdAt = LocalDateTime.now()
                        )
                    )
                    if (id > 0) inserted++
                } else {
                    val merged = existing.copy(
                        phone = row.phone ?: existing.phone,
                        email = row.email ?: existing.email,
                        address = row.address ?: existing.address,
                        nickname = row.nickname ?: existing.nickname,
                        rubrosCsv = row.rubrosCsv ?: existing.rubrosCsv,
                        paymentTerm = row.paymentTerm ?: existing.paymentTerm,
                        paymentMethod = row.paymentMethod ?: existing.paymentMethod
                    )
                    val id = upsert(merged)
                    if (id > 0) updated++
                }
            } catch (t: Throwable) {
                errors += "L${idx + 2}: ${t.message ?: t::class.java.simpleName}"
            }
        }

        return ImportResult(inserted, updated, errors)
    }

    /** Borrado de cliente. */
    suspend fun delete(c: CustomerEntity) {
        val deletedRows = customerDao.delete(c)
        if (deletedRows <= 0 || c.id <= 0) return

        val tenantId = runCatching { tenantProvider.requireTenantId() }.getOrNull()
        if (tenantId.isNullOrBlank()) {
            enqueueCustomerSync(c.id.toLong())
            return
        }

        val docRef = firestore.collection("tenants")
            .document(tenantId)
            .collection("customers")
            .document(c.id.toString())

        runCatching {
            docRef.delete().await()
        }.onFailure {
            enqueueCustomerSync(c.id.toLong())
        }
    }

    private suspend fun enqueueCustomerSync(customerId: Long) {
        syncOutboxDao.upsert(
            SyncOutboxEntity(
                entityType = SyncEntityType.CUSTOMER.storageKey,
                entityId = customerId
            )
        )
    }

    // ---------- Métricas helpers ----------
    private fun ldtToMillis(ldt: LocalDateTime): Long =
        ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun countBetween(start: LocalDateTime, end: LocalDateTime): Int =
        customerDao.countBetweenMillis(ldtToMillis(start), ldtToMillis(end))

    suspend fun countToday(now: LocalDateTime = LocalDateTime.now()): Int {
        val start = now.toLocalDate().atStartOfDay()
        return countBetween(start, now)
    }

    suspend fun countThisWeek(now: LocalDateTime = LocalDateTime.now()): Int {
        val dow = now.toLocalDate().dayOfWeek.value  // 1..7 (Lunes=1)
        val start = now.toLocalDate().minusDays((dow - 1).toLong()).atStartOfDay()
        return countBetween(start, now)
    }

    suspend fun countThisMonth(now: LocalDateTime = LocalDateTime.now()): Int {
        val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay()
        return countBetween(start, now)
    }

    suspend fun countThisYear(now: LocalDateTime = LocalDateTime.now()): Int {
        val start = LocalDate.of(now.year, 1, 1).atStartOfDay()
        return countBetween(start, now)
    }
}
