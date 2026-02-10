// File: app/src/main/java/com/example/selliaapp/data/csv/CsvUtils.kt
package com.example.selliaapp.data.csv


import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Parser CSV simple pero robusto:
 * - Soporta comillas dobles, comas dentro de campos y comillas escapadas "".
 * - Ignora BOM UTF-8.
 * - Permite separador configurable (por defecto ',').
 */
object CsvUtils {

    data class ParseConfig(
        val separator: Char = ',',
        val charset: Charset = Charsets.UTF_8,
        val trimCells: Boolean = true,
        val skipEmptyLines: Boolean = true,
    )

    fun readAll(input: InputStream, config: ParseConfig = ParseConfig()): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(input, config.charset))
        val out = mutableListOf<List<String>>()

        var line = reader.readLine()?.removeBom()
        val sep = config.separator

        while (line != null) {
            if (config.skipEmptyLines && line.isBlank()) {
                line = reader.readLine()
                continue
            }
            val row = parseLine(line, sep)
            out += if (config.trimCells) row.map { it.trim() } else row
            line = reader.readLine()
        }
        return out
    }

    private fun parseLine(line: String, sep: Char): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // comillas escapadas -> "
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == sep && !inQuotes -> {
                    cells += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }

    private fun String.removeBom(): String {
        // BOM UTF-8: 0xEF,0xBB,0xBF
        return if (isNotEmpty() && this[0] == '\uFEFF') substring(1) else this
    }



    /**
     * Devuelve filas de datos (sin encabezado) hasta la primera fila completamente vacía.
     * Se usa para cortar importaciones cuando el usuario deja líneas en blanco al final.
     */
    fun dataRowsUntilFirstBlank(table: List<List<String>>): List<List<String>> {
        if (table.size <= 1) return emptyList()
        val rows = mutableListOf<List<String>>()
        for (row in table.drop(1)) {
            if (row.isEmpty() || row.all { it.isBlank() }) break
            rows += row
        }
        return rows
    }

    /** Utilidad para resolver encabezados con alias. */
    class HeaderIndex(private val header: List<String>) {
        private fun idxOf(name: String, aliases: List<String>): Int? {
            val all = (listOf(name) + aliases).map { it.lowercase() }
            header.forEachIndexed { i, h ->
                if (all.contains(h.lowercase())) return i
            }
            return null
        }
        fun get(row: List<String>, name: String, aliases: List<String> = emptyList()): String? {
            val i = idxOf(name, aliases) ?: return null
            return if (i in row.indices) row[i] else null
        }
    }
}