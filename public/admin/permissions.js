export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-02-24";

export const ROLE_PERMISSIONS = Object.freeze({
  owner: [
    "MANAGE_USERS",
    "MANAGE_CLOUD_SERVICES",
    "VIEW_USAGE_DASHBOARD",
    "REQUEST_TENANT_BACKUP",
    "CASH_OPEN",
    "CASH_AUDIT",
    "CASH_MOVEMENT",
    "CASH_CLOSE",
    "VIEW_CASH_REPORT"
  ],
  admin: [
    "MANAGE_USERS",
    "MANAGE_CLOUD_SERVICES",
    "VIEW_USAGE_DASHBOARD",
    "REQUEST_TENANT_BACKUP",
    "CASH_OPEN",
    "CASH_AUDIT",
    "CASH_MOVEMENT",
    "CASH_CLOSE",
    "VIEW_CASH_REPORT"
  ],
  manager: ["VIEW_USAGE_DASHBOARD", "CASH_OPEN", "CASH_AUDIT", "CASH_MOVEMENT", "CASH_CLOSE", "VIEW_CASH_REPORT"],
  cashier: ["CASH_OPEN", "CASH_MOVEMENT", "VIEW_CASH_REPORT"],
  viewer: []
});

export const INTERNAL_ROLES = new Set(["owner", "admin", "manager", "cashier"]);

export const MODULE_ROLE_POLICIES = Object.freeze({
  dashboard: ["owner", "admin", "manager"],
  pricing: ["owner", "admin"],
  marketing: ["owner", "admin", "manager"],
  users: ["owner", "admin"],
  cloudServices: ["owner", "admin"],
  maintenanceRead: ["owner", "admin"],
  maintenanceWrite: ["owner", "admin"],
  backupsRead: ["owner", "admin"],
  backupsWrite: ["owner", "admin"]
});

export const ROUTE_POLICIES = Object.freeze({
  "#/dashboard": MODULE_ROLE_POLICIES.dashboard,
  "#/settings/pricing": MODULE_ROLE_POLICIES.pricing,
  "#/settings/marketing": MODULE_ROLE_POLICIES.marketing,
  "#/settings/users": MODULE_ROLE_POLICIES.users,
  "#/settings/cloud-services": MODULE_ROLE_POLICIES.cloudServices,
  "#/maintenance": MODULE_ROLE_POLICIES.maintenanceWrite
});

export function hasRouteAccess(role, route) {
  const allowedRoles = ROUTE_POLICIES[route];
  return Array.isArray(allowedRoles) ? allowedRoles.includes(role) : false;
}

export function rolePermissions(role) {
  return ROLE_PERMISSIONS[role] || [];
}
