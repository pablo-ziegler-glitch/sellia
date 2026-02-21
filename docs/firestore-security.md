# Reglas de seguridad Firestore (multi-tenant)

## Política vigente para gestión de usuarios

La política final de negocio queda definida así:

- **Solo `owner` y `admin`** pueden gestionar usuarios de su tenant.
- `manager`, `cashier` y `viewer` **no** pueden hacer escrituras administrativas.
- `isAdmin` / `isSuperAdmin` y claims administrativos siguen habilitando acceso administrativo global.

En `firestore.rules`, esto se implementa removiendo `manager` de `hasManageUsersRole()` y centralizando las validaciones por tenant en `isAdminForTenant(tenantId)`.

## Matriz de permisos (usuarios autenticados del mismo tenant)

| Rol | `/tenant_users` create/update/delete | `/users` create/update/delete (administrativo) | `/account_requests` update/delete |
|---|---|---|---|
| `owner` | ✅ Permitido | ✅ Permitido | ✅ Permitido |
| `admin` | ✅ Permitido | ✅ Permitido | ✅ Permitido |
| `manager` | ❌ Denegado | ❌ Denegado | ❌ Denegado |
| `cashier` | ❌ Denegado | ❌ Denegado | ❌ Denegado |
| `viewer` | ❌ Denegado | ❌ Denegado | ❌ Denegado |

> Nota: existen excepciones explícitas de autoservicio en `users` y `account_requests` (ej. bootstrap de owner, alta de final customer, o lectura/escritura sobre su propio request), que no constituyen gestión administrativa de usuarios.

## Endpoints revisados con `isAdminForTenant`

Se revisaron y dejaron consistentes los `match` administrativos que dependen de `isAdminForTenant(tenantId)`:

- `match /tenant_users/{tenantUserId}`
- `match /users/{userId}` (paths administrativos)
- `match /account_requests/{requestId}`

Con esta consolidación, cualquier cambio futuro de política por rol debe tocar una sola fuente de verdad (`hasManageUsersRole`).

## Validación con Emulator

La validación objetivo para Emulator contempla:

- `owner` / `admin` → permitidos para gestión de usuarios.
- `manager` / `cashier` / `viewer` → denegados en escrituras administrativas de `tenant_users` y `users`.

## Política de mantenimiento (`maintenance_tasks`)

- Lectura permitida para `owner`/`admin` del tenant o usuarios con `MAINTENANCE_READ`.
- Escritura (create/update/delete) permitida para `owner`/`admin` del tenant o usuarios con `MAINTENANCE_WRITE`.
- Se valida integridad mínima de documento: `tenantId`, `title`, `status`, `priority`, timestamps y trazabilidad (`trace`).
