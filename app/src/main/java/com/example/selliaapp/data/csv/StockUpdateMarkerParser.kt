package com.example.selliaapp.data.csv

object StockUpdateMarkerParser {
    data class ParseResult(
        val requested: Boolean,
        val valid: Boolean,
        val normalizedValue: String?
    )

    private val trueValues = setOf("1", "true", "si", "sí", "x", "update", "stock")
    private val falseValues = setOf("0", "false", "no", "")

    fun parse(raw: String?): ParseResult {
        val normalized = raw?.trim()?.lowercase()
        if (normalized == null) return ParseResult(requested = false, valid = true, normalizedValue = null)
        if (normalized in trueValues) return ParseResult(requested = true, valid = true, normalizedValue = normalized)
        if (normalized in falseValues) return ParseResult(requested = false, valid = true, normalizedValue = normalized)
        return ParseResult(requested = false, valid = false, normalizedValue = normalized)
    }
}
