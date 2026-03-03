package com.floki.app.data.model

/**
 * Resultado de importación CSV de productos.
 * Usado por repo y UI para evitar duplicar tipos.
 */
data class ImportResult(
    val inserted: Int,
    val updated: Int,
    val errors: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /** Mensaje resumido para la UI */
    fun toUserMessage(fileName: String?): String {
        val base = buildString {
            append("Archivo: ${fileName ?: "—"}\n")
            append("Insertados: $inserted  •  Actualizados: $updated")
        }
        return if (errors.isEmpty()) base
        else base + "\n" + "Errores: ${errors.size}"
    }
}