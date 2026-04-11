import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { google, monitoring_v3 } from "googleapis";
import { getPointValue } from "./monitoring.helpers";
import {
  buildUsageOverview,
  getCurrentDayKey,
  getNextMonthStart,
  type UsageCollectionError,
  type UsageCollectionOutcome,
  type UsageMetricResult,
  type UsageServiceMetrics,
} from "./usageMetrics.helpers";
import { authorizeUsageMetricsAccess } from "./usageMetricsAccess";
import {
  getBillingConfig as cfgGetBillingConfig,
  type BillingConfig,
  type UsageSource,
} from "./config/getters";
import type { PiiRole } from "./security/piiPolicy";

// ─── Types ───────────────────────────────────────────────────────────────────

export type { UsageSource, BillingConfig };

export type UsageServiceKey =
  | "firestore"
  | "auth"
  | "storage"
  | "functions"
  | "hosting"
  | "other";

export type UsageMetricDefinition = {
  service: UsageServiceKey;
  metricType: string;
  description: string;
  unit: string;
  perSeriesAligner?: string;
  crossSeriesReducer?: string;
};

export type UsageErrorsBlock = {
  count: number;
  items: UsageCollectionError[];
};

export type UsageSnapshot = {
  metrics?: Record<string, number>;
  usage?: Record<string, number>;
  counts?: Record<string, number>;
  periodKey?: string;
  period?: string;
  sourceStatus?: "success" | "partial_success";
  errors?: UsageCollectionError[] | UsageErrorsBlock;
  snapshotAt?: admin.firestore.Timestamp;
  createdAt?: admin.firestore.Timestamp;
};

export type FreeTierLimit = {
  metrics?: Record<string, number>;
  limits?: Record<string, number>;
  freeTier?: Record<string, number>;
};

export type UsageAlertPayload = {
  tenantId: string;
  alertId: string;
  metric: string;
  threshold: number;
  percentage: number;
  currentValue: number;
  limitValue: number;
  severity: string;
  title: string;
  message: string;
  periodKey: string;
};

export type UsageHistoryItem = {
  monthKey: string;
  source: UsageSource;
  sourceStatus: "success" | "partial_success";
  overview: Record<string, Record<string, number>>;
  period: {
    start: string;
    end: string;
    nextResetAt?: string;
  };
  errors: UsageErrorsBlock;
  updatedAt: string | null;
};

// ─── Constants ───────────────────────────────────────────────────────────────

export const USAGE_COLLECTION = "usageMetricsMonthly";
export const USAGE_CURRENT_COLLECTION = "usageMetricsCurrent";
export const USAGE_CURRENT_DOC_ID = "current";
export const USAGE_DAILY_SNAPSHOTS_COLLECTION = "dailySnapshots";
export const ALERT_THRESHOLDS = [70, 90, 100];

const ADMIN_ROLES = new Set(["owner"]);

const METRIC_FLAT_KEY_MAP: Record<string, string> = {
  "firestore.googleapis.com/document/read_count": "firestore_reads",
  "firestore.googleapis.com/document/write_count": "firestore_writes",
  "firestore.googleapis.com/document/delete_count": "firestore_deletes",
  "storage.googleapis.com/storage/total_bytes": "storage_bytes",
  "cloudfunctions.googleapis.com/function/execution_count": "functions_invocations",
  "firebasehosting.googleapis.com/request_count": "hosting_request_count",
  "identitytoolkit.googleapis.com/usage_count": "auth_monthly_active_users",
};

const DEFAULT_FREE_TIER_LIMITS: FreeTierLimit = {
  metrics: {
    firestore_reads: 50_000,
    firestore_writes: 20_000,
    firestore_deletes: 20_000,
    storage_bytes: 5_368_709_120,
    auth_monthly_active_users: 10_000,
    hosting_request_count: 10_000_000,
  },
};

const USAGE_METRICS: UsageMetricDefinition[] = [
  {
    service: "firestore",
    metricType: "firestore.googleapis.com/document/read_count",
    description: "Firestore document reads",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
  {
    service: "firestore",
    metricType: "firestore.googleapis.com/document/write_count",
    description: "Firestore document writes",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
  {
    service: "firestore",
    metricType: "firestore.googleapis.com/document/delete_count",
    description: "Firestore document deletes",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
  {
    service: "storage",
    metricType: "storage.googleapis.com/storage/total_bytes",
    description: "Cloud Storage total bytes",
    unit: "bytes",
    perSeriesAligner: "ALIGN_MEAN",
    crossSeriesReducer: "REDUCE_MEAN",
  },
  {
    service: "functions",
    metricType: "cloudfunctions.googleapis.com/function/execution_count",
    description: "Cloud Functions executions",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
  {
    service: "hosting",
    metricType: "firebasehosting.googleapis.com/request_count",
    description: "Firebase Hosting requests",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
  {
    service: "auth",
    metricType: "identitytoolkit.googleapis.com/usage_count",
    description: "Firebase Auth usage",
    unit: "count",
    perSeriesAligner: "ALIGN_SUM",
    crossSeriesReducer: "REDUCE_SUM",
  },
];

const SERVICE_ALIAS_MAP: Record<string, UsageServiceKey> = {
  "Cloud Firestore": "firestore",
  Firestore: "firestore",
  "Cloud Storage": "storage",
  "Cloud Functions": "functions",
  "Firebase Hosting": "hosting",
  "Firebase Authentication": "auth",
};

// ─── Pure helpers ─────────────────────────────────────────────────────────────

export const getMonthRange = (referenceDate: Date): { start: Date; end: Date } => {
  const start = new Date(
    Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth(), 1, 0, 0, 0)
  );
  const end = new Date(referenceDate);
  return { start, end };
};

export const getMonthKey = (referenceDate: Date): string => {
  const year = referenceDate.getUTCFullYear();
  const month = String(referenceDate.getUTCMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
};

export const toNumberMap = (value: unknown): Record<string, number> => {
  if (!value || typeof value !== "object") {
    return {};
  }
  const map: Record<string, number> = {};
  Object.entries(value as Record<string, unknown>).forEach(([key, raw]) => {
    const numberValue = Number(raw);
    if (Number.isFinite(numberValue)) {
      map[key] = numberValue;
    }
  });
  return map;
};

const firstNonEmptyMap = (...maps: Array<Record<string, number>>): Record<string, number> => {
  for (const map of maps) {
    if (Object.keys(map).length > 0) {
      return map;
    }
  }
  return {};
};

export const resolveUsageMetrics = (snapshot: UsageSnapshot | null): Record<string, number> => {
  if (!snapshot) return {};
  return firstNonEmptyMap(
    toNumberMap(snapshot.metrics),
    toNumberMap(snapshot.usage),
    toNumberMap(snapshot.counts),
    toNumberMap(snapshot as Record<string, unknown>)
  );
};

export const resolveLimitMetrics = (limit: FreeTierLimit | null): Record<string, number> => {
  if (!limit) return {};
  return firstNonEmptyMap(
    toNumberMap(limit.metrics),
    toNumberMap(limit.limits),
    toNumberMap(limit.freeTier),
    toNumberMap(limit as Record<string, unknown>)
  );
};

export const resolvePeriodKey = (snapshot: UsageSnapshot | null): string => {
  if (!snapshot) return "current";
  const explicit = snapshot.periodKey ?? snapshot.period;
  if (explicit) return String(explicit);
  const date = snapshot.snapshotAt?.toDate() ?? snapshot.createdAt?.toDate();
  if (!date) return "current";
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}${month}${day}`;
};

export const extractSnapshotErrors = (snapshot: UsageSnapshot | null): UsageCollectionError[] => {
  if (!snapshot?.errors) {
    return [];
  }
  if (Array.isArray(snapshot.errors)) {
    return snapshot.errors;
  }
  return Array.isArray(snapshot.errors.items) ? snapshot.errors.items : [];
};

export const isPartialUsageSnapshot = (snapshot: UsageSnapshot | null): boolean => {
  if (!snapshot) {
    return false;
  }
  if (snapshot.sourceStatus === "partial_success") {
    return true;
  }
  return extractSnapshotErrors(snapshot).length > 0;
};

export const severityForThreshold = (threshold: number): string => {
  if (threshold >= 100) return "critical";
  if (threshold >= 90) return "high";
  return "warning";
};

export const formatAlertTitle = (metric: string, percentage: number): string =>
  `Uso de ${metric} en ${percentage}%`;

export const formatAlertMessage = (
  metric: string,
  percentage: number,
  currentValue: number,
  limitValue: number
): string => `El ${metric} alcanzó ${percentage}% del límite (${currentValue}/${limitValue}).`;

export const sanitizeAlertId = (value: string): string => value.replace(/[^a-zA-Z0-9_-]/g, "_");

// ─── Monitoring / BigQuery collection helpers ─────────────────────────────────

export const summarizeError = (error: unknown): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  if (typeof error === "string" && error.trim().length > 0) {
    return error;
  }
  try {
    const serialized = JSON.stringify(error);
    if (serialized && serialized !== "{}") {
      return serialized;
    }
  } catch {
    // ignore
  }
  return "Unknown error";
};

const accumulateServiceMetrics = (
  serviceMetrics: UsageServiceMetrics,
  metric: UsageMetricResult
): void => {
  serviceMetrics.metrics.push(metric);
  const current = serviceMetrics.totalsByUnit[metric.unit] ?? 0;
  serviceMetrics.totalsByUnit[metric.unit] = current + metric.value;
};

const sumMonitoringMetric = async (
  projectId: string,
  metric: UsageMetricDefinition,
  startTime: Date,
  endTime: Date
): Promise<UsageMetricResult> => {
  const monitoring = google.monitoring("v3");
  const response = await monitoring.projects.timeSeries.list({
    name: `projects/${projectId}`,
    filter: `metric.type="${metric.metricType}"`,
    "interval.startTime": startTime.toISOString(),
    "interval.endTime": endTime.toISOString(),
    "aggregation.alignmentPeriod": "86400s",
    "aggregation.perSeriesAligner": metric.perSeriesAligner ?? "ALIGN_SUM",
    "aggregation.crossSeriesReducer": metric.crossSeriesReducer ?? "REDUCE_SUM",
    view: "FULL",
  });

  const series = response.data.timeSeries ?? [];
  const total = series.reduce((sum: number, item: monitoring_v3.Schema$TimeSeries) => {
    const points = item.points ?? [];
    return (
      sum +
      points.reduce(
        (innerSum: number, point: monitoring_v3.Schema$Point) => innerSum + getPointValue(point),
        0
      )
    );
  }, 0);

  return {
    metricType: metric.metricType,
    description: metric.description,
    value: total,
    unit: metric.unit,
  };
};

const collectMonitoringUsage = async (
  config: BillingConfig,
  startTime: Date,
  endTime: Date
): Promise<UsageCollectionOutcome> => {
  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/monitoring.read"],
  });
  google.options({ auth });

  const services: Record<UsageServiceKey, UsageServiceMetrics> = {
    firestore: { metrics: [], totalsByUnit: {} },
    auth: { metrics: [], totalsByUnit: {} },
    storage: { metrics: [], totalsByUnit: {} },
    functions: { metrics: [], totalsByUnit: {} },
    hosting: { metrics: [], totalsByUnit: {} },
    other: { metrics: [], totalsByUnit: {} },
  };

  const results = await Promise.allSettled(
    USAGE_METRICS.map((metric) =>
      sumMonitoringMetric(config.projectId, metric, startTime, endTime).then((result) => ({
        result,
        service: metric.service,
      }))
    )
  );

  const errors: UsageCollectionError[] = [];

  results.forEach((result, index) => {
    const metricDefinition = USAGE_METRICS[index];

    if (result.status === "fulfilled") {
      accumulateServiceMetrics(services[result.value.service], result.value.result);
      return;
    }

    const errorMessage = summarizeError(result.reason);
    errors.push({
      metricType: metricDefinition.metricType,
      message: errorMessage,
    });

    console.error("Monitoring metric collection failed", {
      metricType: metricDefinition.metricType,
      error: errorMessage,
    });
  });

  return {
    services,
    errors,
    sourceStatus: errors.length > 0 ? "partial_success" : "success",
  };
};

const mapBigQueryService = (serviceDescription: string): UsageServiceKey =>
  SERVICE_ALIAS_MAP[serviceDescription] ?? "other";

const parseBigQueryValue = (value: unknown): number => {
  if (typeof value === "number") {
    return value;
  }
  if (typeof value === "string") {
    return Number(value);
  }
  return 0;
};

const collectBigQueryUsage = async (
  config: BillingConfig,
  startTime: Date,
  endTime: Date
): Promise<UsageCollectionOutcome> => {
  if (!config.bigqueryProjectId || !config.bigqueryDataset || !config.bigqueryTable) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "BigQuery billing export configuration is missing."
    );
  }

  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/bigquery"],
  });
  google.options({ auth });

  const bigquery = google.bigquery("v2");
  const query = `
    SELECT
      service.description AS service,
      usage.unit AS unit,
      SUM(usage.amount) AS usage_amount
    FROM \`${config.bigqueryProjectId}.${config.bigqueryDataset}.${config.bigqueryTable}\`
    WHERE usage_start_time >= @startTime
      AND usage_start_time < @endTime
    GROUP BY service, unit
  `;

  const response = await bigquery.jobs.query({
    projectId: config.bigqueryProjectId,
    requestBody: {
      query,
      useLegacySql: false,
      parameterMode: "NAMED",
      queryParameters: [
        {
          name: "startTime",
          parameterType: { type: "TIMESTAMP" },
          parameterValue: { value: startTime.toISOString() },
        },
        {
          name: "endTime",
          parameterType: { type: "TIMESTAMP" },
          parameterValue: { value: endTime.toISOString() },
        },
      ],
    },
  });

  const rows = response.data.rows ?? [];
  const services: Record<UsageServiceKey, UsageServiceMetrics> = {
    firestore: { metrics: [], totalsByUnit: {} },
    auth: { metrics: [], totalsByUnit: {} },
    storage: { metrics: [], totalsByUnit: {} },
    functions: { metrics: [], totalsByUnit: {} },
    hosting: { metrics: [], totalsByUnit: {} },
    other: { metrics: [], totalsByUnit: {} },
  };

  rows.forEach((row) => {
    const fields = row.f ?? [];
    const serviceDescription = String(fields[0]?.v ?? "Unknown");
    const unit = String(fields[1]?.v ?? "unit");
    const usageAmount = parseBigQueryValue(fields[2]?.v ?? 0);
    const serviceKey = mapBigQueryService(serviceDescription);

    const metricResult: UsageMetricResult = {
      metricType: serviceDescription,
      description: `BigQuery usage (${serviceDescription})`,
      value: usageAmount,
      unit,
    };

    accumulateServiceMetrics(services[serviceKey], metricResult);
  });

  return {
    services,
    errors: [],
    sourceStatus: "success",
  };
};

// ─── Firestore-dependent helpers ──────────────────────────────────────────────

export const fetchUsageSnapshot = async (
  tenantId: string,
  db: FirebaseFirestore.Firestore
): Promise<UsageSnapshot | null> => {
  const col = db.collection("tenants").doc(tenantId).collection("usageSnapshots");
  const currentDoc = await col.doc("current").get();
  if (currentDoc.exists) {
    return currentDoc.data() as UsageSnapshot;
  }
  const latest = await col.orderBy("snapshotAt", "desc").limit(1).get();
  if (!latest.empty) {
    return latest.docs[0].data() as UsageSnapshot;
  }
  const globalDoc = await db.collection(USAGE_CURRENT_COLLECTION).doc(USAGE_CURRENT_DOC_ID).get();
  if (globalDoc.exists) {
    const globalData = globalDoc.data();
    const metrics = globalData?.metrics as Record<string, number> | undefined;
    if (metrics && Object.keys(metrics).length > 0) {
      return {
        metrics,
        periodKey: globalData?.monthKey,
        sourceStatus: globalData?.sourceStatus,
        errors: globalData?.errors,
      } as UsageSnapshot;
    }
  }
  return null;
};

export const fetchFreeTierLimit = async (
  tenantId: string,
  db: FirebaseFirestore.Firestore
): Promise<FreeTierLimit | null> => {
  const col = db.collection("tenants").doc(tenantId).collection("freeTierLimits");
  const currentDoc = await col.doc("current").get();
  if (currentDoc.exists) {
    return currentDoc.data() as FreeTierLimit;
  }
  const fallbackDoc = await col.doc("default").get();
  if (fallbackDoc.exists) {
    return fallbackDoc.data() as FreeTierLimit;
  }
  const tenantDoc = await db.collection("tenants").doc(tenantId).get();
  const fallback = tenantDoc.get("freeTierLimits");
  if (fallback) {
    return fallback as FreeTierLimit;
  }
  return DEFAULT_FREE_TIER_LIMITS;
};

export const upsertUsageAlert = async (
  payload: UsageAlertPayload,
  db: FirebaseFirestore.Firestore
): Promise<{ created: boolean }> => {
  const alertRef = db
    .collection("tenants")
    .doc(payload.tenantId)
    .collection("alerts")
    .doc(payload.alertId);

  const created = await db.runTransaction(async (tx) => {
    const snap = await tx.get(alertRef);
    const data = {
      tenantId: payload.tenantId,
      metric: payload.metric,
      threshold: payload.threshold,
      percentage: payload.percentage,
      currentValue: payload.currentValue,
      limitValue: payload.limitValue,
      severity: payload.severity,
      title: payload.title,
      message: payload.message,
      periodKey: payload.periodKey,
      status: "active",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (snap.exists) {
      tx.update(alertRef, data);
      return false;
    }
    tx.set(alertRef, {
      ...data,
      readBy: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return true;
  });

  return { created };
};

const fetchAdminNotificationTargets = async (
  tenantId: string,
  db: FirebaseFirestore.Firestore
): Promise<string[]> => {
  const usersSnapshot = await db.collection("users").where("tenantId", "==", tenantId).get();
  const tokens = new Set<string>();
  usersSnapshot.docs.forEach((doc) => {
    const data = doc.data() ?? {};
    const role = String(data.role ?? "").trim().toLowerCase();
    const isAdminFlag = data.isAdmin === true || data.isSuperAdmin === true;
    if (!ADMIN_ROLES.has(role) && !isAdminFlag) {
      return;
    }
    const rawTokens = data.fcmTokens ?? data.fcmToken ?? [];
    const tokenList = Array.isArray(rawTokens) ? rawTokens : [rawTokens];
    tokenList
      .map((token) => String(token))
      .filter(Boolean)
      .forEach((token) => tokens.add(token));
  });
  return Array.from(tokens);
};

const notifyAdmins = async (
  tenantId: string,
  alertPayload: UsageAlertPayload,
  db: FirebaseFirestore.Firestore
): Promise<void> => {
  const tokens = await fetchAdminNotificationTargets(tenantId, db);
  if (tokens.length === 0) {
    console.info("No admin tokens found for usage alert", {
      tenantId,
      alertId: alertPayload.alertId,
    });
    return;
  }

  await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: alertPayload.title,
      body: alertPayload.message,
    },
    data: {
      alertId: alertPayload.alertId,
      tenantId: tenantId,
      metric: alertPayload.metric,
      threshold: String(alertPayload.threshold),
      percentage: String(alertPayload.percentage),
      severity: alertPayload.severity,
    },
  });
};

// ─── Handler factories ────────────────────────────────────────────────────────

export const createCollectUsageMetricsHandler =
  (db: FirebaseFirestore.Firestore) => async (): Promise<null> => {
    const config = cfgGetBillingConfig();
    const now = new Date();
    const { start, end } = getMonthRange(now);
    const monthKey = getMonthKey(now);
    const dayKey = getCurrentDayKey(now);
    const nextResetAt = getNextMonthStart(now).toISOString();

    const usageCollection =
      config.source === "bigquery"
        ? await collectBigQueryUsage(config, start, end)
        : await collectMonitoringUsage(config, start, end);

    const errorsBlock: UsageErrorsBlock = {
      count: usageCollection.errors.length,
      items: usageCollection.errors,
    };

    const usageOverview = buildUsageOverview(usageCollection.services);
    const flatMetrics: Record<string, number> = {};
    Object.values(usageCollection.services).forEach((serviceMetrics) => {
      serviceMetrics.metrics.forEach((metric) => {
        const flatKey = METRIC_FLAT_KEY_MAP[metric.metricType];
        if (flatKey) {
          flatMetrics[flatKey] = metric.value;
        }
      });
    });

    const monthDocRef = db.collection(USAGE_COLLECTION).doc(monthKey);

    await monthDocRef.set(
      {
        monthKey,
        source: config.source,
        sourceStatus: usageCollection.sourceStatus,
        errors: errorsBlock,
        period: {
          start: start.toISOString(),
          end: end.toISOString(),
          nextResetAt,
        },
        services: usageCollection.services,
        overview: usageOverview,
        accumulatedMonthToDate: true,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    await Promise.all([
      db
        .collection(USAGE_CURRENT_COLLECTION)
        .doc(USAGE_CURRENT_DOC_ID)
        .set(
          {
            monthKey,
            source: config.source,
            sourceStatus: usageCollection.sourceStatus,
            errors: errorsBlock,
            period: {
              start: start.toISOString(),
              end: end.toISOString(),
              nextResetAt,
            },
            overview: usageOverview,
            metrics: flatMetrics,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        ),
      monthDocRef.collection(USAGE_DAILY_SNAPSHOTS_COLLECTION).doc(dayKey).set(
        {
          dayKey,
          monthKey,
          sourceStatus: usageCollection.sourceStatus,
          errors: errorsBlock,
          period: {
            start: start.toISOString(),
            end: end.toISOString(),
          },
          overview: usageOverview,
          services: usageCollection.services,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      ),
    ]);

    console.info("Usage metrics collected", {
      monthKey,
      dayKey,
      source: config.source,
      sourceStatus: usageCollection.sourceStatus,
      errors: usageCollection.errors.length,
      nextResetAt,
    });

    return null;
  };

type GetUsageMetricsHistoryDeps = {
  db: FirebaseFirestore.Firestore;
  normalizeString: (value: unknown) => string;
  sanitizePiiForLog: (params: {
    domain: "users" | "customers" | "logs" | "exports";
    role?: PiiRole | null;
    fields: Record<string, unknown>;
  }) => Record<string, unknown>;
};

export const createGetUsageMetricsHistoryHandler =
  ({ db, normalizeString, sanitizePiiForLog }: GetUsageMetricsHistoryDeps) =>
  async (data: unknown, context: functions.https.CallableContext) => {
    const uid = normalizeString(context.auth?.uid);
    if (!uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const requestedLimit = Number((data as { limit?: number } | undefined)?.limit ?? 6);
    const limit = Number.isFinite(requestedLimit)
      ? Math.min(Math.max(Math.trunc(requestedLimit), 1), 24)
      : 6;

    const userDoc = await db.collection("users").doc(uid).get();
    const decision = authorizeUsageMetricsAccess(
      data as { tenantId?: unknown } | undefined,
      {
        auth: {
          uid,
          token: {
            superAdmin: context.auth?.token?.superAdmin === true,
          },
        },
      },
      userDoc.exists ? userDoc.data() : undefined
    );

    const query =
      decision.scope === "tenant"
        ? db.collection("tenants").doc(decision.requestedTenantId).collection(USAGE_COLLECTION)
        : db.collection(USAGE_COLLECTION);

    const snapshot = await query.orderBy("monthKey", "desc").limit(limit).get();

    const history: UsageHistoryItem[] = snapshot.docs.map((doc) => {
      const raw = doc.data() as Record<string, unknown>;
      const updatedAt = raw.updatedAt as admin.firestore.Timestamp | undefined;

      return {
        monthKey: normalizeString(raw.monthKey) || doc.id,
        source: normalizeString(raw.source) === "bigquery" ? "bigquery" : "monitoring",
        sourceStatus: raw.sourceStatus === "partial_success" ? "partial_success" : "success",
        overview: (raw.overview as Record<string, Record<string, number>> | undefined) ?? {},
        period: (raw.period as UsageHistoryItem["period"] | undefined) ?? {
          start: "",
          end: "",
        },
        errors:
          (raw.errors as UsageErrorsBlock | undefined) ?? {
            count: 0,
            items: [],
          },
        updatedAt: updatedAt?.toDate().toISOString() ?? null,
      };
    });

    const auditPayload = {
      eventType: "metrics_access",
      action: "USAGE_METRICS_HISTORY_READ",
      actorUid: decision.uid,
      scope: decision.scope,
      status: "success",
      targetTenantId: decision.requestedTenantId || null,
      limit,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (decision.scope === "tenant") {
      await db
        .collection("tenants")
        .doc(decision.requestedTenantId)
        .collection("audit_logs")
        .doc()
        .set(auditPayload);
    } else {
      await db.collection("audit_logs").doc().set(auditPayload);
    }

    console.info("Usage metrics accessed", {
      ...sanitizePiiForLog({
        domain: "logs",
        role: "auditor",
        fields: {
          actorUid: decision.uid,
        },
      }),
      role: decision.role,
      scope: decision.scope,
      tenantId: decision.requestedTenantId || null,
      limit,
    });

    return {
      items: history,
      limit,
      scope: decision.scope,
      tenantId: decision.requestedTenantId || null,
    };
  };

export const createEvaluateUsageAlertsHandler =
  (db: FirebaseFirestore.Firestore) => async (): Promise<null> => {
    const tenantsSnapshot = await db.collection("tenants").get();
    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      const usageSnapshot = await fetchUsageSnapshot(tenantId, db);
      const freeTierLimit = await fetchFreeTierLimit(tenantId, db);
      const usageMetrics = resolveUsageMetrics(usageSnapshot);
      const limitMetrics = resolveLimitMetrics(freeTierLimit);
      const periodKey = resolvePeriodKey(usageSnapshot);

      if (!usageSnapshot || Object.keys(usageMetrics).length === 0) {
        console.info("No usage snapshot available for tenant", { tenantId });
        continue;
      }
      if (isPartialUsageSnapshot(usageSnapshot)) {
        const snapshotErrors = extractSnapshotErrors(usageSnapshot);
        console.info("Skipping usage alerts for partial snapshot", {
          tenantId,
          periodKey,
          errors: snapshotErrors.length,
          sourceStatus: usageSnapshot.sourceStatus ?? "unknown",
        });
        continue;
      }
      if (!freeTierLimit || Object.keys(limitMetrics).length === 0) {
        console.info("No free tier limits available for tenant", { tenantId });
        continue;
      }

      for (const [metric, limitValue] of Object.entries(limitMetrics)) {
        const currentValue = usageMetrics[metric] ?? 0;
        if (!Number.isFinite(limitValue) || limitValue <= 0) {
          continue;
        }
        const percentage = Math.floor((currentValue / limitValue) * 100);
        for (const threshold of ALERT_THRESHOLDS) {
          if (percentage < threshold) {
            continue;
          }
          const severity = severityForThreshold(threshold);
          const alertId = sanitizeAlertId(`${metric}_${threshold}_${periodKey}`);
          const payload: UsageAlertPayload = {
            tenantId,
            alertId,
            metric,
            threshold,
            percentage,
            currentValue,
            limitValue,
            severity,
            title: formatAlertTitle(metric, percentage),
            message: formatAlertMessage(metric, percentage, currentValue, limitValue),
            periodKey,
          };
          const result = await upsertUsageAlert(payload, db);
          if (result.created) {
            await notifyAdmins(tenantId, payload, db);
          }
        }
      }
    }
    return null;
  };

export const createGetTenantCostDashboardHandler =
  (db: FirebaseFirestore.Firestore, normalizeString: (value: unknown) => string) =>
  async (data: unknown, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);
    if (!tenantId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId requerido");
    }

    const userDoc = await db.collection("users").doc(context.auth.uid).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const userData = userDoc.data() || {};
    const userRole = normalizeString(userData.role).toLowerCase();
    const isSuperAdmin = context.auth.token.superAdmin === true || userRole === "superadmin";
    if (!isSuperAdmin && normalizeString(userData.tenantId) !== tenantId) {
      throw new functions.https.HttpsError("permission-denied", "tenant inválido para el usuario");
    }

    if (!isSuperAdmin && userRole !== "owner") {
      throw new functions.https.HttpsError("permission-denied", "sin permisos para ver costos");
    }

    const [monthlySnap, budgetDoc, usageCurrent] = await Promise.all([
      db
        .collection("tenants")
        .doc(tenantId)
        .collection(USAGE_COLLECTION)
        .orderBy("monthKey", "desc")
        .limit(6)
        .get(),
      db.collection("tenants").doc(tenantId).collection("cost_budgets").doc("monthly").get(),
      db.collection("tenants").doc(tenantId).collection("usageSnapshots").doc("current").get(),
    ]);

    const monthly = monthlySnap.docs.map((monthDoc) => {
      const row = monthDoc.data() as Record<string, unknown>;
      return {
        monthKey: normalizeString(row.monthKey) || monthDoc.id,
        overview: (row.overview as Record<string, Record<string, number>> | undefined) ?? {},
        sourceStatus: normalizeString(row.sourceStatus) || "success",
        period: (row.period as Record<string, unknown> | undefined) ?? null,
      };
    });

    const budgetData = (budgetDoc.data() || {}) as Record<string, unknown>;
    const budgetByService = toNumberMap(budgetData.budgetByService);
    const budgetTotal = Number(budgetData.totalBudget) || 0;

    const usageData = (usageCurrent.data() || {}) as Record<string, unknown>;
    const costByService = toNumberMap(usageData.costByService);
    const currentTotalCost = Object.values(costByService).reduce((acc, v) => acc + v, 0);

    return {
      tenantId,
      generatedAt: new Date().toISOString(),
      budget: {
        total: budgetTotal,
        byService: budgetByService,
      },
      currentCost: {
        total: currentTotalCost,
        byService: costByService,
      },
      monthly,
    };
  };

export const createEvaluateTenantBudgetAlertsHandler =
  (db: FirebaseFirestore.Firestore) => async (): Promise<null> => {
    const tenantsSnapshot = await db.collection("tenants").get();

    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      const [budgetDoc, usageCurrent] = await Promise.all([
        db.collection("tenants").doc(tenantId).collection("cost_budgets").doc("monthly").get(),
        db.collection("tenants").doc(tenantId).collection("usageSnapshots").doc("current").get(),
      ]);

      if (!budgetDoc.exists || !usageCurrent.exists) {
        continue;
      }

      const budgetData = budgetDoc.data() || {};
      const usageData = usageCurrent.data() || {};
      const budgetTotal = Number(budgetData.totalBudget);
      const costByService = toNumberMap(usageData.costByService);
      const totalCost = Object.values(costByService).reduce((acc, value) => acc + value, 0);

      if (!Number.isFinite(budgetTotal) || budgetTotal <= 0 || totalCost <= 0) {
        continue;
      }

      const percent = Math.round((totalCost / budgetTotal) * 100);
      if (percent < 80) {
        continue;
      }

      const now = new Date();
      const monthKey = `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
      const alertId = `budget_${monthKey}_${percent >= 100 ? "100" : "80"}`;

      await db
        .collection("tenants")
        .doc(tenantId)
        .collection("budget_alerts")
        .doc(alertId)
        .set(
          {
            tenantId,
            monthKey,
            threshold: percent >= 100 ? 100 : 80,
            percent,
            totalCost,
            budgetTotal,
            status: percent >= 100 ? "critical" : "warning",
            message:
              percent >= 100
                ? `Costo mensual excedido (${totalCost}/${budgetTotal}).`
                : `Costo mensual en ${percent}% del presupuesto (${totalCost}/${budgetTotal}).`,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
    }

    return null;
  };
