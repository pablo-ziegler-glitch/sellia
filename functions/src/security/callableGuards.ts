import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export type SecurityLogPayload = {
  operation: string;
  guard: "requireAuth" | "requireRole" | "requireTenantScope" | "rateLimit" | "schemaValidation";
  result: "allowed" | "rejected";
  reason: string;
  uid?: string;
  tenantId?: string;
  targetTenantId?: string;
  ip?: string;
  details?: Record<string, unknown>;
};

export type SecurityLogger = (payload: SecurityLogPayload) => Promise<void>;

type RequireAuthParams = {
  operation: string;
  context: functions.https.CallableContext;
  logSecurityEvent: SecurityLogger;
};

export const requireAuth = async ({ operation, context, logSecurityEvent }: RequireAuthParams): Promise<string> => {
  const uid = String(context.auth?.uid ?? "").trim();
  if (!uid) {
    await logSecurityEvent({
      operation,
      guard: "requireAuth",
      result: "rejected",
      reason: "missing_auth",
      ip: String(context.rawRequest?.ip ?? "").trim(),
    });
    throw new functions.https.HttpsError("unauthenticated", "auth requerido");
  }

  await logSecurityEvent({
    operation,
    guard: "requireAuth",
    result: "allowed",
    reason: "authenticated",
    uid,
    ip: String(context.rawRequest?.ip ?? "").trim(),
  });

  return uid;
};

type RequireRoleParams = {
  operation: string;
  uid: string;
  tenantId?: string;
  context: functions.https.CallableContext;
  userData: admin.firestore.DocumentData;
  allowedRoles: string[];
  allowSuperAdmin?: boolean;
  logSecurityEvent: SecurityLogger;
};

export const requireRole = async ({
  operation,
  uid,
  tenantId,
  context,
  userData,
  allowedRoles,
  allowSuperAdmin = false,
  logSecurityEvent,
}: RequireRoleParams): Promise<void> => {
  const normalizedRole = String(userData.role ?? "").trim().toLowerCase();
  const hasAllowedRole = allowedRoles.includes(normalizedRole);
  const isSuperAdmin =
    allowSuperAdmin &&
    (context.auth?.token?.superAdmin === true ||
      userData.isSuperAdmin === true ||
      normalizedRole === "superadmin");

  if (!hasAllowedRole && !isSuperAdmin) {
    await logSecurityEvent({
      operation,
      guard: "requireRole",
      result: "rejected",
      reason: "role_not_allowed",
      uid,
      tenantId,
      details: { normalizedRole, allowedRoles },
    });
    throw new functions.https.HttpsError("permission-denied", "rol insuficiente");
  }

  await logSecurityEvent({
    operation,
    guard: "requireRole",
    result: "allowed",
    reason: isSuperAdmin ? "super_admin_bypass" : "role_allowed",
    uid,
    tenantId,
    details: { normalizedRole },
  });
};

type RequireTenantScopeParams = {
  operation: string;
  uid: string;
  requestedTenantId: string;
  userData: admin.firestore.DocumentData;
  context: functions.https.CallableContext;
  allowSuperAdmin?: boolean;
  logSecurityEvent: SecurityLogger;
};

export const requireTenantScope = async ({
  operation,
  uid,
  requestedTenantId,
  userData,
  context,
  allowSuperAdmin = false,
  logSecurityEvent,
}: RequireTenantScopeParams): Promise<void> => {
  const userTenantId = String(userData.tenantId ?? "").trim();
  const isSuperAdmin =
    allowSuperAdmin &&
    (context.auth?.token?.superAdmin === true || userData.isSuperAdmin === true);

  if (!isSuperAdmin && userTenantId !== requestedTenantId) {
    await logSecurityEvent({
      operation,
      guard: "requireTenantScope",
      result: "rejected",
      reason: "tenant_scope_mismatch",
      uid,
      tenantId: userTenantId,
      targetTenantId: requestedTenantId,
    });
    throw new functions.https.HttpsError("permission-denied", "tenant inválido para el usuario");
  }

  await logSecurityEvent({
    operation,
    guard: "requireTenantScope",
    result: "allowed",
    reason: isSuperAdmin ? "super_admin_bypass" : "tenant_scope_allowed",
    uid,
    tenantId: userTenantId,
    targetTenantId: requestedTenantId,
  });
};

type RateLimitParams = {
  operation: string;
  uid: string;
  tenantId: string;
  ip: string;
  check: () => Promise<void>;
  logSecurityEvent: SecurityLogger;
};

export const rateLimit = async ({ operation, uid, tenantId, ip, check, logSecurityEvent }: RateLimitParams): Promise<void> => {
  try {
    await check();
    await logSecurityEvent({
      operation,
      guard: "rateLimit",
      result: "allowed",
      reason: "within_limit",
      uid,
      tenantId,
      ip,
    });
  } catch (error) {
    await logSecurityEvent({
      operation,
      guard: "rateLimit",
      result: "rejected",
      reason: "rate_limit_exceeded",
      uid,
      tenantId,
      ip,
      details: { error: error instanceof Error ? error.message : String(error) },
    });
    throw error;
  }
};

export const validateAndSanitize = async <T>(params: {
  operation: string;
  uid?: string;
  rawPayload: unknown;
  parser: (raw: unknown) => T;
  logSecurityEvent: SecurityLogger;
}): Promise<T> => {
  try {
    const sanitized = params.parser(params.rawPayload);
    await params.logSecurityEvent({
      operation: params.operation,
      guard: "schemaValidation",
      result: "allowed",
      reason: "schema_valid",
      uid: params.uid,
    });
    return sanitized;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    await params.logSecurityEvent({
      operation: params.operation,
      guard: "schemaValidation",
      result: "rejected",
      reason: "schema_invalid",
      uid: params.uid,
      details: { message },
    });
    throw new functions.https.HttpsError("invalid-argument", message);
  }
};
