# Role Permissions Matrix (Fuente única de verdad)

Esta matriz define el contrato de permisos por canal para **mobile_ops**, **backoffice web (`web_bo_admin`)** y **backend (Cloud Functions/seguridad)**.

- Versión de matriz: `2026-02-25`
- Objeto canónico compartido en código: `functions/src/security/rolePermissionsMatrix.ts`
- Adaptador frontend web: `public/admin/permissions.js`
- Adaptador frontend mobile: `app/src/main/java/com/example/selliaapp/domain/security/RolePermissionMatrix.kt`

## Catálogo de capacidades por canal

### `mobile_ops` (solo operación de alta frecuencia)

- `sales.checkout`
- `cash.open`
- `cash.audit`
- `cash.movement`
- `cash.close`
- `cash.report.read`
- `stock.adjust`
- `stock.movement.read`

### `web_bo_admin` (administración consolidada)

- `pricing.read` / `pricing.write`
- `cloud.config.read` / `cloud.config.write`
- `users.roles.read` / `users.roles.write`
- `tenant.lifecycle.read` / `tenant.lifecycle.write`
- `tenant.backups.read` / `tenant.backups.write`

## Matriz de autorización por rol y tenant (canales)

| Canal | Scope tenant | owner | admin | manager | cashier | viewer | superadmin |
|---|---|---|---|---|---|---|---|
| `mobile_ops` | `sameTenant` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `mobile_ops` | `crossTenant` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `mobile_ops` | `platform` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `web_bo_admin` | `sameTenant` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| `web_bo_admin` | `crossTenant` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `web_bo_admin` | `platform` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

## Módulos BO web y acceso por rol

| Módulo | owner | admin | manager | cashier | viewer |
|---|---|---|---|---|---|
| dashboard | ✅ | ✅ | ✅ | ❌ | ❌ |
| pricing | ✅ | ✅ | ❌ | ❌ | ❌ |
| usersRoles | ✅ | ✅ | ❌ | ❌ | ❌ |
| cloudConfig | ✅ | ✅ | ❌ | ❌ | ❌ |
| tenantLifecycle | ✅ | ✅ | ❌ | ❌ | ❌ |
| maintenanceRead | ✅ | ✅ | ❌ | ❌ | ❌ |
| maintenanceWrite | ✅ | ✅ | ❌ | ❌ | ❌ |
| backupsRead | ✅ | ✅ | ❌ | ❌ | ❌ |
| backupsWrite | ✅ | ✅ | ❌ | ❌ | ❌ |

## Rutas backoffice (mapeo de módulos)

| Ruta | Módulo |
|---|---|
| `#/dashboard` | `dashboard` |
| `#/settings/pricing` | `pricing` |
| `#/settings/users` | `usersRoles` |
| `#/settings/cloud-services` | `cloudConfig` |
| `#/settings/tenant-lifecycle` | `tenantLifecycle` |
| `#/maintenance` | `maintenanceWrite` |

## Regla de enforcement en Firestore

- La gestión administrativa de usuarios en reglas (`/tenant_users`, `/users` administrativo, `/account_requests` administrativo) se permite solo para `owner` y `admin` mediante `isAdminForTenant(tenantId)` + `hasManageUsersRole()`.
- `manager`, `cashier` y `viewer` quedan explícitamente fuera del enforcement administrativo de Firestore.
- Claims administrativos (`isAdmin`, `isSuperAdmin`, claims de admin) mantienen bypass administrativo global por diseño.

## Reglas de producto

1. `viewer` representa **solo cliente final** y no puede operar caja ni administración.
2. El canal mobile no expone capacidades de administración de cloud, usuarios/roles, lifecycle de tenant ni backups.
3. Cualquier cambio en permisos debe actualizar primero `functions/src/security/rolePermissionsMatrix.ts` y luego sus adaptadores (`public/admin/permissions.js`, mobile y docs).

## Historial de cambios relevantes

- **Breaking change (canal):** `mobile_ops` queda restringido a operación de ventas/caja/stock.
- **Breaking change (BO web):** consolidación explícita de módulos administrativos en pricing, cloud config, users/roles, tenant lifecycle y backups.
