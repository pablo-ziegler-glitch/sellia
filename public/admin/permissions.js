/**
 * permissions.js — Adaptador frontend de la matriz de permisos
 *
 * SINCRONIZADO con: functions/src/security/rolePermissionsMatrix.ts
 * Versión: 2026-03-13
 *
 * ARQUITECTURA DE ROLES:
 *
 *  viewer  → Cliente Final: solo catálogo público y novedades.
 *  owner   → Dueño de Tienda: vista cliente + gestión completa de tienda.
 *  admin   → Admin de Plataforma: gestión de plataforma y feature flags
 *            por tienda. NO accede a datos internos de las tiendas.
 *
 *  (Roles de staff asignados por el owner: manager, cashier)
 */

export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-04-03";

// ─────────────────────────────────────────────────────────────────────────────
//  CANALES Y CAPACIDADES
// ─────────────────────────────────────────────────────────────────────────────

export const CHANNEL_CAPABILITIES = Object.freeze({
  storefront: [
    "storefront.catalog.read",
    "storefront.novedades.read",
    "storefront.store.read",
  ],
  mobile_ops: [
    "sales.checkout",
    "cash.open",
    "cash.audit",
    "cash.movement",
    "cash.close",
    "cash.report.read",
    "stock.adjust",
    "stock.movement.read",
  ],
  web_bo_store: [
    "stock.manage",
    "stock.report",
    "sales.manage",
    "sales.report",
    "customers.manage",
    "suppliers.manage",
    "store.config",
    "bulk.import",
    "backups.manage",
    "cloud.sync",
  ],
  web_bo_admin: [
    "pricing.read",
    "pricing.write",
    "cloud.config.read",
    "cloud.config.write",
    "users.roles.read",
    "users.roles.write",
    "tenant.lifecycle.read",
    "tenant.lifecycle.write",
    "tenant.backups.read",
    "tenant.backups.write",
  ],
  web_bo_platform: [
    "platform.tenants.read",
    "platform.tenants.lifecycle",
    "platform.features.read",
    "platform.features.write",
    "platform.analytics.read",
    "platform.announcements.manage",
    "platform.audit.read",
    "platform.support.read",
    "platform.plans.read",
    "platform.plans.write",
  ],
});

// ─────────────────────────────────────────────────────────────────────────────
//  PERMISOS POR ROL (capacidades web)
// ─────────────────────────────────────────────────────────────────────────────

export const ROLE_PERMISSIONS = Object.freeze({
  // Cliente final: solo ve storefront. Sin gestión.
  viewer: [
    ...CHANNEL_CAPABILITIES.storefront,
  ],
  // Dueño: vista cliente + gestión completa de su tienda + config admin
  owner: [
    ...CHANNEL_CAPABILITIES.storefront,
    ...CHANNEL_CAPABILITIES.web_bo_store,
    ...CHANNEL_CAPABILITIES.web_bo_admin,
    ...CHANNEL_CAPABILITIES.web_bo_platform,
  ],
  // Admin de plataforma: gestión de plataforma + soporte operativo de tiendas.
  admin: [
    ...CHANNEL_CAPABILITIES.storefront,
    ...CHANNEL_CAPABILITIES.web_bo_store,
    ...CHANNEL_CAPABILITIES.web_bo_admin,
    ...CHANNEL_CAPABILITIES.web_bo_platform,
  ],
  // Encargado: operaciones de tienda sin config crítica
  manager: [
    ...CHANNEL_CAPABILITIES.storefront,
    "stock.manage", "stock.report",
    "sales.manage", "sales.report",
    "customers.manage",
  ],
  // Vendedor/cajero: solo operaciones de caja
  cashier: [
    ...CHANNEL_CAPABILITIES.storefront,
  ],
});

// ─────────────────────────────────────────────────────────────────────────────
//  SCOPE DE TENANT
// ─────────────────────────────────────────────────────────────────────────────

export const TENANT_SCOPE_ROLE_POLICIES = Object.freeze({
  storefront: Object.freeze({
    sameTenant:  ["viewer", "owner", "admin", "manager", "cashier"],
    crossTenant: ["viewer", "admin"],
    platform:    ["owner", "admin", "superadmin"],
  }),
  mobile_ops: Object.freeze({
    sameTenant:  ["owner", "manager", "cashier"],
    crossTenant: [],
    platform:    ["superadmin"],
  }),
  web_bo_store: Object.freeze({
    sameTenant:  ["owner", "admin", "manager"],
    crossTenant: [],
    platform:    ["superadmin"],
  }),
  web_bo_admin: Object.freeze({
    sameTenant:  ["owner", "admin"],
    crossTenant: [],
    platform:    ["superadmin"],
  }),
  web_bo_platform: Object.freeze({
    sameTenant:  [],
    crossTenant: [],
    platform:    ["owner", "admin", "superadmin"],
  }),
});

// ─────────────────────────────────────────────────────────────────────────────
//  MÓDULOS DE BACKOFFICE WEB
// ─────────────────────────────────────────────────────────────────────────────

export const MODULE_ROLE_POLICIES = Object.freeze({
  // Storefront (público)
  storefrontCatalog:       ["viewer", "owner", "admin", "manager", "cashier"],
  storefrontNovedades:     ["viewer", "owner", "admin", "manager", "cashier"],

  // Gestión de tienda (owner/admin, manager limitado)
  stock:                   ["owner", "admin", "manager"],
  sales:                   ["owner", "admin", "manager", "cashier"],
  customers:               ["owner", "admin", "manager"],
  suppliers:               ["owner", "admin"],
  storeConfig:             ["owner", "admin"],
  bulkImport:              ["owner", "admin"],
  backupsStore:            ["owner", "admin"],
  cloudSync:               ["owner", "admin"],

  // Config administrativa de tienda
  dashboard:               ["owner", "admin", "manager"],
  pricing:                 ["owner", "admin"],
  usersRoles:              ["owner", "admin"],
  cloudConfig:             ["owner", "admin"],
  tenantLifecycle:         ["owner", "admin"],
  maintenanceRead:         ["owner", "admin"],
  maintenanceWrite:        ["owner", "admin"],
  backupsRead:             ["owner", "admin"],
  backupsWrite:            ["owner", "admin"],

  // Administración de plataforma (owner/admin)
  platformTenants:         ["owner", "admin"],
  platformFeatures:        ["owner", "admin"],
  platformAnalytics:       ["owner", "admin"],
  platformAnnouncements:   ["owner", "admin"],
  platformAudit:           ["owner", "admin"],
  platformSupport:         ["owner", "admin"],
  platformPlans:           ["owner", "admin"],
});

// ─────────────────────────────────────────────────────────────────────────────
//  RUTAS DE BACKOFFICE → MÓDULO
// ─────────────────────────────────────────────────────────────────────────────

export const ROUTE_POLICIES = Object.freeze({
  // Vista cliente / storefront
  "#/catalog":                  MODULE_ROLE_POLICIES.storefrontCatalog,
  "#/novedades":                MODULE_ROLE_POLICIES.storefrontNovedades,

  // Gestión de tienda (owner)
  "#/store/stock":              MODULE_ROLE_POLICIES.stock,
  "#/store/sales":              MODULE_ROLE_POLICIES.sales,
  "#/store/products-bulk":      MODULE_ROLE_POLICIES.stock,
  "#/store/customers":          MODULE_ROLE_POLICIES.customers,
  "#/store/suppliers":          MODULE_ROLE_POLICIES.suppliers,
  "#/store/config":             MODULE_ROLE_POLICIES.storeConfig,
  "#/store/bulk-import":        MODULE_ROLE_POLICIES.bulkImport,
  "#/store/backups":            MODULE_ROLE_POLICIES.backupsStore,
  "#/store/cloud-sync":         MODULE_ROLE_POLICIES.cloudSync,

  // Config administrativa de tienda
  "#/dashboard":                MODULE_ROLE_POLICIES.dashboard,
  "#/settings/pricing":         MODULE_ROLE_POLICIES.pricing,
  "#/settings/users":           MODULE_ROLE_POLICIES.usersRoles,
  "#/settings/cloud-services":  MODULE_ROLE_POLICIES.cloudConfig,
  "#/settings/tenant-lifecycle":MODULE_ROLE_POLICIES.tenantLifecycle,
  "#/maintenance":              MODULE_ROLE_POLICIES.maintenanceWrite,
  "#/backups":                  MODULE_ROLE_POLICIES.backupsRead,

  // Administración de plataforma (owner/admin)
  "#/platform/tenants":         MODULE_ROLE_POLICIES.platformTenants,
  "#/platform/features":        MODULE_ROLE_POLICIES.platformFeatures,
  "#/platform/analytics":       MODULE_ROLE_POLICIES.platformAnalytics,
  "#/platform/announcements":   MODULE_ROLE_POLICIES.platformAnnouncements,
  "#/platform/audit":           MODULE_ROLE_POLICIES.platformAudit,
  "#/platform/support":         MODULE_ROLE_POLICIES.platformSupport,
  "#/platform/plans":           MODULE_ROLE_POLICIES.platformPlans,

  // Cuenta (todos los roles internos)
  "#/permissions":              ["owner", "admin", "manager", "cashier"],
});

// ─────────────────────────────────────────────────────────────────────────────
//  FEATURE FLAGS DE TIENDA (defaults — gestionados por admin)
// ─────────────────────────────────────────────────────────────────────────────

export const STORE_FEATURE_FLAGS = Object.freeze({
  "feature.storefront":       { defaultEnabled: true,  description: "Catálogo público y vista de tienda" },
  "feature.pos":              { defaultEnabled: true,  description: "Punto de venta (POS mobile)" },
  "feature.stock":            { defaultEnabled: true,  description: "Gestión de stock e inventario" },
  "feature.cash":             { defaultEnabled: true,  description: "Apertura y cierre de caja" },
  "feature.customers":        { defaultEnabled: true,  description: "Gestión de clientes" },
  "feature.suppliers":        { defaultEnabled: true,  description: "Gestión de proveedores" },
  "feature.reports":          { defaultEnabled: true,  description: "Reportes y estadísticas" },
  "feature.bulk_import":      { defaultEnabled: true,  description: "Cargas masivas de datos" },
  "feature.backups":          { defaultEnabled: true,  description: "Backup y restauración" },
  "feature.cloud_sync":       { defaultEnabled: true,  description: "Sincronización con la nube" },
  "feature.pricing_config":   { defaultEnabled: true,  description: "Configuración de precios y descuentos" },
  "feature.cloud_services":   { defaultEnabled: true,  description: "Servicios cloud (integraciones externas)" },
  "feature.novedades":        { defaultEnabled: true,  description: "Cartel de novedades / banners de tienda" },
});

// Actualmente no hay rutas restringidas para admin dentro del backoffice web.
export const ADMIN_STORE_DATA_RESTRICTED_ROUTES = new Set([]);

export const INTERNAL_ROLES = new Set(["owner", "admin", "manager", "cashier"]);

// ─────────────────────────────────────────────────────────────────────────────
//  CONTRATO
// ─────────────────────────────────────────────────────────────────────────────

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES,
  storeFeatureFlags: STORE_FEATURE_FLAGS,
});

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────

function normalizeRole(role) {
  return typeof role === "string" ? role.trim().toLowerCase() : "";
}

export function hasRouteAccess(role, route) {
  const normalizedRole = normalizeRole(role);
  const allowedRoles = ROUTE_POLICIES[route];
  if (!Array.isArray(allowedRoles)) return false;
  if (!allowedRoles.includes(normalizedRole)) return false;
  // Restricciones adicionales por rol (si existieran rutas restringidas)
  if (normalizedRole === "admin" && ADMIN_STORE_DATA_RESTRICTED_ROUTES.has(route)) return false;
  return true;
}

export function rolePermissions(role) {
  const normalizedRole = normalizeRole(role);
  return ROLE_PERMISSIONS[normalizedRole] || [];
}

export function isInternalRole(role) {
  return INTERNAL_ROLES.has(normalizeRole(role));
}

export function normalizeInternalRole(role) {
  return normalizeRole(role);
}

/**
 * Verifica si el admin puede acceder a una ruta de una tienda específica.
 * El admin solo puede acceder a rutas de feature flags y metadatos, no a datos.
 */
export function adminCanAccessRoute(route) {
  return !ADMIN_STORE_DATA_RESTRICTED_ROUTES.has(route);
}
