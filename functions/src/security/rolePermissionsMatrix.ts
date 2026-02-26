export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-02-25";

export const CHANNEL_CAPABILITIES = Object.freeze({
  mobile_ops: Object.freeze([
    "sales.checkout",
    "cash.open",
    "cash.audit",
    "cash.movement",
    "cash.close",
    "cash.report.read",
    "stock.adjust",
    "stock.movement.read"
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
    "tenant.backups.write"
  ])
} as const);

export type ChannelKey = keyof typeof CHANNEL_CAPABILITIES;

export const CHANNEL_CAPABILITY_ROLE_POLICIES = Object.freeze({
  mobile_ops: Object.freeze({
    "sales.checkout": Object.freeze(["owner", "admin", "manager", "cashier"]),
    "cash.open": Object.freeze(["owner", "admin", "manager", "cashier"]),
    "cash.audit": Object.freeze(["owner", "admin", "manager"]),
    "cash.movement": Object.freeze(["owner", "admin", "manager", "cashier"]),
    "cash.close": Object.freeze(["owner", "admin", "manager"]),
    "cash.report.read": Object.freeze(["owner", "admin", "manager", "cashier"]),
    "stock.adjust": Object.freeze(["owner", "admin", "manager"]),
    "stock.movement.read": Object.freeze(["owner", "admin", "manager", "cashier"]),
  }),
  web_bo_admin: Object.freeze({
    "pricing.read": Object.freeze(["owner", "admin"]),
    "pricing.write": Object.freeze(["owner", "admin"]),
    "cloud.config.read": Object.freeze(["owner", "admin"]),
    "cloud.config.write": Object.freeze(["owner", "admin"]),
    "users.roles.read": Object.freeze(["owner", "admin"]),
    "users.roles.write": Object.freeze(["owner", "admin"]),
    "tenant.lifecycle.read": Object.freeze(["owner", "admin"]),
    "tenant.lifecycle.write": Object.freeze(["owner", "admin"]),
    "tenant.backups.read": Object.freeze(["owner", "admin"]),
    "tenant.backups.write": Object.freeze(["owner", "admin"]),
  }),
} as const);

export type TenantScopeKey = "sameTenant" | "crossTenant" | "platform";

export const TENANT_SCOPE_ROLE_POLICIES: Readonly<Record<ChannelKey, Readonly<Record<TenantScopeKey, readonly string[]>>>> = Object.freeze({
  mobile_ops: Object.freeze({
    sameTenant: Object.freeze(["owner", "admin", "manager", "cashier"]),
    crossTenant: Object.freeze([]),
    platform: Object.freeze(["superadmin"]),
  }),
  web_bo_admin: Object.freeze({
    sameTenant: Object.freeze(["owner", "admin"]),
    crossTenant: Object.freeze([]),
    platform: Object.freeze(["superadmin"]),
  }),
});

export const MODULE_ROLE_POLICIES = Object.freeze({
  dashboard: Object.freeze(["owner", "admin", "manager"]),
  pricing: Object.freeze(["owner", "admin"]),
  usersRoles: Object.freeze(["owner", "admin"]),
  cloudConfig: Object.freeze(["owner", "admin"]),
  tenantLifecycle: Object.freeze(["owner", "admin"]),
  maintenanceRead: Object.freeze(["owner", "admin"]),
  maintenanceWrite: Object.freeze(["owner", "admin"]),
  backupsRead: Object.freeze(["owner", "admin"]),
  backupsWrite: Object.freeze(["owner", "admin"]),
} as const);

export type ModulePolicyKey = keyof typeof MODULE_ROLE_POLICIES;

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
  if (!normalizedRole) {
    return false;
  }
  return MODULE_POLICY_ROLE_SET[module].has(normalizedRole);
};

export const hasRoleForChannelInTenantScope = (
  role: unknown,
  channel: ChannelKey,
  tenantScope: TenantScopeKey
): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) {
    return false;
  }
  return TENANT_SCOPE_ROLE_SET[channel][tenantScope].has(normalizedRole);
};

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  channelCapabilityRolePolicies: CHANNEL_CAPABILITY_ROLE_POLICIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES,
});
