export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-02-25";

export const CHANNEL_CAPABILITIES = Object.freeze({
  mobile_ops: [
    "sales.checkout",
    "cash.open",
    "cash.audit",
    "cash.movement",
    "cash.close",
    "cash.report.read",
    "stock.adjust",
    "stock.movement.read"
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
    "tenant.backups.write"
  ]
});

export const TENANT_SCOPE_ROLE_POLICIES = Object.freeze({
  mobile_ops: Object.freeze({
    sameTenant: ["owner", "admin", "manager", "cashier"],
    crossTenant: [],
    platform: ["superadmin"]
  }),
  web_bo_admin: Object.freeze({
    sameTenant: ["owner", "admin"],
    crossTenant: [],
    platform: ["superadmin"]
  })
});

export const ROLE_PERMISSIONS = Object.freeze({
  owner: [...CHANNEL_CAPABILITIES.web_bo_admin],
  admin: [...CHANNEL_CAPABILITIES.web_bo_admin],
  manager: [],
  cashier: [],
  viewer: []
});

export const INTERNAL_ROLES = new Set(["owner", "admin", "manager", "cashier"]);

export const MODULE_ROLE_POLICIES = Object.freeze({
  dashboard: ["owner", "admin", "manager"],
  pricing: ["owner", "admin"],
  usersRoles: ["owner", "admin"],
  cloudConfig: ["owner", "admin"],
  tenantLifecycle: ["owner", "admin"],
  maintenanceRead: ["owner", "admin"],
  maintenanceWrite: ["owner", "admin"],
  backupsRead: ["owner", "admin"],
  backupsWrite: ["owner", "admin"]
});

export const ROUTE_POLICIES = Object.freeze({
  "#/dashboard": MODULE_ROLE_POLICIES.dashboard,
  "#/settings/pricing": MODULE_ROLE_POLICIES.pricing,
  "#/settings/users": MODULE_ROLE_POLICIES.usersRoles,
  "#/settings/cloud-services": MODULE_ROLE_POLICIES.cloudConfig,
  "#/settings/tenant-lifecycle": MODULE_ROLE_POLICIES.tenantLifecycle,
  "#/maintenance": MODULE_ROLE_POLICIES.maintenanceWrite
});

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES
});

export function hasRouteAccess(role, route) {
  const allowedRoles = ROUTE_POLICIES[route];
  return Array.isArray(allowedRoles) ? allowedRoles.includes(role) : false;
}

export function rolePermissions(role) {
  return ROLE_PERMISSIONS[role] || [];
}
