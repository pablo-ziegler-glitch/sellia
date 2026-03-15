# Role Permissions Matrix (Fuente única de verdad)

Versión de matriz: `2026-03-13`
Objeto canónico: `functions/src/security/rolePermissionsMatrix.ts`
Adaptador frontend web: `public/admin/permissions.js`
Adaptador frontend mobile: `app/src/main/java/com/example/selliaapp/domain/security/RolePermissionMatrix.kt`

---

## Los 3 roles canónicos del sistema

### 1. `viewer` — Cliente Final

El cliente final puede **navegar tiendas** pero no tiene acceso a ninguna función de gestión.

**Puede:**
- Ver el catálogo público de productos de cualquier tienda
- Ver el cartel de novedades / banners de las tiendas
- Ver la información pública de una tienda (nombre, horario, contacto)

**No puede:**
- Operar caja, stock, ventas ni ninguna función de gestión
- Acceder al backoffice de ninguna tienda
- Ver datos privados de ninguna tienda

**accountType:** `final_customer` | **Claims JWT:** sin `admin`, sin `isAdmin`

---

### 2. `owner` — Dueño de Tienda

El dueño tiene **dos vistas**:

**Vista cliente** (igual que `viewer`): puede navegar catálogos y novedades de cualquier tienda, incluyendo la propia.

**Vista de gestión de tienda** (solo su propio tenant):

| Funcionalidad | Descripción |
|---|---|
| Gestión de stock | ABM de productos, categorías, ajustes de stock, movimientos |
| Reportes | Reportes de ventas, stock, caja y estadísticas de la tienda |
| Ventas (POS) | Historial de ventas, notas de crédito, devoluciones |
| Gestión de clientes | ABM de clientes, historial de compras |
| Gestión de proveedores | ABM de proveedores, órdenes de compra |
| Configuración de tienda | Parámetros generales, precios, integraciones |
| Cargas masivas | Importación bulk de productos, clientes, stock (CSV/JSON) |
| Backup y restauración | Copia de seguridad y restauración de datos de la tienda |
| Sincronización cloud | Sync de datos con servicios en la nube |
| Gestión de usuarios | Alta/baja/modificación de staff (manager, cashier) |
| Cloud services | Configuración de servicios cloud y API keys |
| Lifecycle del tenant | Gestión del ciclo de vida de la tienda |

**También puede operar desde app móvil (POS):** caja, ventas, stock.

**Puede asignar a su staff:**
- `manager` (Encargado): ventas, caja, stock, clientes. Sin config crítica de tienda.
- `cashier` (Vendedor): solo operaciones de caja y ventas.

**accountType:** `store_owner` | **Claims JWT:** `admin: true`, `role: "owner"`, `tenantId`

> **Nota de integridad:** Para que las reglas Firestore funcionen correctamente, el owner
> debe tener el JWT claim `admin: true` (requerido por `isTenantAdmin()`). Si falta, ejecutar
> `manage-admin-access.js --role owner`.

---

### 3. `admin` — Administrador de Plataforma

El admin tiene **acceso completo a la plataforma** pero con una restricción importante:

> **RESTRICCIÓN CLAVE:** El admin puede ver y gestionar las *funcionalidades disponibles*
> de cada tienda, pero **NO puede acceder a los datos internos de las tiendas**
> (productos, clientes, ventas, stock, reportes operativos).

**Funciones de plataforma:**

| Funcionalidad | Descripción |
|---|---|
| Gestión de tenants | Ver todas las tiendas, crear, suspender, eliminar tenants |
| Feature flags por tienda | Habilitar/deshabilitar funcionalidades de cada tienda (default: todas activas) |
| Analytics de plataforma | Métricas de uso agregadas de la plataforma |
| Anuncios globales | Gestionar banners y mensajes de plataforma visibles en todas las tiendas |
| Logs de auditoría | Ver logs de auditoría de acciones en la plataforma |
| Herramientas de soporte | Diagnóstico, integridad de cuentas, resolución de incidentes |
| Gestión de planes | Configurar planes y precios de los servicios de la plataforma |
| Config administrativa | Acceso a pricing, cloud config y users/roles de cualquier tienda (para asistencia) |

**No puede acceder a:**
- Productos, stock, inventario de ninguna tienda
- Clientes, proveedores de ninguna tienda
- Historial de ventas ni reportes operativos de ninguna tienda
- Caja (no opera POS de ninguna tienda)
- Backups ni importaciones de datos de tiendas específicas

**accountType:** N/A (cuenta de plataforma) | **Claims JWT:** `admin: true`, `role: "admin"`

---

## Feature flags de tienda (gestionados por admin)

Por defecto **todas las funcionalidades están activas** al crear una tienda nueva.
El admin puede deshabilitar funcionalidades específicas por tienda desde `#/platform/features`.

| Feature flag | Default | Descripción |
|---|---|---|
| `feature.storefront` | ✅ activo | Catálogo público y vista de tienda |
| `feature.pos` | ✅ activo | Punto de venta (POS mobile) |
| `feature.stock` | ✅ activo | Gestión de stock e inventario |
| `feature.cash` | ✅ activo | Apertura y cierre de caja |
| `feature.customers` | ✅ activo | Gestión de clientes |
| `feature.suppliers` | ✅ activo | Gestión de proveedores |
| `feature.reports` | ✅ activo | Reportes y estadísticas |
| `feature.bulk_import` | ✅ activo | Cargas masivas de datos |
| `feature.backups` | ✅ activo | Backup y restauración |
| `feature.cloud_sync` | ✅ activo | Sincronización con la nube |
| `feature.pricing_config` | ✅ activo | Configuración de precios y descuentos |
| `feature.cloud_services` | ✅ activo | Servicios cloud (integraciones externas) |
| `feature.novedades` | ✅ activo | Cartel de novedades / banners de tienda |

---

## Canales y capacidades

### `storefront` — Vista pública/cliente

Accesible por todos los roles (incluso viewer).

| Capacidad | viewer | owner | admin | manager | cashier |
|---|---|---|---|---|---|
| `storefront.catalog.read` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `storefront.novedades.read` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `storefront.store.read` | ✅ | ✅ | ✅ | ✅ | ✅ |

### `mobile_ops` — POS app móvil

El admin de plataforma **no opera caja** en ninguna tienda.

| Capacidad | owner | admin | manager | cashier |
|---|---|---|---|---|
| `sales.checkout` | ✅ | ❌ | ✅ | ✅ |
| `cash.open` | ✅ | ❌ | ✅ | ✅ |
| `cash.audit` | ✅ | ❌ | ✅ | ❌ |
| `cash.movement` | ✅ | ❌ | ✅ | ✅ |
| `cash.close` | ✅ | ❌ | ✅ | ❌ |
| `cash.report.read` | ✅ | ❌ | ✅ | ✅ |
| `stock.adjust` | ✅ | ❌ | ✅ | ❌ |
| `stock.movement.read` | ✅ | ❌ | ✅ | ✅ |

### `web_bo_store` — Gestión de tienda (backoffice del dueño)

| Capacidad | owner | admin | manager |
|---|---|---|---|
| `stock.manage` | ✅ | ❌ | ✅ |
| `stock.report` | ✅ | ❌ | ✅ |
| `sales.manage` | ✅ | ❌ | ✅ |
| `sales.report` | ✅ | ❌ | ✅ |
| `customers.manage` | ✅ | ❌ | ✅ |
| `suppliers.manage` | ✅ | ❌ | ❌ |
| `store.config` | ✅ | ❌ | ❌ |
| `bulk.import` | ✅ | ❌ | ❌ |
| `backups.manage` | ✅ | ❌ | ❌ |
| `cloud.sync` | ✅ | ❌ | ❌ |

### `web_bo_admin` — Config administrativa de tienda

| Capacidad | owner | admin |
|---|---|---|
| `pricing.read` / `pricing.write` | ✅ | ✅ |
| `cloud.config.read` / `cloud.config.write` | ✅ | ✅ |
| `users.roles.read` / `users.roles.write` | ✅ | ✅ |
| `tenant.lifecycle.read` / `tenant.lifecycle.write` | ✅ | ✅ |
| `tenant.backups.read` / `tenant.backups.write` | ✅ | ✅ |

### `web_bo_platform` — Administración de plataforma (solo admin)

| Capacidad | admin |
|---|---|
| `platform.tenants.read` | ✅ |
| `platform.tenants.lifecycle` | ✅ |
| `platform.features.read` | ✅ |
| `platform.features.write` | ✅ |
| `platform.analytics.read` | ✅ |
| `platform.announcements.manage` | ✅ |
| `platform.audit.read` | ✅ |
| `platform.support.read` | ✅ |
| `platform.plans.read` / `platform.plans.write` | ✅ |

---

## Módulos de backoffice web y acceso por rol

| Módulo | viewer | owner | admin | manager | cashier |
|---|---|---|---|---|---|
| storefrontCatalog | ✅ | ✅ | ✅ | ✅ | ✅ |
| storefrontNovedades | ✅ | ✅ | ✅ | ✅ | ✅ |
| stock | ❌ | ✅ | ❌ | ✅ | ❌ |
| sales | ❌ | ✅ | ❌ | ✅ | ✅ |
| customers | ❌ | ✅ | ❌ | ✅ | ❌ |
| suppliers | ❌ | ✅ | ❌ | ❌ | ❌ |
| storeConfig | ❌ | ✅ | ❌ | ❌ | ❌ |
| bulkImport | ❌ | ✅ | ❌ | ❌ | ❌ |
| backupsStore | ❌ | ✅ | ❌ | ❌ | ❌ |
| cloudSync | ❌ | ✅ | ❌ | ❌ | ❌ |
| dashboard | ❌ | ✅ | ❌ | ✅ | ❌ |
| pricing | ❌ | ✅ | ✅ | ❌ | ❌ |
| usersRoles | ❌ | ✅ | ✅ | ❌ | ❌ |
| cloudConfig | ❌ | ✅ | ✅ | ❌ | ❌ |
| tenantLifecycle | ❌ | ✅ | ✅ | ❌ | ❌ |
| maintenanceRead/Write | ❌ | ✅ | ✅ | ❌ | ❌ |
| backupsRead/Write | ❌ | ✅ | ✅ | ❌ | ❌ |
| platformTenants | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformFeatures | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformAnalytics | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformAnnouncements | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformAudit | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformSupport | ❌ | ❌ | ✅ | ❌ | ❌ |
| platformPlans | ❌ | ❌ | ✅ | ❌ | ❌ |

> **admin** tiene ❌ en todos los módulos de gestión de tienda (`stock`, `sales`,
> `customers`, `suppliers`, `storeConfig`, `bulkImport`, `backupsStore`, `cloudSync`,
> `dashboard`) porque esos módulos exponen datos internos de la tienda.
> El admin SOLO accede a los módulos `platform*` y a config administrativa (`pricing`,
> `usersRoles`, `cloudConfig`, etc.) para asistencia técnica.

---

## Rutas de backoffice (mapeo de módulos)

| Ruta | Módulo | Roles |
|---|---|---|
| `#/catalog` | storefrontCatalog | viewer, owner, admin, manager, cashier |
| `#/novedades` | storefrontNovedades | viewer, owner, admin, manager, cashier |
| `#/store/stock` | stock | owner, manager |
| `#/store/sales` | sales | owner, manager, cashier |
| `#/store/customers` | customers | owner, manager |
| `#/store/suppliers` | suppliers | owner |
| `#/store/config` | storeConfig | owner |
| `#/store/bulk-import` | bulkImport | owner |
| `#/store/backups` | backupsStore | owner |
| `#/store/cloud-sync` | cloudSync | owner |
| `#/dashboard` | dashboard | owner, manager |
| `#/settings/pricing` | pricing | owner, admin |
| `#/settings/users` | usersRoles | owner, admin |
| `#/settings/cloud-services` | cloudConfig | owner, admin |
| `#/settings/tenant-lifecycle` | tenantLifecycle | owner, admin |
| `#/maintenance` | maintenanceWrite | owner, admin |
| `#/backups` | backupsRead | owner, admin |
| `#/platform/tenants` | platformTenants | admin |
| `#/platform/features` | platformFeatures | admin |
| `#/platform/analytics` | platformAnalytics | admin |
| `#/platform/announcements` | platformAnnouncements | admin |
| `#/platform/audit` | platformAudit | admin |
| `#/platform/support` | platformSupport | admin |
| `#/platform/plans` | platformPlans | admin |

---

## Scope de tenant por canal

| Canal | sameTenant | crossTenant | platform |
|---|---|---|---|
| `storefront` | viewer, owner, admin, manager, cashier | viewer, admin | admin, superadmin |
| `mobile_ops` | owner, manager, cashier | — | superadmin |
| `web_bo_store` | owner, manager | — | superadmin |
| `web_bo_admin` | owner, admin | — | superadmin |
| `web_bo_platform` | — | — | admin, superadmin |

> El admin opera en scope `platform` para `web_bo_platform`.
> Para `web_bo_admin` opera en `sameTenant` solo cuando asiste técnicamente a una tienda específica.

---

## Reglas de enforcement en Firestore

- Gestión administrativa de `/tenant_users`, `/users`, `/account_requests`:
  permitida solo para `owner` y `admin` mediante `isTenantAdmin()`.
- `manager`, `cashier` y `viewer` quedan explícitamente fuera del enforcement administrativo.
- El admin accede a `/tenants/{tenantId}/config/features` (feature flags) pero NO a
  subcolecciones de datos operativos de la tienda.
- Claims administrativos (`isAdmin`, `isSuperAdmin`) mantienen bypass administrativo global.

---

## Reglas de producto

1. `viewer` representa **solo cliente final** y no puede operar caja ni administración.
2. `owner` puede cambiar entre vista cliente y vista de gestión de su tienda.
3. `admin` gestiona funcionalidades de tiendas pero **nunca accede a datos internos** de las mismas.
4. Los feature flags de tienda son todos `true` por defecto. El admin solo los desactiva si hay razones de plan/soporte.
5. `mobile_ops` está restringido a staff operativo de la tienda (owner, manager, cashier). El admin de plataforma no opera POS.
6. Cualquier cambio en permisos debe actualizar primero `functions/src/security/rolePermissionsMatrix.ts` y luego sus adaptadores.
