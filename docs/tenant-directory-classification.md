# Clasificación de campos: `tenant_directory` (interno) vs `public_tenant_directory` (público)

## Revisión actual de campos en `tenant_directory`

Según los puntos de escritura y lectura vigentes en la app:

- `id`
- `name`
- `ownerUid`
- `skuPrefix`
- `createdAt`
- `publicStoreUrl`
- `publicDomain`
- `updatedAt`
- `storeLogoUrl` (si existe por merge de configuración de marketing)

## Clasificación recomendada

### Campos internos (NO públicos)

Estos campos exponen datos operativos o de administración y deben permanecer en `tenant_directory`:

- `ownerUid` (dato sensible de relación propietario-tenant)
- `skuPrefix` (dato operativo de generación de SKU)
- `createdAt` / `updatedAt` para auditoría interna detallada
- Cualquier metadato de backoffice (roles, flags, trazabilidad)

### Campos públicos mínimos (discovery web)

Colección objetivo: `public_tenant_directory/{tenantId}`

- `id`
- `name`
- `publicStoreUrl`
- `publicDomain`
- `storeLogoUrl` (opcional para branding público)
- `updatedAt` (opcional para invalidación cache)

Este subset minimiza superficie de exposición, reduce riesgo de leakage y mantiene costo bajo de lectura para frontend público.
