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

export const ROUTE_POLICIES = Object.freeze({
  "#/dashboard": ["owner", "admin", "manager"],
  "#/settings/pricing": ["owner", "admin"],
  "#/settings/marketing": ["owner", "admin", "manager"],
  "#/settings/users": ["owner", "admin"],
  "#/settings/cloud-services": ["owner", "admin"],
  "#/maintenance": ["owner", "admin", "manager", "cashier"]
});

export function hasRouteAccess(role, route) {
  const allowedRoles = ROUTE_POLICIES[route];
  return Array.isArray(allowedRoles) ? allowedRoles.includes(role) : false;
}

export function rolePermissions(role) {
  return ROLE_PERMISSIONS[role] || [];
}
