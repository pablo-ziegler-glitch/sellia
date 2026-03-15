import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createApproveTenantRestoreRequestHandler,
  createRequestTenantBackupHandler,
  createRequestTenantRestoreHandler,
} from "../src/tenantBackup";

type AnyRecord = Record<string, unknown>;

type Scenario = {
  role: "owner" | "superAdmin";
  uid: string;
  tenantId: string;
  targetTenantId: string;
  authToken?: AnyRecord;
  userDoc?: AnyRecord;
};

const normalizeString = (value: unknown): string => String(value ?? "").trim();
const toBoolean = (value: unknown): boolean => value === true;
const isAdminRole = (role: unknown): boolean => {
  const normalized = normalizeString(role).toLowerCase();
  return normalized === "owner" || normalized === "admin";
};

const buildCommonDeps = () => ({
  normalizeString,
  toBoolean,
  isAdminRole,
  userCanRequestTenantBackup: vi.fn(() => true),
  estimateRestoreDiff: vi.fn(async () => ({ changedDocs: 2 })),
  writeTenantAuditLog: vi.fn(async () => undefined),
  backupRequestWindowMs: 10 * 60 * 1000,
});

const createBackupDb = (params: {
  userByUid: Record<string, AnyRecord | null>;
  dedupeRequestId?: string | null;
  onSet?: (payload: AnyRecord) => void;
}) => {
  const onSet = params.onSet ?? (() => undefined);
  return {
    collection: (name: string) => {
      if (name === "users") {
        return {
          doc: (uid: string) => ({
            get: async () => {
              const user = params.userByUid[uid] ?? null;
              return { exists: user !== null, data: () => user ?? {} };
            },
          }),
        };
      }

      if (name === "tenant_backups") {
        return {
          doc: () => ({
            collection: (sub: string) => {
              if (sub !== "requests") throw new Error("unexpected subcollection");
              return {
                where: () => ({
                  orderBy: () => ({
                    limit: () => ({
                      get: async () =>
                        params.dedupeRequestId
                          ? { empty: false, docs: [{ id: params.dedupeRequestId }] }
                          : { empty: true, docs: [] },
                    }),
                  }),
                }),
                doc: () => ({
                  id: "req-new",
                  set: async (payload: AnyRecord) => onSet(payload),
                }),
              };
            },
          }),
        };
      }

      throw new Error(`unexpected collection ${name}`);
    },
  } as any;
};

const createRestoreDb = (params: {
  userByUid: Record<string, AnyRecord | null>;
  tenantExists: boolean;
  onSet?: (payload: AnyRecord) => void;
}) => {
  const onSet = params.onSet ?? (() => undefined);
  return {
    collection: (name: string) => {
      if (name === "users") {
        return {
          doc: (uid: string) => ({
            get: async () => {
              const user = params.userByUid[uid] ?? null;
              return { exists: user !== null, data: () => user ?? {} };
            },
          }),
        };
      }

      if (name === "tenants") {
        return {
          doc: () => ({ get: async () => ({ exists: params.tenantExists }) }),
        };
      }

      if (name === "tenant_backups") {
        return {
          doc: () => ({
            collection: (sub: string) => {
              if (sub !== "restore_requests") throw new Error("unexpected subcollection");
              return {
                doc: () => ({
                  id: "restore-new",
                  set: async (payload: AnyRecord) => onSet(payload),
                }),
              };
            },
          }),
        };
      }

      throw new Error(`unexpected collection ${name}`);
    },
  } as any;
};

const createApproveDb = (params: {
  approverByUid: Record<string, AnyRecord | null>;
  restoreDoc: AnyRecord | null;
  onUpdate?: (payload: AnyRecord) => void;
}) => {
  const onUpdate = params.onUpdate ?? (() => undefined);
  return {
    collection: (name: string) => {
      if (name === "users") {
        return {
          doc: (uid: string) => ({
            get: async () => {
              const user = params.approverByUid[uid] ?? null;
              return { exists: user !== null, data: () => user ?? {} };
            },
          }),
        };
      }

      if (name === "tenant_backups") {
        return {
          doc: () => ({
            collection: (sub: string) => {
              if (sub !== "restore_requests") throw new Error("unexpected subcollection");
              return {
                doc: () => ({
                  get: async () => ({
                    exists: params.restoreDoc !== null,
                    get: (field: string) => params.restoreDoc?.[field],
                  }),
                  update: async (payload: AnyRecord) => onUpdate(payload),
                }),
              };
            },
          }),
        };
      }

      throw new Error(`unexpected collection ${name}`);
    },
  } as any;
};

const scenarios: Scenario[] = [
  {
    role: "owner",
    uid: "owner-1",
    tenantId: "tenant-a",
    targetTenantId: "tenant-a",
    userDoc: { role: "owner", tenantId: "tenant-a" },
  },
  {
    role: "owner",
    uid: "owner-1",
    tenantId: "tenant-a",
    targetTenantId: "tenant-b",
    userDoc: { role: "owner", tenantId: "tenant-a" },
  },
  {
    role: "superAdmin",
    uid: "sa-1",
    tenantId: "tenant-a",
    targetTenantId: "tenant-b",
    authToken: { superAdmin: true },
    userDoc: { role: "superadmin", tenantId: "tenant-a", isSuperAdmin: true },
  },
];

describe("backoffice security suite - tenant backup/restore", () => {
  beforeEach(() => vi.clearAllMocks());

  describe("requestTenantBackup", () => {
    it.each(scenarios)(
      "policy role=$role targetTenant=$targetTenantId",
      async ({ uid, targetTenantId, userDoc, authToken, role }) => {
        const setSpy = vi.fn();
        const db = createBackupDb({ userByUid: { [uid]: userDoc ?? null }, onSet: setSpy });
        const deps = buildCommonDeps();
        const handler = createRequestTenantBackupHandler({ ...deps, db });

        const invoke = handler(
          { tenantId: targetTenantId, reason: "backup de seguridad" },
          { auth: { uid, token: authToken ?? {} } } as any
        );

        if (role === "owner" && targetTenantId !== userDoc?.tenantId) {
          await expect(invoke).rejects.toMatchObject({ code: "permission-denied" });
          expect(setSpy).not.toHaveBeenCalled();
          return;
        }

        await expect(invoke).resolves.toMatchObject({ ok: true });
      }
    );

    it("denies escalation when owner forges token.role=superadmin", async () => {
      const db = createBackupDb({
        userByUid: { "owner-1": { role: "owner", tenantId: "tenant-a" } },
      });
      const deps = buildCommonDeps();
      const handler = createRequestTenantBackupHandler({ ...deps, db });

      await expect(
        handler(
          { tenantId: "tenant-b", reason: "backup de seguridad" },
          { auth: { uid: "owner-1", token: { role: "superadmin" } } } as any
        )
      ).rejects.toMatchObject({ code: "permission-denied" });
    });
  });

  describe("requestTenantRestore", () => {
    it.each(scenarios)(
      "policy role=$role targetTenant=$targetTenantId",
      async ({ uid, targetTenantId, userDoc, authToken, role }) => {
        const setSpy = vi.fn();
        const db = createRestoreDb({
          userByUid: { [uid]: userDoc ?? null },
          tenantExists: true,
          onSet: setSpy,
        });
        const deps = buildCommonDeps();
        const handler = createRequestTenantRestoreHandler({ ...deps, db });

        const invoke = handler(
          { tenantId: targetTenantId, runId: "run-1", scope: "full", dryRun: true },
          { auth: { uid, token: authToken ?? {} }, rawRequest: { headers: {} } } as any
        );

        if (role === "owner" && targetTenantId !== userDoc?.tenantId) {
          await expect(invoke).rejects.toMatchObject({ code: "permission-denied" });
          expect(setSpy).not.toHaveBeenCalled();
          return;
        }

        await expect(invoke).resolves.toMatchObject({ ok: true, tenantId: targetTenantId });
      }
    );

    it("denies escalation when owner forges token.role=superadmin", async () => {
      const db = createRestoreDb({
        userByUid: { "owner-1": { role: "owner", tenantId: "tenant-a" } },
        tenantExists: true,
      });
      const deps = buildCommonDeps();
      const handler = createRequestTenantRestoreHandler({ ...deps, db });

      await expect(
        handler(
          { tenantId: "tenant-b", runId: "run-1", scope: "full" },
          { auth: { uid: "owner-1", token: { role: "superadmin" } }, rawRequest: { headers: {} } } as any
        )
      ).rejects.toMatchObject({ code: "permission-denied" });
    });
  });

  describe("approveTenantRestoreRequest", () => {
    it("denies owner approving restore request (escalation attempt)", async () => {
      const db = createApproveDb({
        approverByUid: { "owner-1": { role: "owner", tenantId: "tenant-a" } },
        restoreDoc: { status: "requested", scope: "full", runId: "run-1", dryRun: false },
      });
      const deps = buildCommonDeps();
      const handler = createApproveTenantRestoreRequestHandler({ ...deps, db });

      await expect(
        handler(
          { tenantId: "tenant-a", restoreId: "restore-1" },
          { auth: { uid: "owner-1", token: {} }, rawRequest: { headers: {} } } as any
        )
      ).rejects.toMatchObject({ code: "permission-denied" });
    });

    it("allows superAdmin approving cross-tenant restore request", async () => {
      const updateSpy = vi.fn();
      const db = createApproveDb({
        approverByUid: {
          "sa-1": { role: "superadmin", tenantId: "tenant-a", isSuperAdmin: true },
        },
        restoreDoc: { status: "requested", scope: "full", runId: "run-1", dryRun: false },
        onUpdate: updateSpy,
      });
      const deps = buildCommonDeps();
      const handler = createApproveTenantRestoreRequestHandler({ ...deps, db });

      await expect(
        handler(
          { tenantId: "tenant-b", restoreId: "restore-1" },
          { auth: { uid: "sa-1", token: { superAdmin: true } }, rawRequest: { headers: {} } } as any
        )
      ).resolves.toMatchObject({ ok: true, tenantId: "tenant-b", status: "approved" });
      expect(updateSpy).toHaveBeenCalled();
    });
  });
});
