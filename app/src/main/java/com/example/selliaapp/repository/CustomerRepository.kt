package com.example.selliaapp.repository

import android.content.Context
import android.net.Uri
import com.example.selliaapp.data.csv.CustomerCsvImporter
import com.example.selliaapp.data.dao.CustomerDao
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.data.model.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val customerDao: CustomerDao
) {
    fun observeAll(): Flow<List<CustomerEntity>> = customerDao.observeAll()

    suspend fun upsert(c: CustomerEntity): Int {
        // Si es alta nueva (id=0), el createdAt ya viene con LocalDateTime.now() por default.
        // Si fuese un update, respetamos el createdAt existente.
        return customerDao.upsert(c)
    }

    /** Búsqueda por nombre/email/teléfono/apodo. */
    fun search(q: String): Flow<List<CustomerEntity>> = customerDao.search(q)

    suspend fun importCustomersFromFile(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val rows = CustomerCsvImporter.parseFile(context.contentResolver, uri)
        if (rows.isEmpty()) {
            return@withContext ImportResult(0, 0, listOf("El archivo no contiene filas válidas."))
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
                    customerDao.insert(
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
                    inserted++
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
                    customerDao.update(merged)
                    updated++
                }
            } catch (t: Throwable) {
                errors += "L${idx + 2}: ${t.message ?: t::class.java.simpleName}"
            }
        }

        ImportResult(inserted, updated, errors)
    }

    /** Borrado de cliente. */
    suspend fun delete(c: CustomerEntity) = customerDao.delete(c)

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
