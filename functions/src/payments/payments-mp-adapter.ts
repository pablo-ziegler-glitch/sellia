import axios from "axios";
import { normalizeString } from "../mercadopago.helpers";

const MERCADOPAGO_API = "https://api.mercadopago.com";

type PreferenceItemInput = {
  title?: string;
  name?: string;
  quantity?: number;
  unit_price?: number;
  unitPrice?: number;
  currency_id?: string;
  currencyId?: string;
};

export type CreateMpPaymentInput = {
  accessToken: string;
  tenantId: string;
  orderId: string;
  intentId: string;
  amount: number;
  currency: string;
  description?: string;
  payerEmail?: string;
  items: PreferenceItemInput[];
  metadata?: Record<string, unknown>;
};

export type MpWebhookCanonicalPayload = {
  providerPaymentId: string;
  providerStatus: string;
  amount: number | null;
  currency: string | null;
  tenantId: string;
  orderId: string;
  intentId: string;
  rawProviderPayload: Record<string, unknown>;
};

const buildExternalReference = (tenantId: string, orderId: string, intentId: string): string => {
  return [tenantId, orderId, intentId].map((p) => encodeURIComponent(p)).join("::");
};

const parseExternalReference = (value: unknown): { tenantId: string; orderId: string; intentId: string } => {
  const raw = normalizeString(value);
  if (!raw) {
    return { tenantId: "", orderId: "", intentId: "" };
  }

  const [tenantRaw = "", orderRaw = "", intentRaw = ""] = raw.split("::");
  return {
    tenantId: decodeURIComponent(tenantRaw),
    orderId: decodeURIComponent(orderRaw),
    intentId: decodeURIComponent(intentRaw),
  };
};

const normalizeProviderStatus = (status: unknown): string => {
  return normalizeString(status).toLowerCase();
};

export const createMpPaymentIntent = async (input: CreateMpPaymentInput) => {
  const items = input.items.map((item) => {
    const quantity = Number(item.quantity ?? 1);
    const unitPrice = Number(item.unit_price ?? item.unitPrice ?? input.amount);
    return {
      title: normalizeString(item.title ?? item.name) || input.description || "Item",
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
      unit_price: Number.isFinite(unitPrice) && unitPrice > 0 ? unitPrice : input.amount,
      currency_id: normalizeString(item.currency_id ?? item.currencyId) || input.currency,
    };
  });

  const externalReference = buildExternalReference(input.tenantId, input.orderId, input.intentId);
  const metadata = {
    ...(input.metadata ?? {}),
    tenantId: input.tenantId,
    orderId: input.orderId,
    intentId: input.intentId,
  };

  const response = await axios.post(
    `${MERCADOPAGO_API}/checkout/preferences`,
    {
      items,
      external_reference: externalReference,
      metadata,
      payer: input.payerEmail ? { email: input.payerEmail } : undefined,
    },
    {
      headers: {
        Authorization: `Bearer ${input.accessToken}`,
      },
    }
  );

  return {
    preferenceId: normalizeString(response.data?.id),
    initPoint: normalizeString(response.data?.init_point),
    sandboxInitPoint: normalizeString(response.data?.sandbox_init_point),
    raw: response.data as Record<string, unknown>,
  };
};

export const fetchMpPayment = async (accessToken: string, providerPaymentId: string) => {
  const response = await axios.get(`${MERCADOPAGO_API}/v1/payments/${providerPaymentId}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  const payment = (response.data ?? {}) as Record<string, unknown>;
  const ref = parseExternalReference(payment.external_reference);
  const metadata = (payment.metadata ?? {}) as Record<string, unknown>;

  return {
    providerPaymentId,
    providerStatus: normalizeProviderStatus(payment.status),
    amount: typeof payment.transaction_amount === "number" ? payment.transaction_amount : null,
    currency: normalizeString(payment.currency_id) || null,
    tenantId: normalizeString(metadata.tenantId) || ref.tenantId,
    orderId: normalizeString(metadata.orderId) || ref.orderId,
    intentId: normalizeString(metadata.intentId) || ref.intentId,
    rawProviderPayload: payment,
  } satisfies MpWebhookCanonicalPayload;
};
