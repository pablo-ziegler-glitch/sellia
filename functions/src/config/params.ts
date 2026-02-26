import { defineString } from "firebase-functions/params";

export const MP_ACCESS_TOKEN_PARAM = defineString("MP_ACCESS_TOKEN");
export const MP_WEBHOOK_SECRET_PARAM = defineString("MP_WEBHOOK_SECRET");
export const BILLING_SOURCE_PARAM = defineString("BILLING_SOURCE", {
  default: "monitoring",
});
export const BILLING_PROJECT_ID_PARAM = defineString("BILLING_PROJECT_ID");
export const BILLING_BIGQUERY_PROJECT_PARAM = defineString("BILLING_BIGQUERY_PROJECT");
export const BILLING_BIGQUERY_DATASET_PARAM = defineString("BILLING_BIGQUERY_DATASET");
export const BILLING_BIGQUERY_TABLE_PARAM = defineString("BILLING_BIGQUERY_TABLE");
export const APP_CHECK_ENFORCEMENT_MODE_PARAM = defineString("APP_CHECK_ENFORCEMENT_MODE", {
  default: "monitor",
});
export const MP_WEBHOOK_IP_ALLOWLIST_PARAM = defineString("MP_WEBHOOK_IP_ALLOWLIST", {
  default: "",
});
export const MP_WEBHOOK_SECRET_REFS_PARAM = defineString("MP_WEBHOOK_SECRET_REFS", {
  default: "",
});
export const MP_WEBHOOK_SIGNATURE_WINDOW_MS_PARAM = defineString("MP_WEBHOOK_SIGNATURE_WINDOW_MS", {
  default: "300000",
});
export const MP_WEBHOOK_REPLAY_TTL_MS_PARAM = defineString("MP_WEBHOOK_REPLAY_TTL_MS", {
  default: "86400000",
});
export const ADMIN_RATE_LIMIT_PER_MINUTE_PARAM = defineString("ADMIN_RATE_LIMIT_PER_MINUTE", {
  default: "20",
});
export const MP_RECONCILIATION_PENDING_MINUTES_PARAM = defineString(
  "MP_RECONCILIATION_PENDING_MINUTES",
  {
    default: "15",
  }
);
export const MP_RECONCILIATION_BATCH_SIZE_PARAM = defineString("MP_RECONCILIATION_BATCH_SIZE", {
  default: "100",
});
export const MP_AGED_PENDING_ALERT_MINUTES_PARAM = defineString("MP_AGED_PENDING_ALERT_MINUTES", {
  default: "120",
});

export const getOptionalParam = (param: ReturnType<typeof defineString>): string | undefined => {
  try {
    const value = param.value();
    if (typeof value !== "string") {
      return undefined;
    }
    const normalized = value.trim();
    return normalized.length > 0 ? normalized : undefined;
  } catch (_error) {
    return undefined;
  }
};
