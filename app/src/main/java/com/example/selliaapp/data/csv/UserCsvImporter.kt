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
        return table.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row ->
                Row(
                    name = idx.get(row, "name", listOf("nombre", "usuario")).orEmpty(),
                    email = idx.get(row, "email", listOf("mail", "correo")).orEmpty(),
                    role = idx.get(row, "role", listOf("rol", "perfil")).orEmpty()
                )
            }
    }

    fun templateCsv(): String {
        return buildString {
            appendLine("name,email,role")
            appendLine("Usuario Demo,usuario@example.com,admin")
            appendLine("Super Admin,sadmin@example.com,super_admin")
            appendLine("Owner Demo,owner@example.com,owner")
        }
    }

    fun templateFileName(): String = "plantilla_usuarios.csv"

    fun templateMimeType(): String = "text/csv"
}
