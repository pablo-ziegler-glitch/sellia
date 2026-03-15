import crypto from "crypto";
import { performance } from "perf_hooks";
import { beforeEach, describe, expect, it, vi } from "vitest";

type DocState = Record<string, any>;

type DocRef = {
  path: string;
  id: string;
  collection: (name: string) => CollectionRef;
};

type CollectionRef = {
  path: string;
  doc: (id?: string) => DocRef;
};

const store = new Map<string, DocState>();
const axiosGetMock = vi.fn();

const getFromStore = (path: string): DocState | undefined => store.get(path);

const deepMerge = (target: Record<string, any>, source: Record<string, any>): Record<string, any> => {
  const output = { ...target };
  for (const [key, value] of Object.entries(source)) {
    if (
      value &&
      typeof value === "object" &&
      !Array.isArray(value) &&
      output[key] &&
      typeof output[key] === "object" &&
      !Array.isArray(output[key])
    ) {
      output[key] = deepMerge(output[key], value as Record<string, any>);
      continue;
    }
    output[key] = value;
  }
  return output;
};

const doc = (path: string): DocRef => {
  const segments = path.split("/");
  const id = segments[segments.length - 1];

  return {
    path,
    id,
    collection: (name: string) => collection(`${path}/${name}`),
    set: async (payload: Record<string, any>, options?: { merge?: boolean }) => {
      const existing = getFromStore(path) ?? {};
      const next = options?.merge ? deepMerge(existing, payload) : payload;
      store.set(path, next);
    },
    update: async (payload: Record<string, any>) => {
      const existing = getFromStore(path) ?? {};
      store.set(path, deepMerge(existing, payload));
    },
    get: async () => {
      const existing = getFromStore(path);
      return {
        exists: Boolean(existing),
        data: () => existing,
        get: (field: string) => existing?.[field],
      };
    },
  };
};

const collection = (path: string): CollectionRef => ({
  path,
  doc: (id?: string) => doc(`${path}/${id ?? "auto-id"}`),
});

const runTransactionMock = vi.fn(async (handler: any) => {
  const transaction = {
    get: async (docRef: DocRef) => {
      const existing = getFromStore(docRef.path);
      return {
        exists: Boolean(existing),
        get: (field: string) => existing?.[field],
      };
    },
    set: (docRef: DocRef, payload: Record<string, any>, options?: { merge?: boolean }) => {
      const existing = getFromStore(docRef.path) ?? {};
      const next = options?.merge ? deepMerge(existing, payload) : payload;
      store.set(docRef.path, next);
    },
  };

  return handler(transaction);
});

const firestoreMock = vi.fn(() => ({
  collection,
  collectionGroup: vi.fn(() => ({
    where: vi.fn(() => ({
      limit: vi.fn(() => ({
        get: vi.fn(async () => ({ empty: true, docs: [] })),
      })),
    })),
  })),
  runTransaction: runTransactionMock,
}));

vi.mock("axios", () => ({
  default: {
    get: axiosGetMock,
    isAxiosError: (error: unknown) => Boolean((error as { isAxiosError?: boolean })?.isAxiosError),
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
    storage: vi.fn(),
    default: {
      initializeApp: vi.fn(),
      firestore,
      storage: vi.fn(),
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
      pubsub: {
        schedule: () => ({
          timeZone: () => ({
            onRun: (handler: any) => handler,
          }),
        }),
      },
      firestore: {
        document: () => ({
          onWrite: (handler: any) => handler,
          onCreate: (handler: any) => handler,
        }),
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
        onCreate: (handler: any) => handler,
      }),
    },
  };

  return {
    ...functionsModule,
    default: functionsModule,
  };
});

const sign = (paymentId: string, requestId: string, ts: string): string =>
  crypto.createHmac("sha256", "test-secret").update(`id:${paymentId};request-id:${requestId};ts:${ts};`).digest("hex");

const buildReqRes = (input: { paymentId: string; requestId: string; ts: string }) => {
  const signature = sign(input.paymentId, input.requestId, input.ts);
  const req: any = {
    method: "POST",
    ip: "34.195.82.184",
    query: { "data.id": input.paymentId },
    body: {},
    get: (headerName: string) => {
      const normalized = headerName.toLowerCase();
      if (normalized === "x-signature") {
        return `ts=${input.ts},v1=${signature}`;
      }
      if (normalized === "x-request-id") {
        return input.requestId;
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

  return { req, res };
};

const percentile = (values: number[], p: number): number => {
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(index, 0)];
};

describe("mpWebhook resilience scenarios", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.clear();
    process.env.MP_ACCESS_TOKEN = "test-token";
    process.env.MP_WEBHOOK_SECRET = "test-secret";
  });

  it("handles duplicates, out-of-order webhooks and delayed provider responses", async () => {
    const { mpWebhook } = await import("../src/index");

    let payment502Calls = 0;
    axiosGetMock.mockImplementation(async (url: string) => {
      if (url.endsWith("/501")) {
        return {
          data: {
            id: 501,
            status: "approved",
            metadata: { tenantId: "tenant-resilience", orderId: "order-501" },
            date_created: "2024-06-01T10:00:00.000Z",
          },
        };
      }

      if (url.endsWith("/502")) {
        payment502Calls += 1;
        if (payment502Calls === 1) {
          await new Promise((resolve) => setTimeout(resolve, 80));
          return {
            data: {
              id: 502,
              status: "approved",
              metadata: { tenantId: "tenant-resilience", orderId: "order-502" },
              date_created: "2024-06-01T10:02:00.000Z",
            },
          };
        }

        return {
          data: {
            id: 502,
            status: "pending",
            metadata: { tenantId: "tenant-resilience", orderId: "order-502" },
            date_created: "2024-06-01T10:01:00.000Z",
          },
        };
      }

      return { data: undefined };
    });

    const ts = String(Math.floor(Date.now() / 1000));

    const first = buildReqRes({ paymentId: "501", requestId: "req-a", ts });
    await mpWebhook(first.req, first.res);

    const duplicate = buildReqRes({ paymentId: "501", requestId: "req-a", ts });
    await mpWebhook(duplicate.req, duplicate.res);

    const delayedApproved = buildReqRes({
      paymentId: "502",
      requestId: "req-b",
      ts: String(Number(ts) + 1),
    });
    await mpWebhook(delayedApproved.req, delayedApproved.res);

    const stalePending = buildReqRes({
      paymentId: "502",
      requestId: "req-c",
      ts: String(Number(ts) + 2),
    });
    await mpWebhook(stalePending.req, stalePending.res);

    expect(first.res.statusCode).toBe(200);
    expect(duplicate.res.statusCode).toBe(401);
    expect(delayedApproved.res.statusCode).toBe(200);
    expect(stalePending.res.statusCode).toBe(200);

    const approvedPayment = store.get("tenants/tenant-resilience/payments/502");
    expect(approvedPayment?.status).toBe("APPROVED");
    expect(approvedPayment?.orderId).toBe("order-502");
  });

  it("does not overwrite terminal statuses", async () => {
    const { mpWebhook } = await import("../src/index");

    store.set("tenants/tenant-terminal/payments/700", {
      status: "APPROVED",
      provider: "mercado_pago",
    });

    axiosGetMock.mockResolvedValueOnce({
      data: {
        id: 700,
        status: "pending",
        metadata: { tenantId: "tenant-terminal", orderId: "order-700" },
        date_created: "2024-06-01T10:00:00.000Z",
      },
    });

    const payload = buildReqRes({
      paymentId: "700",
      requestId: "req-terminal",
      ts: String(Math.floor(Date.now() / 1000)),
    });

    await mpWebhook(payload.req, payload.res);

    expect(payload.res.statusCode).toBe(200);
    expect(store.get("tenants/tenant-terminal/payments/700")?.status).toBe("APPROVED");
  });

  it("returns 500 when provider times out", async () => {
    const { mpWebhook } = await import("../src/index");

    axiosGetMock.mockRejectedValueOnce({
      isAxiosError: true,
      code: "ECONNABORTED",
      message: "timeout of 5000ms exceeded",
    });

    const payload = buildReqRes({
      paymentId: "900",
      requestId: "req-timeout",
      ts: String(Math.floor(Date.now() / 1000)),
    });

    await mpWebhook(payload.req, payload.res);

    expect(payload.res.statusCode).toBe(500);
    expect(payload.res.body).toBe("error");
  });

  it("measures p95/p99 latency of confirmation pipeline", async () => {
    const { mpWebhook } = await import("../src/index");

    const delays = Array.from({ length: 50 }, (_, index) => {
      if (index >= 49) return 120;
      if (index >= 47) return 55;
      if (index >= 40) return 25;
      return 5;
    });

    axiosGetMock.mockImplementation(() => {
      const delay = delays.shift() ?? 5;
      const id = 1000 + delays.length;
      return new Promise((resolve) =>
        setTimeout(
          () =>
            resolve({
              data: {
                id,
                status: "approved",
                metadata: { tenantId: "tenant-latency", orderId: `order-${id}` },
                date_created: "2024-06-01T10:00:00.000Z",
              },
            }),
          delay
        )
      );
    });

    const elapsed: number[] = [];

    for (let i = 0; i < 50; i += 1) {
      const started = performance.now();
      const payload = buildReqRes({
        paymentId: String(1000 + i),
        requestId: `req-latency-${i}`,
        ts: String(Math.floor(Date.now() / 1000) + i),
      });
      await mpWebhook(payload.req, payload.res);
      elapsed.push(performance.now() - started);
      expect(payload.res.statusCode).toBe(200);
    }

    const p95 = percentile(elapsed, 95);
    const p99 = percentile(elapsed, 99);

    expect(p95).toBeLessThan(90);
    expect(p99).toBeLessThan(180);
  });
});
