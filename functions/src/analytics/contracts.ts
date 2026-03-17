import { ensureObject, ensureString } from "../contracts.validation";

export type UsageMetricsQueryDto = {
  tenantId: string;
  period: string;
};

export const parseUsageMetricsQuery = (payload: unknown): UsageMetricsQueryDto => {
  const body = ensureObject(payload, "analytics.usageMetrics.query");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    period: ensureString(body.period, "period", { min: 2, max: 24 }),
  };
};
