import * as functions from "firebase-functions";
import { defineString } from "firebase-functions/params";
import * as admin from "firebase-admin";
import axios from "axios";
import { AxiosError } from "axios";
import { createHash, createHmac, timingSafeEqual } from "crypto";
import { gzipSync } from "zlib";
import { google, monitoring_v3 } from "googleapis";
import { getPointValue } from "./monitoring.helpers";
import { hasRoleForModule } from "./security/rolePermissionsMatrix";
import { authorizeUsageMetricsAccess } from "./usageMetricsAccess";
import {
  buildUsageOverview,
  getCurrentDayKey,
  getNextMonthStart,
  type UsageCollectionError,
  type UsageCollectionOutcome,
  type UsageMetricResult,
  type UsageServiceMetrics,
} from "./usageMetrics.helpers";
import {
  createApproveTenantRestoreRequestHandler,
  createRequestTenantBackupHandler,
  createRequestTenantRestoreHandler,
  type ApproveRestoreRequestPayload,
  type RestoreRequestPayload,
  type RestoreScope,
} from "./tenantBackup";

admin.initializeApp();

const db = admin.firestore();
const storage = admin.storage();

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
const USAGE_CURRENT_COLLECTION = "usageMetricsCurrent";
const USAGE_CURRENT_DOC_ID = "current";
const USAGE_DAILY_SNAPSHOTS_COLLECTION = "dailySnapshots";
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
const CREATE_PREFERENCE_ALIAS_RETIREMENT_DATE = "2026-03-31";

const MP_ACCESS_TOKEN_PARAM = defineString("MP_ACCESS_TOKEN");
const MP_WEBHOOK_SECRET_PARAM = defineString("MP_WEBHOOK_SECRET");
const BILLING_SOURCE_PARAM = defineString("BILLING_SOURCE", {
  default: "monitoring",
});
const BILLING_PROJECT_ID_PARAM = defineString("BILLING_PROJECT_ID");
const BILLING_BIGQUERY_PROJECT_PARAM = defineString("BILLING_BIGQUERY_PROJECT");
const BILLING_BIGQUERY_DATASET_PARAM = defineString("BILLING_BIGQUERY_DATASET");
const BILLING_BIGQUERY_TABLE_PARAM = defineString("BILLING_BIGQUERY_TABLE");
const APP_CHECK_ENFORCEMENT_MODE_PARAM = defineString("APP_CHECK_ENFORCEMENT_MODE", {
  default: "monitor",
});
const MP_WEBHOOK_IP_ALLOWLIST_PARAM = defineString("MP_WEBHOOK_IP_ALLOWLIST", {
  default: "",
});
const ADMIN_RATE_LIMIT_PER_MINUTE_PARAM = defineString("ADMIN_RATE_LIMIT_PER_MINUTE", {
  default: "20",
});

const getOptionalParam = (param: ReturnType<typeof defineString>): string | undefined => {
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

const summarizeMercadoPagoError = (error: unknown) => {
  if (!axios.isAxiosError(error)) {
    return {
      message: error instanceof Error ? error.message : String(error),
      status: null as number | null,
      data: null as unknown,
      code: null as string | null,
    };
  }

  const axiosError = error as AxiosError;
  const responseData = axiosError.response?.data;
  const normalizedMessage =
    normalizeString((responseData as Record<string, unknown> | undefined)?.message) ||
    normalizeString((responseData as Record<string, unknown> | undefined)?.error) ||
    normalizeString((responseData as Record<string, unknown> | undefined)?.cause) ||
    normalizeString(axiosError.message) ||
    "Unknown Mercado Pago error";

  return {
    message: normalizedMessage,
    status: axiosError.response?.status ?? null,
    data: responseData ?? null,
    code: axiosError.code ?? null,
  };
};

const mapMercadoPagoStatusToHttpsCode = (
  status: number | null
): functions.https.FunctionsErrorCode => {
  if (status === 400) {
    return "invalid-argument";
  }
  if (status === 401) {
    return "unauthenticated";
  }
  if (status === 403) {
    return "permission-denied";
  }
  if (status === 404) {
    return "not-found";
  }
  if (status !== null && status >= 500) {
    return "unavailable";
  }
  return "internal";
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
  publicStatus: "published";
  updatedAt?: string | admin.firestore.FieldValue;
  publicUpdatedAt: admin.firestore.FieldValue;
};

const isProductPublished = (data: FirebaseFirestore.DocumentData): boolean => {
  const publicStatus =
    typeof data.publicStatus === "string" ? data.publicStatus.toLowerCase() : null;
  if (publicStatus) {
    return publicStatus === "published";
  }
  return data.isPublic === true;
};

const PUBLIC_PRODUCT_IMAGES_ROOT = "public_products";
const FIREBASE_STORAGE_HOST = "firebasestorage.googleapis.com";

const normalizeImageSourceUrl = (value: unknown): string | null => {
  if (typeof value !== "string") {
    return null;
  }
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
};

const sanitizePathSegment = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "") || "image";

const extractFileNameAndExtension = (inputUrl: string): { fileName: string; extension: string } => {
  try {
    const parsedUrl = new URL(inputUrl);
    const pathParts = parsedUrl.pathname.split("/").filter(Boolean);
    const lastSegment = pathParts[pathParts.length - 1] || "";
    const decodedLastSegment = decodeURIComponent(lastSegment);
    const leaf = decodedLastSegment.includes("/")
      ? decodedLastSegment.split("/").filter(Boolean).pop() ?? ""
      : decodedLastSegment;
    const cleanLeaf = leaf.trim();
    if (cleanLeaf.length === 0) {
      return { fileName: "image", extension: "jpg" };
    }
    const dotIndex = cleanLeaf.lastIndexOf(".");
    if (dotIndex <= 0 || dotIndex === cleanLeaf.length - 1) {
      return { fileName: sanitizePathSegment(cleanLeaf), extension: "jpg" };
    }
    const fileName = sanitizePathSegment(cleanLeaf.slice(0, dotIndex));
    const extension = sanitizePathSegment(cleanLeaf.slice(dotIndex + 1));
    return {
      fileName,
      extension: extension || "jpg",
    };
  } catch (_error) {
    return { fileName: "image", extension: "jpg" };
  }
};

const buildPublicImageVersion = (inputUrl: string): string =>
  createHash("sha1").update(inputUrl).digest("hex").slice(0, 10);

const buildPublicStorageMediaUrl = (bucketName: string, objectPath: string): string => {
  const encodedPath = encodeURIComponent(objectPath);
  return `https://${FIREBASE_STORAGE_HOST}/v0/b/${bucketName}/o/${encodedPath}?alt=media`;
};

const normalizePublicImageUrl = (
  inputUrl: string,
  tenantId: string,
  productId: string,
  index: number,
  bucketName: string
): string => {
  try {
    const parsed = new URL(inputUrl);
    const decodedPath = decodeURIComponent(parsed.pathname);
    const alreadyPublicPath = `tenants/${tenantId}/${PUBLIC_PRODUCT_IMAGES_ROOT}/${productId}/images/`;
    if (
      parsed.hostname === FIREBASE_STORAGE_HOST &&
      decodedPath.includes(`/o/${alreadyPublicPath}`)
    ) {
      return buildPublicStorageMediaUrl(
        bucketName,
        decodedPath.slice(decodedPath.indexOf("/o/") + 3)
      );
    }
  } catch (_error) {
    // Si la URL no es válida, se normaliza al target público igual.
  }

  const { fileName, extension } = extractFileNameAndExtension(inputUrl);
  const version = buildPublicImageVersion(inputUrl);
  const normalizedLeaf = `${String(index + 1).padStart(2, "0")}_${fileName}_v${version}.${extension}`;
  const objectPath = [
    "tenants",
    tenantId,
    PUBLIC_PRODUCT_IMAGES_ROOT,
    productId,
    "images",
    normalizedLeaf,
  ].join("/");

  return buildPublicStorageMediaUrl(bucketName, objectPath);
};

const normalizePublicImageUrls = (
  tenantId: string,
  productId: string,
  data: FirebaseFirestore.DocumentData
): string[] => {
  const rawUrls = Array.isArray(data.imageUrls)
    ? data.imageUrls.map(normalizeImageSourceUrl).filter(Boolean)
    : [];
  const legacyUrl = normalizeImageSourceUrl(data.imageUrl);
  const sourceUrls = [legacyUrl, ...rawUrls].filter((url): url is string => Boolean(url));
  const uniqueSourceUrls = [...new Set(sourceUrls)];
  const bucketName = admin.storage().bucket().name;

  return uniqueSourceUrls.map((url, index) =>
    normalizePublicImageUrl(url, tenantId, productId, index, bucketName)
  );
};

const buildPublicProductPayload = (
  tenantId: string,
  productId: string,
  data: FirebaseFirestore.DocumentData
): PublicProductPayload => {
  const imageUrls = normalizePublicImageUrls(tenantId, productId, data);

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
    imageUrl: imageUrls[0] ?? null,
    imageUrls,
    publicStatus: "published",
    updatedAt: data.updatedAt ?? admin.firestore.FieldValue.serverTimestamp(),
    publicUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
};

const getMpConfig = (): MpConfig => {
  const accessToken =
    process.env.MP_ACCESS_TOKEN?.trim() ??
    getOptionalParam(MP_ACCESS_TOKEN_PARAM);
  const webhookSecret =
    process.env.MP_WEBHOOK_SECRET?.trim() ??
    getOptionalParam(MP_WEBHOOK_SECRET_PARAM);

  if (!accessToken || !webhookSecret) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Mercado Pago credentials are missing."
    );
  }

  return { accessToken, webhookSecret };
};

const getBillingConfig = (): BillingConfig => {
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
  const rawSource =
    process.env.BILLING_SOURCE ??
    getOptionalParam(BILLING_SOURCE_PARAM) ??
    "monitoring";
  const source: UsageSource = rawSource === "bigquery" ? "bigquery" : "monitoring";

  return {
    source,
    projectId,
    bigqueryProjectId:
      process.env.BILLING_BIGQUERY_PROJECT ??
      getOptionalParam(BILLING_BIGQUERY_PROJECT_PARAM),
    bigqueryDataset:
      process.env.BILLING_BIGQUERY_DATASET ??
      getOptionalParam(BILLING_BIGQUERY_DATASET_PARAM),
    bigqueryTable:
      process.env.BILLING_BIGQUERY_TABLE ??
      getOptionalParam(BILLING_BIGQUERY_TABLE_PARAM),
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

const createPaymentPreferenceHandler = async (data: unknown) => {
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

  let response;
  try {
    response = await axios.post(
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
  } catch (error) {
    const mpError = summarizeMercadoPagoError(error);
    console.error("Mercado Pago create preference failed", {
      orderId: orderId || "n/a",
      tenantId: requiredTenantId,
      status: mpError.status,
      code: mpError.code,
      message: mpError.message,
      response: mpError.data,
    });
    throw new functions.https.HttpsError(
      mapMercadoPagoStatusToHttpsCode(mpError.status),
      `Mercado Pago error: ${mpError.message}`,
      {
        provider: "mercado_pago",
        status: mpError.status,
        code: mpError.code,
        response: mpError.data,
      }
    );
  }

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
  functions.runWith({ enforceAppCheck: true }).https.onCall(createPaymentPreferenceHandler);

/**
 * @deprecated Use `createPaymentPreference`. This alias will be retired on 2026-03-31.
 */
export const createPreference = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data: unknown, context) => {
    console.warn("Deprecated Cloud Function alias invoked: createPreference", {
      canonicalEndpoint: "createPaymentPreference",
      aliasRetirementDate: CREATE_PREFERENCE_ALIAS_RETIREMENT_DATE,
      uid: context.auth?.uid ?? null,
    });
    return createPaymentPreferenceHandler(data);
  });

export const collectUsageMetrics = functions.pubsub
  .schedule("every 24 hours")
  .timeZone("UTC")
  .onRun(async () => {
    const config = getBillingConfig();
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
  });



type UsageHistoryItem = {
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

export const getUsageMetricsHistory = functions.runWith({ enforceAppCheck: true }).https.onCall(async (data, context) => {
  const uid = normalizeString(context.auth?.uid);
  const userDoc = uid ? await db.collection("users").doc(uid).get() : null;
  const decision = authorizeUsageMetricsAccess(data as { tenantId?: unknown } | undefined, context, userDoc?.data());
  const requestedLimit = Number((data as { limit?: number } | undefined)?.limit ?? 6);
  const limit = Number.isFinite(requestedLimit)
    ? Math.min(Math.max(Math.trunc(requestedLimit), 1), 24)
    : 6;

  const query = decision.scope === "tenant"
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
    uid: decision.uid,
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
});

export const mpWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  let config: MpConfig;
  try {
    config = getMpConfig();
  } catch {
    console.info("Mercado Pago credentials missing for webhook.");
    res.status(500).send("Configuration error");
    return;
  }

  const signatureHeader = req.get("x-signature") ?? "";
  const requestId = req.get("x-request-id") ?? "";
  const dataId = getDataId(req);
  const sourceIp = normalizeString(req.ip);
  const isIpAllowed = getIpAllowlist().some((cidr) => isIpInCidr(sourceIp, cidr));

  if (!isIpAllowed) {
    console.info("Mercado Pago webhook rejected", {
      reason: "ip_not_allowlisted",
      sourceIp: sourceIp || "n/a",
      requestId: requestId || "n/a",
      paymentId: dataId || "n/a",
    });
    res.status(401).send("Invalid signature");
    return;
  }

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
    const mpError = summarizeMercadoPagoError(error);
    console.error("Mercado Pago webhook processing failed", {
      paymentId,
      requestId: requestId || "n/a",
      status: mpError.status,
      code: mpError.code,
      message: mpError.message,
      response: mpError.data,
    });
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

    if (!isProductPublished(afterData)) {
      await publicRef.delete();
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

      const publishedProductsSnapshot = await db
        .collection("tenants")
        .doc(tenantId)
        .collection("products")
        .where("publicStatus", "==", "published")
        .get();
      const legacyPublicSnapshot = await db
        .collection("tenants")
        .doc(tenantId)
        .collection("products")
        .where("isPublic", "==", true)
        .get();

      const publishedProducts = new Map<string, FirebaseFirestore.DocumentSnapshot>();
      for (const doc of publishedProductsSnapshot.docs) {
        publishedProducts.set(doc.id, doc);
      }
      for (const doc of legacyPublicSnapshot.docs) {
        if (!publishedProducts.has(doc.id)) {
          publishedProducts.set(doc.id, doc);
        }
      }

      const publicProductsSnapshot = await db
        .collection("tenants")
        .doc(tenantId)
        .collection("public_products")
        .get();
      const publishedIds = new Set(publishedProducts.keys());

      let batch = db.batch();
      let batchCount = 0;
      for (const productDoc of publishedProducts.values()) {
        const productData = productDoc.data();
        if (!productData || !isProductPublished(productData)) {
          continue;
        }
        const payload = buildPublicProductPayload(
          tenantId,
          productDoc.id,
          productData
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

      for (const publicProductDoc of publicProductsSnapshot.docs) {
        if (publishedIds.has(publicProductDoc.id)) {
          continue;
        }
        batch.delete(publicProductDoc.ref);
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

type TenantOwnershipAction =
  | "ASSOCIATE_OWNER"
  | "TRANSFER_PRIMARY_OWNER"
  | "DELEGATE_STORE";

type ManageTenantOwnershipPayload = {
  tenantId?: unknown;
  action?: unknown;
  targetUid?: unknown;
  targetEmail?: unknown;
  keepPreviousOwnerAccess?: unknown;
};

type TenantBackupDocument = {
  path: string;
  data: admin.firestore.DocumentData;
};

type RestoreScope = "full" | "collection" | "document";

type RestoreRequestStatus =
  | "requested"
  | "approved"
  | "running"
  | "completed"
  | "failed";

type RestoreRequestPayload = {
  tenantId?: unknown;
  runId?: unknown;
  scope?: unknown;
  dryRun?: unknown;
};

type ApproveRestoreRequestPayload = {
  tenantId?: unknown;
  restoreId?: unknown;
};

type TenantBackupRunResult = {
  runId: string;
  docCount: number;
};

const BACKUP_RETENTION_COUNT = 7;
const BACKUP_CHUNK_SIZE = 150;
const BACKUP_REQUEST_WINDOW_MS = 10 * 60 * 1000;

const APP_CHECK_REJECT_COLLECTION = "securityTelemetry";
const ADMIN_RATE_LIMIT_COLLECTION = "adminRateLimits";
const MP_DEFAULT_ALLOWED_CIDRS = [
  "34.195.82.184/32",
  "100.24.156.160/32",
  "35.196.38.56/32",
  "44.217.34.150/32",
  "44.219.124.34/32",
] as const;

type AppCheckEnforcementMode = "monitor" | "enforce";

const getAppCheckEnforcementMode = (): AppCheckEnforcementMode => {
  const configured =
    process.env.APP_CHECK_ENFORCEMENT_MODE ??
    getOptionalParam(APP_CHECK_ENFORCEMENT_MODE_PARAM) ??
    "monitor";

  return configured.trim().toLowerCase() === "enforce" ? "enforce" : "monitor";
};

const getAdminRateLimitPerMinute = (): number => {
  const configured =
    process.env.ADMIN_RATE_LIMIT_PER_MINUTE ??
    getOptionalParam(ADMIN_RATE_LIMIT_PER_MINUTE_PARAM) ??
    "20";
  const parsed = Number(configured);
  if (!Number.isFinite(parsed)) {
    return 20;
  }
  return Math.min(Math.max(Math.trunc(parsed), 5), 300);
};

const ipToUint32 = (ip: string): number | null => {
  const octets = ip.split(".").map((segment) => Number(segment));
  if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
    return null;
  }

  return (((octets[0] << 24) >>> 0) + ((octets[1] << 16) >>> 0) + ((octets[2] << 8) >>> 0) + octets[3]) >>> 0;
};

const isIpInCidr = (ip: string, cidr: string): boolean => {
  const [rawNetwork, rawMask] = cidr.split("/");
  const network = ipToUint32(rawNetwork.trim());
  const target = ipToUint32(ip.trim());
  const maskBits = Number(rawMask);

  if (network === null || target === null || !Number.isInteger(maskBits) || maskBits < 0 || maskBits > 32) {
    return false;
  }

  if (maskBits === 0) {
    return true;
  }

  const mask = (0xffffffff << (32 - maskBits)) >>> 0;
  return (network & mask) === (target & mask);
};

const getIpAllowlist = (): string[] => {
  const configured =
    process.env.MP_WEBHOOK_IP_ALLOWLIST ?? getOptionalParam(MP_WEBHOOK_IP_ALLOWLIST_PARAM) ?? "";

  const parsed = configured
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);

  return parsed.length > 0 ? parsed : [...MP_DEFAULT_ALLOWED_CIDRS];
};

const getRequestIp = (context: functions.https.CallableContext): string =>
  normalizeString(context.rawRequest?.ip);

const recordAppCheckRejection = async (params: {
  operation: string;
  uid: string;
  tenantId: string;
  ip: string;
  appId: string;
  reason: string;
}): Promise<void> => {
  const docRef = db.collection(APP_CHECK_REJECT_COLLECTION).doc("app_check").collection("events").doc();
  await docRef.set({
    operation: params.operation,
    uid: params.uid,
    tenantId: params.tenantId || null,
    ip: params.ip || null,
    appId: params.appId || null,
    reason: params.reason,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
};

const assertAppCheckForInternalCallable = async (params: {
  operation: string;
  context: functions.https.CallableContext;
  tenantId?: string;
}): Promise<void> => {
  const mode = getAppCheckEnforcementMode();
  const appId = normalizeString(params.context.app?.appId);
  if (appId) {
    return;
  }

  const uid = normalizeString(params.context.auth?.uid);
  const tenantId = normalizeString(params.tenantId);
  const ip = getRequestIp(params.context);

  await recordAppCheckRejection({
    operation: params.operation,
    uid,
    tenantId,
    ip,
    appId,
    reason: mode === "enforce" ? "missing_app_check_enforced" : "missing_app_check_monitor",
  });

  if (mode === "enforce") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "App Check token requerido para este endpoint"
    );
  }
};

const enforceAdminRateLimit = async (params: {
  operation: string;
  uid: string;
  tenantId: string;
  ip: string;
}): Promise<void> => {
  const uid = normalizeString(params.uid);
  const tenantId = normalizeString(params.tenantId);
  if (!uid || !tenantId) {
    return;
  }

  const ip = normalizeString(params.ip) || "unknown";
  const now = Date.now();
  const windowStartMs = now - 60_000;
  const bucketMinute = Math.floor(now / 60_000);
  const idSource = `${params.operation}|${uid}|${tenantId}|${ip}|${bucketMinute}`;
  const bucketId = createHash("sha256").update(idSource).digest("hex");
  const bucketRef = db.collection(ADMIN_RATE_LIMIT_COLLECTION).doc(bucketId);

  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(bucketRef);
    const count = Number(snapshot.get("count") ?? 0) + 1;
    const limit = getAdminRateLimitPerMinute();

    if (count > limit) {
      throw new functions.https.HttpsError(
        "resource-exhausted",
        `Rate limit excedido para ${params.operation}`
      );
    }

    tx.set(
      bucketRef,
      {
        operation: params.operation,
        uid,
        tenantId,
        ip,
        minuteBucket: bucketMinute,
        count,
        windowStartAt: admin.firestore.Timestamp.fromMillis(windowStartMs),
        expiresAt: admin.firestore.Timestamp.fromMillis(now + 10 * 60_000),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: snapshot.exists
          ? snapshot.get("createdAt") ?? admin.firestore.FieldValue.serverTimestamp()
          : admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  });
};

const normalizeEmail = (value: unknown): string =>
  String(value ?? "").trim().toLowerCase();

const isAdminRole = (role: unknown): boolean => {
  const normalized = normalizeString(role).toLowerCase();
  return normalized === "admin" || normalized === "owner";
};

const toBoolean = (value: unknown): boolean => {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "string") {
    return value.trim().toLowerCase() === "true";
  }
  if (typeof value === "number") {
    return value === 1;
  }
  return false;
};

const writeTenantAuditLog = async (
  tenantId: string,
  payload: {
    eventType: "backup" | "restore";
    action: string;
    actorUid?: string;
    scope?: RestoreScope | "full";
    runId?: string;
    restoreRequestId?: string;
    status: string;
    dryRun?: boolean;
    diffEstimate?: Record<string, unknown>;
    result?: Record<string, unknown>;
    ip?: string;
    userAgent?: string;
  }
): Promise<void> => {
  const eventRef = db.collection("tenants").doc(tenantId).collection("audit_logs").doc();
  await eventRef.set({
    eventType: payload.eventType,
    action: payload.action,
    tenantId,
    actorUid: payload.actorUid ?? null,
    scope: payload.scope ?? "full",
    runId: payload.runId ?? null,
    restoreRequestId: payload.restoreRequestId ?? null,
    status: payload.status,
    dryRun: payload.dryRun === true,
    diffEstimate: payload.diffEstimate ?? null,
    result: payload.result ?? null,
    ip: normalizeString(payload.ip) || null,
    userAgent: normalizeString(payload.userAgent) || null,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
};

const estimateRestoreDiff = async (
  tenantId: string,
  runId: string,
  scope: RestoreScope
): Promise<Record<string, unknown>> => {
  const runRef = db.collection("tenant_backups").doc(tenantId).collection("runs").doc(runId);
  const runDoc = await runRef.get();
  if (!runDoc.exists) {
    throw new functions.https.HttpsError("not-found", "runId no existe para el tenant");
  }

  const chunkPreview = await runRef.collection("chunks").limit(1).get();
  const previewPaths = chunkPreview.docs.flatMap((doc) => {
    const documents = Array.isArray(doc.get("documents"))
      ? (doc.get("documents") as TenantBackupDocument[])
      : [];
    return documents
      .slice(0, 3)
      .map((backupDoc) => normalizeString(backupDoc.path))
      .filter(Boolean);
  });

  const backupDocCount = Number(runDoc.get("docCount") ?? 0);
  const chunkCount = Number(runDoc.get("chunkCount") ?? 0);

  return {
    scope,
    backupDocCount,
    chunkCount,
    previewPaths,
    estimatedRisk:
      scope === "full" ? "high" : scope === "collection" ? "medium" : "low",
    note: "Estimación de bajo costo basada en metadata del backup (sin escaneo completo del tenant actual).",
  };
};

const hasModuleAccess = (
  userData: admin.firestore.DocumentData,
  module: "maintenanceRead" | "maintenanceWrite" | "backupsRead" | "backupsWrite"
): boolean => {
  const role = normalizeString(userData.role).toLowerCase();
  if (
    hasRoleForModule(role, module) ||
    userData.isAdmin === true ||
    userData.isSuperAdmin === true
  ) {
    return true;
  }

  const permissions = new Set(normalizeStringList(userData.permissions));
  if (module === "maintenanceRead") {
    return permissions.has(MAINTENANCE_READ) || permissions.has(MAINTENANCE_WRITE);
  }
  if (module === "maintenanceWrite") {
    return permissions.has(MAINTENANCE_WRITE);
  }

  return false;
};

const MAINTENANCE_READ = "MAINTENANCE_READ";
const MAINTENANCE_WRITE = "MAINTENANCE_WRITE";
const MAINTENANCE_STATUS = new Set([
  "pending",
  "in_progress",
  "blocked",
  "completed",
  "cancelled",
]);
const MAINTENANCE_PRIORITY = new Set(["low", "medium", "high", "critical"]);

const normalizeStringList = (value: unknown): string[] => {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => normalizeString(item))
    .filter(Boolean);
};

const canReadMaintenance = (userData: admin.firestore.DocumentData): boolean =>
  hasModuleAccess(userData, "maintenanceRead");

const canWriteMaintenance = (userData: admin.firestore.DocumentData): boolean =>
  hasModuleAccess(userData, "maintenanceWrite");

const sanitizeMaintenanceTaskPayload = (
  payload: Record<string, unknown>,
  tenantId: string,
  actorUid: string,
  previous?: admin.firestore.DocumentData
): admin.firestore.DocumentData => {
  const title = normalizeString(payload.title);
  const description = normalizeString(payload.description);
  const status = normalizeString(payload.status).toLowerCase() || "pending";
  const priority = normalizeString(payload.priority).toLowerCase() || "medium";
  const assigneeUid = normalizeString(payload.assigneeUid) || null;
  const dueAtRaw = payload.dueAt;
  const dueAt =
    dueAtRaw instanceof admin.firestore.Timestamp
      ? dueAtRaw
      : typeof dueAtRaw === "string" && dueAtRaw
      ? admin.firestore.Timestamp.fromDate(new Date(dueAtRaw))
      : null;
  const operationalBlocker = payload.operationalBlocker === true;

  if (title.length < 3) {
    throw new functions.https.HttpsError("invalid-argument", "title requiere al menos 3 caracteres");
  }
  if (!MAINTENANCE_STATUS.has(status)) {
    throw new functions.https.HttpsError("invalid-argument", "status inválido");
  }
  if (!MAINTENANCE_PRIORITY.has(priority)) {
    throw new functions.https.HttpsError("invalid-argument", "priority inválida");
  }
  if (dueAt && Number.isNaN(dueAt.toMillis())) {
    throw new functions.https.HttpsError("invalid-argument", "dueAt inválido");
  }

  return {
    tenantId,
    title,
    description: description || null,
    status,
    priority,
    assigneeUid,
    dueAt,
    operationalBlocker,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedByUid: actorUid,
    createdAt: previous?.createdAt ?? admin.firestore.FieldValue.serverTimestamp(),
    createdByUid: previous?.createdByUid ?? actorUid,
    trace: {
      lastAction: previous ? "update" : "create",
      source: "cloud_function",
    },
  };
};

const writeMaintenanceAuditLog = async ({
  tenantId,
  taskId,
  actorUid,
  action,
  before,
  after,
}: {
  tenantId: string;
  taskId: string;
  actorUid: string;
  action: "create" | "update";
  before?: admin.firestore.DocumentData | null;
  after: admin.firestore.DocumentData;
}): Promise<void> => {
  await db.collection("tenants").doc(tenantId).collection("maintenance_audit").add({
    tenantId,
    taskId,
    action,
    actorUid,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
    change: {
      before: before ?? null,
      after,
    },
    source: "cloud_function",
  });
};

const resolveTargetUserId = async (
  payload: ManageTenantOwnershipPayload
): Promise<string> => {
  const targetUid = normalizeString(payload.targetUid);
  if (targetUid) {
    const targetDoc = await db.collection("users").doc(targetUid).get();
    if (!targetDoc.exists) {
      throw new functions.https.HttpsError("not-found", "targetUid no existe en users/");
    }
    return targetUid;
  }

  const targetEmail = normalizeEmail(payload.targetEmail);
  if (!targetEmail) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Debés enviar targetUid o targetEmail"
    );
  }

  const byEmailSnapshot = await db
    .collection("users")
    .where("email", "==", targetEmail)
    .limit(1)
    .get();

  if (!byEmailSnapshot.empty) {
    return byEmailSnapshot.docs[0].id;
  }

  try {
    const authUser = await admin.auth().getUserByEmail(targetEmail);
    return authUser.uid;
  } catch {
    throw new functions.https.HttpsError(
      "not-found",
      "No existe usuario activo con ese email"
    );
  }
};

const upsertTenantUserMembership = async (
  tenantId: string,
  uid: string,
  role: "owner" | "manager"
): Promise<void> => {
  const userRef = db.collection("users").doc(uid);
  const tenantUserRef = db.collection("tenant_users").doc(`${tenantId}_${uid}`);

  await db.runTransaction(async (tx) => {
    const userDoc = await tx.get(userRef);
    let userData = userDoc.data() || {};

    if (!userDoc.exists) {
      const authUser = await admin.auth().getUser(uid);
      userData = {
        name: authUser.displayName ?? "",
        email: normalizeEmail(authUser.email),
      };
    }

    tx.set(
      userRef,
      {
        tenantId,
        role,
        accountType: role === "owner" ? "store_owner" : userData.accountType ?? "store_owner",
        isActive: true,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    tx.set(
      tenantUserRef,
      {
        tenantId,
        uid,
        name: userData.name ?? userData.displayName ?? "",
        email: normalizeEmail(userData.email),
        role,
        isActive: true,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  });
};

export const manageTenantOwnership = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall(async (data: ManageTenantOwnershipPayload, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Requiere sesión autenticada");
    }

    const tenantId = normalizeString(data?.tenantId);
    const action = normalizeString(data?.action) as TenantOwnershipAction;
    if (!tenantId || !action) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "tenantId y action son obligatorios"
      );
    }

    await assertAppCheckForInternalCallable({
      operation: "manageTenantOwnership",
      context,
      tenantId,
    });

    await enforceAdminRateLimit({
      operation: "manageTenantOwnership",
      uid: context.auth.uid,
      tenantId,
      ip: getRequestIp(context),
    });

    const allowedActions: TenantOwnershipAction[] = [
      "ASSOCIATE_OWNER",
      "TRANSFER_PRIMARY_OWNER",
      "DELEGATE_STORE",
    ];
    if (!allowedActions.includes(action)) {
      throw new functions.https.HttpsError("invalid-argument", "action inválida");
    }

    const callerUid = context.auth.uid;
    const tenantRef = db.collection("tenants").doc(tenantId);
    const callerUserRef = db.collection("users").doc(callerUid);
    const [tenantDoc, callerUserDoc] = await Promise.all([
      tenantRef.get(),
      callerUserRef.get(),
    ]);

    if (!tenantDoc.exists) {
      throw new functions.https.HttpsError("not-found", "tenant no existe");
    }
    if (!callerUserDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const callerRole = normalizeString(callerUserDoc.get("role")).toLowerCase();
    const callerTenantId = normalizeString(callerUserDoc.get("tenantId"));
    const hasAdminClaim =
      context.auth.token.superAdmin === true ||
      context.auth.token.admin === true ||
      context.auth.token.role === "admin";

    if (!hasAdminClaim && (!isAdminRole(callerRole) || callerTenantId !== tenantId)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Solo admin/owner del tenant puede gestionar titularidad"
      );
    }

    const targetUid = await resolveTargetUserId(data ?? {});
    const keepPreviousOwnerAccess = data.keepPreviousOwnerAccess !== false;

    const result = await db.runTransaction(async (tx) => {
      const freshTenantDoc = await tx.get(tenantRef);
      if (!freshTenantDoc.exists) {
        throw new functions.https.HttpsError("not-found", "tenant no existe");
      }

      const tenantData = freshTenantDoc.data() || {};
      const primaryOwnerUid = normalizeString(tenantData.ownerUid);
      const ownerUids = new Set<string>(
        Array.isArray(tenantData.ownerUids)
          ? tenantData.ownerUids.map((value: unknown) => normalizeString(value)).filter(Boolean)
          : []
      );
      if (primaryOwnerUid) {
        ownerUids.add(primaryOwnerUid);
      }

      const delegatedStoreUids = new Set<string>(
        Array.isArray(tenantData.delegatedStoreUids)
          ? tenantData.delegatedStoreUids.map((value: unknown) => normalizeString(value)).filter(Boolean)
          : []
      );

      if (action === "ASSOCIATE_OWNER") {
        ownerUids.add(targetUid);
      }

      if (action === "TRANSFER_PRIMARY_OWNER") {
        if (!keepPreviousOwnerAccess && primaryOwnerUid) {
          ownerUids.delete(primaryOwnerUid);
        }
        ownerUids.add(targetUid);
        tx.set(
          tenantRef,
          {
            ownerUid: targetUid,
          },
          { merge: true }
        );
      }

      if (action === "DELEGATE_STORE") {
        delegatedStoreUids.add(targetUid);
      }

      tx.set(
        tenantRef,
        {
          ownerUids: Array.from(ownerUids),
          delegatedStoreUids: Array.from(delegatedStoreUids),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          ownershipVersion: admin.firestore.FieldValue.increment(1),
        },
        { merge: true }
      );

      const eventRef = tenantRef.collection("ownershipEvents").doc();
      tx.set(eventRef, {
        action,
        tenantId,
        actorUid: callerUid,
        targetUid,
        previousPrimaryOwnerUid: primaryOwnerUid || null,
        keepPreviousOwnerAccess,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return {
        ownerUids: Array.from(ownerUids),
        delegatedStoreUids: Array.from(delegatedStoreUids),
      };
    });

    if (action === "DELEGATE_STORE") {
      await upsertTenantUserMembership(tenantId, targetUid, "manager");
    } else {
      await upsertTenantUserMembership(tenantId, targetUid, "owner");
    }

    return {
      ok: true,
      tenantId,
      action,
      targetUid,
      ownerUids: result.ownerUids,
      delegatedStoreUids: result.delegatedStoreUids,
      note:
        "Cambio aplicado sin mover datos operativos del tenant. Inventario, ventas e histórico permanecen intactos.",
    };
  });

const listTenantDocumentsRecursively = async (
  docRef: admin.firestore.DocumentReference,
  docs: TenantBackupDocument[]
): Promise<void> => {
  const docSnap = await docRef.get();
  if (!docSnap.exists) {
    return;
  }
  docs.push({ path: docRef.path, data: docSnap.data() || {} });

  const collections = await docRef.listCollections();
  for (const collectionRef of collections) {
    const colSnap = await collectionRef.get();
    for (const nestedDoc of colSnap.docs) {
      await listTenantDocumentsRecursively(nestedDoc.ref, docs);
    }
  }
};

const loadPurgedProductIds = async (tenantId: string): Promise<Set<string>> => {
  const deletionsSnap = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("product_deletions")
    .where("purgeBackup", "==", true)
    .get();

  return new Set(
    deletionsSnap.docs
      .map((doc) => normalizeString(doc.id))
      .filter(Boolean)
  );
};

const shouldKeepBackupDocument = (
  path: string,
  tenantId: string,
  purgedProductIds: Set<string>
): boolean => {
  if (purgedProductIds.size === 0) {
    return true;
  }
  for (const productId of purgedProductIds) {
    const productRoot = `tenants/${tenantId}/products/${productId}`;
    if (path === productRoot || path.startsWith(`${productRoot}/`)) {
      return false;
    }
  }
  return true;
};

const resolveBackupStorageLocation = (
  runData: admin.firestore.DocumentData
): { storageBucket: string; storagePath: string } | null => {
  const storagePath = normalizeString(runData.storagePath);
  if (!storagePath) {
    return null;
  }
  const storageBucket = normalizeString(runData.storageBucket) || storage.bucket().name;
  return { storageBucket, storagePath };
};

const deleteRunWithChunks = async (
  runDoc: admin.firestore.QueryDocumentSnapshot
): Promise<void> => {
  const storageLocation = resolveBackupStorageLocation(runDoc.data());
  if (storageLocation) {
    try {
      await storage
        .bucket(storageLocation.storageBucket)
        .file(storageLocation.storagePath)
        .delete({ ignoreNotFound: true });
    } catch (error) {
      console.warn("Unable to delete backup artifact from storage", {
        runPath: runDoc.ref.path,
        ...storageLocation,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  await runDoc.ref.delete();
};

const cleanupTenantBackupRetention = async (tenantId: string): Promise<void> => {
  const runsRef = db.collection("tenant_backups").doc(tenantId).collection("runs");
  const runsSnap = await runsRef.orderBy("createdAt", "desc").get();

  if (runsSnap.size <= BACKUP_RETENTION_COUNT) {
    return;
  }

  const staleRuns = runsSnap.docs.slice(BACKUP_RETENTION_COUNT);
  for (const staleRun of staleRuns) {
    await deleteRunWithChunks(staleRun);
  }
};

const backupTenant = async (
  tenantId: string,
  actor = "system:createDailyTenantBackups"
): Promise<TenantBackupRunResult> => {
  const tenantRef = db.collection("tenants").doc(tenantId);
  const documents: TenantBackupDocument[] = [];
  await listTenantDocumentsRecursively(tenantRef, documents);
  const purgedProductIds = await loadPurgedProductIds(tenantId);
  const filteredDocuments = documents.filter((doc) =>
    shouldKeepBackupDocument(doc.path, tenantId, purgedProductIds)
  );

  const runId = new Date().toISOString().replace(/[.:]/g, "-");
  const bucket = storage.bucket();
  const storagePath = `tenant-backups/${tenantId}/${runId}.json.gz`;
  const runRef = db
    .collection("tenant_backups")
    .doc(tenantId)
    .collection("runs")
    .doc(runId);

  await runRef.set({
    tenantId,
    runId,
    actor,
    status: "in_progress",
    retentionLimit: BACKUP_RETENTION_COUNT,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const chunks: TenantBackupDocument[][] = [];
  for (let index = 0; index < filteredDocuments.length; index += BACKUP_CHUNK_SIZE) {
    chunks.push(filteredDocuments.slice(index, index + BACKUP_CHUNK_SIZE));
  }

  const backupFile = bucket.file(storagePath);
  let backupUploaded = false;

  try {
    let batch = db.batch();
    let batchCount = 0;
    for (let index = 0; index < chunks.length; index += 1) {
      const chunk = chunks[index];
      const chunkRef = runRef.collection("chunks").doc(String(index + 1).padStart(4, "0"));
      batch.set(chunkRef, {
        tenantId,
        runId,
        chunkIndex: index,
        exportedAt: new Date().toISOString(),
        documents: chunk,
      });
      batchCount += 1;

      if (batchCount >= 400) {
        await batch.commit();
        batch = db.batch();
        batchCount = 0;
      }
    }

    if (batchCount > 0) {
      await batch.commit();
    }

    const serializedPayload = {
      tenantId,
      runId,
      actor,
      exportedAt: new Date().toISOString(),
      docCount: filteredDocuments.length,
      chunkCount: chunks.length,
      documents: filteredDocuments,
    };

    const jsonBuffer = Buffer.from(JSON.stringify(serializedPayload));
    const gzippedBuffer = gzipSync(jsonBuffer);
    const payloadHash = createHash("sha256").update(gzippedBuffer).digest("hex");

    await backupFile.save(gzippedBuffer, {
      resumable: false,
      contentType: "application/json",
      metadata: {
        contentEncoding: "gzip",
        metadata: {
          tenantId,
          runId,
          payloadHash,
        },
      },
    });
    backupUploaded = true;

    await runRef.set(
      {
        status: "completed",
        docCount: filteredDocuments.length,
        chunkCount: chunks.length,
        payloadBytes: jsonBuffer.length,
        compressedBytes: gzippedBuffer.length,
        payloadHash,
        storageBucket: bucket.name,
        storagePath,
        storageUri: `gs://${bucket.name}/${storagePath}`,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    await writeTenantAuditLog(tenantId, {
      eventType: "backup",
      action: "BACKUP_COMPLETED",
      scope: "full",
      runId,
      status: "completed",
      result: {
        docCount: filteredDocuments.length,
        chunkCount: chunks.length,
        retentionLimit: BACKUP_RETENTION_COUNT,
      },
    });

    await cleanupTenantBackupRetention(tenantId);
  } catch (error) {
    if (backupUploaded) {
      try {
        await backupFile.delete({ ignoreNotFound: true });
      } catch (cleanupError) {
        console.warn("Unable to rollback failed backup artifact", {
          tenantId,
          runId,
          storagePath,
          error: cleanupError instanceof Error ? cleanupError.message : String(cleanupError),
        });
      }
    }

    await runRef.set(
      {
        status: "failed",
        errorMessage: error instanceof Error ? error.message : String(error),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    throw error;
  }

  return {
    runId,
    docCount: filteredDocuments.length,
  };
};

const logBoSecurityEvent = async (payload: { operation: string; result: string; reason: string; uid?: string; tenantId?: string }): Promise<void> => {
  await db.collection(APP_CHECK_REJECT_COLLECTION).doc("bo_callable_guards").collection("events").add({
    ...payload,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
};

const userCanRequestTenantBackup = (
  context: functions.https.CallableContext,
  userData: admin.firestore.DocumentData
): boolean => {
  if (context.auth?.token?.superAdmin === true) {
    return true;
  }

  const userRole = normalizeString(userData.role).toLowerCase();
  return userRole === "owner" || userRole === "admin";
};

const requestTenantBackupHandler = createRequestTenantBackupHandler({
  db,
  normalizeString,
  toBoolean,
  userCanRequestTenantBackup,
  estimateRestoreDiff,
  writeTenantAuditLog,
  backupRequestWindowMs: BACKUP_REQUEST_WINDOW_MS,
  enforceAdminRateLimit,
  logSecurityEvent: async (payload) => logBoSecurityEvent({ operation: payload.operation, result: payload.result, reason: payload.reason, uid: payload.uid, tenantId: payload.tenantId }),
});

const requestTenantRestoreHandler = createRequestTenantRestoreHandler({
  db,
  normalizeString,
  toBoolean,
  userCanRequestTenantBackup,
  estimateRestoreDiff,
  writeTenantAuditLog,
  backupRequestWindowMs: BACKUP_REQUEST_WINDOW_MS,
  enforceAdminRateLimit,
  logSecurityEvent: async (payload) => logBoSecurityEvent({ operation: payload.operation, result: payload.result, reason: payload.reason, uid: payload.uid, tenantId: payload.tenantId }),
});

const approveTenantRestoreRequestHandler = createApproveTenantRestoreRequestHandler({
  db,
  normalizeString,
  toBoolean,
  userCanRequestTenantBackup,
  estimateRestoreDiff,
  writeTenantAuditLog,
  backupRequestWindowMs: BACKUP_REQUEST_WINDOW_MS,
  enforceAdminRateLimit,
  logSecurityEvent: async (payload) => logBoSecurityEvent({ operation: payload.operation, result: payload.result, reason: payload.reason, uid: payload.uid, tenantId: payload.tenantId }),
});

export const requestTenantBackup = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall((data: unknown, context) => requestTenantBackupHandler(data, context));

export const requestTenantRestore = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall((data: RestoreRequestPayload, context) => requestTenantRestoreHandler(data, context));

export const approveTenantRestoreRequest = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall((data: ApproveRestoreRequestPayload, context) =>
    approveTenantRestoreRequestHandler(data, context)
  );

export const processTenantBackupRequest = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .firestore
  .document("tenant_backups/{tenantId}/requests/{requestId}")
  .onCreate(async (snapshot, context) => {
    const tenantId = normalizeString(context.params.tenantId);
    const requestId = normalizeString(context.params.requestId);
    if (!tenantId || !requestId) {
      return;
    }

    await snapshot.ref.set(
      {
        status: "running",
        startedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    try {
      const backupResult = await backupTenant(tenantId);
      await snapshot.ref.set(
        {
          status: "completed",
          runId: backupResult.runId,
          docCount: backupResult.docCount,
          completedAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          errorMessage: null,
        },
        { merge: true }
      );
    } catch (error) {
      await snapshot.ref.set(
        {
          status: "failed",
          errorMessage: error instanceof Error ? error.message : String(error),
          failedAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  });

export const createDailyTenantBackups = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .pubsub
  .schedule("every 24 hours")
  .timeZone("UTC")
  .onRun(async () => {
    const tenantsSnapshot = await db.collection("tenants").get();
    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      try {
        await backupTenant(tenantId, "system:createDailyTenantBackups");
        console.info("Tenant backup completed", { tenantId });
      } catch (error) {
        console.error("Tenant backup failed", {
          tenantId,
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }
    return null;
  });

export const purgeDeletedProductFromBackups = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .firestore
  .document("tenants/{tenantId}/product_deletions/{productId}")
  .onWrite(async (change, context) => {
    if (!change.after.exists) {
      return;
    }
    const tenantId = normalizeString(context.params.tenantId);
    const productId = normalizeString(context.params.productId);
    if (!tenantId || !productId) {
      return;
    }

    const purgeBackup = change.after.get("purgeBackup") === true;
    if (!purgeBackup) {
      return;
    }

    const wasPurgeBackup = change.before.exists && change.before.get("purgeBackup") === true;
    if (wasPurgeBackup) {
      return;
    }

    await backupTenant(tenantId, "system:purgeDeletedProductFromBackups");
  });


export const createMaintenanceTask = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall(async (data: unknown, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);
    if (!tenantId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId requerido");
    }

    await assertAppCheckForInternalCallable({
      operation: "createMaintenanceTask",
      context,
      tenantId,
    });

    await enforceAdminRateLimit({
      operation: "createMaintenanceTask",
      uid: context.auth.uid,
      tenantId,
      ip: getRequestIp(context),
    });

    const userDoc = await db.collection("users").doc(context.auth.uid).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }
    const userData = userDoc.data() || {};
    if (normalizeString(userData.tenantId) !== tenantId || !canWriteMaintenance(userData)) {
      throw new functions.https.HttpsError("permission-denied", "sin permisos de mantenimiento");
    }

    const taskRef = db.collection("tenants").doc(tenantId).collection("maintenance_tasks").doc();
    const taskData = sanitizeMaintenanceTaskPayload(payload, tenantId, context.auth.uid);
    await taskRef.set(taskData, { merge: false });
    await writeMaintenanceAuditLog({
      tenantId,
      taskId: taskRef.id,
      actorUid: context.auth.uid,
      action: "create",
      after: taskData,
    });
    return { ok: true, taskId: taskRef.id };
  });

export const updateMaintenanceTask = functions
  .runWith({ enforceAppCheck: false })
  .https.onCall(async (data: unknown, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);
    const taskId = normalizeString(payload.taskId);
    if (!tenantId || !taskId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId y taskId requeridos");
    }

    await assertAppCheckForInternalCallable({
      operation: "updateMaintenanceTask",
      context,
      tenantId,
    });

    await enforceAdminRateLimit({
      operation: "updateMaintenanceTask",
      uid: context.auth.uid,
      tenantId,
      ip: getRequestIp(context),
    });

    const [userDoc, taskDoc] = await Promise.all([
      db.collection("users").doc(context.auth.uid).get(),
      db.collection("tenants").doc(tenantId).collection("maintenance_tasks").doc(taskId).get(),
    ]);
    if (!userDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }
    if (!taskDoc.exists) {
      throw new functions.https.HttpsError("not-found", "task no existe");
    }

    const userData = userDoc.data() || {};
    if (normalizeString(userData.tenantId) !== tenantId || !canWriteMaintenance(userData)) {
      throw new functions.https.HttpsError("permission-denied", "sin permisos de mantenimiento");
    }

    const previousData = taskDoc.data() || null;
    const nextData = sanitizeMaintenanceTaskPayload(
      { ...previousData, ...payload },
      tenantId,
      context.auth.uid,
      previousData || undefined
    );

    await taskDoc.ref.set(nextData, { merge: false });
    await writeMaintenanceAuditLog({
      tenantId,
      taskId,
      actorUid: context.auth.uid,
      action: "update",
      before: previousData,
      after: nextData,
    });
    return { ok: true, taskId };
  });
