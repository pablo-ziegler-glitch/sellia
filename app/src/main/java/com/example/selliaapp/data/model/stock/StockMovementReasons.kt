package com.example.selliaapp.data.model.stock

/**
 * Catálogo de códigos de movimientos de stock para uso consistente entre capas.
 */
object StockMovementReasons {
    const val PRODUCT_CREATE = "PRODUCT_CREATE"
    const val PRODUCT_UPDATE = "PRODUCT_UPDATE"
    const val MANUAL_ADJUST = "MANUAL_ADJUST"
    const val INVENTORY_COUNT = "INVENTORY_COUNT"
    const val DAMAGE = "DAMAGE"
    const val MANUAL_RECEIVE = "MANUAL_RECEIVE"
    const val CSV_IMPORT = "CSV_IMPORT"
    const val QUICK_ORDER_RECEIVED = "QUICK_ORDER_RECEIVED"
    const val SCAN_ADJUST = "SCAN_ADJUST"
    const val SALE = "SALE"
    const val SALE_CANCEL = "SALE_CANCEL"

    /**
     * Devuelve una etiqueta legible para mostrar en la UI.
     */
    fun humanReadable(code: String): String = when (code) {
        PRODUCT_CREATE -> "Alta de producto"
        PRODUCT_UPDATE -> "Edición de producto"
        MANUAL_ADJUST -> "Ajuste manual"
        INVENTORY_COUNT -> "Reconteo de inventario"
        DAMAGE -> "Producto dañado"
        MANUAL_RECEIVE -> "Mercadería recibida"
        CSV_IMPORT -> "Importación CSV"
        QUICK_ORDER_RECEIVED -> "Orden rápida recibida"
        SCAN_ADJUST -> "Ajuste por escaneo"
        SALE -> "Venta"
        SALE_CANCEL -> "Anulación de venta"
        else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Razones disponibles para ajustes manuales desde la UI.
 */
enum class StockAdjustmentReason(val code: String, val label: String) {
    INVENTORY(StockMovementReasons.INVENTORY_COUNT, "Reconteo de inventario"),
    DAMAGE(StockMovementReasons.DAMAGE, "Producto dañado"),
    RECEIVED(StockMovementReasons.MANUAL_RECEIVE, "Mercadería recibida"),
    OTHER(StockMovementReasons.MANUAL_ADJUST, "Ajuste manual");
}
