import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

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
  isAdminRole: (role: unknown) => boolean;
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
};

const RESTORE_SCOPES = new Set<RestoreScope>(["full", "collection", "document"]);

export const createRequestTenantBackupHandler = ({
  db,
  normalizeString,
  userCanRequestTenantBackup,
  backupRequestWindowMs,
}: TenantBackupHandlersDeps) => {
  return async (data: unknown, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);
    const reason = normalizeString(payload.reason);

    if (!tenantId || !reason) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId y reason son requeridos");
    }
    if (reason.length < 6) {
      throw new functions.https.HttpsError("invalid-argument", "reason requiere al menos 6 caracteres");
    }

    const userDoc = await db.collection("users").doc(context.auth.uid).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const userData = userDoc.data() || {};
    const isSuperAdmin = context.auth.token.superAdmin === true;
    if (!isSuperAdmin && normalizeString(userData.tenantId) !== tenantId) {
      throw new functions.https.HttpsError("permission-denied", "tenant inválido para el usuario");
    }
    if (!userCanRequestTenantBackup(context, userData)) {
      throw new functions.https.HttpsError("permission-denied", "sin permisos para solicitar backup");
    }

    const requestsRef = db.collection("tenant_backups").doc(tenantId).collection("requests");
    const windowStart = admin.firestore.Timestamp.fromMillis(Date.now() - backupRequestWindowMs);
    const recentRequestSnap = await requestsRef
      .where("createdAt", ">=", windowStart)
      .orderBy("createdAt", "desc")
      .limit(1)
      .get();

    if (!recentRequestSnap.empty) {
      const latestRequest = recentRequestSnap.docs[0];
      return {
        ok: true,
        requestId: latestRequest.id,
        deduplicated: true,
      };
    }

    const requestRef = requestsRef.doc();
    await requestRef.set({
      tenantId,
      requestId: requestRef.id,
      reason,
      status: "queued",
      createdByUid: context.auth.uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      errorMessage: null,
      docCount: null,
      runId: null,
    });

    return {
      ok: true,
      requestId: requestRef.id,
      deduplicated: false,
    };
  };
};

export const createRequestTenantRestoreHandler = ({
  db,
  normalizeString,
  toBoolean,
  isAdminRole,
  estimateRestoreDiff,
  writeTenantAuditLog,
}: TenantBackupHandlersDeps) => {
  return async (data: RestoreRequestPayload, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const tenantId = normalizeString(data?.tenantId);
    const runId = normalizeString(data?.runId);
    const scope = normalizeString(data?.scope).toLowerCase() as RestoreScope;
    const dryRun = toBoolean(data?.dryRun);

    if (!tenantId || !runId || !RESTORE_SCOPES.has(scope)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "tenantId, runId y scope(full|collection|document) son requeridos"
      );
    }

    const callerUid = context.auth.uid;
    const callerDoc = await db.collection("users").doc(callerUid).get();
    if (!callerDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const callerData = callerDoc.data() || {};
    const callerRole = normalizeString(callerData.role).toLowerCase();
    const callerTenantId = normalizeString(callerData.tenantId);
    const isSuperAdmin =
      context.auth.token.superAdmin === true ||
      callerData.isSuperAdmin === true ||
      callerRole === "superadmin";

    if (!isSuperAdmin && (!isAdminRole(callerRole) || callerTenantId !== tenantId)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Solo owner/admin del tenant o superAdmin pueden solicitar restore"
      );
    }

    const tenantDoc = await db.collection("tenants").doc(tenantId).get();
    if (!tenantDoc.exists) {
      throw new functions.https.HttpsError("not-found", "tenant no existe");
    }

    const diffEstimate = await estimateRestoreDiff(tenantId, runId, scope);
    const requiresSuperAdminApproval = !isSuperAdmin;
    const nextStatus: RestoreRequestStatus = requiresSuperAdminApproval ? "requested" : "approved";
    const now = admin.firestore.FieldValue.serverTimestamp();

    const restoreRef = db
      .collection("tenant_backups")
      .doc(tenantId)
      .collection("restore_requests")
      .doc();

    await restoreRef.set({
      tenantId,
      restoreId: restoreRef.id,
      runId,
      scope,
      dryRun,
      status: nextStatus,
      requestedBy: callerUid,
      approvedBy: requiresSuperAdminApproval ? null : callerUid,
      requiresSuperAdminApproval,
      diffEstimate,
      result: null,
      createdAt: now,
      updatedAt: now,
    });

    const ip = normalizeString(context.rawRequest?.ip);
    const userAgent = normalizeString(context.rawRequest?.headers?.["user-agent"]);
    await writeTenantAuditLog(tenantId, {
      eventType: "restore",
      action: "RESTORE_REQUESTED",
      actorUid: callerUid,
      scope,
      runId,
      restoreRequestId: restoreRef.id,
      status: nextStatus,
      dryRun,
      diffEstimate,
      result: {
        requiresSuperAdminApproval,
      },
      ip,
      userAgent,
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
}: TenantBackupHandlersDeps) => {
  return async (data: ApproveRestoreRequestPayload, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const tenantId = normalizeString(data?.tenantId);
    const restoreId = normalizeString(data?.restoreId);
    if (!tenantId || !restoreId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId y restoreId son requeridos");
    }

    const approverUid = context.auth.uid;
    const approverDoc = await db.collection("users").doc(approverUid).get();
    if (!approverDoc.exists) {
      throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
    }

    const approverData = approverDoc.data() || {};
    const approverRole = normalizeString(approverData.role).toLowerCase();
    const hasSuperAdmin =
      context.auth.token.superAdmin === true ||
      approverData.isSuperAdmin === true ||
      approverRole === "superadmin";
    if (!hasSuperAdmin) {
      throw new functions.https.HttpsError("permission-denied", "Solo superAdmin puede aprobar restore");
    }

    const restoreRef = db
      .collection("tenant_backups")
      .doc(tenantId)
      .collection("restore_requests")
      .doc(restoreId);

    const restoreDoc = await restoreRef.get();
    if (!restoreDoc.exists) {
      throw new functions.https.HttpsError("not-found", "restoreId no existe");
    }

    const currentStatus = normalizeString(restoreDoc.get("status")).toLowerCase();
    if (currentStatus !== "requested") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Solo solicitudes en estado requested pueden aprobarse"
      );
    }

    await restoreRef.update({
      status: "approved",
      approvedBy: approverUid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    const ip = normalizeString(context.rawRequest?.ip);
    const userAgent = normalizeString(context.rawRequest?.headers?.["user-agent"]);
    await writeTenantAuditLog(tenantId, {
      eventType: "restore",
      action: "RESTORE_APPROVED",
      actorUid: approverUid,
      scope: (normalizeString(restoreDoc.get("scope")).toLowerCase() as RestoreScope) || "full",
      runId: normalizeString(restoreDoc.get("runId")),
      restoreRequestId: restoreId,
      status: "approved",
      dryRun: restoreDoc.get("dryRun") === true,
      diffEstimate: (restoreDoc.get("diffEstimate") as Record<string, unknown>) ?? null,
      result: {
        approvedBy: approverUid,
      },
      ip,
      userAgent,
    });

    return {
      ok: true,
      tenantId,
      restoreId,
      status: "approved",
      approvedBy: approverUid,
      message: "Solicitud aprobada. Restore sigue pendiente de ejecución controlada.",
    };
  };
};
