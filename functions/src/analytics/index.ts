import * as legacy from "../legacyHandlers";
import { RuntimeFlagKeys } from "../config/runtime_flags";

export { parseUsageMetricsQuery, type UsageMetricsQueryDto } from "./contracts";

export const analyticsRuntimeFlag = RuntimeFlagKeys.ANALYTICS_ROUTES;
export const collectUsageMetrics = legacy.collectUsageMetrics;
export const getUsageMetricsHistory = legacy.getUsageMetricsHistory;
export const getTenantCostDashboard = legacy.getTenantCostDashboard;
export const evaluateUsageAlerts = legacy.evaluateUsageAlerts;
export const evaluateTenantBudgetAlerts = legacy.evaluateTenantBudgetAlerts;
export const createMaintenanceTask = legacy.createMaintenanceTask;
export const updateMaintenanceTask = legacy.updateMaintenanceTask;
