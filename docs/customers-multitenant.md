# Clientes finales en Firestore (1 tienda o N tiendas)

## Estado actual del proyecto

Hoy el proyecto **sí tiene clientes en Room local** (`customers`) para operación en Android, pero **no hay una colección cloud dedicada para clientes finales compartidos entre múltiples tiendas** en Firestore.

- En reglas actuales, todo lo operativo está centrado en `tenants/{tenantId}/...`.
- Esto soporta clientes por tienda (aislados por tenant), pero no un "cliente maestro" asignable a N tiendas.

## ¿Está permitido asignar clientes a 1 o N tiendas?

**Actualmente no está modelado explícitamente en Firestore** en este repo.

Para producción escalable, conviene separar:

1. **Perfil global de cliente** (identidad única).
2. **Relación cliente↔tienda** (membresía por tenant, con metadatos comerciales).

## Modelo recomendado (escalable y de bajo costo)

### 1) Perfil global

**Ruta:** `customer_profiles/{customerId}`

Campos sugeridos:

- `id: string`
- `fullName: string`
- `email: string|null`
- `phoneE164: string|null`
- `documentNumber: string|null`
- `createdAt: timestamp`
- `updatedAt: timestamp`

> Este documento **no** guarda datos sensibles de negocio por tienda (saldo, descuentos, crédito), solo identidad base.

### 2) Relación por tienda (N a N)

**Ruta:** `tenants/{tenantId}/customers/{customerId}`

Campos sugeridos:

- `customerId: string` (referencia lógica a `customer_profiles/{customerId}`)
- `tenantId: string`
- `displayName: string` (override opcional por tienda)
- `tags: string[]`
- `creditLimitCents: number`
- `paymentTermsDays: number`
- `isActive: boolean`
- `createdAt: timestamp`
- `updatedAt: timestamp`

Con esto, el mismo `customerId` puede existir en múltiples tiendas sin duplicar identidad global.

## Reglas de seguridad recomendadas

- `customer_profiles`: lectura/escritura solo por backend/admin (o por reglas estrictas según ownership).
- `tenants/{tenantId}/customers`: lectura/escritura para usuarios autenticados del tenant (`isTenantUser(tenantId)`), con operaciones administrativas según rol.

## Índices sugeridos

En `tenants/{tenantId}/customers`:

- `isActive ASC, displayName ASC`
- `updatedAt DESC`
- `tags ARRAY_CONTAINS, updatedAt DESC` (si se filtra por etiqueta)

## Impacto de negocio

- Permite CRM multi-sucursal real sin romper aislamiento por tenant.
- Reduce inconsistencias de datos de clientes repetidos.
- Mejora costo Firestore al consultar por subcolección tenant en vez de escanear global.

## Cambio aplicado: onboarding explícito para clientes públicos

Se eliminó la creación silenciosa de tenants `Cliente público` por UUID desde login Google.

Nuevo comportamiento en `AuthManager.ensurePublicCustomerSession`:

1. Si el usuario ya tiene `tenantId/storeId`, entra normal.
2. Si **no** tiene tenant:
   - usa `GLOBAL_PUBLIC_CUSTOMER_TENANT_ID` (BuildConfig) **solo si está configurado** y existe en `tenants/{id}`.
   - caso contrario, deja la sesión en estado parcial y exige seleccionar tienda para completar onboarding.

Esto evita generar tenants huérfanos y reduce costo/ruido operativo en Firestore.

### Operación recomendada en producción

- Definir `GLOBAL_PUBLIC_CUSTOMER_TENANT_ID` solo si querés un tenant público global controlado.
- Si no, forzar selección de tienda desde onboarding (flujo por catálogo público).

### Limpieza de tenants huérfanos históricos

Se agregó script administrativo:

- `npm run cleanup:public-customers:dry`
- `npm run cleanup:public-customers:apply`

Ubicación: `functions/scripts/cleanup-orphan-public-customers.js`.
