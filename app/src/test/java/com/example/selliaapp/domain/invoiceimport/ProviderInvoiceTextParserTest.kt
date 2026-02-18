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
    fun parse_returnsWarnings_whenTextHasNoStructure() {
        val result = parser.parse("Texto sin datos útiles")

        assertThat(result.items).isEmpty()
        assertThat(result.warnings).isNotEmpty()
        assertThat(result.totalAmount).isNull()
    }
}
