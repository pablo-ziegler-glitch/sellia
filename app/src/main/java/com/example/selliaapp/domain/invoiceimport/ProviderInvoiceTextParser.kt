package com.example.selliaapp.domain.invoiceimport

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Parser heurístico para facturas heterogéneas de proveedores. */
import javax.inject.Inject

class ProviderInvoiceTextParser @Inject constructor() {

    fun parse(rawText: String): ParsedProviderInvoiceDraft {
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

        val extractedItems = extractItems(lines)
        val items = extractedItems.items
        val consumedLines = items.map { it.sourceLine }.toSet()
        val warnings = extractedItems.warnings.toMutableList()

        val invoiceNumber = extractInvoiceNumber(lines)
        val issueDateMillis = extractIssueDateMillis(lines)
        val totalAmount = extractTotalAmount(lines)

        if (invoiceNumber == null) warnings += "No se pudo reconocer número de factura"
        if (issueDateMillis == null) warnings += "No se pudo reconocer fecha de emisión"
        if (totalAmount == null) warnings += "No se pudo reconocer total de factura"
        if (items.isEmpty()) warnings += "No se pudieron reconocer renglones de productos"

        return ParsedProviderInvoiceDraft(
            invoiceNumber = invoiceNumber,
            providerName = extractProviderName(lines),
            providerTaxId = extractProviderTaxId(lines),
            issueDateMillis = issueDateMillis,
            totalAmount = totalAmount,
            currencySymbol = detectCurrency(lines),
            items = items,
            unparsedLines = lines.filterNot(consumedLines::contains),
            warnings = warnings
        )
    }

    private fun extractInvoiceNumber(lines: List<String>): String? {
        val regex = Regex("""(?i)(?:factura|comprobante|nro|numero|n°|nº|no\.?)(?:\s*(?:de)?)?\s*[:#-]?\s*([a-z0-9\-]{4,})""")
        return lines.firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1)?.uppercase() }
    }

    private fun extractProviderName(lines: List<String>): String? {
        val regex = Regex("""(?i)(?:proveedor|razon\s+social|empresa)\s*[:\-]\s*(.+)$""")
        return lines.firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1)?.trim()?.takeIf { name -> name.length > 2 } }
    }

    private fun extractProviderTaxId(lines: List<String>): String? {
        val regex = Regex("""(?i)(?:cuit|rut|nit|ruc|tax\s*id)\s*[:\-]?\s*([0-9\-\.]{8,})""")
        return lines.firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1)?.trim() }
    }

    private fun extractIssueDateMillis(lines: List<String>): Long? {
        val regex = Regex("""(?i)(?:fecha|emision|issued?)\s*[:\-]?\s*(\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4})""")
        val dateRaw = lines.firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1) }
            ?: lines.firstNotNullOfOrNull { findDate(it) }
            ?: return null

        val parts = dateRaw.replace('-', '/').replace('.', '/').split('/')
        if (parts.size != 3) return null
        val year = parts[2].toIntOrNull() ?: return null
        val fullYear = if (year < 100) year + 2000 else year
        val formatter = DateTimeFormatter.ofPattern("d/M/yyyy")
        val date = LocalDate.parse("${parts[0]}/${parts[1]}/$fullYear", formatter)
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun extractTotalAmount(lines: List<String>): Double? {
        val totalRegex = Regex("""(?i)(?:importe\s+total|total\s*(?:a\s+pagar)?|grand\s+total)\s*[:\-]?\s*([$€£]?\s*[0-9.,]+)""")
        return lines.asReversed().firstNotNullOfOrNull { line ->
            totalRegex.find(line)?.groupValues?.get(1)?.let(::parseFlexibleNumber)
        } ?: lines.asReversed().firstNotNullOfOrNull { line ->
            if (!normalize(line).contains("total")) return@firstNotNullOfOrNull null
            Regex("""[$€£]?\s*[0-9][0-9.,]*""").findAll(line).lastOrNull()?.value?.let(::parseFlexibleNumber)
        }
    }

    private fun extractItems(lines: List<String>): ItemExtractionResult {
        val items = mutableListOf<ParsedProviderInvoiceItem>()
        val warnings = mutableListOf<String>()
        lines.forEach { line ->
            when (val parsedLine = parseItemLine(line)) {
                null -> Unit
                is ItemLineParseResult.Discarded -> warnings += parsedLine.warning
                is ItemLineParseResult.Parsed -> items += parsedLine.item
            }
        }
        return ItemExtractionResult(items = items, warnings = warnings)
    }

    private fun parseItemLine(line: String): ItemLineParseResult? {
        if (normalize(line).contains("total")) return null

        val numericMatches = Regex("""\d+[\d.,]*""").findAll(line)
            .filterNot { line.getOrNull(it.range.last + 1) == '%' }
            .toList()
        if (numericMatches.size < 3) return null

        val unitMatch = numericMatches[numericMatches.size - 2]
        val totalMatch = numericMatches.last()
        val unitPrice = parseFlexibleNumber(unitMatch.value) ?: return null
        val lineTotal = parseFlexibleNumber(totalMatch.value) ?: return null

        val candidatesBeforeUnit = numericMatches.filter { it.range.last < unitMatch.range.first }
        if (candidatesBeforeUnit.isEmpty()) return null

        val firstNumeric = candidatesBeforeUnit.first()
        val maybeCode = firstNumeric.value.takeIf(::looksLikeSkuCode)
        val quantityMatch = if (maybeCode != null) {
            candidatesBeforeUnit.firstOrNull { it.range.first > firstNumeric.range.last }
        } else {
            candidatesBeforeUnit.lastOrNull()
        } ?: return null

        val quantity = parseFlexibleNumber(quantityMatch.value) ?: return null
        if (quantity <= 0.0 || unitPrice <= 0.0 || lineTotal <= 0.0) return null

        val tolerance = maxOf(0.05, lineTotal * 0.02)
        if (abs((quantity * unitPrice) - lineTotal) > tolerance) {
            return ItemLineParseResult.Discarded(
                "Renglón descartado por incoherencia cantidad/precio/total: '$line'"
            )
        }

        val nameStart = maybeCode?.let { firstNumeric.range.last + 1 } ?: 0
        val nameEnd = quantityMatch.range.first
        val name = line.substring(nameStart, nameEnd)
            .trim()
            .replace(Regex("""\s{2,}"""), " ")
            .ifBlank { "Ítem sin descripción" }
        val vatPercent = Regex("""(\d{1,2}(?:[.,]\d+)?)\s*%""").find(line)?.groupValues?.get(1)?.let(::parseFlexibleNumber)

        return ItemLineParseResult.Parsed(
            ParsedProviderInvoiceItem(
                code = maybeCode,
                name = name,
                quantity = quantity,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
                vatPercent = vatPercent,
                sourceLine = line
            )
        )
    }

    private fun looksLikeSkuCode(token: String): Boolean {
        val clean = token.filter(Char::isDigit)
        return clean.length in setOf(8, 12, 13) && clean == token
    }

    private fun detectCurrency(lines: List<String>): String {
        val text = lines.joinToString(" ")
        return when {
            text.contains("US$", ignoreCase = true) || text.contains("USD", ignoreCase = true) -> "USD"
            text.contains("€") || text.contains("EUR", ignoreCase = true) -> "EUR"
            else -> "ARS"
        }
    }

    private fun findDate(line: String): String? = Regex("""\b\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}\b""").find(line)?.value

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

    private fun parseFlexibleNumber(raw: String): Double? {
        val clean = raw.replace("$", "").replace("€", "").replace("£", "").trim()
        if (clean.isBlank()) return null
        val commaCount = clean.count { it == ',' }
        val dotCount = clean.count { it == '.' }
        val normalized = when {
            commaCount > 0 && dotCount > 0 -> if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                clean.replace(".", "").replace(',', '.')
            } else {
                clean.replace(",", "")
            }
            commaCount > 0 -> clean.replace(".", "").replace(',', '.')
            else -> clean
        }
        return normalized.toDoubleOrNull()
    }
}

private data class ItemExtractionResult(
    val items: List<ParsedProviderInvoiceItem>,
    val warnings: List<String>
)

private sealed interface ItemLineParseResult {
    data class Parsed(val item: ParsedProviderInvoiceItem) : ItemLineParseResult
    data class Discarded(val warning: String) : ItemLineParseResult
}

data class ParsedProviderInvoiceDraft(
    val invoiceNumber: String?,
    val providerName: String?,
    val providerTaxId: String?,
    val issueDateMillis: Long?,
    val totalAmount: Double?,
    val currencySymbol: String,
    val items: List<ParsedProviderInvoiceItem>,
    val unparsedLines: List<String>,
    val warnings: List<String>
)

data class ParsedProviderInvoiceItem(
    val code: String?,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val vatPercent: Double?,
    val sourceLine: String
)
