import { describe, expect, it } from "vitest";
import {
  adminRuntimeFlag,
  getBackgroundJobsConfig,
  parseRuntimeToggleInput,
  runBackgroundJobNow,
  setBackgroundJobConfig,
  setMainLandingConfig,
} from "../src/admin";

describe("admin domain integration", () => {
  it("expone handlers administrativos", () => {
    expect(setMainLandingConfig).toBeTruthy();
    expect(getBackgroundJobsConfig).toBeTruthy();
    expect(setBackgroundJobConfig).toBeTruthy();
    expect(runBackgroundJobNow).toBeTruthy();
    expect(adminRuntimeFlag).toBe("admin.routes.enabled");
    expect(parseRuntimeToggleInput({ tenantId: "tenant-a", enabled: true })).toEqual({
      tenantId: "tenant-a",
      enabled: true,
    });
  });
});
