import * as admin from "firebase-admin";
import * as functions from "firebase-functions";

export const BACKGROUND_JOBS_COLLECTION = "background_jobs_config";
export const BACKGROUND_JOBS_SCHEMA_VERSION = 1;

export type BackgroundJobMode = "automatic" | "on_demand";
export type BackgroundJobIntervalUnit = "seconds" | "minutes" | "hours" | "days";
export type BackgroundJobTrigger = "scheduler" | "manual";
export type BackgroundJobResult = "success" | "error";

export const BACKGROUND_JOB_IDS = [
  "collect_usage_metrics",
  "evaluate_usage_alerts",
  "refresh_public_products",
  "reconcile_pending_payments",
  "create_daily_tenant_backups",
  "archive_and_purge_tenant_backups",
  "evaluate_tenant_budget_alerts",
] as const;

export type BackgroundJobId = (typeof BACKGROUND_JOB_IDS)[number];

export type BackgroundJobDefinition = {
  jobId: BackgroundJobId;
  name: string;
  description: string;
  service: "functions" | "firestore" | "scheduler" | "payments";
  defaultMode: BackgroundJobMode;
  defaultActive: boolean;
  defaultIntervalValue: number;
  defaultIntervalUnit: BackgroundJobIntervalUnit;
};

export type BackgroundJobConfigView = {
  jobId: BackgroundJobId;
  name: string;
  description: string;
  service: BackgroundJobDefinition["service"];
  mode: BackgroundJobMode;
  active: boolean;
  intervalValue: number;
  intervalUnit: BackgroundJobIntervalUnit;
  intervalMs: number;
  environment: string;
  schemaVersion: number;
  updatedBy: string | null;
  updatedAt: string | null;
  lastRunAt: string | null;
  nextRunAt: string | null;
  lastDurationMs: number | null;
  lastResult: BackgroundJobResult | null;
  lastError: string | null;
  executionCount: number;
  lastTrigger: BackgroundJobTrigger | null;
  costTier: "low" | "medium" | "high";
  history: Array<{
    ranAt: string;
    trigger: BackgroundJobTrigger;
    durationMs: number;
    result: BackgroundJobResult;
    error: string | null;
  }>;
};

const MIN_INTERVAL_MS = 5 * 60 * 1000;
const MAX_INTERVAL_MS = 30 * 24 * 60 * 60 * 1000;

const INTERVAL_UNIT_TO_MS: Record<BackgroundJobIntervalUnit, number> = {
  seconds: 1000,
  minutes: 60 * 1000,
  hours: 60 * 60 * 1000,
  days: 24 * 60 * 60 * 1000,
};

export const BACKGROUND_JOB_DEFINITIONS: Record<BackgroundJobId, BackgroundJobDefinition> = {
  collect_usage_metrics: {
    jobId: "collect_usage_metrics",
    name: "Collect Usage Metrics",
    description: "Recolecta métricas de uso/costo desde Monitoring o BigQuery.",
    service: "functions",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 24,
    defaultIntervalUnit: "hours",
  },
  evaluate_usage_alerts: {
    jobId: "evaluate_usage_alerts",
    name: "Evaluate Usage Alerts",
    description: "Evalúa alertas de uso por tenant.",
    service: "firestore",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 6,
    defaultIntervalUnit: "hours",
  },
  refresh_public_products: {
    jobId: "refresh_public_products",
    name: "Refresh Public Products",
    description: "Sincroniza catálogo público desde products hacia public_products.",
    service: "firestore",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 2,
    defaultIntervalUnit: "hours",
  },
  reconcile_pending_payments: {
    jobId: "reconcile_pending_payments",
    name: "Reconcile Pending Payments",
    description: "Reconcilia pagos pendientes con provider externo.",
    service: "payments",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 10,
    defaultIntervalUnit: "minutes",
  },
  create_daily_tenant_backups: {
    jobId: "create_daily_tenant_backups",
    name: "Create Daily Tenant Backups",
    description: "Encola solicitudes de backup diario para tenants.",
    service: "scheduler",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 24,
    defaultIntervalUnit: "hours",
  },
  archive_and_purge_tenant_backups: {
    jobId: "archive_and_purge_tenant_backups",
    name: "Archive/Purge Tenant Backups",
    description: "Aplica retención y archive/purge de backups.",
    service: "scheduler",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 24,
    defaultIntervalUnit: "hours",
  },
  evaluate_tenant_budget_alerts: {
    jobId: "evaluate_tenant_budget_alerts",
    name: "Evaluate Tenant Budget Alerts",
    description: "Evalúa desvíos de presupuesto por tenant.",
    service: "functions",
    defaultMode: "automatic",
    defaultActive: true,
    defaultIntervalValue: 24,
    defaultIntervalUnit: "hours",
  },
};

const asString = (value: unknown): string => String(value ?? "").trim();

const normalizeMode = (value: unknown): BackgroundJobMode => {
  const normalized = asString(value).toLowerCase();
  return normalized === "on_demand" ? "on_demand" : "automatic";
};

const normalizeUnit = (value: unknown): BackgroundJobIntervalUnit => {
  const normalized = asString(value).toLowerCase();
  if (normalized === "seconds") return "seconds";
  if (normalized === "hours") return "hours";
  if (normalized === "days") return "days";
  return "minutes";
};

const coercePositiveInt = (value: unknown, fallback: number): number => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  const rounded = Math.trunc(parsed);
  return rounded > 0 ? rounded : fallback;
};

const toIso = (value: unknown): string | null => {
  if (!value) return null;
  if (typeof (value as { toDate?: () => Date }).toDate === "function") {
    return (value as { toDate: () => Date }).toDate().toISOString();
  }
  const parsed = new Date(String(value));
  if (Number.isNaN(parsed.getTime())) return null;
  return parsed.toISOString();
};

export const intervalToMs = (value: number, unit: BackgroundJobIntervalUnit): number => {
  const ms = value * INTERVAL_UNIT_TO_MS[unit];
  if (ms < MIN_INTERVAL_MS) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "El intervalo mínimo permitido es de 5 minutos."
    );
  }
  if (ms > MAX_INTERVAL_MS) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "El intervalo máximo permitido es de 30 días."
    );
  }
  return ms;
};

const toMsLenient = (value: number, unit: BackgroundJobIntervalUnit): number => {
  const rawMs = Math.trunc(value) * INTERVAL_UNIT_TO_MS[unit];
  if (!Number.isFinite(rawMs)) {
    return MIN_INTERVAL_MS;
  }
  return Math.min(Math.max(rawMs, MIN_INTERVAL_MS), MAX_INTERVAL_MS);
};

const parseHistory = (
  value: unknown
): BackgroundJobConfigView["history"] => {
  if (!Array.isArray(value)) return [];
  return value
    .map((entry) => {
      if (!entry || typeof entry !== "object") return null;
      const item = entry as Record<string, unknown>;
      const ranAt = toIso(item.ranAt);
      if (!ranAt) return null;
      const trigger = asString(item.trigger) === "manual" ? "manual" : "scheduler";
      const result = asString(item.result) === "error" ? "error" : "success";
      const durationMs = Math.max(0, Math.trunc(Number(item.durationMs) || 0));
      const error = asString(item.error) || null;
      return { ranAt, trigger, result, durationMs, error };
    })
    .filter((entry): entry is BackgroundJobConfigView["history"][number] => Boolean(entry))
    .slice(-20);
};

const defaultEnvironment = (): string =>
  asString(process.env.RUNTIME_ENVIRONMENT) || asString(process.env.NODE_ENV) || "prod";

const costTierForJob = (jobId: BackgroundJobId): "low" | "medium" | "high" => {
  if (jobId === "refresh_public_products" || jobId === "reconcile_pending_payments") return "high";
  if (jobId === "create_daily_tenant_backups" || jobId === "archive_and_purge_tenant_backups") return "high";
  if (jobId === "evaluate_usage_alerts" || jobId === "evaluate_tenant_budget_alerts") return "medium";
  return "low";
};

const computeNextRunAt = (
  mode: BackgroundJobMode,
  active: boolean,
  intervalMs: number,
  lastRunAtIso: string | null
): string | null => {
  if (!active || mode !== "automatic") return null;
  if (!lastRunAtIso) return new Date(Date.now() + intervalMs).toISOString();
  const lastRunMs = Date.parse(lastRunAtIso);
  if (!Number.isFinite(lastRunMs)) return new Date(Date.now() + intervalMs).toISOString();
  return new Date(lastRunMs + intervalMs).toISOString();
};

const buildConfigView = (
  definition: BackgroundJobDefinition,
  docData: Record<string, unknown> | null
): BackgroundJobConfigView => {
  const mode = docData ? normalizeMode(docData.mode) : definition.defaultMode;
  const active = docData ? docData.active !== false : definition.defaultActive;
  const intervalUnit = docData ? normalizeUnit(docData.intervalUnit) : definition.defaultIntervalUnit;
  const intervalValue = docData
    ? coercePositiveInt(docData.intervalValue, definition.defaultIntervalValue)
    : definition.defaultIntervalValue;
  const intervalMs = toMsLenient(intervalValue, intervalUnit);
  const lastRunAt = docData ? toIso(docData.lastRunAt) : null;

  return {
    jobId: definition.jobId,
    name: definition.name,
    description: definition.description,
    service: definition.service,
    mode,
    active,
    intervalValue,
    intervalUnit,
    intervalMs,
    environment: docData ? asString(docData.environment) || defaultEnvironment() : defaultEnvironment(),
    schemaVersion: Number(docData?.schemaVersion) || BACKGROUND_JOBS_SCHEMA_VERSION,
    updatedBy: docData ? asString(docData.updatedBy) || null : null,
    updatedAt: docData ? toIso(docData.updatedAt) : null,
    lastRunAt,
    nextRunAt: computeNextRunAt(mode, active, intervalMs, lastRunAt),
    lastDurationMs:
      docData && Number.isFinite(Number(docData.lastDurationMs))
        ? Math.max(0, Math.trunc(Number(docData.lastDurationMs)))
        : null,
    lastResult: docData
      ? (asString(docData.lastResult) === "error" ? "error" : asString(docData.lastResult) === "success" ? "success" : null)
      : null,
    lastError: docData ? asString(docData.lastError) || null : null,
    executionCount: docData ? Math.max(0, Math.trunc(Number(docData.executionCount) || 0)) : 0,
    lastTrigger: docData
      ? (asString(docData.lastTrigger) === "manual" ? "manual" : asString(docData.lastTrigger) === "scheduler" ? "scheduler" : null)
      : null,
    costTier: costTierForJob(definition.jobId),
    history: parseHistory(docData?.history),
  };
};

const assertValidJobId = (value: unknown): BackgroundJobId => {
  const normalized = asString(value);
  if ((BACKGROUND_JOB_IDS as readonly string[]).includes(normalized)) {
    return normalized as BackgroundJobId;
  }
  throw new functions.https.HttpsError("invalid-argument", "jobId inválido");
};

export const parseBackgroundJobUpdateInput = (payload: unknown): {
  jobId: BackgroundJobId;
  mode?: BackgroundJobMode;
  active?: boolean;
  intervalValue?: number;
  intervalUnit?: BackgroundJobIntervalUnit;
} => {
  if (!payload || typeof payload !== "object") {
    throw new functions.https.HttpsError("invalid-argument", "payload inválido");
  }
  const body = payload as Record<string, unknown>;
  const jobId = assertValidJobId(body.jobId);
  const result: {
    jobId: BackgroundJobId;
    mode?: BackgroundJobMode;
    active?: boolean;
    intervalValue?: number;
    intervalUnit?: BackgroundJobIntervalUnit;
  } = { jobId };

  if ("mode" in body) {
    result.mode = normalizeMode(body.mode);
  }
  if ("active" in body) {
    result.active = body.active === true;
  }
  if ("intervalUnit" in body) {
    result.intervalUnit = normalizeUnit(body.intervalUnit);
  }
  if ("intervalValue" in body) {
    const parsed = Number(body.intervalValue);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      throw new functions.https.HttpsError("invalid-argument", "intervalValue inválido");
    }
    result.intervalValue = Math.trunc(parsed);
  }

  if (result.intervalUnit && !result.intervalValue) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "intervalValue es requerido cuando se especifica intervalUnit."
    );
  }
  if (result.intervalValue && !result.intervalUnit) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "intervalUnit es requerido cuando se especifica intervalValue."
    );
  }

  return result;
};

export const parseBackgroundJobRunInput = (payload: unknown): { jobId: BackgroundJobId } => {
  if (!payload || typeof payload !== "object") {
    throw new functions.https.HttpsError("invalid-argument", "payload inválido");
  }
  const body = payload as Record<string, unknown>;
  return {
    jobId: assertValidJobId(body.jobId),
  };
};

export const listBackgroundJobsConfig = async (
  db: FirebaseFirestore.Firestore
): Promise<BackgroundJobConfigView[]> => {
  const docs = await db.collection(BACKGROUND_JOBS_COLLECTION).get();
  const byId = new Map<string, Record<string, unknown>>();
  docs.docs.forEach((doc) => byId.set(doc.id, (doc.data() || {}) as Record<string, unknown>));

  return BACKGROUND_JOB_IDS.map((jobId) =>
    buildConfigView(BACKGROUND_JOB_DEFINITIONS[jobId], byId.get(jobId) ?? null)
  );
};

export const getBackgroundJobConfig = async (
  db: FirebaseFirestore.Firestore,
  jobId: BackgroundJobId
): Promise<BackgroundJobConfigView> => {
  const snap = await db.collection(BACKGROUND_JOBS_COLLECTION).doc(jobId).get();
  return buildConfigView(
    BACKGROUND_JOB_DEFINITIONS[jobId],
    snap.exists ? ((snap.data() || {}) as Record<string, unknown>) : null
  );
};

export const upsertBackgroundJobConfig = async (params: {
  db: FirebaseFirestore.Firestore;
  input: ReturnType<typeof parseBackgroundJobUpdateInput>;
  actorUid: string;
  environment?: string;
}): Promise<BackgroundJobConfigView> => {
  const { db, input, actorUid } = params;
  const ref = db.collection(BACKGROUND_JOBS_COLLECTION).doc(input.jobId);
  const current = await getBackgroundJobConfig(db, input.jobId);
  const mode = input.mode ?? current.mode;
  const active = input.active ?? current.active;
  const intervalValue = input.intervalValue ?? current.intervalValue;
  const intervalUnit = input.intervalUnit ?? current.intervalUnit;
  const intervalMs = intervalToMs(intervalValue, intervalUnit);
  const environment = asString(params.environment) || current.environment || defaultEnvironment();

  const nextRunAt = computeNextRunAt(mode, active, intervalMs, current.lastRunAt);

  await ref.set(
    {
      jobId: input.jobId,
      name: current.name,
      description: current.description,
      service: current.service,
      mode,
      active,
      intervalValue,
      intervalUnit,
      intervalMs,
      environment,
      schemaVersion: BACKGROUND_JOBS_SCHEMA_VERSION,
      updatedBy: actorUid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      nextRunAt,
    },
    { merge: true }
  );

  return getBackgroundJobConfig(db, input.jobId);
};

export const shouldRunBackgroundJobNow = (params: {
  config: BackgroundJobConfigView;
  nowMs?: number;
}): { shouldRun: boolean; reason: string; nextRunAt: string | null } => {
  const nowMs = params.nowMs ?? Date.now();
  const { config } = params;
  const nextRunAt = computeNextRunAt(config.mode, config.active, config.intervalMs, config.lastRunAt);

  if (!config.active) {
    return { shouldRun: false, reason: "inactive", nextRunAt };
  }
  if (config.mode !== "automatic") {
    return { shouldRun: false, reason: "on_demand_mode", nextRunAt };
  }
  if (!config.lastRunAt) {
    return { shouldRun: true, reason: "first_run", nextRunAt };
  }
  const dueAtMs = Date.parse(config.lastRunAt) + config.intervalMs;
  if (!Number.isFinite(dueAtMs) || nowMs >= dueAtMs) {
    return { shouldRun: true, reason: "ttl_expired", nextRunAt };
  }
  return { shouldRun: false, reason: "ttl_not_expired", nextRunAt: new Date(dueAtMs).toISOString() };
};

export const completeBackgroundJobRun = async (params: {
  db: FirebaseFirestore.Firestore;
  jobId: BackgroundJobId;
  trigger: BackgroundJobTrigger;
  startedAtMs: number;
  result: BackgroundJobResult;
  errorMessage?: string | null;
}): Promise<BackgroundJobConfigView> => {
  const endedAtMs = Date.now();
  const durationMs = Math.max(0, endedAtMs - params.startedAtMs);
  const config = await getBackgroundJobConfig(params.db, params.jobId);
  const ref = params.db.collection(BACKGROUND_JOBS_COLLECTION).doc(params.jobId);
  const history = [
    ...config.history,
    {
      ranAt: new Date(endedAtMs).toISOString(),
      trigger: params.trigger,
      durationMs,
      result: params.result,
      error: params.errorMessage ? String(params.errorMessage).slice(0, 1200) : null,
    },
  ].slice(-20);
  const nextRunAt = computeNextRunAt(config.mode, config.active, config.intervalMs, new Date(endedAtMs).toISOString());

  await ref.set(
    {
      jobId: params.jobId,
      name: config.name,
      description: config.description,
      service: config.service,
      mode: config.mode,
      active: config.active,
      intervalValue: config.intervalValue,
      intervalUnit: config.intervalUnit,
      intervalMs: config.intervalMs,
      environment: config.environment,
      schemaVersion: BACKGROUND_JOBS_SCHEMA_VERSION,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastRunAt: admin.firestore.Timestamp.fromMillis(endedAtMs),
      nextRunAt,
      lastDurationMs: durationMs,
      lastResult: params.result,
      lastError: params.errorMessage ? String(params.errorMessage).slice(0, 1200) : null,
      executionCount: config.executionCount + 1,
      lastTrigger: params.trigger,
      history,
    },
    { merge: true }
  );

  return getBackgroundJobConfig(params.db, params.jobId);
};
