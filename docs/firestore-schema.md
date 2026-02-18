# Esquema Firestore: órdenes, pagos y catálogo CROSS de códigos de barras

Este esquema define el modelo de **órdenes** y **pagos** para el flujo de checkout/pagos con Mercado Pago, optimizado para costos y seguridad, y agrega un **catálogo CROSS** global para acelerar altas de stock por barcode.

> **Nota:** se asume multi-tenant. Las colecciones de negocio viven bajo `tenants/{tenantId}` para evitar lecturas cruzadas. El catálogo CROSS vive en una colección raíz porque es compartido por todas las tiendas.

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

## 3) Catálogo CROSS de barcodes

**Ruta:** `cross_catalog/{barcode}`

- `barcode` se usa como `documentId` (clave natural para lookup O(1)).
- Esta colección la alimentan importaciones administradas (no edición manual de operador).
- **Carga de catálogo maestro:** sólo se sincroniza desde procesos admin/backend cuando la carga se hace por archivo **CSV o XLSX**.
- Las tiendas sólo leen para autocompletar `name` y `brand` cuando el producto no existe localmente.

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `barcode` | string | sí | Código de barras normalizado (también coincide con el docId). |
| `name` | string | sí | Nombre de referencia compartido. |
| `brand` | string | no | Marca de referencia compartida. |
| `createdAt` | timestamp | sí | Fecha de alta inicial del barcode. |
| `updatedAt` | timestamp | sí | Última actualización de datos compartidos. |
| `createdBy` | map | sí | Auditoría inicial: `uid`, `email`, `tenantId`, `storeName`. |
| `updatedBy` | map | sí | Último actor que modificó la línea: `uid`, `email`, `tenantId`, `storeName`. |

## 4) Seguridad (resumen)

- **Cliente:**
  - Lectura de `orders` dentro de su tenant.
  - Lectura autenticada de `cross_catalog` para lookup de barcode.
- **Backend / administradores:**
  - Escritura de `orders` y `payments` según regla existente.
  - Escritura de `cross_catalog` restringida a backend o usuarios admin (no operadores estándar).

Esto evita que usuarios no autenticados lean el catálogo CROSS y mantiene trazabilidad de cambios.


## 5) Clientes finales (multi-tienda)

Ver propuesta completa en `docs/customers-multitenant.md`.

- **Implementado ahora (operacional):** persistencia accionable de clientes en `tenants/{tenantId}/customers/{customerId}` (alta/edición/baja con fallback outbox).
- **Siguiente paso recomendado:** sumar `customer_profiles/{customerId}` para identidad global compartida entre N tiendas.
