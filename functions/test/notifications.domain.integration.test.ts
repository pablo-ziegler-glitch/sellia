import { describe, expect, it } from "vitest";
import { createDailyTenantBackups, parseNotificationDispatchInput } from "../src/notifications";

describe("notifications domain integration", () => {
  it("mantiene handlers batch y contratos", () => {
    expect(createDailyTenantBackups).toBeTruthy();
    expect(parseNotificationDispatchInput({ tenantId: "tenant-a", templateId: "backup-ready" })).toEqual({
      tenantId: "tenant-a",
      templateId: "backup-ready",
    });
  });
});
