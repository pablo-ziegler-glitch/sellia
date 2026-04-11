package com.example.selliaapp.repository

import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductImportPlannerTest {

    @Test
    fun productNew_isCreated() {
        val rows = parseRows(
            listOf("name", "quantity"),
            listOf("Nuevo", "12")
        )

        val plan = ProductImportPlanner.plan(rows, existingProducts = emptyList())

        assertThat(plan.totalCreated).isEqualTo(1)
        assertThat(plan.totalStockUpdated).isEqualTo(0)
        assertThat(plan.totalRejected).isEqualTo(0)
    }

    @Test
    fun productExistingWithoutMarker_isRejected() {
        val rows = parseRows(
            listOf("name", "quantity"),
            listOf("Coca Cola", "5")
        )
        val existing = listOf(ProductEntity(id = 1, name = "Coca Cola", quantity = 20))

        val plan = ProductImportPlanner.plan(rows, existing)

        assertThat(plan.totalCreated).isEqualTo(0)
        assertThat(plan.totalStockUpdated).isEqualTo(0)
        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("existing_product_without_update_marker")
    }

    @Test
    fun productExistingWithMarker_updatesStock() {
        val rows = parseRows(
            listOf("name", "quantity", "actualizar_stock"),
            listOf("Coca Cola", "7", "si")
        )
        val existing = listOf(ProductEntity(id = 1, name = "Coca Cola", quantity = 20))

        val plan = ProductImportPlanner.plan(rows, existing)

        assertThat(plan.totalCreated).isEqualTo(0)
        assertThat(plan.totalStockUpdated).isEqualTo(1)
        assertThat(plan.totalRejected).isEqualTo(0)
        assertThat(plan.actions.single()).isInstanceOf(ProductImportPlanner.Action.UpdateStock::class.java)
    }

    @Test
    fun duplicateInsideFile_isRejected() {
        val rows = parseRows(
            listOf("name", "quantity", "code"),
            listOf("Producto A", "2", "SKU-1"),
            listOf("Producto A", "4", "SKU-1")
        )

        val plan = ProductImportPlanner.plan(rows, existingProducts = emptyList())

        assertThat(plan.totalCreated).isEqualTo(1)
        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("duplicate_inside_file")
    }

    @Test
    fun normalizedNameWithSpacesAndAccents_matchesExisting() {
        val rows = parseRows(
            listOf("name", "quantity"),
            listOf("  COCA   CÓLA ", "4")
        )
        val existing = listOf(ProductEntity(id = 1, name = "coca cola", quantity = 10))

        val plan = ProductImportPlanner.plan(rows, existing)

        assertThat(plan.totalCreated).isEqualTo(0)
        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("existing_product_without_update_marker")
    }

    @Test
    fun invalidStock_generatesValidationError() {
        val rows = parseRows(
            listOf("name", "quantity"),
            listOf("Producto A", "abc")
        )

        val plan = ProductImportPlanner.plan(rows, existingProducts = emptyList())

        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.totalValidationErrors).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("invalid_stock_value")
    }

    @Test
    fun missingRequiredName_generatesValidationError() {
        val rows = parseRows(
            listOf("name", "quantity"),
            listOf("", "3")
        )

        val plan = ProductImportPlanner.plan(rows, existingProducts = emptyList())

        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.totalValidationErrors).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("missing_required_name")
    }

    @Test
    fun invalidBarcode_generatesValidationError() {
        val rows = parseRows(
            listOf("name", "quantity", "barcode"),
            listOf("Producto A", "3", "ABC-123")
        )

        val plan = ProductImportPlanner.plan(rows, existingProducts = emptyList())

        assertThat(plan.totalRejected).isEqualTo(1)
        assertThat(plan.totalValidationErrors).isEqualTo(1)
        assertThat(plan.issues.first().technicalReason).contains("invalid_barcode_format")
    }

    private fun parseRows(vararg rows: List<String>): List<ProductCsvImporter.Row> {
        val table = rows.toList()
        return ProductCsvImporter.parseTable(table)
    }
}
