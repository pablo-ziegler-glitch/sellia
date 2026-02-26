import * as admin from "firebase-admin";
import {
  PaymentAttempt,
  PaymentEvent,
  PaymentIntent,
  PaymentIntentStatus,
  PaymentProvider,
  PaymentTransaction,
  PaymentTransition,
} from "./entities";

const INTENT_PRIORITY: Record<PaymentIntentStatus, number> = {
  CREATED: 10,
  REQUIRES_CONFIRMATION: 20,
  PROCESSING: 30,
  FAILED: 40,
  CANCELED: 40,
  SUCCEEDED: 50,
};

const PROVIDER_TO_TRANSITION: Record<string, PaymentTransition> = {
  pending: {
    intentStatus: "PROCESSING",
    attemptStatus: "PENDING_PROVIDER",
    transactionStatus: "PENDING",
  },
  in_process: {
    intentStatus: "PROCESSING",
    attemptStatus: "AUTHORIZED",
    transactionStatus: "PENDING",
  },
  approved: {
    intentStatus: "SUCCEEDED",
    attemptStatus: "CAPTURED",
    transactionStatus: "APPROVED",
  },
  rejected: {
    intentStatus: "FAILED",
    attemptStatus: "FAILED",
    transactionStatus: "REJECTED",
  },
  cancelled: {
    intentStatus: "CANCELED",
    attemptStatus: "CANCELED",
    transactionStatus: "FAILED",
  },
  charged_back: {
    intentStatus: "FAILED",
    attemptStatus: "FAILED",
    transactionStatus: "REJECTED",
  },
};

export type CreateIntentInput = {
  tenantId: string;
  orderId: string;
  amount: number;
  currency: string;
  provider: PaymentProvider;
  metadata?: Record<string, unknown>;
  actorUid?: string | null;
};

export type RegisterProviderAttemptInput = {
  tenantId: string;
  intentId: string;
  attemptId: string;
  providerPreferenceId: string;
};

export type ConfirmByWebhookInput = {
  tenantId: string;
  intentId: string;
  attemptId: string;
  providerPaymentId: string;
  providerStatus: string;
  providerEventId?: string;
  requestId?: string;
  amount?: number | null;
  currency?: string | null;
  rawProviderPayload: Record<string, unknown>;
  source: "webhook" | "reconciliation";
  actorUid?: string | null;
};

const normalizeStatus = (value: string): string => value.trim().toLowerCase();

const resolveTransition = (providerStatus: string): PaymentTransition => {
  return (
    PROVIDER_TO_TRANSITION[normalizeStatus(providerStatus)] ?? {
      intentStatus: "FAILED",
      attemptStatus: "FAILED",
      transactionStatus: "FAILED",
    }
  );
};

const shouldTransition = (from: PaymentIntentStatus, to: PaymentIntentStatus): boolean => {
  return INTENT_PRIORITY[to] >= INTENT_PRIORITY[from];
};

export class PaymentsCoreService {
  constructor(private readonly db: FirebaseFirestore.Firestore) {}

  async createPaymentIntent(input: CreateIntentInput): Promise<{ intentId: string; attemptId: string }> {
    const now = admin.firestore.FieldValue.serverTimestamp();
    const tenantRef = this.db.collection("tenants").doc(input.tenantId);
    const intentsRef = tenantRef.collection("paymentIntents");
    const attemptsRef = tenantRef.collection("paymentAttempts");
    const eventsRef = tenantRef.collection("paymentEvents");

    const intentId = intentsRef.doc().id;
    const attemptId = attemptsRef.doc().id;

    const intent: PaymentIntent = {
      id: intentId,
      tenantId: input.tenantId,
      orderId: input.orderId,
      amount: input.amount,
      currency: input.currency,
      provider: input.provider,
      status: "REQUIRES_CONFIRMATION",
      metadata: input.metadata ?? {},
      providerPreferenceId: null,
      createdAt: now,
      updatedAt: now,
    };

    const attempt: PaymentAttempt = {
      id: attemptId,
      intentId,
      tenantId: input.tenantId,
      provider: input.provider,
      status: "INITIATED",
      providerPreferenceId: null,
      providerPaymentId: null,
      lastError: null,
      createdAt: now,
      updatedAt: now,
    };

    await this.db.runTransaction(async (tx) => {
      tx.set(intentsRef.doc(intentId), intent);
      tx.set(attemptsRef.doc(attemptId), attempt);
      const eventId = eventsRef.doc().id;
      const event: PaymentEvent = {
        id: eventId,
        tenantId: input.tenantId,
        intentId,
        attemptId,
        type: "INTENT_CREATED",
        fromStatus: null,
        toStatus: "REQUIRES_CONFIRMATION",
        source: "system",
        audit: {
          actorUid: input.actorUid ?? null,
          providerEventId: null,
          requestId: null,
        },
        payload: {
          orderId: input.orderId,
          amount: input.amount,
          currency: input.currency,
          provider: input.provider,
        },
        createdAt: now,
      };
      tx.set(eventsRef.doc(eventId), event);
    });

    return { intentId, attemptId };
  }

  async registerProviderAttempt(input: RegisterProviderAttemptInput): Promise<void> {
    const now = admin.firestore.FieldValue.serverTimestamp();
    const tenantRef = this.db.collection("tenants").doc(input.tenantId);
    await this.db.runTransaction(async (tx) => {
      tx.set(
        tenantRef.collection("paymentIntents").doc(input.intentId),
        {
          providerPreferenceId: input.providerPreferenceId,
          updatedAt: now,
        },
        { merge: true }
      );
      tx.set(
        tenantRef.collection("paymentAttempts").doc(input.attemptId),
        {
          providerPreferenceId: input.providerPreferenceId,
          status: "PENDING_PROVIDER",
          updatedAt: now,
        },
        { merge: true }
      );
    });
  }

  async confirmByWebhook(input: ConfirmByWebhookInput): Promise<{ transitionApplied: boolean }> {
    const tenantRef = this.db.collection("tenants").doc(input.tenantId);
    const intentRef = tenantRef.collection("paymentIntents").doc(input.intentId);
    const attemptRef = tenantRef.collection("paymentAttempts").doc(input.attemptId);
    const transactionRef = tenantRef.collection("paymentTransactions").doc(input.providerPaymentId);
    const idempotencyKey =
      input.providerEventId ?? `${input.providerPaymentId}:${normalizeStatus(input.providerStatus)}`;
    const eventRef = tenantRef.collection("paymentEvents").doc(idempotencyKey);
    const transition = resolveTransition(input.providerStatus);

    return this.db.runTransaction(async (tx) => {
      const [intentSnap, attemptSnap, eventSnap] = await Promise.all([
        tx.get(intentRef),
        tx.get(attemptRef),
        tx.get(eventRef),
      ]);

      if (eventSnap.exists) {
        return { transitionApplied: false };
      }

      const currentStatus = (intentSnap.get("status") as PaymentIntentStatus | undefined) ?? "CREATED";
      const transitionApplied = shouldTransition(currentStatus, transition.intentStatus);
      const now = admin.firestore.FieldValue.serverTimestamp();

      if (transitionApplied) {
        tx.set(
          intentRef,
          {
            status: transition.intentStatus,
            updatedAt: now,
          },
          { merge: true }
        );
      }

      tx.set(
        attemptRef,
        {
          providerPaymentId: input.providerPaymentId,
          status: transition.attemptStatus,
          updatedAt: now,
          lastError:
            transition.attemptStatus === "FAILED"
              ? `provider_status:${normalizeStatus(input.providerStatus)}`
              : null,
        },
        { merge: true }
      );

      const transactionPayload: PaymentTransaction = {
        id: input.providerPaymentId,
        tenantId: input.tenantId,
        intentId: input.intentId,
        attemptId: input.attemptId,
        provider: "mercado_pago",
        providerPaymentId: input.providerPaymentId,
        status: transition.transactionStatus,
        amount: input.amount ?? null,
        currency: input.currency ?? null,
        rawProviderPayload: input.rawProviderPayload,
        createdAt: now,
        updatedAt: now,
      };
      tx.set(transactionRef, transactionPayload, { merge: true });

      const event: PaymentEvent = {
        id: idempotencyKey,
        tenantId: input.tenantId,
        intentId: input.intentId,
        attemptId: input.attemptId,
        type: input.source === "webhook" ? "WEBHOOK_CONFIRMED" : "RECONCILED",
        fromStatus: currentStatus,
        toStatus: transition.intentStatus,
        source: input.source,
        audit: {
          actorUid: input.actorUid ?? null,
          providerEventId: input.providerEventId ?? null,
          requestId: input.requestId ?? null,
        },
        payload: {
          providerStatus: input.providerStatus,
          providerPaymentId: input.providerPaymentId,
        },
        createdAt: now,
      };
      tx.set(eventRef, event);

      return { transitionApplied };
    });
  }
}
