package com.example.selliaapp.repository

import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductImportFlowIntegrationTest {

    @Test
    fun mixedImport_generatesExpectedSummaryAndIssues() {
        val existing = listOf(
            ProductEntity(id = 1, code = "SKU-1", barcode = "7791234567890", name = "Coca Cola", quantity = 10),
            ProductEntity(id = 2, code = "SKU-2", barcode = "7791234567891", name = "Pepsi", quantity = 5)
        )
        val rows = ProductCsvImporter.parseTable(
            listOf(
                listOf("name", "quantity", "code", "actualizar_stock"),
                listOf("Coca Cola", "20", "SKU-1", "si"),   // update
                listOf("Pepsi", "4", "SKU-2", ""),          // reject existing without marker
                listOf("Producto Nuevo", "8", "SKU-3", ""), // create
                listOf("Producto Malo", "abc", "SKU-4", "") // invalid stock
            )
        )

        val plan = ProductImportPlanner.plan(rows, existing)

        assertThat(plan.totalProcessed).isEqualTo(4)
        assertThat(plan.totalCreated).isEqualTo(1)
        assertThat(plan.totalStockUpdated).isEqualTo(1)
        assertThat(plan.totalRejected).isEqualTo(2)
        assertThat(plan.totalValidationErrors).isEqualTo(1)
        assertThat(plan.issues).hasSize(2)
        assertThat(plan.issues.map { it.line }).containsExactly(3, 5)
    }

    @Test
    fun reimportSameFile_doesNotCreateDuplicates() {
        val firstImportRows = ProductCsvImporter.parseTable(
            listOf(
                listOf("name", "quantity", "code"),
                listOf("Detergente", "6", "SKU-11"),
                listOf("Jabón", "3", "SKU-12")
            )
        )
        val firstPlan = ProductImportPlanner.plan(firstImportRows, existingProducts = emptyList())
        assertThat(firstPlan.totalCreated).isEqualTo(2)
        assertThat(firstPlan.totalRejected).isEqualTo(0)

        val importedAsExisting = firstPlan.actions
            .filterIsInstance<ProductImportPlanner.Action.Create>()
            .mapIndexed { index, action ->
                ProductEntity(
                    id = index + 1,
                    code = action.row.code,
                    barcode = action.row.barcode,
                    name = action.row.name,
                    quantity = action.row.quantity ?: 0,
                    brand = action.row.brand,
                    parentCategory = action.row.parentCategory,
                    category = action.row.category,
                    color = action.row.color,
                    sizes = action.row.sizes,
                    providerSku = action.row.providerSku
                )
            }

        val secondPlan = ProductImportPlanner.plan(firstImportRows, existingProducts = importedAsExisting)
        assertThat(secondPlan.totalCreated).isEqualTo(0)
        assertThat(secondPlan.totalStockUpdated).isEqualTo(0)
        assertThat(secondPlan.totalRejected).isEqualTo(2)
        assertThat(secondPlan.issues.all { it.technicalReason.contains("existing_product_without_update_marker") }).isTrue()
    }
}
