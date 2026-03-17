import { describe, expect, it } from "vitest";
import { analyticsRuntimeFlag, collectUsageMetrics, parseUsageMetricsQuery } from "../src/analytics";

describe("analytics domain integration", () => {
  it("preserva funciones de analytics", () => {
    expect(collectUsageMetrics).toBeTruthy();
    expect(analyticsRuntimeFlag).toBe("analytics.routes.enabled");
    expect(parseUsageMetricsQuery({ tenantId: "tenant-a", period: "last_30d" })).toEqual({
      tenantId: "tenant-a",
      period: "last_30d",
    });
  });
});
