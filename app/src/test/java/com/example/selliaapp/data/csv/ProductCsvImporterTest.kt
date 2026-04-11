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
            listOf("name", "quantity", "actualizar_stock"),
            listOf("Producto 1", "5", "100"),
            listOf("", "10", "x"),
            listOf("Producto 3", "2", "si")
        )

        val rows = ProductCsvImporter.parseTable(table)

        assertThat(rows).hasSize(3)
        assertThat(rows.map { it.name }).containsExactly("Producto 1", "", "Producto 3").inOrder()
        assertThat(rows[0].updateStockRequested).isFalse()
        assertThat(rows[1].updateStockRequested).isTrue()
        assertThat(rows[2].updateStockRequested).isTrue()
    }

    @Test
    fun parseTable_parsesActualizarStockVariantsAndInvalidValues() {
        val table = listOf(
            listOf("name", "quantity", "actualizar_stock"),
            listOf("A", "1", "x"),
            listOf("B", "1", "sí"),
            listOf("C", "1", "false"),
            listOf("D", "1", "tal vez")
        )

        val rows = ProductCsvImporter.parseTable(table)

        assertThat(rows[0].updateStockRequested).isTrue()
        assertThat(rows[0].updateStockMarkerValid).isTrue()
        assertThat(rows[1].updateStockRequested).isTrue()
        assertThat(rows[1].updateStockMarkerValid).isTrue()
        assertThat(rows[2].updateStockRequested).isFalse()
        assertThat(rows[2].updateStockMarkerValid).isTrue()
        assertThat(rows[3].updateStockRequested).isFalse()
        assertThat(rows[3].updateStockMarkerValid).isFalse()
    }

    @Test
    fun parseTable_marksInvalidStock() {
        val table = listOf(
            listOf("name", "quantity"),
            listOf("A", "abc")
        )

        val rows = ProductCsvImporter.parseTable(table)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].quantity).isNull()
        assertThat(rows[0].hasInvalidQuantity).isTrue()
    }
}
