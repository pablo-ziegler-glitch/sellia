export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-04-11-owner-only";

export const CHANNEL_CAPABILITIES = Object.freeze({
  storefront: Object.freeze([
    "storefront.catalog.read",
    "storefront.novedades.read",
    "storefront.store.read",
  ]),
  mobile_ops: Object.freeze([
    "sales.checkout",
    "cash.open",
    "cash.audit",
    "cash.movement",
    "cash.close",
    "cash.report.read",
    "stock.adjust",
    "stock.movement.read",
  ]),
  web_bo_store: Object.freeze([
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
  ]),
  web_bo_admin: Object.freeze([
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
  ]),
  web_bo_platform: Object.freeze([
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
  ]),
} as const);

export type ChannelKey = keyof typeof CHANNEL_CAPABILITIES;
const OWNER_ONLY = Object.freeze(["owner"]);

export const CHANNEL_CAPABILITY_ROLE_POLICIES = Object.freeze({
  storefront: Object.freeze(
    Object.fromEntries(CHANNEL_CAPABILITIES.storefront.map((capability) => [capability, OWNER_ONLY]))
  ),
  mobile_ops: Object.freeze(
    Object.fromEntries(CHANNEL_CAPABILITIES.mobile_ops.map((capability) => [capability, OWNER_ONLY]))
  ),
  web_bo_store: Object.freeze(
    Object.fromEntries(CHANNEL_CAPABILITIES.web_bo_store.map((capability) => [capability, OWNER_ONLY]))
  ),
  web_bo_admin: Object.freeze(
    Object.fromEntries(CHANNEL_CAPABILITIES.web_bo_admin.map((capability) => [capability, OWNER_ONLY]))
  ),
  web_bo_platform: Object.freeze(
    Object.fromEntries(CHANNEL_CAPABILITIES.web_bo_platform.map((capability) => [capability, OWNER_ONLY]))
  ),
} as const);

export type TenantScopeKey = "sameTenant" | "crossTenant" | "platform";

export const TENANT_SCOPE_ROLE_POLICIES: Readonly<Record<ChannelKey, Readonly<Record<TenantScopeKey, readonly string[]>>>> = Object.freeze({
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
} as const);

export type ModulePolicyKey = keyof typeof MODULE_ROLE_POLICIES;

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
} as const);

export type StoreFeatureFlag = keyof typeof STORE_FEATURE_FLAGS;

const MODULE_POLICY_ROLE_SET: Record<ModulePolicyKey, Set<string>> = Object.freeze(
  Object.entries(MODULE_ROLE_POLICIES).reduce((acc, [module, roles]) => {
    acc[module as ModulePolicyKey] = new Set(roles as readonly string[]);
    return acc;
  }, {} as Record<ModulePolicyKey, Set<string>>)
);

const TENANT_SCOPE_ROLE_SET = Object.freeze(
  Object.fromEntries(
    Object.entries(TENANT_SCOPE_ROLE_POLICIES).map(([channel, scopePolicies]) => [
      channel,
      Object.fromEntries(
        Object.entries(scopePolicies).map(([scope, roles]) => [scope, new Set(roles)])
      ),
    ])
  ) as Record<ChannelKey, Record<TenantScopeKey, Set<string>>>
);

export const hasRoleForModule = (role: unknown, module: ModulePolicyKey): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) return false;
  return MODULE_POLICY_ROLE_SET[module].has(normalizedRole);
};

export const hasRoleForChannelInTenantScope = (
  role: unknown,
  channel: ChannelKey,
  tenantScope: TenantScopeKey
): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) return false;
  return TENANT_SCOPE_ROLE_SET[channel][tenantScope].has(normalizedRole);
};

export const ADMIN_STORE_DATA_RESTRICTED_MODULES: ReadonlySet<ModulePolicyKey> = new Set([]);

export const adminCanAccessModule = (module: ModulePolicyKey): boolean => {
  return !ADMIN_STORE_DATA_RESTRICTED_MODULES.has(module);
};

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  channelCapabilityRolePolicies: CHANNEL_CAPABILITY_ROLE_POLICIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES,
  storeFeatureFlags: STORE_FEATURE_FLAGS,
});
