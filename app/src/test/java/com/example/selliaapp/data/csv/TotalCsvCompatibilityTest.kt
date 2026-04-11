package com.example.selliaapp.data.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TotalCsvCompatibilityTest {

    @Test
    fun normalizeForImport_addsMissingProductsColumns() {
        val legacy = """
            #SECTION:PRODUCTS
            code,barcode,name,quantity,purchase_price,list_price,cash_price,transfer_price,transfer_net_price,ml_price,ml_3c_price,ml_6c_price,parent_category,category,provider,brand,color,sizes,provider_sku,min_stock,description,image_url,image_urls,updated_at
            SKU1,7790000000001,Producto A,10,100,200,180,180,180,220,230,240,Rubro,Cat,Prov,Marca,,,PSKU,1,Desc,,,
            
            #SECTION:CUSTOMERS
            name,phone
            Juan,123
            
            #SECTION:SALES
            invoice_id,product_id,product_name,quantity,unit_price
            
            #SECTION:EXPENSES
            name,amount,month,year
        """.trimIndent()

        val normalized = TotalCsvCompatibility.normalizeForImport(legacy)
        assertThat(normalized.changed).isTrue()
        assertThat(normalized.notes).isNotEmpty()

        val parse = TotalCsvBundle.splitSections(normalized.content)
        val products = CsvUtils.readAll(parse.sections[TotalCsvBundle.PRODUCTS]!!.byteInputStream())
        val header = products.first()
        assertThat(header).contains("actualizar_stock")
        assertThat(header).contains("public_status")
    }

    @Test
    fun normalizeForImport_keepsContentWhenColumnsAlreadyPresent() {
        val current = TotalCsvBundle.bundle(
            productsCsv = buildString {
                appendLine(
                    ProductCsvExporter.headerColumns().joinToString(",")
                )
                appendLine("SKU1,7790000000001,Producto A,10,100,200,180,180,180,220,230,240,Rubro,Cat,Prov,Marca,,,PSKU,1,,Desc,,,draft,2026-04-11")
            },
            customersCsv = "name,phone",
            salesCsv = "invoice_id,product_id,product_name,quantity,unit_price",
            expensesCsv = "name,amount,month,year"
        )

        val normalized = TotalCsvCompatibility.normalizeForImport(current)
        assertThat(normalized.changed).isFalse()
        assertThat(normalized.notes).isEmpty()
    }
}
