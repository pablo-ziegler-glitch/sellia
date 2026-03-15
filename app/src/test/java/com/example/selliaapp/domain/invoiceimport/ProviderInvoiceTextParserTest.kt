package com.example.selliaapp.domain.invoiceimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderInvoiceTextParserTest {

    private val parser = ProviderInvoiceTextParser()

    @Test
    fun parse_recognizesMetadataAndItems_withDifferentNumberFormats() {
        val raw = """
            Proveedor: Distribuidora Norte SRL
            CUIT: 30-71234567-8
            Factura N°: A-0003-00001234
            Fecha: 15/01/2026
            COD001 Yerba 1kg 2 3.250,00 6.500,00
            SKU-77 Azucar 10 1,250.50 12,505.00
            Importe Total: $19.005,00
        """.trimIndent()

        val result = parser.parse(raw)

        assertThat(result.invoiceNumber).isEqualTo("A-0003-00001234")
        assertThat(result.providerTaxId).isEqualTo("30-71234567-8")
        assertThat(result.totalAmount).isWithin(0.001).of(19005.0)
        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].quantity).isWithin(0.001).of(2.0)
        assertThat(result.items[0].lineTotal).isWithin(0.001).of(6500.0)
        assertThat(result.items[1].unitPrice).isWithin(0.001).of(1250.5)
    }

    @Test
    fun parse_itemLineWithNumericSku_detectsCodeAndQuantityFromNextToken() {
        val raw = """
            Factura: B-0004-00000077
            7791234567890 Galletitas Clasicas 6 450,00 2700,00
            Total: 2700,00
        """.trimIndent()

        val result = parser.parse(raw)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().code).isEqualTo("7791234567890")
        assertThat(result.items.first().quantity).isWithin(0.001).of(6.0)
        assertThat(result.items.first().unitPrice).isWithin(0.001).of(450.0)
        assertThat(result.items.first().lineTotal).isWithin(0.001).of(2700.0)
    }

    @Test
    fun parse_itemLineWithoutSku_parsesMixedDecimalFormats() {
        val raw = """
            Factura: C-0002-00000015
            Leche Entera 12 1,250.50 15,006.00
            Total: 15,006.00
        """.trimIndent()

        val result = parser.parse(raw)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().code).isNull()
        assertThat(result.items.first().name).isEqualTo("Leche Entera")
        assertThat(result.items.first().quantity).isWithin(0.001).of(12.0)
        assertThat(result.items.first().unitPrice).isWithin(0.001).of(1250.5)
        assertThat(result.items.first().lineTotal).isWithin(0.001).of(15006.0)
    }

    @Test
    fun parse_itemLineWithVatIncluded_capturesVatPercent() {
        val raw = """
            Factura: A-0001-00000098
            12345678 Arroz Premium IVA 21% 2 1.000,00 2.000,00
            Total: 2.000,00
        """.trimIndent()

        val result = parser.parse(raw)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().vatPercent).isWithin(0.001).of(21.0)
        assertThat(result.items.first().code).isEqualTo("12345678")
    }

    @Test
    fun parse_discardsItemLine_whenQtyUnitTotalAreIncoherent() {
        val raw = """
            Factura: A-0001-00000101
            Shampoo Neutro 2 100,00 999,00
            Total: 999,00
        """.trimIndent()

        val result = parser.parse(raw)

        assertThat(result.items).isEmpty()
        assertThat(result.warnings).contains(
            "Renglón descartado por incoherencia cantidad/precio/total: 'Shampoo Neutro 2 100,00 999,00'"
        )
    }

    @Test
    fun parse_returnsWarnings_whenTextHasNoStructure() {
        val result = parser.parse("Texto sin datos útiles")

        assertThat(result.items).isEmpty()
        assertThat(result.warnings).isNotEmpty()
        assertThat(result.totalAmount).isNull()
    }
}
