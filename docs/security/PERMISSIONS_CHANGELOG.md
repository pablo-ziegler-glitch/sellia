# Permissions Changelog

Registro inmutable de cambios de permisos. Cada versión debe tener:

- Fecha y autor técnico.
- Motivación de negocio/seguridad.
- Impacto en runtime (backend/frontend/docs).
- `Security review: APPROVED` con responsable.

## 2026-03-13

- Date: 2026-03-13
- Author: product-architecture
- Change summary:
  - Se redefinieron los 3 roles canónicos del sistema con responsabilidades claras:
    · `viewer` (Cliente Final): solo storefront público y cartel de novedades.
    · `owner` (Dueño de Tienda): vista cliente + gestión completa de su tienda.
    · `admin` (Admin de Plataforma): gestión de plataforma y feature flags por tienda.
      RESTRICCIÓN: el admin NO accede a datos internos de las tiendas.
  - Se agregó canal `storefront` para capacidades de vista pública (viewer, owner, admin, manager, cashier).
  - Se agregó canal `web_bo_store` para gestión de tienda del owner:
    stock.manage, stock.report, sales.manage, sales.report, customers.manage,
    suppliers.manage, store.config, bulk.import, backups.manage, cloud.sync.
  - Se agregó canal `web_bo_platform` exclusivo del admin de plataforma:
    platform.tenants.*, platform.features.*, platform.analytics.read,
    platform.announcements.manage, platform.audit.read, platform.support.read,
    platform.plans.*.
  - Se removió al `admin` del canal `mobile_ops`: el admin de plataforma no opera caja.
  - Se agregó `STORE_FEATURE_FLAGS`: catálogo de 13 feature flags de tienda,
    todos habilitados por default, gestionables por admin desde #/platform/features.
  - Se agregaron módulos: storefrontCatalog, storefrontNovedades, stock, sales, customers,
    suppliers, storeConfig, bulkImport, backupsStore, cloudSync, platformTenants,
    platformFeatures, platformAnalytics, platformAnnouncements, platformAudit,
    platformSupport, platformPlans.
  - Se agregaron rutas de backoffice: #/store/*, #/platform/*.
  - Se agregó helper `adminCanAccessModule()` / `adminCanAccessRoute()` para enforce
    de la restricción de datos internos del admin.
  - Versión de matriz actualizada de "2026-02-25" a "2026-03-13".
- Impact:
  - Backend: rolePermissionsMatrix.ts actualizado.
  - Frontend web: public/admin/permissions.js sincronizado.
  - Docs: ROLE_PERMISSIONS_MATRIX.md reescrito.
  - Mobile: pendiente sincronizar RolePermissionMatrix.kt.
  - Firestore rules: pendiente agregar restricción de acceso a datos internos para admin.
- Security review: APPROVED (product-architecture)

## 2026-02-24

- Date: 2026-02-24
- Author: platform-security
- Change summary:
  - Se consolidó `MODULE_ROLE_POLICIES` como matriz canónica en backend (`functions/src/security/rolePermissionsMatrix.ts`).
  - Se aplicó sincronización obligatoria hacia frontend (`public/admin/permissions.js`) y documentación (`docs/security/ROLE_PERMISSIONS_MATRIX.md`).
  - Se agregó control de drift automatizado y puerta de CI.
  - Se formalizó aprobación temporal para diffs excepcionales vía `permissions-drift-approvals.json`.
- Security review: APPROVED (security-architecture)
