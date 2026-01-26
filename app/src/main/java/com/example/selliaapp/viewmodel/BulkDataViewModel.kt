package com.example.selliaapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.csv.CsvUtils
import com.example.selliaapp.data.csv.CustomerCsvExporter
import com.example.selliaapp.data.csv.ExpenseCsvExporter
import com.example.selliaapp.data.csv.ProductCsvExporter
import com.example.selliaapp.data.csv.SalesCsvExporter
import com.example.selliaapp.data.csv.SalesCsvImporter
import com.example.selliaapp.data.csv.TotalCsvBundle
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BulkDataViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val invoiceDao: InvoiceDao,
    private val expenseRepository: ExpenseRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    data class ExportPayload(
        val fileName: String,
        val mimeType: String,
        val content: String
    )

    data class TotalImportSummary(
        val message: String,
        val errors: List<String>
    )

    fun importCustomers(
        context: Context,
        uri: Uri,
        onCompleted: (ImportResult) -> Unit
    ) {
        viewModelScope.launch(io) {
            val result = customerRepository.importCustomersFromFile(context, uri)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun importUsers(
        context: Context,
        uri: Uri,
        onCompleted: (ImportResult) -> Unit
    ) {
        viewModelScope.launch(io) {
            val result = userRepository.importUsersFromFile(context, uri)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun exportProducts(onCompleted: (Result<ExportPayload>) -> Unit) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val products = productRepository.getAllForExport()
                ExportPayload(
                    fileName = ProductCsvExporter.exportFileName(timestamp()),
                    mimeType = ProductCsvExporter.mimeType(),
                    content = ProductCsvExporter.export(products)
                )
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun exportCustomers(onCompleted: (Result<ExportPayload>) -> Unit) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val customers = customerRepository.getAllOnce()
                ExportPayload(
                    fileName = CustomerCsvExporter.exportFileName(timestamp()),
                    mimeType = CustomerCsvExporter.mimeType(),
                    content = CustomerCsvExporter.export(customers)
                )
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun exportSales(onCompleted: (Result<ExportPayload>) -> Unit) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val invoices = invoiceDao.getAllInvoicesOnce()
                ExportPayload(
                    fileName = SalesCsvExporter.exportFileName(timestamp()),
                    mimeType = SalesCsvExporter.mimeType(),
                    content = SalesCsvExporter.export(invoices)
                )
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun exportExpenses(onCompleted: (Result<ExportPayload>) -> Unit) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val records = expenseRepository.getAllRecordsOnce()
                ExportPayload(
                    fileName = ExpenseCsvExporter.exportFileName(timestamp()),
                    mimeType = ExpenseCsvExporter.mimeType(),
                    content = ExpenseCsvExporter.export(records)
                )
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun exportAll(onCompleted: (Result<ExportPayload>) -> Unit) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val products = productRepository.getAllForExport()
                val customers = customerRepository.getAllOnce()
                val invoices = invoiceDao.getAllInvoicesOnce()
                val expenses = expenseRepository.getAllRecordsOnce()
                val bundled = TotalCsvBundle.bundle(
                    productsCsv = ProductCsvExporter.export(products),
                    customersCsv = CustomerCsvExporter.export(customers),
                    salesCsv = SalesCsvExporter.export(invoices),
                    expensesCsv = ExpenseCsvExporter.export(expenses)
                )
                ExportPayload(
                    fileName = "exportacion_total_${timestamp()}.csv",
                    mimeType = "text/csv",
                    content = bundled
                )
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    fun importAll(
        context: Context,
        uri: Uri,
        onCompleted: (Result<TotalImportSummary>) -> Unit
    ) {
        viewModelScope.launch(io) {
            val result = runCatching {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    String(stream.readBytes())
                } ?: ""
                val sections = TotalCsvBundle.splitSections(content)
                val errors = mutableListOf<String>()
                var productsResult: ImportResult? = null
                var customersResult: ImportResult? = null
                var expensesResult: ImportResult? = null
                var salesInserted = 0

                sections[TotalCsvBundle.PRODUCTS]?.takeIf { it.isNotBlank() }?.let { csv ->
                    val table = CsvUtils.readAll(ByteArrayInputStream(csv.toByteArray()))
                    productsResult = productRepository.importProductsFromTable(
                        table,
                        ProductRepository.ImportStrategy.Append
                    )
                    errors += productsResult?.errors.orEmpty()
                }

                sections[TotalCsvBundle.CUSTOMERS]?.takeIf { it.isNotBlank() }?.let { csv ->
                    val table = CsvUtils.readAll(ByteArrayInputStream(csv.toByteArray()))
                    customersResult = customerRepository.importCustomersFromTable(table)
                    errors += customersResult?.errors.orEmpty()
                }

                sections[TotalCsvBundle.EXPENSES]?.takeIf { it.isNotBlank() }?.let { csv ->
                    val table = CsvUtils.readAll(ByteArrayInputStream(csv.toByteArray()))
                    expensesResult = expenseRepository.importRecordsFromTable(table)
                    errors += expensesResult?.errors.orEmpty()
                }

                sections[TotalCsvBundle.SALES]?.takeIf { it.isNotBlank() }?.let { csv ->
                    val table = CsvUtils.readAll(ByteArrayInputStream(csv.toByteArray()))
                    val rows = SalesCsvImporter.parseTable(table)
                    val (parsedSales, salesErrors) = SalesCsvImporter.groupRows(rows)
                    errors += salesErrors
                    parsedSales.forEach { parsed ->
                        invoiceDao.insertInvoiceWithItems(parsed.invoice, parsed.items)
                        salesInserted++
                    }
                }

                val message = buildString {
                    append("Importación total completa.")
                    productsResult?.let { append(" Productos: ${it.inserted}/${it.updated}.") }
                    customersResult?.let { append(" Clientes: ${it.inserted}/${it.updated}.") }
                    expensesResult?.let { append(" Gastos: ${it.inserted}.") }
                    if (salesInserted > 0) {
                        append(" Ventas: $salesInserted.")
                    }
                }
                TotalImportSummary(message = message, errors = errors)
            }
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }

    private fun timestamp(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        return LocalDateTime.now().format(formatter)
    }
}
