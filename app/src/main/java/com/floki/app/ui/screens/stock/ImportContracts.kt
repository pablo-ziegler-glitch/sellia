package com.floki.app.ui.screens.stock


/**
 * Estrategia de cómo fusionar los datos importados:
 * - Append: suma cantidades si ya existe producto; si no existe, crea.
 * - Replace: reemplaza precio y cantidad; si no existe, crea.
 */

/** Resultado del import para mostrar en UI. */
data class ImportResult(
    val inserted: Int,
    val updated: Int,
    val errors: List<String>
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun toUserMessage(fileName: String?): String = buildString {
        appendLine("Importación finalizada" + (if (fileName != null) " para \"$fileName\"" else "") + ":")
        appendLine("• Insertados: $inserted")
        appendLine("• Actualizados: $updated")
        if (errors.isNotEmpty()) {
            appendLine("• Errores (${errors.size}):")
            errors.take(5).forEach { appendLine("  - $it") }
            if (errors.size > 5) appendLine("  ... (${errors.size - 5} más)")
        }
    }.trim()

}
