package com.example.selliaapp.data.csv

import android.content.ContentResolver
import android.net.Uri

/**
 * Importador de usuarios desde archivos tabulares (CSV/Excel/Sheets).
 */
object UserCsvImporter {

    data class Row(
        val name: String,
        val email: String,
        val role: String
    )

    fun parseFile(resolver: ContentResolver, uri: Uri): List<Row> =
        parseTable(TabularFileReader.readAll(resolver, uri))

    fun parseTable(table: List<List<String>>): List<Row> {
        if (table.isEmpty()) return emptyList()
        val header = table.first()
        val idx = CsvUtils.HeaderIndex(header)
        return CsvUtils.dataRowsUntilFirstBlank(table)
            .mapNotNull { row ->
                val name = idx.get(row, "name", listOf("nombre", "usuario"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val email = idx.get(row, "email", listOf("mail", "correo"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val role = idx.get(row, "role", listOf("rol", "perfil"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Row(
                    name = name,
                    email = email,
                    role = role
                )
            }
    }

    fun templateCsv(): String {
        return buildString {
            appendLine("name,email,role")
            appendLine("Usuario Admin,admin@example.com,admin")
            appendLine("Usuario Admin 2,admin2@example.com,admin")
        }
    }

    fun templateFileName(): String = "plantilla_usuarios.csv"

    fun templateMimeType(): String = "text/csv"
}
