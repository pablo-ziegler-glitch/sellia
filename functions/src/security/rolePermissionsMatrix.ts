export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-02-24";

export const MODULE_ROLE_POLICIES = Object.freeze({
  dashboard: Object.freeze(["owner", "admin", "manager"]),
  pricing: Object.freeze(["owner", "admin"]),
  marketing: Object.freeze(["owner", "admin", "manager"]),
  users: Object.freeze(["owner", "admin"]),
  cloudServices: Object.freeze(["owner", "admin"]),
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

export const hasRoleForModule = (role: unknown, module: ModulePolicyKey): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) {
    return false;
  }
  return MODULE_POLICY_ROLE_SET[module].has(normalizedRole);
};
