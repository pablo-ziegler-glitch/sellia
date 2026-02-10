package com.example.selliaapp.data.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductCsvImporterTest {

    @Test
    fun parseTable_stopsWhenFindsCompletelyBlankRow() {
        val table = listOf(
            listOf("name", "quantity", "price"),
            listOf("Producto 1", "5", "100"),
            listOf("", "", ""),
            listOf("Producto 2", "10", "200")
        )

        val rows = ProductCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().name).isEqualTo("Producto 1")
    }

    @Test
    fun parseTable_skipsRowsWithMissingRequiredField() {
        val table = listOf(
            listOf("name", "quantity", "price"),
            listOf("Producto 1", "5", "100"),
            listOf("", "10", "200"),
            listOf("Producto 3", "2", "50")
        )

        val rows = ProductCsvImporter.parseTable(table)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.name }).containsExactly("Producto 1", "Producto 3").inOrder()
    }
}
