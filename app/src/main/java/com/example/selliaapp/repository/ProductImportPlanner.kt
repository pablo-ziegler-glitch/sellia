package com.example.selliaapp.repository

import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportRowIssue
import java.text.Normalizer

object ProductImportPlanner {

    sealed interface Action {
        data class Create(val row: ProductCsvImporter.Row) : Action
        data class UpdateStock(val row: ProductCsvImporter.Row, val existing: ProductEntity) : Action
    }

    data class Plan(
        val actions: List<Action>,
        val issues: List<ImportRowIssue>,
        val totalProcessed: Int,
        val totalCreated: Int,
        val totalStockUpdated: Int,
        val totalRejected: Int,
        val totalValidationErrors: Int
    )

    private data class ExistingIndexes(
        val byCode: Map<String, List<ProductEntity>>,
        val byBarcode: Map<String, List<ProductEntity>>,
        val byComposite: Map<String, List<ProductEntity>>,
        val byFallbackName: Map<String, List<ProductEntity>>
    )

    private data class MatchResult(
        val product: ProductEntity?,
        val conflict: String?
    )

    fun plan(
        rows: List<ProductCsvImporter.Row>,
        existingProducts: List<ProductEntity>
    ): Plan {
        val indexes = buildIndexes(existingProducts)
        val seenInFile = mutableSetOf<String>()
        val actions = mutableListOf<Action>()
        val issues = mutableListOf<ImportRowIssue>()
        var validationErrors = 0
        var created = 0
        var updated = 0

        rows.forEach { row ->
            val name = row.name.trim()
            val skuOrBarcode = row.code?.trim()?.ifBlank { null } ?: row.barcode?.trim()?.ifBlank { null }
            val requestedAction = if (row.updateStockRequested) "actualizar stock" else "crear"

            if (name.isBlank()) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "missing_required_name",
                    userMessage = "Falta el nombre del producto.",
                    suggestion = "Completá la columna name/nombre antes de reintentar."
                )
                validationErrors += 1
                return@forEach
            }

            if (row.hasInvalidQuantity || row.quantity == null) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "invalid_stock_value",
                    userMessage = "El stock es inválido.",
                    suggestion = "Ingresá quantity como número entero (0 o mayor)."
                )
                validationErrors += 1
                return@forEach
            }
            if (row.quantity < 0) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "negative_stock_value",
                    userMessage = "El stock no puede ser negativo.",
                    suggestion = "Usá un valor de stock mayor o igual a 0."
                )
                validationErrors += 1
                return@forEach
            }

            val barcode = row.barcode?.trim()?.ifBlank { null }
            if (barcode != null && !barcode.matches(Regex("^\\d{8,14}$"))) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "invalid_barcode_format",
                    userMessage = "El código de barras es inválido.",
                    suggestion = "Usá un barcode numérico de 8 a 14 dígitos o dejalo vacío."
                )
                validationErrors += 1
                return@forEach
            }

            val rowKeys = buildRowKeys(row)
            if (rowKeys.allKeys.any { !seenInFile.add(it) }) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "duplicate_inside_file",
                    userMessage = "El producto está duplicado dentro del mismo archivo.",
                    suggestion = "Eliminá o consolidá filas duplicadas antes de importar."
                )
                return@forEach
            }

            val match = findExisting(rowKeys, indexes)
            if (match.conflict != null) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = requestedAction,
                    technicalReason = "uniqueness_conflict",
                    userMessage = "Conflicto de unicidad: la fila coincide con más de un producto.",
                    suggestion = "Completá código/barcode único y corregí duplicados del catálogo.",
                    extra = match.conflict
                )
                return@forEach
            }

            if (match.product == null) {
                actions += Action.Create(row)
                created += 1
                return@forEach
            }

            if (!row.updateStockMarkerValid) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = "actualizar stock",
                    technicalReason = "existing_product_invalid_update_marker",
                    userMessage = "El producto ya existe y la marca actualizar_stock es inválida.",
                    suggestion = "Usá actualizar_stock con 1/true/si/sí/x/update/stock para actualizar existencias."
                )
                return@forEach
            }

            if (!row.updateStockRequested) {
                issues += buildIssue(
                    row = row,
                    skuOrBarcode = skuOrBarcode,
                    attemptedAction = "crear",
                    technicalReason = "existing_product_without_update_marker",
                    userMessage = "El producto ya existe.",
                    suggestion = "Si querés modificar existencias, completá actualizar_stock."
                )
                return@forEach
            }

            actions += Action.UpdateStock(row, match.product)
            updated += 1
        }

        return Plan(
            actions = actions,
            issues = issues,
            totalProcessed = rows.size,
            totalCreated = created,
            totalStockUpdated = updated,
            totalRejected = issues.size,
            totalValidationErrors = validationErrors
        )
    }

    private fun buildIssue(
        row: ProductCsvImporter.Row,
        skuOrBarcode: String?,
        attemptedAction: String,
        technicalReason: String,
        userMessage: String,
        suggestion: String,
        extra: String? = null
    ): ImportRowIssue {
        val detail = if (extra.isNullOrBlank()) technicalReason else "$technicalReason ($extra)"
        return ImportRowIssue(
            line = row.lineNumber,
            productName = row.name.ifBlank { null },
            skuOrBarcode = skuOrBarcode,
            attemptedAction = attemptedAction,
            technicalReason = detail,
            userMessage = userMessage,
            suggestion = suggestion
        )
    }

    private fun findExisting(rowKeys: RowKeys, indexes: ExistingIndexes): MatchResult {
        val byCode = rowKeys.codeKey?.let { indexes.byCode[it].orEmpty() } ?: emptyList()
        if (byCode.size > 1) return MatchResult(null, "multiple_products_for_code")
        if (byCode.size == 1) {
            val codeMatch = byCode.first()
            val barcodeMatch = rowKeys.barcodeKey?.let { indexes.byBarcode[it].orEmpty() } ?: emptyList()
            if (barcodeMatch.size > 1) return MatchResult(null, "multiple_products_for_barcode")
            if (barcodeMatch.size == 1 && barcodeMatch.first().id != codeMatch.id) {
                return MatchResult(null, "code_and_barcode_match_different_products")
            }
            return MatchResult(codeMatch, null)
        }

        val byBarcode = rowKeys.barcodeKey?.let { indexes.byBarcode[it].orEmpty() } ?: emptyList()
        if (byBarcode.size > 1) return MatchResult(null, "multiple_products_for_barcode")
        if (byBarcode.size == 1) return MatchResult(byBarcode.first(), null)

        val byComposite = rowKeys.compositeKey?.let { indexes.byComposite[it].orEmpty() } ?: emptyList()
        if (byComposite.size > 1) return MatchResult(null, "multiple_products_for_composite_key")
        if (byComposite.size == 1) return MatchResult(byComposite.first(), null)

        val byFallbackName = rowKeys.fallbackNameKey?.let { indexes.byFallbackName[it].orEmpty() } ?: emptyList()
        if (byFallbackName.size > 1) return MatchResult(null, "multiple_products_for_name_fallback")
        if (byFallbackName.size == 1) return MatchResult(byFallbackName.first(), null)

        return MatchResult(null, null)
    }

    private fun buildIndexes(products: List<ProductEntity>): ExistingIndexes {
        val byCode = products
            .mapNotNull { product -> product.code?.normalizeIdKey()?.let { it to product } }
            .groupBy({ it.first }, { it.second })
        val byBarcode = products
            .mapNotNull { product -> product.barcode?.normalizeIdKey()?.let { it to product } }
            .groupBy({ it.first }, { it.second })
        val byComposite = products
            .mapNotNull { product -> buildCompositeKey(product)?.let { it to product } }
            .groupBy({ it.first }, { it.second })
        val byFallbackName = products
            .mapNotNull { product -> normalizeText(product.name)?.let { it to product } }
            .groupBy({ it.first }, { it.second })
        return ExistingIndexes(
            byCode = byCode,
            byBarcode = byBarcode,
            byComposite = byComposite,
            byFallbackName = byFallbackName
        )
    }

    private data class RowKeys(
        val codeKey: String?,
        val barcodeKey: String?,
        val compositeKey: String?,
        val fallbackNameKey: String?
    ) {
        val allKeys: List<String>
            get() = listOfNotNull(
                codeKey?.let { "code:$it" },
                barcodeKey?.let { "barcode:$it" },
                compositeKey?.let { "composite:$it" },
                fallbackNameKey?.let { "name:$it" }
            )
    }

    private fun buildRowKeys(row: ProductCsvImporter.Row): RowKeys {
        val code = row.code?.normalizeIdKey()
        val barcode = row.barcode?.normalizeIdKey()
        val composite = buildCompositeKey(row)
        val fallback = normalizeText(row.name)
        return RowKeys(codeKey = code, barcodeKey = barcode, compositeKey = composite, fallbackNameKey = fallback)
    }

    private fun buildCompositeKey(row: ProductCsvImporter.Row): String? {
        val name = normalizeText(row.name) ?: return null
        return listOf(
            name,
            normalizeText(row.brand),
            normalizeText(row.parentCategory),
            normalizeText(row.category),
            normalizeText(row.color),
            normalizeText(row.providerSku),
            normalizeText(row.sizes.sorted().joinToString("|"))
        ).joinToString("|")
    }

    private fun buildCompositeKey(product: ProductEntity): String? {
        val name = normalizeText(product.name) ?: return null
        return listOf(
            name,
            normalizeText(product.brand),
            normalizeText(product.parentCategory),
            normalizeText(product.category),
            normalizeText(product.color),
            normalizeText(product.providerSku),
            normalizeText(product.sizes.sorted().joinToString("|"))
        ).joinToString("|")
    }

    private fun String.normalizeIdKey(): String? = trim().takeIf { it.isNotBlank() }?.lowercase()

    private fun normalizeText(raw: String?): String? {
        val base = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val noAccents = Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noAccents
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
