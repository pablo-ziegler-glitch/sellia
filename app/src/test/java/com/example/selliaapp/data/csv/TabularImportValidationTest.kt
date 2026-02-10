package com.example.selliaapp.data.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabularImportValidationTest {

    @Test
    fun customerImporter_stopsAtFirstBlankRow_andSkipsMissingName() {
        val table = listOf(
            listOf("name", "phone"),
            listOf("Cliente 1", "111"),
            listOf("", "222"),
            listOf("Cliente 2", "333")
        )

        val rows = CustomerCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().name).isEqualTo("Cliente 1")
    }

    @Test
    fun userImporter_skipsRowsWithMissingRequiredFields() {
        val table = listOf(
            listOf("name", "email", "role"),
            listOf("Admin", "admin@sellia.com", "admin"),
            listOf("", "ops@sellia.com", "operator"),
            listOf("Operador", "", "operator"),
            listOf("Caja", "cash@sellia.com", "")
        )

        val rows = UserCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().email).isEqualTo("admin@sellia.com")
    }

    @Test
    fun expenseImporter_skipsRowsWithoutNameOrAmount() {
        val table = listOf(
            listOf("name", "amount", "month", "year"),
            listOf("Luz", "15000", "4", "2026"),
            listOf("", "5000", "4", "2026"),
            listOf("Internet", "", "4", "2026")
        )

        val rows = ExpenseCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().name).isEqualTo("Luz")
        assertThat(rows.first().amount).isEqualTo(15000.0)
    }

    @Test
    fun salesImporter_stopsAtFirstBlankRow_andSkipsIncompleteRows() {
        val table = listOf(
            listOf("invoice_id", "product_id", "product_name", "quantity", "unit_price"),
            listOf("101", "1", "Producto A", "2", "500"),
            listOf("102", "", "Producto B", "1", "200"),
            listOf("", "", "", "", ""),
            listOf("103", "2", "Producto C", "1", "300")
        )

        val rows = SalesCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().invoiceId).isEqualTo(101L)
    }
}
