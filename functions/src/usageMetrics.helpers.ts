export type UsageMetricResult = {
  metricType: string;
  description: string;
  value: number;
  unit: string;
};

export type UsageServiceMetrics = {
  metrics: UsageMetricResult[];
  totalsByUnit: Record<string, number>;
};

export type UsageCollectionError = {
  metricType: string;
  message: string;
};

export type UsageCollectionOutcome = {
  services: Record<string, UsageServiceMetrics>;
  errors: UsageCollectionError[];
  sourceStatus: "success" | "partial_success";
};

export const getNextMonthStart = (referenceDate: Date): Date =>
  new Date(Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth() + 1, 1, 0, 0, 0));

export const buildUsageOverview = (
  services: Record<string, UsageServiceMetrics>
): Record<string, Record<string, number>> => {
  const overview: Record<string, Record<string, number>> = {};

  Object.entries(services).forEach(([serviceKey, serviceMetrics]) => {
    overview[serviceKey] = { ...serviceMetrics.totalsByUnit };
  });

  return overview;
};

export const getCurrentDayKey = (referenceDate: Date): string =>
  referenceDate.toISOString().slice(0, 10);
