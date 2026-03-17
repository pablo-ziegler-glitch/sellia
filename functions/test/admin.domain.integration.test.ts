import { describe, expect, it } from "vitest";
import { adminRuntimeFlag, parseRuntimeToggleInput, setMainLandingConfig } from "../src/admin";

describe("admin domain integration", () => {
  it("expone handlers administrativos", () => {
    expect(setMainLandingConfig).toBeTruthy();
    expect(adminRuntimeFlag).toBe("admin.routes.enabled");
    expect(parseRuntimeToggleInput({ tenantId: "tenant-a", enabled: true })).toEqual({
      tenantId: "tenant-a",
      enabled: true,
    });
  });
});
