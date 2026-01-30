import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import axios from "axios";
import * as crypto from "crypto";

admin.initializeApp();

const db = admin.firestore();

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

const MERCADOPAGO_API = "https://api.mercadopago.com";

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

const getDataId = (req: functions.https.Request): string => {
  const dataId =
    req.body?.data?.id ??
    req.query?.["data.id"] ??
    req.query?.["data[id]"] ??
    req.query?.["id"]; // fallback for test calls
  return dataId ? String(dataId) : "";
};

type UsageSnapshot = {
  metrics?: Record<string, number>;
  usage?: Record<string, number>;
  counts?: Record<string, number>;
  periodKey?: string;
  period?: string;
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
const ADMIN_ROLES = new Set(["admin", "super_admin", "owner"]);

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
  const col = db.collection("tenants").document(tenantId).collection("usageSnapshots");
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
  const col = db.collection("tenants").document(tenantId).collection("freeTierLimits");
  const currentDoc = await col.doc("current").get();
  if (currentDoc.exists) {
    return currentDoc.data() as FreeTierLimit;
  }
  const fallbackDoc = await col.doc("default").get();
  if (fallbackDoc.exists) {
    return fallbackDoc.data() as FreeTierLimit;
  }
  const tenantDoc = await db.collection("tenants").document(tenantId).get();
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

export const createPreference = functions.https.onCall(async (data) => {
  const amount = Number(data?.amount);
  const items: PreferenceItemInput[] = Array.isArray(data?.items)
    ? data.items
    : [];
  const orderId = String(data?.orderId ?? "");

  if (!Number.isFinite(amount) || amount <= 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "amount must be a positive number."
    );
  }

  if (!orderId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "orderId is required."
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

  return { init_point: initPoint };
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
    const orderId = payment?.external_reference
      ? String(payment.external_reference)
      : "";

    const paymentUpdate = {
      status: payment?.status ?? "unknown",
      statusDetail: payment?.status_detail ?? "",
      orderId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection("payments").doc(paymentId).set(paymentUpdate, {
      merge: true,
    });

    if (orderId) {
      await db.collection("orders").doc(orderId).set(
        {
          paymentId,
          paymentStatus: payment?.status ?? "unknown",
          paymentStatusDetail: payment?.status_detail ?? "",
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }

    console.info("Mercado Pago webhook processed", {
      paymentId,
      orderId: orderId || "n/a",
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
