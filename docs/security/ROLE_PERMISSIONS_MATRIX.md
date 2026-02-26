# Role Permissions Matrix (Fuente única de verdad)

Esta matriz define el contrato de permisos por rol para **backoffice web**, **UI Android** y **backend (Cloud Functions/seguridad)**.

> Fuente canónica única: `functions/src/security/rolePermissionsMatrix.ts`.
> `public/admin/permissions.js` y esta documentación son proyecciones sincronizadas y validadas en CI.

- Versión de matriz: `2026-02-24`
- Objeto canónico compartido en código: `functions/src/security/rolePermissionsMatrix.ts`

## Matriz de negocio (permisos operativos)

| Rol | Permisos |
|---|---|
| `admin` | Todos los permisos (`MANAGE_USERS`, `MANAGE_CLOUD_SERVICES`, `VIEW_USAGE_DASHBOARD`, `REQUEST_TENANT_BACKUP`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT`) |
| `owner` | Todos los permisos (`MANAGE_USERS`, `MANAGE_CLOUD_SERVICES`, `VIEW_USAGE_DASHBOARD`, `REQUEST_TENANT_BACKUP`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT`) |
| `manager` | `VIEW_USAGE_DASHBOARD`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT` |
| `cashier` | `CASH_OPEN`, `CASH_MOVEMENT`, `VIEW_CASH_REPORT` |
| `viewer` | Sin permisos operativos internos |

## Módulos y acceso por rol

| Módulo | owner | admin | manager | cashier | viewer |
|---|---|---|---|---|---|
| dashboard | ✅ | ✅ | ✅ | ❌ | ❌ |
| pricing | ✅ | ✅ | ❌ | ❌ | ❌ |
| marketing | ✅ | ✅ | ✅ | ❌ | ❌ |
| users | ✅ | ✅ | ❌ | ❌ | ❌ |
| cloudServices | ✅ | ✅ | ❌ | ❌ | ❌ |
| maintenanceRead | ✅ | ✅ | ❌* | ❌ | ❌ |
| maintenanceWrite | ✅ | ✅ | ❌* | ❌ | ❌ |
| backupsRead | ✅ | ✅ | ❌ | ❌ | ❌ |
| backupsWrite | ✅ | ✅ | ❌ | ❌ | ❌ |

`*` `manager` puede habilitarse a futuro con permiso explícito en `users.permissions`, pero no está habilitado por defecto en matriz.

## Rutas backoffice (mapeo de módulos)

| Ruta | Módulo |
|---|---|
| `#/dashboard` | `dashboard` |
| `#/settings/pricing` | `pricing` |
| `#/settings/marketing` | `marketing` |
| `#/settings/users` | `users` |
| `#/settings/cloud-services` | `cloudServices` |
| `#/maintenance` | `maintenanceWrite` |

## Regla de enforcement en Firestore

- La gestión administrativa de usuarios en reglas (`/tenant_users`, `/users` administrativo, `/account_requests` administrativo) se permite solo para `owner` y `admin` mediante `isAdminForTenant(tenantId)` + `hasManageUsersRole()`.
- `manager`, `cashier` y `viewer` quedan explícitamente fuera del enforcement administrativo de Firestore.
- Claims administrativos (`isAdmin`, `isSuperAdmin`, claims de admin) mantienen su bypass administrativo global por diseño.

## Reglas de producto

1. `viewer` representa **solo cliente final** y no puede operar caja ni administración.
2. `owner`, `manager`, `cashier` son **operadores internos** y deben crearse por flujo administrativo.
3. Cualquier cambio en permisos debe actualizar primero esta matriz y luego sus adaptadores técnicos.

## Historial de cambios relevantes

- **Breaking change (permisos):** `manager` no tiene acceso por defecto a mantenimiento ni gestión administrativa de usuarios a nivel Firestore. Toda operación administrativa de usuarios requiere `owner` o `admin`.
