import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
const LANDING_SCHEMA_VERSION = 1;
const LATEST_LANDING_SCHEMA_VERSION = 2;

const landingSchemaV1 = {
  type: "object",
  additionalProperties: false,
  required: ["hero", "story", "highlights", "catalogPreview", "socialProof", "faq", "ctaFinal", "footer"],
  properties: {
    hero: {
      type: "object",
      additionalProperties: false,
      required: ["title", "subtitle", "primaryCtaLabel"],
      properties: {
        title: { type: "string", minLength: 1, maxLength: 120 },
        subtitle: { type: "string", minLength: 1, maxLength: 280 },
        primaryCtaLabel: { type: "string", minLength: 1, maxLength: 60 },
        secondaryCtaLabel: { type: "string", maxLength: 60 },
        mediaUrl: { type: "string", maxLength: 2048 },
      },
    },
    story: {
      type: "object",
      additionalProperties: false,
      required: ["title", "paragraphs"],
      properties: {
        title: { type: "string", minLength: 1, maxLength: 120 },
        paragraphs: {
          type: "array",
          minItems: 1,
          maxItems: 6,
          items: { type: "string", minLength: 1, maxLength: 400 },
        },
      },
    },
    highlights: {
      type: "object",
      additionalProperties: false,
      required: ["items"],
      properties: {
        items: {
          type: "array",
          minItems: 1,
          maxItems: 12,
          items: {
            type: "object",
            additionalProperties: false,
            required: ["title", "description"],
            properties: {
              title: { type: "string", minLength: 1, maxLength: 80 },
              description: { type: "string", minLength: 1, maxLength: 240 },
              icon: { type: "string", maxLength: 60 },
            },
          },
        },
      },
    },
    catalogPreview: {
      type: "object",
      additionalProperties: false,
      required: ["title", "productIds"],
      properties: {
        title: { type: "string", minLength: 1, maxLength: 120 },
        productIds: {
          type: "array",
          maxItems: 24,
          items: { type: "string", minLength: 1, maxLength: 120 },
        },
        showPrices: { type: "boolean" },
      },
    },
    socialProof: {
      type: "object",
      additionalProperties: false,
      required: ["testimonials"],
      properties: {
        testimonials: {
          type: "array",
          maxItems: 12,
          items: {
            type: "object",
            additionalProperties: false,
            required: ["quote", "author"],
            properties: {
              quote: { type: "string", minLength: 1, maxLength: 280 },
              author: { type: "string", minLength: 1, maxLength: 120 },
              rating: { type: "number", minimum: 1, maximum: 5 },
            },
          },
        },
        logos: {
          type: "array",
          maxItems: 20,
          items: { type: "string", maxLength: 2048 },
        },
      },
    },
    faq: {
      type: "object",
      additionalProperties: false,
      required: ["items"],
      properties: {
        items: {
          type: "array",
          minItems: 1,
          maxItems: 20,
          items: {
            type: "object",
            additionalProperties: false,
            required: ["question", "answer"],
            properties: {
              question: { type: "string", minLength: 1, maxLength: 160 },
              answer: { type: "string", minLength: 1, maxLength: 700 },
            },
          },
        },
      },
    },
    ctaFinal: {
      type: "object",
      additionalProperties: false,
      required: ["title", "buttonLabel"],
      properties: {
        title: { type: "string", minLength: 1, maxLength: 120 },
        subtitle: { type: "string", maxLength: 280 },
        buttonLabel: { type: "string", minLength: 1, maxLength: 60 },
      },
    },
    footer: {
      type: "object",
      additionalProperties: false,
      required: ["copyright"],
      properties: {
        copyright: { type: "string", minLength: 1, maxLength: 120 },
        links: {
          type: "array",
          maxItems: 12,
          items: {
            type: "object",
            additionalProperties: false,
            required: ["label", "href"],
            properties: {
              label: { type: "string", minLength: 1, maxLength: 60 },
              href: { type: "string", minLength: 1, maxLength: 2048 },
            },
          },
        },
      },
    },
  },
} as const;

const requiredBlocks = ["hero", "story", "highlights", "catalogPreview", "socialProof", "faq", "ctaFinal", "footer"] as const;

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null && !Array.isArray(value);

const validateLandingV1 = (value: unknown): string[] => {
  if (!isRecord(value)) {
    return ["/ must be object"]; 
  }

  const errors: string[] = [];
  for (const block of requiredBlocks) {
    if (!isRecord(value[block])) {
      errors.push(`/${block} must be object`);
    }
  }

  const hero = value.hero;
  if (isRecord(hero)) {
    if (typeof hero.title !== "string" || !hero.title.trim()) errors.push("/hero/title is required");
    if (typeof hero.subtitle !== "string" || !hero.subtitle.trim()) errors.push("/hero/subtitle is required");
    if (typeof hero.primaryCtaLabel !== "string" || !hero.primaryCtaLabel.trim()) errors.push("/hero/primaryCtaLabel is required");
  }

  const story = value.story;
  if (isRecord(story)) {
    if (typeof story.title !== "string" || !story.title.trim()) errors.push("/story/title is required");
    if (!Array.isArray(story.paragraphs) || story.paragraphs.length === 0) errors.push("/story/paragraphs must be non-empty array");
  }

  const highlights = value.highlights;
  if (isRecord(highlights) && (!Array.isArray(highlights.items) || highlights.items.length === 0)) {
    errors.push("/highlights/items must be non-empty array");
  }

  const catalogPreview = value.catalogPreview;
  if (isRecord(catalogPreview)) {
    if (typeof catalogPreview.title !== "string" || !catalogPreview.title.trim()) errors.push("/catalogPreview/title is required");
    if (!Array.isArray(catalogPreview.productIds)) errors.push("/catalogPreview/productIds must be array");
  }

  const socialProof = value.socialProof;
  if (isRecord(socialProof) && !Array.isArray(socialProof.testimonials)) {
    errors.push("/socialProof/testimonials must be array");
  }

  const faq = value.faq;
  if (isRecord(faq) && (!Array.isArray(faq.items) || faq.items.length === 0)) {
    errors.push("/faq/items must be non-empty array");
  }

  const ctaFinal = value.ctaFinal;
  if (isRecord(ctaFinal)) {
    if (typeof ctaFinal.title !== "string" || !ctaFinal.title.trim()) errors.push("/ctaFinal/title is required");
    if (typeof ctaFinal.buttonLabel !== "string" || !ctaFinal.buttonLabel.trim()) errors.push("/ctaFinal/buttonLabel is required");
  }

  const footer = value.footer;
  if (isRecord(footer) && (typeof footer.copyright !== "string" || !footer.copyright.trim())) {
    errors.push("/footer/copyright is required");
  }

  return errors;
};

type LandingConfigWritePayload = {
  tenantId?: unknown;
  draftVersion: unknown;
  publishedVersion?: unknown;
  publishNowConfirmed?: unknown;
};

type LandingConfigDocument = {
  schemaVersion: number;
  draftVersion: Record<string, unknown>;
  publishedVersion: Record<string, unknown> | null;
  metadata?: Record<string, unknown>;
};

const normalizeString = (value: unknown): string => (typeof value === "string" ? value.trim() : "");

const assertLandingVersion = (value: unknown, fieldName: "draftVersion" | "publishedVersion"): Record<string, unknown> | null => {
  if (value === null || value === undefined) {
    return fieldName === "publishedVersion" ? null : (() => { throw new Error(`${fieldName} es requerido`); })();
  }

  const validationErrors = validateLandingV1(value);
  if (validationErrors.length > 0) {
    throw new Error(`${fieldName} inválido: ${validationErrors.join("; ")}`);
  }

  return value as Record<string, unknown>;
};

const ensureCanWriteGlobal = (context: functions.https.CallableContext, userData: Record<string, unknown>): void => {
  const role = normalizeString(userData.role).toLowerCase();
  const isSuperAdmin = context.auth?.token?.superAdmin === true || userData.isSuperAdmin === true;
  if (!isSuperAdmin && role !== "owner") {
    throw new functions.https.HttpsError("permission-denied", "solo owner/superAdmin puede editar landing global");
  }
};

const ensureCanWriteTenant = (
  context: functions.https.CallableContext,
  userData: Record<string, unknown>,
  requestedTenantId: string
): void => {
  const role = normalizeString(userData.role).toLowerCase();
  const callerTenantId = normalizeString(userData.tenantId);
  const isSuperAdmin = context.auth?.token?.superAdmin === true || userData.isSuperAdmin === true;
  if (isSuperAdmin) {
    return;
  }
  if (!["owner", "admin"].includes(role) || callerTenantId !== requestedTenantId) {
    throw new functions.https.HttpsError("permission-denied", "sin permisos sobre tenant objetivo");
  }
};

export const isPublishConfirmationAccepted = (value: unknown): boolean => value === true;

const parsePayload = (
  data: unknown
): { draftVersion: Record<string, unknown>; publishedVersion: Record<string, unknown> | null; publishNowConfirmed: boolean } => {
  const payload = (data ?? {}) as LandingConfigWritePayload;
  const draftVersion = assertLandingVersion(payload.draftVersion, "draftVersion");
  const publishedVersion = assertLandingVersion(payload.publishedVersion, "publishedVersion");
  const publishNowConfirmed = isPublishConfirmationAccepted(payload.publishNowConfirmed);

  if (publishedVersion && !publishNowConfirmed) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Confirmación requerida: publicar impacta de forma inmediata en la landing pública."
    );
  }

  return { draftVersion: draftVersion as Record<string, unknown>, publishedVersion, publishNowConfirmed };
};

const getUserData = async (db: admin.firestore.Firestore, uid: string): Promise<Record<string, unknown>> => {
  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists) {
    throw new functions.https.HttpsError("permission-denied", "perfil no encontrado");
  }
  return (userDoc.data() ?? {}) as Record<string, unknown>;
};

const migrateV1toV2 = (doc: LandingConfigDocument): LandingConfigDocument => ({
  ...doc,
  schemaVersion: 2,
  metadata: {
    ...(doc.metadata ?? {}),
    experimentTags: Array.isArray(doc.metadata?.experimentTags) ? doc.metadata?.experimentTags : [],
  },
});

export const migrateLandingConfigDocument = (input: unknown, targetVersion = LATEST_LANDING_SCHEMA_VERSION): LandingConfigDocument => {
  const source = (input ?? {}) as LandingConfigDocument;
  const currentVersion = Number(source.schemaVersion || LANDING_SCHEMA_VERSION);

  if (currentVersion > targetVersion) {
    throw new Error(`No se puede migrar desde versión futura ${currentVersion}`);
  }

  let migrated: LandingConfigDocument = {
    schemaVersion: currentVersion,
    draftVersion: assertLandingVersion(source.draftVersion, "draftVersion") as Record<string, unknown>,
    publishedVersion: assertLandingVersion(source.publishedVersion, "publishedVersion"),
    metadata: source.metadata,
  };

  if (migrated.schemaVersion === 1 && targetVersion >= 2) {
    migrated = migrateV1toV2(migrated);
  }

  return migrated;
};

export const createSetMainLandingConfigHandler = (db: admin.firestore.Firestore) => async (data: unknown, context: functions.https.CallableContext) => {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError("unauthenticated", "auth requerido");
  }

  const userData = await getUserData(db, context.auth.uid);
  ensureCanWriteGlobal(context, userData);
  const { draftVersion, publishedVersion } = parsePayload(data);

  await db.collection("global").doc("public_site").collection("config").doc("main_landing").set(
    {
      schemaVersion: LANDING_SCHEMA_VERSION,
      draftVersion,
      publishedVersion,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: context.auth.uid,
    },
    { merge: true }
  );

  return { ok: true, scope: "global", docPath: "global/public_site/config/main_landing" };
};

export const createSetTenantStoreLandingConfigHandler = (db: admin.firestore.Firestore) => async (
  data: unknown,
  context: functions.https.CallableContext
) => {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError("unauthenticated", "auth requerido");
  }

  const payload = (data ?? {}) as LandingConfigWritePayload;
  const tenantId = normalizeString(payload.tenantId);
  if (!tenantId) {
    throw new functions.https.HttpsError("invalid-argument", "tenantId requerido");
  }

  const userData = await getUserData(db, context.auth.uid);
  ensureCanWriteTenant(context, userData, tenantId);
  const { draftVersion, publishedVersion } = parsePayload(data);

  await db.collection("tenants").doc(tenantId).collection("config").doc("store_landing").set(
    {
      schemaVersion: LANDING_SCHEMA_VERSION,
      draftVersion,
      publishedVersion,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: context.auth.uid,
    },
    { merge: true }
  );

  return { ok: true, tenantId, scope: "tenant", docPath: `tenants/${tenantId}/config/store_landing` };
};
