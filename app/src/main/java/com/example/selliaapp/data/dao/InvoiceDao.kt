package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.selliaapp.data.local.projections.SumByBucket
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import kotlinx.coroutines.flow.Flow

/**
 * Relación 1-N: Invoice con sus items.
 */
data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItem>
)
data class DayRow(val day: Long, val total: Double)
data class HourRow(val hour: Long, val total: Double)

@Dao
interface InvoiceDao {

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY dateMillis DESC, id DESC")
    fun getAllInvoices(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY dateMillis DESC, id DESC")
    suspend fun getAllInvoicesOnce(): List<InvoiceWithItems>

    @Transaction
    @Query(
        "SELECT * FROM invoices WHERE id IN (:ids) ORDER BY dateMillis DESC, id DESC"
    )
    suspend fun getInvoicesWithItemsByIds(ids: List<Long>): List<InvoiceWithItems>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoiceItems(items: List<InvoiceItem>)

    @Query("SELECT * FROM Invoices WHERE id = :id")
    suspend fun getById(id: Long): Invoice?

    // --- Reportes ---

    // Reporte: suma total agrupado por día (yyyy-MM-dd) en rango [from,to)
    @Query("""
        SELECT strftime('%Y-%m-%d', (dateMillis/1000), 'unixepoch') AS bucket,
               SUM(total) AS amount
        FROM invoices
        WHERE dateMillis >= :from AND dateMillis < :to
        GROUP BY bucket
        ORDER BY bucket
    """)
    suspend fun sumTotalByDay(from: Long, to: Long): List<SumByBucket>

    /**
     * Agrupa por DÍA: floor(dateMillis a 00:00) con aritmética en milisegundos.
     * (dateMillis / 86_400_000) * 86_400_000 => inicio del día en epochMillis
     */
    @Query("""
        SELECT ((dateMillis / 86400000) * 86400000) AS day,
               SUM(total) AS total
        FROM Invoices
        WHERE dateMillis BETWEEN :startMillis AND :endMillis
        GROUP BY day
        ORDER BY day
    """)
    suspend fun salesGroupedByDay(startMillis: Long, endMillis: Long): List<DayRow>

    /**
     * Agrupa por HORA: floor a hora (3600_000 ms).
     */
    @Query("""
        SELECT ((dateMillis / 3600000) * 3600000) AS hour,
               SUM(total) AS total
        FROM Invoices
        WHERE dateMillis BETWEEN :startMillis AND :endMillis
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun salesGroupedByHour(startMillis: Long, endMillis: Long): List<HourRow>


    /**
     * Suma el total de facturas entre dos fechas (epoch millis) inclusive.
     * Devuelve 0 si no hay resultados.
     */

    // ----------------------------
    // Inserts básicos
    // ----------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItem>)

    // ----------------------------
    // Lecturas
    // ----------------------------
    @Transaction
    @Query("""
        SELECT * FROM invoices
        ORDER BY dateMillis DESC, id DESC
    """)
    fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM invoices
        WHERE (:q = '__no_match__')
           OR (customerName LIKE '%' || :q || '%')
        ORDER BY dateMillis DESC, id DESC
    """)
    fun observeInvoicesByCustomerQuery(q: String): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM invoices
        WHERE id = :id
        LIMIT 1
    """)
    suspend fun getInvoiceWithItemsById(id: Long): InvoiceWithItems?

    // ----------------------------
    // Sumatorias
    // ----------------------------
    /**
     * Suma el total de facturas entre dos fechas (epoch millis) inclusive.
     * Devuelve 0 si no hay resultados.
     */
    @Query("""
        SELECT IFNULL(SUM(total), 0.0) FROM invoices
        WHERE dateMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun sumTotalBetween(startMillis: Long, endMillis: Long): Double


    // Búsqueda por cliente (se filtra por customerName conteniendo el query)
    @Transaction
    @Query("""
        SELECT * FROM Invoices
        WHERE (:query IS NULL OR :query = '' OR customerName LIKE '%' || :query || '%')
        ORDER BY dateMillis DESC
    """)
    fun observeInvoicesWithItemsByCustomerQuery(query: String): Flow<List<InvoiceWithItems>>




}






