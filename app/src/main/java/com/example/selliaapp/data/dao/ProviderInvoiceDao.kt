package com.example.selliaapp.data.dao



import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.data.model.ProviderInvoiceStatus
import kotlinx.coroutines.flow.Flow

data class ProviderInvoiceWithItems(
    @Embedded val invoice: ProviderInvoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<ProviderInvoiceItem>
)

@Dao
interface ProviderInvoiceDao {

    @Transaction
    @Query("SELECT * FROM provider_invoices WHERE providerId = :providerId ORDER BY issueDateMillis DESC, id DESC")
    fun observeByProvider(providerId: Int): Flow<List<ProviderInvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM provider_invoices WHERE status = :status ORDER BY issueDateMillis ASC, id ASC")
    fun observeByStatus(status: ProviderInvoiceStatus): Flow<List<ProviderInvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM provider_invoices WHERE id = :invoiceId")
    fun observeDetail(invoiceId: Int): Flow<ProviderInvoiceWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: ProviderInvoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ProviderInvoiceItem>): List<Long>

    @Update
    suspend fun updateInvoice(invoice: ProviderInvoice): Int

    @Query("""
        SELECT CAST(strftime('%Y', (paymentDateMillis/1000), 'unixepoch') AS INTEGER) AS year,
               CAST(strftime('%m', (paymentDateMillis/1000), 'unixepoch') AS INTEGER) AS month,
               SUM(COALESCE(paymentAmount, total)) AS total
        FROM provider_invoices
        WHERE status = :status
          AND paymentDateMillis IS NOT NULL
        GROUP BY year, month
        ORDER BY year DESC, month DESC
    """)
    suspend fun sumPaidByMonth(status: ProviderInvoiceStatus = ProviderInvoiceStatus.PAGA): List<MonthlyTotal>
}
