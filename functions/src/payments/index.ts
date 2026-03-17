import * as legacy from "../legacyHandlers";
import { RuntimeFlagKeys } from "../config/runtime_flags";

export { parseCreatePaymentIntentInput, type CreatePaymentIntentInputDto } from "./contracts";

export const paymentsRuntimeFlag = RuntimeFlagKeys.PAYMENTS_ROUTES;
export const createPaymentIntent = legacy.createPaymentIntent;
export const confirmByWebhook = legacy.confirmByWebhook;
export const reconcilePayment = legacy.reconcilePayment;
export const createPaymentPreference = legacy.createPaymentPreference;
export const mpWebhook = legacy.mpWebhook;
export const reconcilePendingPayments = legacy.reconcilePendingPayments;
export const reconcilePendingPayment = legacy.reconcilePendingPayment;
export const setMercadoPagoToggle = legacy.setMercadoPagoToggle;
