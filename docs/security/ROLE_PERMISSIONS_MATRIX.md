# Role Permissions Matrix (Fuente única de verdad)

Esta matriz define el contrato de permisos por rol para **UI Android** y **backend (Cloud Functions/seguridad)**.

## Matriz de negocio

| Rol | Permisos |
|---|---|
| `admin` | Todos los permisos (`MANAGE_USERS`, `MANAGE_CLOUD_SERVICES`, `VIEW_USAGE_DASHBOARD`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT`) |
| `owner` | Todos los permisos (`MANAGE_USERS`, `MANAGE_CLOUD_SERVICES`, `VIEW_USAGE_DASHBOARD`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT`) |
| `manager` | `VIEW_USAGE_DASHBOARD`, `CASH_OPEN`, `CASH_AUDIT`, `CASH_MOVEMENT`, `CASH_CLOSE`, `VIEW_CASH_REPORT` |
| `cashier` | `CASH_OPEN`, `CASH_MOVEMENT`, `VIEW_CASH_REPORT` |
| `viewer` | Sin permisos operativos internos |

## Regla de enforcement en Firestore

- La gestión administrativa de usuarios en reglas (`/tenant_users`, `/users` administrativo, `/account_requests` administrativo) se permite solo para `owner` y `admin` mediante `isAdminForTenant(tenantId)` + `hasManageUsersRole()`.
- `manager`, `cashier` y `viewer` quedan explícitamente fuera del enforcement administrativo de Firestore.
- Claims administrativos (`isAdmin`, `isSuperAdmin`, claims de admin) mantienen su bypass administrativo global por diseño.

## Reglas de producto

1. `viewer` representa **solo cliente final** y no puede operar caja ni administración.
2. `owner`, `manager`, `cashier` son **operadores internos** y deben crearse por flujo administrativo.
3. Cualquier cambio en permisos debe actualizar primero esta matriz y luego sus adaptadores técnicos.

## Implementación actual

- Android consume esta matriz vía `RolePermissionMatrix`.
- El onboarding público de Auth crea exclusivamente `viewer` para cliente final.

## Historial de cambios relevantes

- **Breaking change (permisos):** se elimina la capacidad efectiva de `manager` para gestionar usuarios a nivel Firestore. Toda operación administrativa de usuarios requiere `owner` o `admin`.
