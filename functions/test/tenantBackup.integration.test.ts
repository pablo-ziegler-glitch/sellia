import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("firebase-admin", () => ({
  firestore: {
    Timestamp: {
      fromMillis: vi.fn((value: number) => ({ value })),
    },
    FieldValue: {
      serverTimestamp: vi.fn(() => "SERVER_TIMESTAMP"),
    },
  },
  default: {
    firestore: {
      Timestamp: {
        fromMillis: vi.fn((value: number) => ({ value })),
      },
      FieldValue: {
        serverTimestamp: vi.fn(() => "SERVER_TIMESTAMP"),
      },
    },
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
    https: {
      HttpsError,
    },
    default: {
      https: {
        HttpsError,
      },
    },
  };
});

type Snapshot = {
  exists: boolean;
  data: () => Record<string, unknown>;
  get: (field: string) => unknown;
};

const makeSnapshot = (data: Record<string, unknown> | null): Snapshot => ({
  exists: data !== null,
  data: () => data ?? {},
  get: (field: string) => (data ?? {})[field],
});

const normalizeString = (value: unknown): string => String(value ?? "").trim();
const toBoolean = (value: unknown): boolean => value === true;
const isAdminRole = (role: unknown): boolean => {
  const normalized = normalizeString(role).toLowerCase();
  return normalized === "owner" || normalized === "admin";
};

const baseDeps = {
  normalizeString,
  toBoolean,
  isAdminRole,
  userCanRequestTenantBackup: vi.fn(() => true),
  estimateRestoreDiff: vi.fn(async () => ({ diff: true })),
  writeTenantAuditLog: vi.fn(async () => undefined),
  backupRequestWindowMs: 10 * 60 * 1000,
};

describe("tenantBackup integration handlers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("deduplica requestTenantBackup cuando existe request reciente", async () => {
    const requestSet = vi.fn();
    const latestRequest = { id: "req-existing" };

    const db: any = {
      collection: (name: string) => {
        if (name === "users") {
          return {
            doc: () => ({
              get: async () => makeSnapshot({ role: "admin", tenantId: "tenant-a" }),
            }),
          };
        }
        if (name === "tenant_backups") {
          return {
            doc: () => ({
              collection: () => ({
                where: () => ({
                  orderBy: () => ({
                    limit: () => ({
                      get: async () => ({ empty: false, docs: [latestRequest] }),
                    }),
                  }),
                }),
                doc: () => ({ id: "new-id", set: requestSet }),
              }),
            }),
          };
        }
        throw new Error(`unexpected collection ${name}`);
      },
    };

    const { createRequestTenantBackupHandler } = await import("../src/tenantBackup");
    const handler = createRequestTenantBackupHandler({ ...baseDeps, db });

    const result = await handler(
      { tenantId: "tenant-a", reason: "backup mensual" },
      { auth: { uid: "u1", token: {} } } as any
    );

    expect(result).toEqual({ ok: true, requestId: "req-existing", deduplicated: true });
    expect(requestSet).not.toHaveBeenCalled();
  });

  it("aprueba restore como superAdmin", async () => {
    const restoreUpdate = vi.fn(async () => undefined);

    const db: any = {
      collection: (name: string) => {
        if (name === "users") {
          return {
            doc: () => ({
              get: async () => makeSnapshot({ role: "superadmin" }),
            }),
          };
        }
        if (name === "tenant_backups") {
          return {
            doc: (tenantId: string) => ({
              collection: (sub: string) => {
                if (sub !== "restore_requests") throw new Error("unexpected");
                return {
                  doc: (restoreId: string) => ({
                    get: async () =>
                      makeSnapshot({
                        tenantId,
                        restoreId,
                        status: "requested",
                        scope: "full",
                        runId: "run-1",
                        dryRun: false,
                        diffEstimate: { changed: 1 },
                      }),
                    update: restoreUpdate,
                  }),
                };
              },
            }),
          };
        }
        throw new Error(`unexpected collection ${name}`);
      },
    };

    const { createApproveTenantRestoreRequestHandler } = await import("../src/tenantBackup");
    const handler = createApproveTenantRestoreRequestHandler({ ...baseDeps, db });

    const result = await handler(
      { tenantId: "tenant-a", restoreId: "restore-1" },
      { auth: { uid: "sa-1", token: { superAdmin: true } }, rawRequest: { headers: {} } } as any
    );

    expect(restoreUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ status: "approved", approvedBy: "sa-1" })
    );
    expect(result).toEqual(
      expect.objectContaining({
        ok: true,
        tenantId: "tenant-a",
        restoreId: "restore-1",
        status: "approved",
        approvedBy: "sa-1",
      })
    );
  });

  it("rechaza tenant mismatch en requestTenantBackup", async () => {
    const db: any = {
      collection: (name: string) => {
        if (name === "users") {
          return {
            doc: () => ({
              get: async () => makeSnapshot({ role: "admin", tenantId: "tenant-a" }),
            }),
          };
        }
        if (name === "tenant_backups") {
          return {
            doc: () => ({
              collection: () => ({
                where: () => ({
                  orderBy: () => ({
                    limit: () => ({ get: async () => ({ empty: true, docs: [] }) }),
                  }),
                }),
                doc: () => ({ id: "new-id", set: vi.fn() }),
              }),
            }),
          };
        }
        throw new Error(`unexpected collection ${name}`);
      },
    };

    const { createRequestTenantBackupHandler } = await import("../src/tenantBackup");
    const handler = createRequestTenantBackupHandler({ ...baseDeps, db });

    await expect(
      handler(
        { tenantId: "tenant-b", reason: "backup mensual" },
        { auth: { uid: "u1", token: {} } } as any
      )
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("valida payload inválido en requestTenantRestore", async () => {
    const db: any = { collection: vi.fn() };

    const { createRequestTenantRestoreHandler } = await import("../src/tenantBackup");
    const handler = createRequestTenantRestoreHandler({ ...baseDeps, db });

    await expect(
      handler(
        { tenantId: "tenant-a", runId: "run-1", scope: "invalid" },
        { auth: { uid: "u1", token: {} } } as any
      )
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });
});
