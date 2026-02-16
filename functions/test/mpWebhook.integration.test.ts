import crypto from "crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";

const axiosGetMock = vi.fn();
const transactionSetMock = vi.fn();
const transactionGetMock = vi.fn(async () => ({
  exists: false,
  get: vi.fn(() => undefined),
}));
const runTransactionMock = vi.fn(async (handler: any) =>
  handler({
    get: transactionGetMock,
    set: transactionSetMock,
  })
);
const webhookNonceDocMock = vi.fn(() => ({}));
const webhookNoncesCollectionMock = vi.fn(() => ({ doc: webhookNonceDocMock }));
const paymentDocMock = vi.fn(() => ({
  collection: (name: string) =>
    name === "webhookNonces" ? webhookNoncesCollectionMock() : { doc: vi.fn(() => ({})) },
}));
const paymentEventDocMock = vi.fn(() => ({}));
const paymentsCollectionMock = vi.fn(() => ({ doc: paymentDocMock }));
const paymentEventsCollectionMock = vi.fn(() => ({ doc: paymentEventDocMock }));
const tenantDocMock = vi.fn(() => ({
  collection: (name: string) =>
    name === "payments" ? paymentsCollectionMock() : paymentEventsCollectionMock(),
}));
const tenantsCollectionMock = vi.fn(() => ({ doc: tenantDocMock }));

const firestoreMock = vi.fn(() => ({
  collection: tenantsCollectionMock,
  collectionGroup: vi.fn(),
  runTransaction: runTransactionMock,
}));

vi.mock("axios", () => ({
  default: {
    get: axiosGetMock,
  },
}));

vi.mock("firebase-admin", () => {
  const firestore = Object.assign(firestoreMock, {
    FieldPath: {
      documentId: vi.fn(() => "__name__"),
    },
    FieldValue: {
      serverTimestamp: vi.fn(() => "SERVER_TIMESTAMP"),
    },
    Timestamp: {
      fromDate: vi.fn((date: Date) => ({ __timestamp: date.toISOString() })),
      fromMillis: vi.fn((value: number) => ({ toMillis: () => value })),
    },
  });

  return {
    initializeApp: vi.fn(),
    firestore,
    default: {
      initializeApp: vi.fn(),
      firestore,
    },
  };
});


vi.mock("firebase-functions/params", () => ({
  defineString: (name: string, options?: { default?: string }) => ({
    value: () => process.env[name] ?? options?.default ?? "",
  }),
}));

vi.mock("firebase-functions", () => {
  class HttpsError extends Error {
    code: string;

    constructor(code: string, message: string) {
      super(message);
      this.code = code;
    }
  }

  const functionsModule = {
    runWith: () => ({
      https: {
        onCall: (handler: any) => handler,
      },
    }),
    https: {
      onRequest: (handler: any) => handler,
      onCall: (handler: any) => handler,
      HttpsError,
    },
    pubsub: {
      schedule: () => ({
        timeZone: () => ({
          onRun: (handler: any) => handler,
        }),
        onRun: (handler: any) => handler,
      }),
    },
    firestore: {
      document: () => ({
        onWrite: (handler: any) => handler,
      }),
    },
  };

  return {
    ...functionsModule,
    default: functionsModule,
  };
});

describe("mpWebhook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    delete process.env.MP_ACCESS_TOKEN;
    delete process.env.MP_WEBHOOK_SECRET;
  });

  it("writes payment data into tenants/{tenantId}/payments/{paymentId}", async () => {
    process.env.MP_ACCESS_TOKEN = "test-token";
    process.env.MP_WEBHOOK_SECRET = "test-secret";
    axiosGetMock.mockResolvedValueOnce({
      data: {
        id: 123,
        status: "approved",
        metadata: {
          tenantId: "tenant-001",
        },
        date_created: "2024-06-01T10:00:00.000Z",
      },
    });

    const { mpWebhook } = await import("../src/index");

    const paymentId = "123";
    const ts = String(Math.floor(Date.now() / 1000));
    const requestId = "req-123";
    const signature = crypto
      .createHmac("sha256", "test-secret")
       .update(`id:${paymentId};request-id:${requestId};ts:${ts};`)
      .digest("hex");

    const req: any = {
      method: "POST",
      query: { "data.id": paymentId },
      body: {},
      get: (headerName: string) => {
        const normalized = headerName.toLowerCase();
        if (normalized === "x-signature") {
          return `ts=${ts},v1=${signature}`;
        }
        if (normalized === "x-request-id") {
          return requestId;
        }
        return "";
      },
    };

    const res: any = {
      statusCode: 0,
      body: "",
      status(code: number) {
        this.statusCode = code;
        return this;
      },
      send(payload: string) {
        this.body = payload;
        return this;
      },
    };

    await mpWebhook(req, res);

    expect(axiosGetMock).toHaveBeenCalledWith(
      "https://api.mercadopago.com/v1/payments/123",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
      })
    );

    expect(tenantsCollectionMock).toHaveBeenCalledWith("tenants");
    expect(tenantDocMock).toHaveBeenCalledWith("tenant-001");
    expect(paymentsCollectionMock).toHaveBeenCalledTimes(2);
    expect(paymentDocMock).toHaveBeenCalledWith("123");
    expect(transactionSetMock).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        provider: "mercado_pago",
        status: "APPROVED",
        raw: expect.objectContaining({ id: 123, status: "approved" }),
      }),
      { merge: true }
    );
    expect(res.statusCode).toBe(200);
    expect(res.body).toBe("ok");
  });
});
