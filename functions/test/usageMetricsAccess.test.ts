import { describe, expect, it } from "vitest";
import { authorizeUsageMetricsAccess } from "../src/usageMetricsAccess";

describe("authorizeUsageMetricsAccess", () => {
  it("throws unauthenticated when uid is missing", () => {
    try {
      authorizeUsageMetricsAccess({}, { auth: {} }, { role: "owner", tenantId: "t-1" });
      throw new Error("expected unauthenticated error");
    } catch (error: any) {
      expect(error.code).toBe("unauthenticated");
    }
  });

  it("throws permission-denied when tenant role is not allowed", () => {
    try {
      authorizeUsageMetricsAccess(
        { tenantId: "t-1" },
        { auth: { uid: "u-1", token: {} } },
        { role: "viewer", tenantId: "t-1" }
      );
      throw new Error("expected permission-denied error");
    } catch (error: any) {
      expect(error.code).toBe("permission-denied");
    }
  });

  it("allows owner access for same tenant", () => {
    const decision = authorizeUsageMetricsAccess(
      { tenantId: "t-1" },
      { auth: { uid: "u-1", token: {} } },
      { role: "owner", tenantId: "t-1" }
    );

    expect(decision).toMatchObject({
      uid: "u-1",
      scope: "tenant",
      requestedTenantId: "t-1",
    });
  });
});
