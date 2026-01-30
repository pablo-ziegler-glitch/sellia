# Esquema Firestore: órdenes y pagos

Este esquema define el modelo de **órdenes** y **pagos** para el flujo de checkout/pagos con Mercado Pago, optimizado para costos y seguridad.

> **Nota:** se asume multi-tenant. Las colecciones viven bajo `tenants/{tenantId}` para evitar lecturas cruzadas.

## 1) Órdenes

**Ruta:** `tenants/{tenantId}/orders/{orderId}`

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `status` | string | sí | Estado de la orden (p.ej. `pending`, `paid`, `cancelled`). |
| `amount` | number (int) | sí | Monto en **centavos** (evita errores de coma flotante). |
| `currency` | string | sí | Moneda ISO-4217 (p.ej. `ARS`). |
| `createdAt` | timestamp | sí | Fecha de creación (serverTimestamp). |
| `updatedAt` | timestamp | sí | Fecha de última actualización (serverTimestamp). |
| `paymentId` | string | no | Referencia a `payments/{paymentId}`. |

## 2) Pagos

**Ruta:** `tenants/{tenantId}/payments/{paymentId}`

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `orderId` | string | sí | ID de la orden asociada. |
| `provider` | string | sí | Proveedor de pagos. **Valor fijo:** `mercado_pago`. |
| `status` | string | sí | `pending` / `approved` / `rejected` / `failed`. |
| `raw` | map | sí | Resumen del payload recibido del provider (solo campos necesarios). |
| `createdAt` | timestamp | sí | Fecha de creación (serverTimestamp). |
| `updatedAt` | timestamp | sí | Fecha de última actualización (serverTimestamp). |

## 3) Seguridad (resumen)

- **Cliente:** solo lectura de `orders`.
- **Backend:** escritura de `orders` y `payments`, y lectura de `payments`.

Esto evita que el cliente pueda alterar el estado del pago y reduce riesgos de fraude.
