import { monitoring_v3 } from "googleapis";

export const getPointValue = (point: monitoring_v3.Schema$Point): number => {
  if (!point?.value) {
    return 0;
  }
  if (typeof point.value.doubleValue === "number") {
    return point.value.doubleValue;
  }
  if (point.value.int64Value !== undefined && point.value.int64Value !== null) {
    return Number(point.value.int64Value);
  }
  return 0;
};
