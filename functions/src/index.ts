import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import axios from "axios";
import * as crypto from "crypto";
import { google } from "googleapis";

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

type UsageServiceMetrics = {
  metrics: UsageMetricResult[];
  totalsByUnit: Record<string, number>;
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

type MpSignature = {
  ts: string;
  v1: string;
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

const MERCADOPAGO_API = "https://api.mercadopago.com";
const USAGE_COLLECTION = "usageMetricsMonthly";

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

const parseSignature = (signatureHeader: string): MpSignature => {
  const parts = signatureHeader
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  const signature: MpSignature = { ts: "", v1: "" };

  for (const part of parts) {
    const [key, value] = part.split("=");
    if (!key || !value) {
      continue;
    }
    if (key === "ts") {
      signature.ts = value;
    }
    if (key === "v1") {
      signature.v1 = value;
    }
  }

  return signature;
};

const timingSafeEqual = (a: string, b: string): boolean => {
  const bufferA = Buffer.from(a, "utf8");
  const bufferB = Buffer.from(b, "utf8");
  if (bufferA.length !== bufferB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufferA, bufferB);
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

const getPointValue = (
  point: { value?: { doubleValue?: number; int64Value?: string | number } }
): number => {
  if (!point?.value) {
    return 0;
  }
  if (typeof point.value.doubleValue === "number") {
    return point.value.doubleValue;
  }
  if (point.value.int64Value !== undefined) {
    return Number(point.value.int64Value);
  }
  return 0;
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
    interval: {
      startTime: startTime.toISOString(),
      endTime: endTime.toISOString(),
    },
    aggregation: {
      alignmentPeriod: "86400s",
      perSeriesAligner: metric.perSeriesAligner ?? "ALIGN_SUM",
      crossSeriesReducer: metric.crossSeriesReducer ?? "REDUCE_SUM",
    },
    view: "FULL",
  });

  const series = response.data.timeSeries ?? [];
  const total = series.reduce((sum, item) => {
    const points = item.points ?? [];
    return (
      sum +
      points.reduce((innerSum, point) => innerSum + getPointValue(point), 0)
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

const collectMonitoringUsage = async (
  config: BillingConfig,
  startTime: Date,
  endTime: Date
): Promise<Record<UsageServiceKey, UsageServiceMetrics>> => {
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

  const results = await Promise.all(
    USAGE_METRICS.map((metric) =>
      sumMonitoringMetric(config.projectId, metric, startTime, endTime).then(
        (result) => ({
          result,
          service: metric.service,
        })
      )
    )
  );

  results.forEach(({ result, service }) => {
    accumulateServiceMetrics(services[service], result);
  });

  return services;
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
): Promise<Record<UsageServiceKey, UsageServiceMetrics>> => {
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

  return services;
};

const getDataId = (req: functions.https.Request): string => {
  const dataId =
    req.body?.data?.id ??
    req.query?.["data.id"] ??
    req.query?.["data[id]"] ??
    req.query?.["id"]; // fallback for test calls
  return dataId ? String(dataId) : "";
};

const normalizeString = (value: unknown): string => {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
};

const mapPaymentStatus = (status: string | undefined | null): PaymentStatus => {
  switch (String(status ?? "").toLowerCase()) {
    case "approved":
      return "APPROVED";
    case "rejected":
    case "cancelled":
    case "canceled":
      return "REJECTED";
    case "in_process":
    case "pending":
      return "PENDING";
    case "failed":
      return "FAILED";
    default:
      return "PENDING";
  }
};

const extractTenantId = (payment: any): string => {
  const metadata = payment?.metadata ?? {};
  return normalizeString(metadata.tenantId ?? metadata.tenant_id);
};

const resolveTenantId = async ({
  tenantIdFromMetadata,
  orderId,
  paymentId,
}: {
  tenantIdFromMetadata: string;
  orderId: string;
  paymentId: string;
}): Promise<string> => {
  if (tenantIdFromMetadata) {
    return tenantIdFromMetadata;
  }

  const candidates: string[] = [];

  if (orderId) {
    const orderSnapshot = await db.collection("orders").doc(orderId).get();
    if (orderSnapshot.exists) {
      const legacyTenantId = normalizeString(orderSnapshot.get("tenantId"));
      if (legacyTenantId) {
        candidates.push(legacyTenantId);
      }
    }
  }

  const paymentSnapshot = await db.collection("payments").doc(paymentId).get();
  if (paymentSnapshot.exists) {
    const legacyTenantId = normalizeString(paymentSnapshot.get("tenantId"));
    if (legacyTenantId) {
      candidates.push(legacyTenantId);
    }
  }

  return candidates[0] ?? "";
};

export const createPreference = functions.https.onCall(async (data) => {
  const amount = Number(data?.amount);
  const items: PreferenceItemInput[] = Array.isArray(data?.items)
    ? data.items
    : [];
  const orderId = String(data?.orderId ?? "");
  const tenantId = normalizeString(data?.tenantId);

  if (!Number.isFinite(amount) || amount <= 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "amount must be a positive number."
    );
  }

  if (!orderId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "external_reference is required."
    );
  }

  if (!tenantId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "tenantId is required."
    );
  }

  if (items.length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "items must contain at least one entry."
    );
  }

  const { accessToken } = getMpConfig();

  const preferenceItems = items.map((item) => {
    const quantity = Number(item.quantity ?? 1);
    const unitPrice = Number(item.unit_price ?? item.unitPrice ?? amount);

    return {
      title: String(item.title ?? item.name ?? "Item"),
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
      unit_price: Number.isFinite(unitPrice) ? unitPrice : amount,
      currency_id: String(item.currency_id ?? item.currencyId ?? "ARS"),
    };
  });

  const response = await axios.post(
    `${MERCADOPAGO_API}/checkout/preferences`,
    {
      items: preferenceItems,
      external_reference: orderId,
      metadata: {
        orderId,
        tenantId,
      },
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
});

export const collectUsageMetrics = functions.pubsub
  .schedule("every 24 hours")
  .timeZone("UTC")
  .onRun(async () => {
    const config = getBillingConfig();
    const now = new Date();
    const { start, end } = getMonthRange(now);
    const monthKey = getMonthKey(now);

    let services: Record<UsageServiceKey, UsageServiceMetrics>;
    if (config.source === "bigquery") {
      services = await collectBigQueryUsage(config, start, end);
    } else {
      services = await collectMonitoringUsage(config, start, end);
    }

    await db.collection(USAGE_COLLECTION).doc(monthKey).set(
      {
        monthKey,
        source: config.source,
        period: {
          start: start.toISOString(),
          end: end.toISOString(),
        },
        services,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    console.info("Usage metrics collected", {
      monthKey,
      source: config.source,
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
  const { ts, v1 } = parseSignature(signatureHeader);
  const dataId = getDataId(req);

  if (!ts || !v1 || !requestId || !dataId) {
    res.status(401).send("Invalid signature");
    return;
  }

  const manifest = `${ts}.${requestId}.${dataId}`;
  const expectedSignature = crypto
    .createHmac("sha256", config.webhookSecret)
    .update(manifest)
    .digest("hex");

  if (!timingSafeEqual(expectedSignature, v1)) {
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
    const metadataOrderId = normalizeString(payment?.metadata?.orderId);
    const orderId = payment?.external_reference
      ? String(payment.external_reference)
      : metadataOrderId;
    const tenantIdFromMetadata = extractTenantId(payment);
    const tenantId = await resolveTenantId({
      tenantIdFromMetadata,
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

    await db
      .collection("tenants")
      .doc(tenantId)
      .collection("payments")
      .doc(paymentId)
      .set(paymentUpdate, {
        merge: true,
      });

    if (orderId) {
      await db
        .collection("tenants")
        .doc(tenantId)
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
    });

    res.status(200).send("ok");
  } catch (error) {
    console.info("Mercado Pago webhook processing failed");
    res.status(500).send("error");
  }
});
