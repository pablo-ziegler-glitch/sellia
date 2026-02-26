import { describe, expect, it } from "vitest";
import { PII_RETENTION_RULES, resolveFieldVisibility } from "../src/security/piiPolicy";

describe("piiPolicy", () => {
  it("applies stricter default visibility when role has no override", () => {
    expect(resolveFieldVisibility("customers", "documentNumber", "cashier")).toBe("masked");
    expect(resolveFieldVisibility("users", "phone", "manager")).toBe("masked");
  });

  it("allows role-specific visibility overrides", () => {
    expect(resolveFieldVisibility("users", "email", "admin")).toBe("full");
    expect(resolveFieldVisibility("logs", "requesterIp", "auditor")).toBe("partial");
  });

  it("returns full visibility for unknown fields", () => {
    expect(resolveFieldVisibility("users", "unknownField", "owner")).toBe("full");
  });

  it("defines retention windows by domain", () => {
    expect(PII_RETENTION_RULES.logs.maxAgeDays).toBe(180);
    expect(PII_RETENTION_RULES.exports.maxAgeDays).toBeLessThan(PII_RETENTION_RULES.users.maxAgeDays);
  });
});
