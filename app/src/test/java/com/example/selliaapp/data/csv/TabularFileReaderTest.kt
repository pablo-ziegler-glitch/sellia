package com.example.selliaapp.data.csv

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Test

class TabularFileReaderTest {

    @Test
    fun readSpreadsheet_parsesXlsxCellsAsStrings() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Hoja1")
        sheet.createRow(0).apply {
            createCell(0).setCellValue("name")
            createCell(1).setCellValue("quantity")
            createCell(2).setCellValue("price")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("Jabón")
            createCell(1).setCellValue(12.0)
            createCell(2).setCellValue(1999.99)
        }

        val bytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }
        workbook.close()

        val table = ByteArrayInputStream(bytes).use { stream ->
            TabularFileReader.readSpreadsheet(stream)
        }

        assertThat(table).containsExactly(
            listOf("name", "quantity", "price"),
            listOf("Jabón", "12", "1999.99")
        ).inOrder()
    }

    @Test
    fun readSpreadsheet_dropsTrailingBlankCells() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet()
        sheet.createRow(0).apply {
            createCell(0).setCellValue("code")
            createCell(1).setCellValue("barcode")
            createCell(2).setCellValue("name")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("SKU-1")
            createCell(1).setCellValue("1234567890123")
            createCell(2).setCellValue("Café molido")
            createCell(3) // vacío
            createCell(4) // vacío extra
        }

        val bytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }
        workbook.close()

        val table = ByteArrayInputStream(bytes).use { stream ->
            TabularFileReader.readSpreadsheet(stream)
        }

        assertThat(table[1]).containsExactly("SKU-1", "1234567890123", "Café molido").inOrder()
    }
}
