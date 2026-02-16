import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import axios from "axios";
import { createHash, createHmac, timingSafeEqual } from "crypto";
import { google, monitoring_v3 } from "googleapis";
import { getPointValue } from "./monitoring.helpers";

admin.initializeApp();

const db = admin.firestore();

type UsageSource = "monitoring" | "bigquery";

type UsageServiceKey =
  | "firestore"
  | "auth"
  | "storage"
  | "functions"
  | "hosting"
  | "other";

type UsageMetricDefinition = {
  service: UsageServiceKey;
  metricType: string;
  description: string;
  unit: string;
  perSeriesAligner?: string;
  crossSeriesReducer?: string;
};

type UsageMetricResult = {
  metricType: string;
  description: string;
  value: number;
  unit: string;
};

type UsageCollectionError = {
  metricType: string;
  message: string;
};

type UsageServiceMetrics = {
  metrics: UsageMetricResult[];
  totalsByUnit: Record<string, number>;
};

type UsageCollectionOutcome = {
  services: Record<UsageServiceKey, UsageServiceMetrics>;
  errors: UsageCollectionError[];
  sourceStatus: "success" | "partial_success";
};

type UsageErrorsBlock = {
  count: number;
  items: UsageCollectionError[];
};

type BillingConfig = {
  source: UsageSource;
  projectId: string;
  bigqueryProjectId?: string;
  bigqueryDataset?: string;
  bigqueryTable?: string;
};

type MpConfig = {
  accessToken: string;
  webhookSecret: string;
};

type PreferenceItemInput = {
  title?: string;
  name?: string;
  quantity?: number;
  unit_price?: number;
  unitPrice?: number;
  currency_id?: string;
  currencyId?: string;
};

type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "FAILED";

type PaymentWebhookTransactionResult = {
  ignoredDuplicate: boolean;
  transitionApplied: boolean;
};

type ResolveTenantInput = {
  tenantIdFromReference: string;
  orderId: string;
  paymentId: string;
};

type ExternalReferenceData = {
  tenantId: string;
  orderId: string;
};

const MERCADOPAGO_API = "https://api.mercadopago.com";
const USAGE_COLLECTION = "usageMetricsMonthly";
const PAYMENT_STATUS_PRIORITY: Record<PaymentStatus, number> = {
  PENDING: 10,
  REJECTED: 20,
  FAILED: 20,
  APPROVED: 30,
};
const TERMINAL_PAYMENT_STATUSES = new Set<PaymentStatus>([
  "APPROVED",
  "REJECTED",
  "FAILED",
]);
const MP_SIGNATURE_WINDOW_MS = 5 * 60 * 1000;

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

type PublicProductPayload = {
  id: string;
  tenantId: string;
  code?: string | null;
  barcode?: string | null;
  name: string;
  sku?: string | null;
  storeName?: string | null;
  description?: string | null;
  brand?: string | null;
  parentCategory?: string | null;
  category?: string | null;
  color?: string | null;
  sizes: string[];
  listPrice?: number | null;
  cashPrice?: number | null;
  transferPrice?: number | null;
  imageUrl?: string | null;
  imageUrls: string[];
  updatedAt?: string | admin.firestore.FieldValue;
  publicUpdatedAt: admin.firestore.FieldValue;
};

const buildPublicProductPayload = (
  tenantId: string,
  productId: string,
  data: FirebaseFirestore.DocumentData
): PublicProductPayload => {
  const rawUrls = Array.isArray(data.imageUrls)
    ? data.imageUrls.filter((url) => typeof url === "string")
    : [];
  const legacyUrl = typeof data.imageUrl === "string" ? data.imageUrl : null;
  const imageUrls = [...new Set([legacyUrl, ...rawUrls].filter(Boolean))] as string[];

  return {
    id: productId,
    tenantId,
    code: data.code ?? null,
    barcode: data.barcode ?? null,
    name: data.name ?? "Producto",
    sku: data.sku ?? data.code ?? data.barcode ?? null,
    storeName: data.storeName ?? data.tenantName ?? null,
    description: data.description ?? null,
    brand: data.brand ?? null,
    parentCategory: data.parentCategory ?? null,
    category: data.category ?? null,
    color: data.color ?? null,
    sizes: Array.isArray(data.sizes)
      ? data.sizes.filter((size) => typeof size === "string")
      : [],
    listPrice: typeof data.listPrice === "number" ? data.listPrice : null,
    cashPrice: typeof data.cashPrice === "number" ? data.cashPrice : null,
    transferPrice:
      typeof data.transferPrice === "number" ? data.transferPrice : null,
    imageUrl: legacyUrl,
    imageUrls,
    updatedAt: data.updatedAt ?? admin.firestore.FieldValue.serverTimestamp(),
    publicUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
};

const getMpConfig = (): MpConfig => {
  const config = functions.config()?.mercadopago ?? {};
  const accessToken = process.env.MP_ACCESS_TOKEN ?? config.access_token;
  const webhookSecret = process.env.MP_WEBHOOK_SECRET ?? config.webhook_secret;

  if (!accessToken || !webhookSecret) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Mercado Pago credentials are missing."
    );
  }

  return { accessToken, webhookSecret };
};

const getBillingConfig = (): BillingConfig => {
  const config = functions.config()?.billing ?? {};
  const projectId =
    process.env.GCP_PROJECT ??
    process.env.GCLOUD_PROJECT ??
    config.project_id ??
    "";
  if (!projectId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Billing projectId is missing."
    );
  }
  const source = (process.env.BILLING_SOURCE ??
    config.source ??
    "monitoring") as UsageSource;
  return {
    source,
    projectId,
    bigqueryProjectId:
      process.env.BILLING_BIGQUERY_PROJECT ?? config.bigquery_project_id,
    bigqueryDataset:
      process.env.BILLING_BIGQUERY_DATASET ?? config.bigquery_dataset,
    bigqueryTable: process.env.BILLING_BIGQUERY_TABLE ?? config.bigquery_table,
  };
};

const normalizeString = (value: unknown): string => {
  if (typeof value === "string") {
    return value.trim();
  }
  if (typeof value === "number") {
    return String(value);
  }
  return "";
};

const extractTenantId = (payment: unknown): string => {
  const source = (payment ?? {}) as Record<string, unknown>;
  const metadata = (source.metadata ?? {}) as Record<string, unknown>;
  const additionalInfo = (source.additional_info ?? {}) as Record<string, unknown>;

  return normalizeString(
    metadata.tenantId ??
      metadata.tenant_id ??
      source.tenantId ??
      source.tenant_id ??
      additionalInfo.tenantId ??
      additionalInfo.tenant_id
  );
};

const buildExternalReference = (tenantId: string, orderId: string): string =>
  `tenant:${tenantId}|order:${orderId}`;

const parseExternalReference = (externalReference: unknown): ExternalReferenceData => {
  const raw = normalizeString(externalReference);
  if (!raw) {
    return { tenantId: "", orderId: "" };
  }

  const segments = raw
    .split("|")
    .map((segment) => segment.trim())
    .filter(Boolean);

  const tenantSegment = segments.find((segment) => segment.startsWith("tenant:"));
  const orderSegment = segments.find((segment) => segment.startsWith("order:"));

  return {
    tenantId: tenantSegment ? normalizeString(tenantSegment.slice("tenant:".length)) : "",
    orderId: orderSegment ? normalizeString(orderSegment.slice("order:".length)) : "",
  };
};

const resolveTenantId = async ({
  tenantIdFromReference,
  orderId,
  paymentId,
}: ResolveTenantInput): Promise<string> => {
  if (tenantIdFromReference) {
    return tenantIdFromReference;
  }

  if (orderId) {
    console.info("Mercado Pago tenant fallback started", {
      paymentId,
      orderId,
      strategy: "orders_collection_group",
    });

    const orderLookup = await db
      .collectionGroup("orders")
      .where(admin.firestore.FieldPath.documentId(), "==", orderId)
      .limit(1)
      .get();

    if (!orderLookup.empty) {
      console.info("Mercado Pago tenant fallback resolved", {
        paymentId,
        orderId,
        strategy: "orders_collection_group",
      });
      return orderLookup.docs[0].ref.parent.parent?.id ?? "";
    }

    console.info("Mercado Pago tenant fallback unresolved", {
      paymentId,
      orderId,
      strategy: "orders_collection_group",
    });
  }

  return "";
};

const mapPaymentStatus = (status: unknown): PaymentStatus => {
  const normalized = normalizeString(status).toLowerCase();
  if (normalized === "approved") {
    return "APPROVED";
  }
  if (normalized === "pending" || normalized === "in_process") {
    return "PENDING";
  }
  if (normalized === "rejected" || normalized === "cancelled" || normalized === "charged_back") {
    return "REJECTED";
  }
  return "FAILED";
};

const parseStoredPaymentStatus = (status: unknown): PaymentStatus | null => {
  if (status === "PENDING" || status === "APPROVED" || status === "REJECTED" || status === "FAILED") {
    return status;
  }
  return null;
};

const canApplyPaymentTransition = (
  currentStatus: PaymentStatus | null,
  incomingStatus: PaymentStatus
): boolean => {
  if (!currentStatus) {
    return true;
  }
  if (currentStatus === incomingStatus) {
    return true;
  }
  if (TERMINAL_PAYMENT_STATUSES.has(currentStatus)) {
    return false;
  }
  return PAYMENT_STATUS_PRIORITY[incomingStatus] >= PAYMENT_STATUS_PRIORITY[currentStatus];
};

const buildPaymentEventKey = (input: {
  paymentId: string;
  paymentStatus: PaymentStatus;
  requestId: string;
  signatureTs: string;
}): string => {
  const payload = `${input.paymentId}|${input.paymentStatus}|${input.requestId}|${input.signatureTs}`;
  return createHash("sha256").update(payload).digest("hex");
};

const getMonthRange = (referenceDate: Date): { start: Date; end: Date } => {
  const start = new Date(
    Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth(), 1, 0, 0, 0)
  );
  const end = new Date(referenceDate);
  return { start, end };
};

const getMonthKey = (referenceDate: Date): string => {
  const year = referenceDate.getUTCFullYear();
  const month = String(referenceDate.getUTCMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
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

const accumulateServiceMetrics = (
  serviceMetrics: UsageServiceMetrics,
  metric: UsageMetricResult
): void => {
  serviceMetrics.metrics.push(metric);
  const current = serviceMetrics.totalsByUnit[metric.unit] ?? 0;
  serviceMetrics.totalsByUnit[metric.unit] = current + metric.value;
};

const summarizeError = (error: unknown): string => {
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
    // Ignore serialization failures and fall back to a stable message.
  }

  return "Unknown error";
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
      sumMonitoringMetric(config.projectId, metric, startTime, endTime).then(
        (result) => ({
          result,
          service: metric.service,
        })
      )
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

const mapBigQueryService = (serviceDescription: string): UsageServiceKey => {
  return SERVICE_ALIAS_MAP[serviceDescription] ?? "other";
};

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

const getDataId = (req: functions.https.Request): string => {
  const dataId =
    req.body?.data?.id ??
    req.query?.["data.id"] ??
    req.query?.["data[id]"] ??
    req.query?.["id"]; // fallback for test calls
  return dataId ? String(dataId) : "";
};

const parseMpSignatureHeader = (
  signatureHeader: string
): { ts: number; tsRaw: string; v1: string } | null => {
  if (!signatureHeader) {
    return null;
  }

  const segments = signatureHeader
    .split(",")
    .map((segment) => segment.trim())
    .filter(Boolean);

  let tsRaw = "";
  let v1 = "";

  for (const segment of segments) {
    const separatorIndex = segment.indexOf("=");
    if (separatorIndex <= 0) {
      continue;
    }
    const key = segment.slice(0, separatorIndex).trim().toLowerCase();
    const value = segment.slice(separatorIndex + 1).trim();

    if (key === "ts") {
      tsRaw = value;
      continue;
    }
    if (key === "v1") {
      v1 = value.toLowerCase();
    }
  }

  if (!tsRaw || !v1 || !/^\d+$/.test(tsRaw) || !/^[0-9a-f]{64}$/.test(v1)) {
    return null;
  }

  const tsParsed = Number(tsRaw);
  const ts = tsRaw.length >= 13 ? Math.floor(tsParsed / 1000) : tsParsed;
  if (!Number.isFinite(ts) || ts <= 0) {
    return null;
  }

  return {
    ts,
    tsRaw,
    v1,
  };
};

const validateMpSignature = (input: {
  signatureHeader: string;
  requestId: string;
  dataId: string;
  webhookSecret: string;
}): { isValid: boolean; reason?: string; ts: number } => {
  const parsedHeader = parseMpSignatureHeader(input.signatureHeader);
  if (!parsedHeader) {
    return {
      isValid: false,
      reason: "invalid_signature_header",
      ts: 0,
    };
  }

  const ageMs = Math.abs(Date.now() - parsedHeader.ts * 1000);
  if (ageMs > MP_SIGNATURE_WINDOW_MS) {
    return {
      isValid: false,
      reason: "signature_out_of_window",
      ts: parsedHeader.ts,
    };
  }

  if (!input.requestId || !input.dataId) {
    return {
      isValid: false,
      reason: "missing_signature_context",
      ts: parsedHeader.ts,
    };
  }

  const manifest = `id:${input.dataId};request-id:${input.requestId};ts:${parsedHeader.tsRaw};`;
  const expectedV1 = createHmac("sha256", input.webhookSecret)
    .update(manifest)
    .digest("hex");

  const receivedBuffer = Buffer.from(parsedHeader.v1, "hex");
  const expectedBuffer = Buffer.from(expectedV1, "hex");
  const isMatch =
    receivedBuffer.length === expectedBuffer.length &&
    timingSafeEqual(receivedBuffer, expectedBuffer);

  if (!isMatch) {
    return {
      isValid: false,
      reason: "signature_mismatch",
      ts: parsedHeader.ts,
    };
  }

  return {
    isValid: true,
    ts: parsedHeader.ts,
  };
};

const consumeWebhookNonce = async ({
  tenantId,
  paymentId,
  requestId,
  ts,
}: {
  tenantId: string;
  paymentId: string;
  requestId: string;
  ts: number;
}): Promise<boolean> => {
  const nonceId = `${requestId}.${ts}`;
  const nonceRef = db
    .collection("tenants")
    .doc(tenantId)
    .collection("payments")
    .doc(paymentId)
    .collection("webhookNonces")
    .doc(nonceId);

  const nowMs = Date.now();
  const expiresAt = admin.firestore.Timestamp.fromMillis(
    nowMs + MP_SIGNATURE_WINDOW_MS
  );

  return db.runTransaction(async (transaction) => {
    const nonceDoc = await transaction.get(nonceRef);
    const existingExpiresAt = nonceDoc.get(
      "expiresAt"
    ) as admin.firestore.Timestamp | undefined;

    if (
      nonceDoc.exists &&
      existingExpiresAt &&
      existingExpiresAt.toMillis() > nowMs
    ) {
      return false;
    }

    transaction.set(
      nonceRef,
      {
        requestId,
        ts,
        nonceId,
        expiresAt,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: nonceDoc.exists
          ? nonceDoc.get("createdAt") ?? admin.firestore.FieldValue.serverTimestamp()
          : admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    return true;
  });
};

type UsageSnapshot = {
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

type FreeTierLimit = {
  metrics?: Record<string, number>;
  limits?: Record<string, number>;
  freeTier?: Record<string, number>;
};

type UsageAlertPayload = {
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

const ALERT_THRESHOLDS = [70, 90, 100];
const ADMIN_ROLES = new Set(["admin", "owner"]);

const toNumberMap = (value: unknown): Record<string, number> => {
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

const firstNonEmptyMap = (
  ...maps: Array<Record<string, number>>
): Record<string, number> => {
  for (const map of maps) {
    if (Object.keys(map).length > 0) {
      return map;
    }
  }
  return {};
};

const resolveUsageMetrics = (snapshot: UsageSnapshot | null): Record<string, number> => {
  if (!snapshot) return {};
  return firstNonEmptyMap(
    toNumberMap(snapshot.metrics),
    toNumberMap(snapshot.usage),
    toNumberMap(snapshot.counts),
    toNumberMap(snapshot as Record<string, unknown>)
  );
};

const resolveLimitMetrics = (limit: FreeTierLimit | null): Record<string, number> => {
  if (!limit) return {};
  return firstNonEmptyMap(
    toNumberMap(limit.metrics),
    toNumberMap(limit.limits),
    toNumberMap(limit.freeTier),
    toNumberMap(limit as Record<string, unknown>)
  );
};

const resolvePeriodKey = (snapshot: UsageSnapshot | null): string => {
  if (!snapshot) return "current";
  const explicit = snapshot.periodKey ?? snapshot.period;
  if (explicit) return String(explicit);
  const date =
    snapshot.snapshotAt?.toDate() ??
    snapshot.createdAt?.toDate();
  if (!date) return "current";
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}${month}${day}`;
};

const extractSnapshotErrors = (snapshot: UsageSnapshot | null): UsageCollectionError[] => {
  if (!snapshot?.errors) {
    return [];
  }

  if (Array.isArray(snapshot.errors)) {
    return snapshot.errors;
  }

  return Array.isArray(snapshot.errors.items) ? snapshot.errors.items : [];
};

const isPartialUsageSnapshot = (snapshot: UsageSnapshot | null): boolean => {
  if (!snapshot) {
    return false;
  }
  if (snapshot.sourceStatus === "partial_success") {
    return true;
  }
  return extractSnapshotErrors(snapshot).length > 0;
};

const severityForThreshold = (threshold: number): string => {
  if (threshold >= 100) return "critical";
  if (threshold >= 90) return "high";
  return "warning";
};

const formatAlertTitle = (metric: string, percentage: number): string =>
  `Uso de ${metric} en ${percentage}%`;

const formatAlertMessage = (
  metric: string,
  percentage: number,
  currentValue: number,
  limitValue: number
): string =>
  `El ${metric} alcanzó ${percentage}% del límite (${currentValue}/${limitValue}).`;

const sanitizeAlertId = (value: string): string =>
  value.replace(/[^a-zA-Z0-9_-]/g, "_");

const fetchUsageSnapshot = async (tenantId: string): Promise<UsageSnapshot | null> => {
  const col = db.collection("tenants").doc(tenantId).collection("usageSnapshots");
  const currentDoc = await col.doc("current").get();
  if (currentDoc.exists) {
    return currentDoc.data() as UsageSnapshot;
  }
  const latest = await col.orderBy("snapshotAt", "desc").limit(1).get();
  if (!latest.empty) {
    return latest.docs[0].data() as UsageSnapshot;
  }
  return null;
};

const fetchFreeTierLimit = async (tenantId: string): Promise<FreeTierLimit | null> => {
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
  return fallback ? (fallback as FreeTierLimit) : null;
};

const upsertUsageAlert = async (
  payload: UsageAlertPayload
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

const fetchAdminNotificationTargets = async (tenantId: string): Promise<string[]> => {
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
  alertPayload: UsageAlertPayload
): Promise<void> => {
  const tokens = await fetchAdminNotificationTargets(tenantId);
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

const createPreferenceHandler = async (data: unknown) => {
  const payload = (data ?? {}) as Record<string, unknown>;
  const amount = Number(payload.amount);
  const items: PreferenceItemInput[] = Array.isArray(payload.items)
    ? (payload.items as PreferenceItemInput[])
    : [];
  const orderId = normalizeString(
    payload.orderId ?? payload.external_reference ?? payload.externalReference
  );
  const description = normalizeString(payload.description);
  const tenantId = normalizeString(payload.tenantId);
  const metadataInput =
    typeof payload.metadata === "object" && payload.metadata !== null
      ? (payload.metadata as Record<string, unknown>)
      : {};
  const metadataTenantId = normalizeString(
    metadataInput.tenantId ?? metadataInput.tenant_id
  );
  const requiredTenantId = tenantId || metadataTenantId;
  const payerEmail = normalizeString(payload.payer_email ?? payload.payerEmail);

  if (!Number.isFinite(amount) || amount <= 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "amount must be a positive number."
    );
  }

  if (items.length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "items must contain at least one entry."
    );
  }

  if (!requiredTenantId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "tenantId is required to create a payment preference."
    );
  }

  const { accessToken } = getMpConfig();

  const preferenceItems = items.map((item) => {
    const quantity = Number(item.quantity ?? 1);
    const unitPrice = Number(item.unit_price ?? item.unitPrice ?? amount);
    const title =
      normalizeString(item.title ?? item.name) ||
      description ||
      "Item";

    return {
      title,
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
      unit_price: Number.isFinite(unitPrice) ? unitPrice : amount,
      currency_id: String(item.currency_id ?? item.currencyId ?? "ARS"),
    };
  });

  const metadata = {
    ...metadataInput,
    orderId: orderId || undefined,
    tenantId: requiredTenantId,
  };
  const externalReference = buildExternalReference(requiredTenantId, orderId);

  const response = await axios.post(
    `${MERCADOPAGO_API}/checkout/preferences`,
    {
      items: preferenceItems,
      external_reference: externalReference,
      metadata,
      payer: payerEmail ? { email: payerEmail } : undefined,
    },
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  const initPoint = response.data?.init_point;
  if (!initPoint) {
    throw new functions.https.HttpsError(
      "internal",
      "Mercado Pago response missing init_point."
    );
  }

  return {
    init_point: initPoint,
    preference_id: response.data?.id,
    sandbox_init_point: response.data?.sandbox_init_point,
  };
};

export const createPaymentPreference =
  functions.https.onCall(createPreferenceHandler);
export const createPreference = functions.https.onCall(createPreferenceHandler);

export const collectUsageMetrics = functions.pubsub
  .schedule("every 24 hours")
  .timeZone("UTC")
  .onRun(async () => {
    const config = getBillingConfig();
    const now = new Date();
    const { start, end } = getMonthRange(now);
    const monthKey = getMonthKey(now);

    const usageCollection =
      config.source === "bigquery"
        ? await collectBigQueryUsage(config, start, end)
        : await collectMonitoringUsage(config, start, end);

    await db.collection(USAGE_COLLECTION).doc(monthKey).set(
      {
        monthKey,
        source: config.source,
        sourceStatus: usageCollection.sourceStatus,
        errors: {
          count: usageCollection.errors.length,
          items: usageCollection.errors,
        },
        period: {
          start: start.toISOString(),
          end: end.toISOString(),
        },
        services: usageCollection.services,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    console.info("Usage metrics collected", {
      monthKey,
      source: config.source,
      sourceStatus: usageCollection.sourceStatus,
      errors: usageCollection.errors.length,
    });
  });

export const mpWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  let config: MpConfig;
  try {
    config = getMpConfig();
  } catch (error) {
    console.info("Mercado Pago credentials missing for webhook.");
    res.status(500).send("Configuration error");
    return;
  }

  const signatureHeader = req.get("x-signature") ?? "";
  const requestId = req.get("x-request-id") ?? "";
  const dataId = getDataId(req);

  const signatureValidation = validateMpSignature({
    signatureHeader,
    requestId,
    dataId,
    webhookSecret: config.webhookSecret,
  });

  if (!signatureValidation.isValid) {
    console.info("Mercado Pago webhook rejected", {
      reason: signatureValidation.reason ?? "invalid_signature",
      requestId: requestId || "n/a",
      paymentId: dataId || "n/a",
    });
    res.status(401).send("Invalid signature");
    return;
  }

  const paymentId = String(dataId);

  try {
    const paymentResponse = await axios.get(
      `${MERCADOPAGO_API}/v1/payments/${paymentId}`,
      {
        headers: {
          Authorization: `Bearer ${config.accessToken}`,
        },
      }
    );

    const payment = paymentResponse.data;
    const externalReferenceData = parseExternalReference(payment?.external_reference);
    const metadataOrderId = normalizeString(payment?.metadata?.orderId);
    const orderId = externalReferenceData.orderId || metadataOrderId;
    const tenantIdFromMetadata = extractTenantId(payment);
    const tenantIdFromReference =
      externalReferenceData.tenantId || tenantIdFromMetadata;
    const tenantId = await resolveTenantId({
      tenantIdFromReference,
      orderId,
      paymentId,
    });

    if (!tenantId) {
      console.info("Mercado Pago webhook missing tenantId", {
        paymentId,
        orderId: orderId || "n/a",
      });
      res.status(200).send("ok");
      return;
    }

    const nonceAccepted = await consumeWebhookNonce({
      tenantId,
      paymentId,
      requestId,
      ts: signatureValidation.ts,
    });

    if (!nonceAccepted) {
      console.info("Mercado Pago webhook rejected", {
        reason: "signature_reused",
        tenantId,
        paymentId,
        requestId,
      });
      res.status(401).send("Invalid signature");
      return;
    }

    const paymentStatus = mapPaymentStatus(payment?.status);
    const rawPayment = {
      id: payment?.id ?? paymentId,
      status: payment?.status ?? null,
      statusDetail: payment?.status_detail ?? null,
      transactionAmount: payment?.transaction_amount ?? null,
      currencyId: payment?.currency_id ?? null,
      installments: payment?.installments ?? null,
      paymentMethodId: payment?.payment_method_id ?? null,
      payerEmail: payment?.payer?.email ?? null,
      approvedAt: payment?.date_approved ?? null,
      createdAt: payment?.date_created ?? null,
    };

    const eventKey = buildPaymentEventKey({
      paymentId,
      paymentStatus,
      requestId,
      signatureTs: String(signatureValidation.ts),
    });

    const createdAtValue =
      payment?.date_created && !Number.isNaN(Date.parse(payment.date_created))
        ? admin.firestore.Timestamp.fromDate(new Date(payment.date_created))
        : admin.firestore.FieldValue.serverTimestamp();

    const paymentUpdate = {
      orderId,
      provider: "mercado_pago",
      status: paymentStatus,
      raw: rawPayment,
      createdAt: createdAtValue,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const tenantRef = db.collection("tenants").doc(tenantId);
    const paymentRef = tenantRef.collection("payments").doc(paymentId);
    const paymentEventRef = tenantRef.collection("paymentEvents").doc(eventKey);

    const transactionResult = await db.runTransaction<PaymentWebhookTransactionResult>(
      async (transaction) => {
        const eventSnapshot = await transaction.get(paymentEventRef);
        if (eventSnapshot.exists) {
          return {
            ignoredDuplicate: true,
            transitionApplied: false,
          };
        }

        transaction.set(paymentEventRef, {
          eventKey,
          tenantId,
          paymentId,
          orderId: orderId || null,
          requestId,
          signatureTs: String(signatureValidation.ts),
          status: paymentStatus,
          provider: "mercado_pago",
          receivedAt: admin.firestore.FieldValue.serverTimestamp(),
          raw: rawPayment,
        });

        const paymentSnapshot = await transaction.get(paymentRef);
        const currentStatus = parseStoredPaymentStatus(paymentSnapshot.get("status"));
        const transitionApplied = canApplyPaymentTransition(currentStatus, paymentStatus);

        transaction.set(
          paymentRef,
          {
            lastWebhookAt: admin.firestore.FieldValue.serverTimestamp(),
            lastWebhookRequestId: requestId,
            lastWebhookSignatureTs: signatureValidation.ts,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );

        if (transitionApplied) {
          transaction.set(paymentRef, paymentUpdate, { merge: true });
        }

        return {
          ignoredDuplicate: false,
          transitionApplied,
        };
      }
    );

    if (transactionResult.ignoredDuplicate) {
      console.info("Mercado Pago webhook duplicate ignored", {
        tenantId,
        paymentId,
        transitionApplied: false,
        ignoredDuplicate: true,
      });
      res.status(200).send("ok");
      return;
    }

    if (orderId) {
      await tenantRef
        .collection("orders")
        .doc(orderId)
        .set(
          {
            paymentId,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
    }

    console.info("Mercado Pago webhook processed", {
      paymentId,
      orderId: orderId || "n/a",
      tenantId,
      transitionApplied: transactionResult.transitionApplied,
      ignoredDuplicate: transactionResult.ignoredDuplicate,
    });

    res.status(200).send("ok");
  } catch (error) {
    console.info("Mercado Pago webhook processing failed");
    res.status(500).send("error");
  }
});

export const evaluateUsageAlerts = functions.pubsub
  .schedule("every 1 hours")
  .onRun(async () => {
    const tenantsSnapshot = await db.collection("tenants").get();
    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      const usageSnapshot = await fetchUsageSnapshot(tenantId);
      const freeTierLimit = await fetchFreeTierLimit(tenantId);
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
          const result = await upsertUsageAlert(payload);
          if (result.created) {
            await notifyAdmins(tenantId, payload);
          }
        }
      }
    }
    return null;
  });

export const syncPublicProductOnWrite = functions.firestore
  .document("tenants/{tenantId}/products/{productId}")
  .onWrite(async (change, context) => {
    const { tenantId, productId } = context.params;
    const publicRef = db
      .collection("tenants")
      .doc(tenantId)
      .collection("public_products")
      .doc(productId);

    if (!change.after.exists) {
      await publicRef.delete();
      return null;
    }

    const afterData = change.after.data();
    if (!afterData) {
      return null;
    }

    const payload = buildPublicProductPayload(
      tenantId,
      productId,
      afterData
    );
    await publicRef.set(payload, { merge: true });
    return null;
  });

export const refreshPublicProducts = functions.pubsub
  .schedule("every 15 minutes")
  .onRun(async () => {
    const tenantsSnapshot = await db.collection("tenants").get();
    const now = Date.now();

    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      const configRef = db
        .collection("tenants")
        .doc(tenantId)
        .collection("config")
        .doc("public_store");
      const configSnap = await configRef.get();
      const configData = configSnap.data() || {};
      const enabled = configData.publicEnabled === true;
      if (!enabled) {
        continue;
      }

      const intervalMinutes = Number(configData.syncIntervalMinutes) || 15;
      const lastSyncedAt = configData.lastSyncedAt?.toDate?.();
      if (lastSyncedAt && now - lastSyncedAt.getTime() < intervalMinutes * 60000) {
        continue;
      }

      const productsSnapshot = await db
        .collection("tenants")
        .doc(tenantId)
        .collection("products")
        .get();

      let batch = db.batch();
      let batchCount = 0;
      for (const productDoc of productsSnapshot.docs) {
        const payload = buildPublicProductPayload(
          tenantId,
          productDoc.id,
          productDoc.data()
        );
        const publicRef = db
          .collection("tenants")
          .doc(tenantId)
          .collection("public_products")
          .doc(productDoc.id);
        batch.set(publicRef, payload, { merge: true });
        batchCount += 1;
        if (batchCount === 450) {
          await batch.commit();
          batch = db.batch();
          batchCount = 0;
        }
      }
      if (batchCount > 0) {
        await batch.commit();
      }
      await configRef.set(
        { lastSyncedAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );
    }
    return null;
  });
