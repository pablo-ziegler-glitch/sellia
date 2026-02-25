import * as functions from "firebase-functions";

const TENANT_METRICS_ROLES = new Set(["owner", "admin", "manager"]);

type UserProfile = {
  role?: unknown;
  tenantId?: unknown;
  isSuperAdmin?: unknown;
};

type AuthContext = {
  auth?: {
    uid?: string;
    token?: {
      superAdmin?: boolean;
    };
  };
};

export type MetricsAccessDecision = {
  uid: string;
  role: string;
  tenantId: string;
  isSuperAdmin: boolean;
  scope: "global" | "tenant";
  requestedTenantId: string;
};

const normalize = (value: unknown): string => String(value ?? "").trim();

export const authorizeUsageMetricsAccess = (
  data: { tenantId?: unknown } | undefined,
  context: AuthContext,
  userProfile: UserProfile | undefined
): MetricsAccessDecision => {
  const uid = normalize(context.auth?.uid);
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "auth requerido");
  }

  const role = normalize(userProfile?.role).toLowerCase();
  const tenantId = normalize(userProfile?.tenantId);
  const isSuperAdmin =
    context.auth?.token?.superAdmin === true ||
    userProfile?.isSuperAdmin === true ||
    role === "superadmin";
  const requestedTenantId = normalize(data?.tenantId);

  if (!TENANT_METRICS_ROLES.has(role) && !isSuperAdmin) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Solo owner/admin/manager pueden acceder a métricas"
    );
  }

  if (requestedTenantId) {
    if (!isSuperAdmin && tenantId !== requestedTenantId) {
      throw new functions.https.HttpsError("permission-denied", "tenant inválido para el usuario");
    }
    return {
      uid,
      role,
      tenantId,
      isSuperAdmin,
      scope: "tenant",
      requestedTenantId,
    };
  }

  if (!isSuperAdmin) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Solo superAdmin puede consultar métricas globales"
    );
  }

  return {
    uid,
    role,
    tenantId,
    isSuperAdmin,
    scope: "global",
    requestedTenantId: "",
  };
};

