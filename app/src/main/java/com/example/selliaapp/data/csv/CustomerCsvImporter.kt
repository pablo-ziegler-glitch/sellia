package com.example.selliaapp.data.csv

import android.content.ContentResolver
import android.net.Uri
import java.time.LocalDateTime

/**
 * Importador de clientes desde archivos tabulares (CSV/Excel/Sheets).
 * Usa encabezados con alias para mapear columnas.
 */
object CustomerCsvImporter {

    data class Row(
        val name: String,
        val phone: String?,
        val email: String?,
        val address: String?,
        val nickname: String?,
        val rubrosCsv: String?,
        val paymentTerm: String?,
        val paymentMethod: String?,
        val createdAt: LocalDateTime?
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
                    name = idx.get(row, "name", listOf("nombre", "cliente", "customer")).orEmpty(),
                    phone = idx.get(row, "phone", listOf("telefono", "teléfono", "celular", "whatsapp"))?.ifBlank { null },
                    email = idx.get(row, "email", listOf("mail", "correo"))?.ifBlank { null },
                    address = idx.get(row, "address", listOf("direccion", "dirección", "domicilio"))?.ifBlank { null },
                    nickname = idx.get(row, "nickname", listOf("alias", "apodo"))?.ifBlank { null },
                    rubrosCsv = idx.get(row, "rubros", listOf("rubro", "rubros_csv", "categorias", "categorías"))?.ifBlank { null },
                    paymentTerm = idx.get(row, "payment_term", listOf("plazo_pago", "termino_pago"))?.ifBlank { null },
                    paymentMethod = idx.get(row, "payment_method", listOf("metodo_pago", "método_pago"))?.ifBlank { null },
                    createdAt = null
                )
            }
    }

    fun templateCsv(): String {
        return buildString {
            appendLine("name,phone,email,address,nickname,rubros,payment_term,payment_method")
            appendLine("Cliente Demo,1122334455,cliente@example.com,Av. Siempre Viva 123,Mayorista,Supermercado;Barrio,30 días,Transferencia")
        }
    }

    fun templateFileName(): String = "plantilla_clientes.csv"

    fun templateMimeType(): String = "application/vnd.ms-excel"
}
