export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-04-11-owner-only";

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

const OWNER_ONLY = Object.freeze(["owner"]);

export const ROLE_PERMISSIONS = Object.freeze({
  owner: [
    ...CHANNEL_CAPABILITIES.storefront,
    ...CHANNEL_CAPABILITIES.mobile_ops,
    ...CHANNEL_CAPABILITIES.web_bo_store,
    ...CHANNEL_CAPABILITIES.web_bo_admin,
    ...CHANNEL_CAPABILITIES.web_bo_platform,
  ],
});

export const TENANT_SCOPE_ROLE_POLICIES = Object.freeze({
  storefront: Object.freeze({ sameTenant: OWNER_ONLY, crossTenant: [], platform: OWNER_ONLY }),
  mobile_ops: Object.freeze({ sameTenant: OWNER_ONLY, crossTenant: [], platform: OWNER_ONLY }),
  web_bo_store: Object.freeze({ sameTenant: OWNER_ONLY, crossTenant: [], platform: OWNER_ONLY }),
  web_bo_admin: Object.freeze({ sameTenant: OWNER_ONLY, crossTenant: [], platform: OWNER_ONLY }),
  web_bo_platform: Object.freeze({ sameTenant: OWNER_ONLY, crossTenant: [], platform: OWNER_ONLY }),
});

export const MODULE_ROLE_POLICIES = Object.freeze({
  storefrontCatalog: OWNER_ONLY,
  storefrontNovedades: OWNER_ONLY,
  stock: OWNER_ONLY,
  sales: OWNER_ONLY,
  customers: OWNER_ONLY,
  suppliers: OWNER_ONLY,
  storeConfig: OWNER_ONLY,
  bulkImport: OWNER_ONLY,
  backupsStore: OWNER_ONLY,
  cloudSync: OWNER_ONLY,
  dashboard: OWNER_ONLY,
  pricing: OWNER_ONLY,
  usersRoles: OWNER_ONLY,
  cloudConfig: OWNER_ONLY,
  tenantLifecycle: OWNER_ONLY,
  maintenanceRead: OWNER_ONLY,
  maintenanceWrite: OWNER_ONLY,
  backupsRead: OWNER_ONLY,
  backupsWrite: OWNER_ONLY,
  platformTenants: OWNER_ONLY,
  platformFeatures: OWNER_ONLY,
  platformAnalytics: OWNER_ONLY,
  platformAnnouncements: OWNER_ONLY,
  platformAudit: OWNER_ONLY,
  platformSupport: OWNER_ONLY,
  platformPlans: OWNER_ONLY,
});

export const ROUTE_POLICIES = Object.freeze({
  "#/catalog": MODULE_ROLE_POLICIES.storefrontCatalog,
  "#/novedades": MODULE_ROLE_POLICIES.storefrontNovedades,
  "#/store/stock": MODULE_ROLE_POLICIES.stock,
  "#/store/sales": MODULE_ROLE_POLICIES.sales,
  "#/store/products-bulk": MODULE_ROLE_POLICIES.stock,
  "#/store/customers": MODULE_ROLE_POLICIES.customers,
  "#/store/suppliers": MODULE_ROLE_POLICIES.suppliers,
  "#/store/config": MODULE_ROLE_POLICIES.storeConfig,
  "#/store/bulk-import": MODULE_ROLE_POLICIES.bulkImport,
  "#/store/backups": MODULE_ROLE_POLICIES.backupsStore,
  "#/store/cloud-sync": MODULE_ROLE_POLICIES.cloudSync,
  "#/dashboard": MODULE_ROLE_POLICIES.dashboard,
  "#/settings/pricing": MODULE_ROLE_POLICIES.pricing,
  "#/settings/users": MODULE_ROLE_POLICIES.usersRoles,
  "#/settings/cloud-services": MODULE_ROLE_POLICIES.cloudConfig,
  "#/settings/tenant-lifecycle": MODULE_ROLE_POLICIES.tenantLifecycle,
  "#/maintenance": MODULE_ROLE_POLICIES.maintenanceWrite,
  "#/backups": MODULE_ROLE_POLICIES.backupsRead,
  "#/platform/tenants": MODULE_ROLE_POLICIES.platformTenants,
  "#/platform/features": MODULE_ROLE_POLICIES.platformFeatures,
  "#/platform/analytics": MODULE_ROLE_POLICIES.platformAnalytics,
  "#/platform/announcements": MODULE_ROLE_POLICIES.platformAnnouncements,
  "#/platform/audit": MODULE_ROLE_POLICIES.platformAudit,
  "#/platform/support": MODULE_ROLE_POLICIES.platformSupport,
  "#/platform/plans": MODULE_ROLE_POLICIES.platformPlans,
  "#/permissions": OWNER_ONLY,
});

export const STORE_FEATURE_FLAGS = Object.freeze({
  "feature.storefront": { defaultEnabled: true, description: "Catálogo público y vista de tienda" },
  "feature.pos": { defaultEnabled: true, description: "Punto de venta (POS mobile)" },
  "feature.stock": { defaultEnabled: true, description: "Gestión de stock e inventario" },
  "feature.cash": { defaultEnabled: true, description: "Apertura y cierre de caja" },
  "feature.customers": { defaultEnabled: true, description: "Gestión de clientes" },
  "feature.suppliers": { defaultEnabled: true, description: "Gestión de proveedores" },
  "feature.reports": { defaultEnabled: true, description: "Reportes y estadísticas" },
  "feature.bulk_import": { defaultEnabled: true, description: "Cargas masivas de datos" },
  "feature.backups": { defaultEnabled: true, description: "Backup y restauración" },
  "feature.cloud_sync": { defaultEnabled: true, description: "Sincronización con la nube" },
  "feature.pricing_config": { defaultEnabled: true, description: "Configuración de precios y descuentos" },
  "feature.cloud_services": { defaultEnabled: true, description: "Servicios cloud (integraciones externas)" },
  "feature.novedades": { defaultEnabled: true, description: "Cartel de novedades / banners de tienda" },
});

export const ADMIN_STORE_DATA_RESTRICTED_ROUTES = new Set([]);
export const INTERNAL_ROLES = new Set(["owner"]);

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES,
  storeFeatureFlags: STORE_FEATURE_FLAGS,
});

function normalizeRole(role) {
  return typeof role === "string" ? role.trim().toLowerCase() : "";
}

export function hasRouteAccess(role, route) {
  const normalizedRole = normalizeRole(role);
  const allowedRoles = ROUTE_POLICIES[route];
  if (!Array.isArray(allowedRoles)) return false;
  return allowedRoles.includes(normalizedRole);
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

export function adminCanAccessRoute(route) {
  return !ADMIN_STORE_DATA_RESTRICTED_ROUTES.has(route);
}
