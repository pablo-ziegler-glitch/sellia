export const RuntimeFlagKeys = {
  ORDERS_ROUTES: "orders.routes.enabled",
  NOTIFICATIONS_ROUTES: "notifications.routes.enabled",
  PAYMENTS_ROUTES: "payments.routes.enabled",
  CATALOG_ROUTES: "catalog.routes.enabled",
  ANALYTICS_ROUTES: "analytics.routes.enabled",
  ADMIN_ROUTES: "admin.routes.enabled",
} as const;

export type RuntimeFlagKey = (typeof RuntimeFlagKeys)[keyof typeof RuntimeFlagKeys];

export type RuntimeFlagsSnapshot = Partial<Record<RuntimeFlagKey, boolean>>;

export const isRuntimeFlagEnabled = (
  snapshot: RuntimeFlagsSnapshot | null | undefined,
  key: RuntimeFlagKey,
  fallback = true,
): boolean => {
  if (!snapshot) return fallback;
  const value = snapshot[key];
  return typeof value === "boolean" ? value : fallback;
};

export const getDefaultRuntimeFlags = (): RuntimeFlagsSnapshot => ({
  [RuntimeFlagKeys.ORDERS_ROUTES]: true,
  [RuntimeFlagKeys.NOTIFICATIONS_ROUTES]: true,
  [RuntimeFlagKeys.PAYMENTS_ROUTES]: true,
  [RuntimeFlagKeys.CATALOG_ROUTES]: true,
  [RuntimeFlagKeys.ANALYTICS_ROUTES]: true,
  [RuntimeFlagKeys.ADMIN_ROUTES]: false,
});
