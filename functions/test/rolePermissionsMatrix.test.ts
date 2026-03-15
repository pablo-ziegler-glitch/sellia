import { describe, expect, it } from "vitest";
import { hasRoleForModule, MODULE_ROLE_POLICIES } from "../src/security/rolePermissionsMatrix";

describe("rolePermissionsMatrix critical regression", () => {
  const CRITICAL_MODULES = ["users", "cloudServices", "maintenanceWrite", "backupsWrite"] as const;

  it("limits critical modules to owner/admin only", () => {
    for (const module of CRITICAL_MODULES) {
      expect(MODULE_ROLE_POLICIES[module]).toEqual(["owner", "admin"]);
      expect(hasRoleForModule("owner", module)).toBe(true);
      expect(hasRoleForModule("admin", module)).toBe(true);
      expect(hasRoleForModule("manager", module)).toBe(false);
      expect(hasRoleForModule("cashier", module)).toBe(false);
      expect(hasRoleForModule("viewer", module)).toBe(false);
    }
  });

  it("normalizes role input before authorization", () => {
    expect(hasRoleForModule(" Owner ", "users")).toBe(true);
    expect(hasRoleForModule("ADMIN", "users")).toBe(true);
    expect(hasRoleForModule("", "users")).toBe(false);
    expect(hasRoleForModule(null, "users")).toBe(false);
  });
});
