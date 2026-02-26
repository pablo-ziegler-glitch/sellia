import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import {
  rateLimit,
  requireAuth,
  requireRole,
  requireTenantScope,
  validateAndSanitize,
  type SecurityLogger,
} from "./security/callableGuards";

export type RestoreScope = "full" | "collection" | "document";
export type RestoreRequestStatus = "requested" | "approved" | "running" | "completed" | "failed";

export type RestoreRequestPayload = {
  tenantId?: unknown;
  runId?: unknown;
  scope?: unknown;
  dryRun?: unknown;
};

export type ApproveRestoreRequestPayload = {
  tenantId?: unknown;
  restoreId?: unknown;
};

type AuditPayload = {
  eventType: "backup" | "restore";
  action: string;
  actorUid?: string;
  scope?: RestoreScope | "full";
  runId?: string;
  restoreRequestId?: string;
  status: string;
  dryRun?: boolean;
  diffEstimate?: Record<string, unknown>;
  result?: Record<string, unknown>;
  ip?: string;
  userAgent?: string;
};

type TenantBackupHandlersDeps = {
  db: FirebaseFirestore.Firestore;
  normalizeString: (value: unknown) => string;
  toBoolean: (value: unknown) => boolean;
  userCanRequestTenantBackup: (
    context: functions.https.CallableContext,
    userData: admin.firestore.DocumentData
  ) => boolean;
  estimateRestoreDiff: (
    tenantId: string,
    runId: string,
    scope: RestoreScope
  ) => Promise<Record<string, unknown>>;
  writeTenantAuditLog: (tenantId: string, payload: AuditPayload) => Promise<void>;
  backupRequestWindowMs: number;
  enforceAdminRateLimit: (params: { operation: string; uid: string; tenantId: string; ip: string }) => Promise<void>;
  logSecurityEvent: SecurityLogger;
};

const RESTORE_SCOPES = new Set<RestoreScope>(["full", "collection", "document"]);

const parseBackupPayload = (
  raw: unknown,
  normalizeString: (value: unknown) => string
): { tenantId: string; reason: string } => {
  const payload = (raw ?? {}) as Record<string, unknown>;
  const tenantId = normalizeString(payload.tenantId);
  const reason = normalizeString(payload.reason);
  if (!tenantId || !reason) {
    throw new Error("tenantId y reason son requeridos");
  }
  if (reason.length < 6) {
    throw new Error("reason requiere al menos 6 caracteres");
  }
  return { tenantId, reason };
};

const parseRestorePayload = (
  raw: RestoreRequestPayload,
  normalizeString: (value: unknown) => string,
  toBoolean: (value: unknown) => boolean
): { tenantId: string; runId: string; scope: RestoreScope; dryRun: boolean } => {
  const tenantId = normalizeString(raw?.tenantId);
  const runId = normalizeString(raw?.runId);
  const scope = normalizeString(raw?.scope).toLowerCase() as RestoreScope;
  const dryRun = toBoolean(raw?.dryRun);
  if (!tenantId || !runId || !RESTORE_SCOPES.has(scope)) {
    throw new Error("tenantId, runId y scope(full|collection|document) son requeridos");
  }
  return { tenantId, runId, scope, dryRun };
};

const parseApproveRestorePayload = (
  raw: ApproveRestoreRequestPayload,
  normalizeString: (value: unknown) => string
): { tenantId: string; restoreId: string } => {
  const tenantId = normalizeString(raw?.tenantId);
  const restoreId = normalizeString(raw?.restoreId);
  if (!tenantId || !restoreId) {
    throw new Error("tenantId y restoreId son requeridos");
  }
  return { tenantId, restoreId };
};

export const createRequestTenantBackupHandler = ({
  db,
  normalizeString,
  userCanRequestTenantBackup,
  backupRequestWindowMs,
  enforceAdminRateLimit,
  logSecurityEvent,
}: TenantBackupHandlersDeps) => {
  return async (data: unknown, context: functions.https.CallableContext) => {
    const operation = "requestTenantBackup";
    const uid = await requireAuth({ operation, context, logSecurityEvent });
    const { tenantId, reason } = await validateAndSanitize({
      operation,
      uid,
      rawPayload: data,
      parser: (raw) => parseBackupPayload(raw, normalizeString),
      logSecurityEvent,
    });

    const userDoc = await db.collection("users").doc(uid).get();
    if (!userDoc.exists) {
      await logSecurityEvent({ operation, guard: "requireRole", result: "rejected", reason: "missing_user_profile", uid, tenantId });
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const userData = userDoc.data() || {};
    await requireTenantScope({
      operation,
      uid,
      requestedTenantId: tenantId,
      userData,
      context,
      allowSuperAdmin: true,
      logSecurityEvent,
    });

    await requireRole({
      operation,
      uid,
      tenantId,
      context,
      userData,
      allowedRoles: ["owner", "admin"],
      allowSuperAdmin: true,
      logSecurityEvent,
    });

    if (!userCanRequestTenantBackup(context, userData)) {
      await logSecurityEvent({ operation, guard: "requireRole", result: "rejected", reason: "custom_backup_guard_denied", uid, tenantId });
      throw new functions.https.HttpsError("permission-denied", "sin permisos para solicitar backup");
    }

    await rateLimit({
      operation,
      uid,
      tenantId,
      ip: normalizeString(context.rawRequest?.ip),
      check: () => enforceAdminRateLimit({ operation, uid, tenantId, ip: normalizeString(context.rawRequest?.ip) }),
      logSecurityEvent,
    });

    const requestsRef = db.collection("tenant_backups").doc(tenantId).collection("requests");
    const windowStart = admin.firestore.Timestamp.fromMillis(Date.now() - backupRequestWindowMs);
    const recentRequestSnap = await requestsRef
      .where("createdAt", ">=", windowStart)
      .orderBy("createdAt", "desc")
      .limit(1)
      .get();

    if (!recentRequestSnap.empty) {
      const latestRequest = recentRequestSnap.docs[0];
      return { ok: true, requestId: latestRequest.id, deduplicated: true };
    }

    const requestRef = requestsRef.doc();
    await requestRef.set({
      tenantId,
      requestId: requestRef.id,
      reason,
      status: "queued",
      createdByUid: uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      errorMessage: null,
      docCount: null,
      runId: null,
    });

    return { ok: true, requestId: requestRef.id, deduplicated: false };
  };
};

export const createRequestTenantRestoreHandler = ({
  db,
  normalizeString,
  toBoolean,
  estimateRestoreDiff,
  writeTenantAuditLog,
  enforceAdminRateLimit,
  logSecurityEvent,
}: TenantBackupHandlersDeps) => {
  return async (data: RestoreRequestPayload, context: functions.https.CallableContext) => {
    const operation = "requestTenantRestore";
    const uid = await requireAuth({ operation, context, logSecurityEvent });
    const { tenantId, runId, scope, dryRun } = await validateAndSanitize({
      operation,
      uid,
      rawPayload: data,
      parser: (raw) => parseRestorePayload(raw as RestoreRequestPayload, normalizeString, toBoolean),
      logSecurityEvent,
    });

    const callerDoc = await db.collection("users").doc(uid).get();
    if (!callerDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const callerData = callerDoc.data() || {};
    await requireTenantScope({
      operation,
      uid,
      requestedTenantId: tenantId,
      userData: callerData,
      context,
      allowSuperAdmin: true,
      logSecurityEvent,
    });
    await requireRole({
      operation,
      uid,
      tenantId,
      context,
      userData: callerData,
      allowedRoles: ["owner", "admin"],
      allowSuperAdmin: true,
      logSecurityEvent,
    });
    await rateLimit({
      operation,
      uid,
      tenantId,
      ip: normalizeString(context.rawRequest?.ip),
      check: () => enforceAdminRateLimit({ operation, uid, tenantId, ip: normalizeString(context.rawRequest?.ip) }),
      logSecurityEvent,
    });

    const tenantDoc = await db.collection("tenants").doc(tenantId).get();
    if (!tenantDoc.exists) {
      throw new functions.https.HttpsError("not-found", "tenant no existe");
    }

    const callerRole = normalizeString(callerData.role).toLowerCase();
    const isSuperAdmin =
      context.auth?.token?.superAdmin === true || callerData.isSuperAdmin === true || callerRole === "superadmin";
    const diffEstimate = await estimateRestoreDiff(tenantId, runId, scope);
    const requiresSuperAdminApproval = !isSuperAdmin;
    const nextStatus: RestoreRequestStatus = requiresSuperAdminApproval ? "requested" : "approved";
    const now = admin.firestore.FieldValue.serverTimestamp();

    const restoreRef = db.collection("tenant_backups").doc(tenantId).collection("restore_requests").doc();
    await restoreRef.set({
      tenantId,
      restoreId: restoreRef.id,
      runId,
      scope,
      dryRun,
      status: nextStatus,
      requestedBy: uid,
      approvedBy: requiresSuperAdminApproval ? null : uid,
      requiresSuperAdminApproval,
      diffEstimate,
      result: null,
      createdAt: now,
      updatedAt: now,
    });

    await writeTenantAuditLog(tenantId, {
      eventType: "restore",
      action: "RESTORE_REQUESTED",
      actorUid: uid,
      scope,
      runId,
      restoreRequestId: restoreRef.id,
      status: nextStatus,
      dryRun,
      diffEstimate,
      result: { requiresSuperAdminApproval },
      ip: normalizeString(context.rawRequest?.ip),
      userAgent: normalizeString(context.rawRequest?.headers?.["user-agent"]),
    });

    return {
      ok: true,
      tenantId,
      restoreId: restoreRef.id,
      runId,
      scope,
      dryRun,
      status: nextStatus,
      requiresSuperAdminApproval,
      message: requiresSuperAdminApproval
        ? "Solicitud de restore creada. Requiere aprobación de superAdmin antes de ejecutar."
        : "Solicitud de restore aprobada (superAdmin). No ejecuta restore automáticamente.",
    };
  };
};

export const createApproveTenantRestoreRequestHandler = ({
  db,
  normalizeString,
  writeTenantAuditLog,
  enforceAdminRateLimit,
  logSecurityEvent,
}: TenantBackupHandlersDeps) => {
  return async (data: ApproveRestoreRequestPayload, context: functions.https.CallableContext) => {
    const operation = "approveTenantRestoreRequest";
    const uid = await requireAuth({ operation, context, logSecurityEvent });
    const { tenantId, restoreId } = await validateAndSanitize({
      operation,
      uid,
      rawPayload: data,
      parser: (raw) => parseApproveRestorePayload(raw as ApproveRestoreRequestPayload, normalizeString),
      logSecurityEvent,
    });

    const approverDoc = await db.collection("users").doc(uid).get();
    if (!approverDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const approverData = approverDoc.data() || {};
    await requireRole({
      operation,
      uid,
      tenantId,
      context,
      userData: approverData,
      allowedRoles: ["superadmin"],
      allowSuperAdmin: true,
      logSecurityEvent,
    });
    await rateLimit({
      operation,
      uid,
      tenantId,
      ip: normalizeString(context.rawRequest?.ip),
      check: () => enforceAdminRateLimit({ operation, uid, tenantId, ip: normalizeString(context.rawRequest?.ip) }),
      logSecurityEvent,
    });

    const restoreRef = db.collection("tenant_backups").doc(tenantId).collection("restore_requests").doc(restoreId);
    const restoreDoc = await restoreRef.get();
    if (!restoreDoc.exists) {
      throw new functions.https.HttpsError("not-found", "restoreId no existe");
    }

    const currentStatus = normalizeString(restoreDoc.get("status")).toLowerCase();
    if (currentStatus !== "requested") {
      throw new functions.https.HttpsError("failed-precondition", "Solo solicitudes en estado requested pueden aprobarse");
    }

    await restoreRef.update({
      status: "approved",
      approvedBy: uid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await writeTenantAuditLog(tenantId, {
      eventType: "restore",
      action: "RESTORE_APPROVED",
      actorUid: uid,
      scope: (normalizeString(restoreDoc.get("scope")).toLowerCase() as RestoreScope) || "full",
      runId: normalizeString(restoreDoc.get("runId")),
      restoreRequestId: restoreId,
      status: "approved",
      dryRun: restoreDoc.get("dryRun") === true,
      diffEstimate: (restoreDoc.get("diffEstimate") as Record<string, unknown>) ?? null,
      result: { approvedBy: uid },
      ip: normalizeString(context.rawRequest?.ip),
      userAgent: normalizeString(context.rawRequest?.headers?.["user-agent"]),
    });

    return {
      ok: true,
      tenantId,
      restoreId,
      status: "approved",
      approvedBy: uid,
      message: "Solicitud aprobada. Restore sigue pendiente de ejecución controlada.",
    };
  };
};
