import { describe, expect, it, vi } from "vitest";

vi.mock("firebase-functions", () => {
  class HttpsError extends Error {
    code: string;
    constructor(code: string, message: string) {
      super(message);
      this.code = code;
    }
  }
  return { https: { HttpsError }, default: { https: { HttpsError } } };
});

import { requireAuth, requireRole, requireTenantScope, validateAndSanitize } from "../src/security/callableGuards";

describe("callableGuards", () => {
  it("allow requireAuth with uid", async () => {
    const log = vi.fn(async () => undefined);
    const uid = await requireAuth({ operation: "op", context: { auth: { uid: "u1" }, rawRequest: {} } as any, logSecurityEvent: log });
    expect(uid).toBe("u1");
  });

  it("deny requireTenantScope when tenant mismatch", async () => {
    const log = vi.fn(async () => undefined);
    await expect(
      requireTenantScope({
        operation: "op",
        uid: "u1",
        requestedTenantId: "t2",
        userData: { tenantId: "t1" },
        context: { auth: { token: {} } } as any,
        logSecurityEvent: log,
      })
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("deny requireRole when role not allowed", async () => {
    const log = vi.fn(async () => undefined);
    await expect(
      requireRole({
        operation: "op",
        uid: "u1",
        tenantId: "t1",
        context: { auth: { token: {} } } as any,
        userData: { role: "viewer" },
        allowedRoles: ["admin"],
        logSecurityEvent: log,
      })
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("validateAndSanitize returns parser output", async () => {
    const log = vi.fn(async () => undefined);
    const out = await validateAndSanitize({
      operation: "op",
      rawPayload: { tenantId: " t1 " },
      parser: (raw) => ({ tenantId: String((raw as any).tenantId).trim() }),
      logSecurityEvent: log,
    });
    expect(out).toEqual({ tenantId: "t1" });
  });
});
