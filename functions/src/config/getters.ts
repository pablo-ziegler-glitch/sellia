import * as functions from "firebase-functions";
import {
  ADMIN_RATE_LIMIT_PER_MINUTE_PARAM,
  APP_CHECK_ENFORCEMENT_MODE_PARAM,
  BILLING_BIGQUERY_DATASET_PARAM,
  BILLING_BIGQUERY_PROJECT_PARAM,
  BILLING_BIGQUERY_TABLE_PARAM,
  BILLING_PROJECT_ID_PARAM,
  BILLING_SOURCE_PARAM,
  MP_ACCESS_TOKEN_PARAM,
  MP_AGED_PENDING_ALERT_MINUTES_PARAM,
  MP_RECONCILIATION_BATCH_SIZE_PARAM,
  MP_RECONCILIATION_PENDING_MINUTES_PARAM,
  MP_WEBHOOK_IP_ALLOWLIST_PARAM,
  MP_WEBHOOK_REPLAY_TTL_MS_PARAM,
  MP_WEBHOOK_SECRET_PARAM,
  MP_WEBHOOK_SECRET_REFS_PARAM,
  MP_WEBHOOK_SIGNATURE_WINDOW_MS_PARAM,
  getOptionalParam,
} from "./params";

export type UsageSource = "monitoring" | "bigquery";

export type BillingConfig = {
  source: UsageSource;
  projectId: string;
  bigqueryProjectId?: string;
  bigqueryDataset?: string;
  bigqueryTable?: string;
};

export type MpConfig = {
  accessToken: string;
};

export type AppCheckEnforcementMode = "monitor" | "enforce";

const MP_SIGNATURE_WINDOW_MS = 5 * 60 * 1000;
const DEFAULT_WEBHOOK_REPLAY_TTL_MS = 24 * 60 * 60 * 1000;
const MP_DEFAULT_ALLOWED_CIDRS = [
  "34.195.82.184/32",
  "100.24.156.160/32",
  "35.196.38.56/32",
  "44.217.34.150/32",
  "44.219.124.34/32",
] as const;

const parseBoundedInteger = (
  value: string | undefined,
  fallback: number,
  min: number,
  max: number
): number => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.min(Math.max(Math.trunc(parsed), min), max);
};

export const getMpConfig = (): MpConfig => {
  const accessToken = process.env.MP_ACCESS_TOKEN?.trim() ?? getOptionalParam(MP_ACCESS_TOKEN_PARAM);

  if (!accessToken) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Mercado Pago access token is missing."
    );
  }

  return { accessToken };
};

export const getMpWebhookSignatureWindowMs = (): number => {
  const configured =
    process.env.MP_WEBHOOK_SIGNATURE_WINDOW_MS ??
    getOptionalParam(MP_WEBHOOK_SIGNATURE_WINDOW_MS_PARAM) ??
    String(MP_SIGNATURE_WINDOW_MS);
  const parsed = Number(configured);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return MP_SIGNATURE_WINDOW_MS;
  }
  return Math.min(Math.trunc(parsed), 30 * 60 * 1000);
};

export const getMpWebhookReplayTtlMs = (): number => {
  const configured =
    process.env.MP_WEBHOOK_REPLAY_TTL_MS ??
    getOptionalParam(MP_WEBHOOK_REPLAY_TTL_MS_PARAM) ??
    String(DEFAULT_WEBHOOK_REPLAY_TTL_MS);
  const parsed = Number(configured);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return DEFAULT_WEBHOOK_REPLAY_TTL_MS;
  }
  return Math.min(Math.max(Math.trunc(parsed), 60_000), 7 * 24 * 60 * 60 * 1000);
};

export const parseWebhookSecretRefs = (): string[] => {
  const configured = process.env.MP_WEBHOOK_SECRET_REFS ?? getOptionalParam(MP_WEBHOOK_SECRET_REFS_PARAM) ?? "";

  return configured
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
};

export const getFallbackWebhookSecret = (): string =>
  process.env.MP_WEBHOOK_SECRET?.trim() ?? getOptionalParam(MP_WEBHOOK_SECRET_PARAM) ?? "";

export const getBillingConfig = (): BillingConfig => {
  const projectId =
    process.env.GCP_PROJECT ??
    process.env.GCLOUD_PROJECT ??
    process.env.BILLING_PROJECT_ID ??
    getOptionalParam(BILLING_PROJECT_ID_PARAM) ??
    "";
  if (!projectId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Billing projectId is missing."
    );
  }
  const rawSource = process.env.BILLING_SOURCE ?? getOptionalParam(BILLING_SOURCE_PARAM) ?? "monitoring";
  const source: UsageSource = rawSource === "bigquery" ? "bigquery" : "monitoring";

  return {
    source,
    projectId,
    bigqueryProjectId:
      process.env.BILLING_BIGQUERY_PROJECT ?? getOptionalParam(BILLING_BIGQUERY_PROJECT_PARAM),
    bigqueryDataset: process.env.BILLING_BIGQUERY_DATASET ?? getOptionalParam(BILLING_BIGQUERY_DATASET_PARAM),
    bigqueryTable: process.env.BILLING_BIGQUERY_TABLE ?? getOptionalParam(BILLING_BIGQUERY_TABLE_PARAM),
  };
};

export const getAppCheckEnforcementMode = (): AppCheckEnforcementMode => {
  const configured =
    process.env.APP_CHECK_ENFORCEMENT_MODE ??
    getOptionalParam(APP_CHECK_ENFORCEMENT_MODE_PARAM) ??
    "monitor";

  return configured.trim().toLowerCase() === "enforce" ? "enforce" : "monitor";
};

export const getAdminRateLimitPerMinute = (): number => {
  const configured = process.env.ADMIN_RATE_LIMIT_PER_MINUTE ?? getOptionalParam(ADMIN_RATE_LIMIT_PER_MINUTE_PARAM) ?? "20";
  const parsed = Number(configured);
  if (!Number.isFinite(parsed)) {
    return 20;
  }
  return Math.min(Math.max(Math.trunc(parsed), 5), 300);
};

export const getMpPendingReconciliationMinutes = (): number =>
  parseBoundedInteger(
    process.env.MP_RECONCILIATION_PENDING_MINUTES ??
      getOptionalParam(MP_RECONCILIATION_PENDING_MINUTES_PARAM) ??
      "15",
    15,
    5,
    1440
  );

export const getMpReconciliationBatchSize = (): number =>
  parseBoundedInteger(
    process.env.MP_RECONCILIATION_BATCH_SIZE ??
      getOptionalParam(MP_RECONCILIATION_BATCH_SIZE_PARAM) ??
      "100",
    100,
    1,
    500
  );

export const getAgedPendingAlertMinutes = (): number =>
  parseBoundedInteger(
    process.env.MP_AGED_PENDING_ALERT_MINUTES ??
      getOptionalParam(MP_AGED_PENDING_ALERT_MINUTES_PARAM) ??
      "120",
    120,
    10,
    10080
  );

export const getIpAllowlist = (): string[] => {
  const configured = process.env.MP_WEBHOOK_IP_ALLOWLIST ?? getOptionalParam(MP_WEBHOOK_IP_ALLOWLIST_PARAM) ?? "";

  const parsed = configured
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);

  return parsed.length > 0 ? parsed : [...MP_DEFAULT_ALLOWED_CIDRS];
};
