import { describe, expect, it } from "vitest";
import { buildUsageOverview, getCurrentDayKey, getNextMonthStart } from "../src/usageMetrics.helpers";

describe("usageMetrics.helpers", () => {
  it("buildUsageOverview clones totalsByUnit for every service", () => {
    const overview = buildUsageOverview({
      firestore: {
        metrics: [],
        totalsByUnit: { count: 12 },
      },
      auth: {
        metrics: [],
        totalsByUnit: { users: 5 },
      },
    });

    expect(overview).toEqual({
      firestore: { count: 12 },
      auth: { users: 5 },
    });
  });

  it("returns deterministic UTC day key", () => {
    const date = new Date("2025-02-19T16:44:12.000Z");
    expect(getCurrentDayKey(date)).toBe("2025-02-19");
  });

  it("returns next month reset timestamp in UTC", () => {
    const resetDate = getNextMonthStart(new Date("2025-02-19T16:44:12.000Z"));
    expect(resetDate.toISOString()).toBe("2025-03-01T00:00:00.000Z");
  });
});
