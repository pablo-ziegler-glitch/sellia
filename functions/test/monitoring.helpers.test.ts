import { describe, expect, it } from "vitest";
import { getPointValue } from "../src/monitoring.helpers";

describe("getPointValue", () => {
  it("returns doubleValue when available", () => {
    expect(getPointValue({ value: { doubleValue: 12.5 } } as any)).toBe(12.5);
  });

  it("returns int64Value when doubleValue is not available", () => {
    expect(getPointValue({ value: { int64Value: "42" } } as any)).toBe(42);
    expect(getPointValue({ value: { int64Value: 7 } } as any)).toBe(7);
  });

  it("returns 0 for null/undefined or missing values", () => {
    expect(getPointValue({} as any)).toBe(0);
    expect(getPointValue({ value: {} } as any)).toBe(0);
    expect(getPointValue(undefined as any)).toBe(0);
    expect(getPointValue(null as any)).toBe(0);
  });
});
