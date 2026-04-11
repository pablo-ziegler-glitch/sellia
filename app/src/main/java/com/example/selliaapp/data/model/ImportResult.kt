package com.example.selliaapp.data.model

data class ImportRowIssue(
    val line: Int,
    val productName: String?,
    val skuOrBarcode: String?,
    val attemptedAction: String,
    val technicalReason: String,
    val userMessage: String,
    val suggestion: String,
    val status: String = "rechazado"
)

/**
 * Resultado de importación CSV de productos.
 * Usado por repo y UI para evitar duplicar tipos.
 */
data class ImportResult(
    val inserted: Int,
    val updated: Int,
    val errors: List<String> = emptyList(),
    val totalProcessed: Int = inserted + updated + errors.size,
    val totalCreated: Int = inserted,
    val totalStockUpdated: Int = updated,
    val totalRejected: Int = errors.size,
    val totalValidationErrors: Int = 0,
    val rowIssues: List<ImportRowIssue> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /** Mensaje resumido para la UI */
    fun toUserMessage(fileName: String?): String {
        val base = buildString {
            append("Archivo: ${fileName ?: "—"}\n")
            append("Procesadas: $totalProcessed  •  Creadas: $totalCreated  •  Stock actualizado: $totalStockUpdated")
            append("\nRechazadas: $totalRejected  •  Errores de validación: $totalValidationErrors")
        }
        return if (errors.isEmpty()) base
        else base + "\n" + "Errores: ${errors.size}"
    }
}
