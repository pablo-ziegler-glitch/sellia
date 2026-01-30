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

type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "FAILED";

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
      "orderId is required."
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
