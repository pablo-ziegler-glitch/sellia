package com.floki.app.data.model

/**
 * Proveedor del negocio.
 * - rubros: texto libre separado por coma (simple y práctico para primera versión).
 * - paymentTerm: forma de pago pactada.
 * - paymentMethod: medio de pago aceptado.
 */
 data class Provider(
    val id: Int = 0,
    val name: String,
    val phone: String? = null,
    val rubrosCsv: String? = null,       // p.ej. "electricidad,plomería"
    val paymentTerm: String? = null,     // p.ej. "contado", "30 días"
    val paymentMethod: String? = null    // p.ej. "transferencia", "efectivo"
)

/** Forma de pago */
enum class PaymentTerm {
    CUENTA_CORRIENTE, CONTRA_FACTURA, FACTURA_VENCIDA, OTRO
}

/** Medio de pago aceptado */
enum class PaymentMethod {
    EFECTIVO, TARJETA, TRANSFERENCIA, CHEQUE
}
