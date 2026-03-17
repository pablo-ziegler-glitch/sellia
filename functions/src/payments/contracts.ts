import { ensureNumber, ensureObject, ensureString } from "../contracts.validation";

export type CreatePaymentIntentInputDto = {
  tenantId: string;
  orderId: string;
  amount: number;
};

export const parseCreatePaymentIntentInput = (payload: unknown): CreatePaymentIntentInputDto => {
  const body = ensureObject(payload, "payments.createPaymentIntent.payload");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    orderId: ensureString(body.orderId, "orderId", { min: 3, max: 120 }),
    amount: ensureNumber(body.amount, "amount", { min: 0.01 }),
  };
};
