export type PaymentProvider = "mercado_pago";

export type PaymentIntentStatus =
  | "CREATED"
  | "REQUIRES_CONFIRMATION"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELED";

export type PaymentAttemptStatus =
  | "INITIATED"
  | "PENDING_PROVIDER"
  | "AUTHORIZED"
  | "CAPTURED"
  | "FAILED"
  | "CANCELED";

export type PaymentTransactionStatus = "PENDING" | "APPROVED" | "REJECTED" | "FAILED";

export type PaymentEventType =
  | "INTENT_CREATED"
  | "ATTEMPT_CREATED"
  | "STATUS_TRANSITION"
  | "WEBHOOK_CONFIRMED"
  | "RECONCILED";

export type PaymentIntent = {
  id: string;
  tenantId: string;
  orderId: string;
  amount: number;
  currency: string;
  provider: PaymentProvider;
  status: PaymentIntentStatus;
  metadata: Record<string, unknown>;
  providerPreferenceId: string | null;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
};

export type PaymentAttempt = {
  id: string;
  intentId: string;
  tenantId: string;
  provider: PaymentProvider;
  status: PaymentAttemptStatus;
  providerPreferenceId: string | null;
  providerPaymentId: string | null;
  lastError: string | null;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
};

export type PaymentTransaction = {
  id: string;
  tenantId: string;
  intentId: string;
  attemptId: string;
  provider: PaymentProvider;
  providerPaymentId: string;
  status: PaymentTransactionStatus;
  amount: number | null;
  currency: string | null;
  rawProviderPayload: Record<string, unknown>;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
};

export type PaymentEvent = {
  id: string;
  tenantId: string;
  intentId: string;
  attemptId: string | null;
  type: PaymentEventType;
  fromStatus: string | null;
  toStatus: string;
  source: "system" | "webhook" | "reconciliation";
  audit: {
    actorUid: string | null;
    providerEventId: string | null;
    requestId: string | null;
  };
  payload: Record<string, unknown>;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
};

export type PaymentTransition = {
  intentStatus: PaymentIntentStatus;
  attemptStatus: PaymentAttemptStatus;
  transactionStatus: PaymentTransactionStatus;
};
