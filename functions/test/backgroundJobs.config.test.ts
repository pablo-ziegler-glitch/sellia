import { describe, expect, it } from "vitest";
import {
  intervalToMs,
  parseBackgroundJobRunInput,
  parseBackgroundJobUpdateInput,
  shouldRunBackgroundJobNow,
  type BackgroundJobConfigView,
} from "../src/backgroundJobs";

const baseConfig = (overrides: Partial<BackgroundJobConfigView> = {}): BackgroundJobConfigView => ({
  jobId: "refresh_public_products",
  name: "Refresh Public Products",
  description: "test",
  service: "firestore",
  mode: "automatic",
  active: true,
  intervalValue: 2,
  intervalUnit: "hours",
  intervalMs: 2 * 60 * 60 * 1000,
  environment: "test",
  schemaVersion: 1,
  updatedBy: null,
  updatedAt: null,
  lastRunAt: null,
  nextRunAt: null,
  lastDurationMs: null,
  lastResult: null,
  lastError: null,
  executionCount: 0,
  lastTrigger: null,
  costTier: "high",
  history: [],
  ...overrides,
});

describe("background jobs config", () => {
  it("parsea actualización válida de TTL", () => {
    const parsed = parseBackgroundJobUpdateInput({
      jobId: "refresh_public_products",
      mode: "automatic",
      active: true,
      intervalValue: 6,
      intervalUnit: "hours",
    });

    expect(parsed).toEqual({
      jobId: "refresh_public_products",
      mode: "automatic",
      active: true,
      intervalValue: 6,
      intervalUnit: "hours",
    });
  });

  it("rechaza input incompleto de TTL", () => {
    expect(() =>
      parseBackgroundJobUpdateInput({
        jobId: "refresh_public_products",
        intervalUnit: "hours",
      })
    ).toThrow(/intervalValue/);
  });

  it("valida límites de intervalo mínimo/máximo", () => {
    expect(() => intervalToMs(1, "minutes")).toThrow(/mínimo/);
    expect(() => intervalToMs(31, "days")).toThrow(/máximo/);
    expect(intervalToMs(6, "hours")).toBe(6 * 60 * 60 * 1000);
  });

  it("decide no correr si modo es on_demand", () => {
    const decision = shouldRunBackgroundJobNow({
      config: baseConfig({ mode: "on_demand" }),
      nowMs: Date.parse("2026-04-11T00:00:00.000Z"),
    });
    expect(decision.shouldRun).toBe(false);
    expect(decision.reason).toBe("on_demand_mode");
  });

  it("decide no correr si TTL no venció", () => {
    const nowMs = Date.parse("2026-04-11T10:00:00.000Z");
    const decision = shouldRunBackgroundJobNow({
      config: baseConfig({
        intervalMs: 2 * 60 * 60 * 1000,
        lastRunAt: "2026-04-11T09:10:00.000Z",
      }),
      nowMs,
    });
    expect(decision.shouldRun).toBe(false);
    expect(decision.reason).toBe("ttl_not_expired");
  });

  it("decide correr si TTL venció", () => {
    const nowMs = Date.parse("2026-04-11T10:00:00.000Z");
    const decision = shouldRunBackgroundJobNow({
      config: baseConfig({
        intervalMs: 30 * 60 * 1000,
        lastRunAt: "2026-04-11T08:00:00.000Z",
      }),
      nowMs,
    });
    expect(decision.shouldRun).toBe(true);
    expect(decision.reason).toBe("ttl_expired");
  });

  it("parsea run manual con jobId válido", () => {
    expect(parseBackgroundJobRunInput({ jobId: "collect_usage_metrics" })).toEqual({
      jobId: "collect_usage_metrics",
    });
  });
});
