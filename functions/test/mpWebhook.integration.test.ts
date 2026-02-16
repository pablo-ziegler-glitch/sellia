import crypto from "crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";

const axiosGetMock = vi.fn();
const paymentsSetMock = vi.fn().mockResolvedValue(undefined);
const paymentDocMock = vi.fn(() => ({ set: paymentsSetMock }));
const paymentsCollectionMock = vi.fn(() => ({ doc: paymentDocMock }));
const tenantDocMock = vi.fn(() => ({ collection: paymentsCollectionMock }));
const tenantsCollectionMock = vi.fn(() => ({ doc: tenantDocMock }));

const firestoreMock = vi.fn(() => ({
  collection: tenantsCollectionMock,
  collectionGroup: vi.fn(),
}));

vi.mock("axios", () => ({
  default: {
    get: axiosGetMock,
  },
}));

vi.mock("firebase-admin", () => ({
  default: {
    initializeApp: vi.fn(),
    firestore: Object.assign(firestoreMock, {
      FieldPath: {
        documentId: vi.fn(() => "__name__"),
      },
      FieldValue: {
        serverTimestamp: vi.fn(() => "SERVER_TIMESTAMP"),
      },
      Timestamp: {
        fromDate: vi.fn((date: Date) => ({ __timestamp: date.toISOString() })),
      },
    }),
  },
}));

vi.mock("firebase-functions", () => {
  class HttpsError extends Error {
    code: string;

    constructor(code: string, message: string) {
      super(message);
      this.code = code;
    }
  }

  return {
    default: {
      config: () => ({
        mercadopago: {
          access_token: "test-token",
          webhook_secret: "test-secret",
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
    },
  };
});

describe("mpWebhook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("writes payment data into tenants/{tenantId}/payments/{paymentId}", async () => {
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
    const ts = "1700000000";
    const requestId = "req-123";
    const signature = crypto
      .createHmac("sha256", "test-secret")
      .update(`${ts}.${requestId}.${paymentId}`)
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
    expect(paymentsCollectionMock).toHaveBeenCalledWith("payments");
    expect(paymentDocMock).toHaveBeenCalledWith("123");
    expect(paymentsSetMock).toHaveBeenCalledWith(
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
