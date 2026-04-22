import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { createHash } from "crypto";

export type PublicProductPayload = {
  id: string;
  tenantId: string;
  code?: string | null;
  barcode?: string | null;
  name: string;
  sku?: string | null;
  storeName?: string | null;
  description?: string | null;
  brand?: string | null;
  parentCategory?: string | null;
  category?: string | null;
  color?: string | null;
  sizes: string[];
  listPrice?: number | null;
  cashPrice?: number | null;
  transferPrice?: number | null;
  imageUrl?: string | null;
  imageUrls: string[];
  publicStatus: "published";
  updatedAt?: string | admin.firestore.FieldValue;
  publicUpdatedAt: admin.firestore.FieldValue;
};

type PublicCatalogInventoryItem = {
  id: string;
  name: string;
  imageUrl: string | null;
  listPrice: number | null;
  cashPrice: number | null;
  transferPrice: number | null;
  parentCategory: string | null;
  category: string | null;
  description: string | null;
};

export const PUBLIC_PRODUCT_IMAGES_ROOT = "public_products";
export const FIREBASE_STORAGE_HOST = "firebasestorage.googleapis.com";
export const BATCH_MAX_OPS = 450;
const PUBLIC_CATALOG_INVENTORY_COLLECTION = "public_catalog_inventory";

export const isProductPublished = (data: FirebaseFirestore.DocumentData): boolean => {
  const publicStatus =
    typeof data.publicStatus === "string" ? data.publicStatus.toLowerCase() : null;
  if (publicStatus) {
    return publicStatus === "published";
  }
  return data.isPublic === true;
};

export const normalizeImageSourceUrl = (value: unknown): string | null => {
  if (typeof value !== "string") {
    return null;
  }
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
};

export const sanitizePathSegment = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "") || "image";

export const extractFileNameAndExtension = (
  inputUrl: string
): { fileName: string; extension: string } => {
  try {
    const parsedUrl = new URL(inputUrl);
    const pathParts = parsedUrl.pathname.split("/").filter(Boolean);
    const lastSegment = pathParts[pathParts.length - 1] || "";
    const decodedLastSegment = decodeURIComponent(lastSegment);
    const leaf = decodedLastSegment.includes("/")
      ? decodedLastSegment.split("/").filter(Boolean).pop() ?? ""
      : decodedLastSegment;
    const cleanLeaf = leaf.trim();
    if (cleanLeaf.length === 0) {
      return { fileName: "image", extension: "jpg" };
    }
    const dotIndex = cleanLeaf.lastIndexOf(".");
    if (dotIndex <= 0 || dotIndex === cleanLeaf.length - 1) {
      return { fileName: sanitizePathSegment(cleanLeaf), extension: "jpg" };
    }
    const fileName = sanitizePathSegment(cleanLeaf.slice(0, dotIndex));
    const extension = sanitizePathSegment(cleanLeaf.slice(dotIndex + 1));
    return {
      fileName,
      extension: extension || "jpg",
    };
  } catch (_error) {
    return { fileName: "image", extension: "jpg" };
  }
};

export const buildPublicImageVersion = (inputUrl: string): string =>
  createHash("sha1").update(inputUrl).digest("hex").slice(0, 10);

export const buildPublicStorageMediaUrl = (bucketName: string, objectPath: string): string => {
  const encodedPath = encodeURIComponent(objectPath);
  return `https://${FIREBASE_STORAGE_HOST}/v0/b/${bucketName}/o/${encodedPath}?alt=media`;
};

export const normalizePublicImageUrl = (
  inputUrl: string,
  tenantId: string,
  productId: string,
  index: number,
  bucketName: string
): string => {
  try {
    const parsed = new URL(inputUrl);
    const decodedPath = decodeURIComponent(parsed.pathname);
    const alreadyPublicPath = `tenants/${tenantId}/${PUBLIC_PRODUCT_IMAGES_ROOT}/${productId}/images/`;
    if (
      parsed.hostname === FIREBASE_STORAGE_HOST &&
      decodedPath.includes(`/o/${alreadyPublicPath}`)
    ) {
      return buildPublicStorageMediaUrl(
        bucketName,
        decodedPath.slice(decodedPath.indexOf("/o/") + 3)
      );
    }
  } catch (_error) {
    // Si la URL no es válida, se normaliza al target público igual.
  }

  const { fileName, extension } = extractFileNameAndExtension(inputUrl);
  const version = buildPublicImageVersion(inputUrl);
  const normalizedLeaf = `${String(index + 1).padStart(2, "0")}_${fileName}_v${version}.${extension}`;
  const objectPath = [
    "tenants",
    tenantId,
    PUBLIC_PRODUCT_IMAGES_ROOT,
    productId,
    "images",
    normalizedLeaf,
  ].join("/");

  return buildPublicStorageMediaUrl(bucketName, objectPath);
};

export const normalizePublicImageUrls = (
  tenantId: string,
  productId: string,
  data: FirebaseFirestore.DocumentData
): string[] => {
  const rawUrls = Array.isArray(data.imageUrls)
    ? data.imageUrls.map(normalizeImageSourceUrl).filter(Boolean)
    : [];
  const legacyUrl = normalizeImageSourceUrl(data.imageUrl);
  const sourceUrls = [legacyUrl, ...rawUrls].filter((url): url is string => Boolean(url));
  const uniqueSourceUrls = [...new Set(sourceUrls)];
  const bucketName = admin.storage().bucket().name;

  return uniqueSourceUrls.map((url, index) =>
    normalizePublicImageUrl(url, tenantId, productId, index, bucketName)
  );
};

export const buildPublicProductPayload = (
  tenantId: string,
  productId: string,
  data: FirebaseFirestore.DocumentData
): PublicProductPayload => {
  const imageUrls = normalizePublicImageUrls(tenantId, productId, data);

  return {
    id: productId,
    tenantId,
    code: data.code ?? null,
    barcode: data.barcode ?? null,
    name: data.name ?? "Producto",
    sku: data.sku ?? data.code ?? data.barcode ?? null,
    storeName: data.storeName ?? data.tenantName ?? null,
    description: data.description ?? null,
    brand: data.brand ?? null,
    parentCategory: data.parentCategory ?? null,
    category: data.category ?? null,
    color: data.color ?? null,
    sizes: Array.isArray(data.sizes)
      ? data.sizes.filter((size) => typeof size === "string")
      : [],
    listPrice: typeof data.listPrice === "number" ? data.listPrice : null,
    cashPrice: typeof data.cashPrice === "number" ? data.cashPrice : null,
    transferPrice:
      typeof data.transferPrice === "number" ? data.transferPrice : null,
    imageUrl: imageUrls[0] ?? null,
    imageUrls,
    publicStatus: "published",
    updatedAt: data.updatedAt ?? admin.firestore.FieldValue.serverTimestamp(),
    publicUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
};

const toInventoryItem = (payload: PublicProductPayload): PublicCatalogInventoryItem => ({
  id: payload.id,
  name: payload.name,
  imageUrl: payload.imageUrl ?? null,
  listPrice: typeof payload.listPrice === "number" ? payload.listPrice : null,
  cashPrice: typeof payload.cashPrice === "number" ? payload.cashPrice : null,
  transferPrice:
    typeof payload.transferPrice === "number" ? payload.transferPrice : null,
  parentCategory: payload.parentCategory ?? null,
  category: payload.category ?? null,
  description: payload.description ?? null,
});

const upsertInventoryItem = async (
  db: FirebaseFirestore.Firestore,
  tenantId: string,
  productId: string,
  payload: PublicProductPayload
): Promise<void> => {
  const inventoryRef = db.collection(PUBLIC_CATALOG_INVENTORY_COLLECTION).doc(tenantId);
  await inventoryRef.set(
    {
      tenantId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      [`items.${productId}`]: toInventoryItem(payload),
    },
    { merge: true }
  );
};

const removeInventoryItem = async (
  db: FirebaseFirestore.Firestore,
  tenantId: string,
  productId: string
): Promise<void> => {
  const inventoryRef = db.collection(PUBLIC_CATALOG_INVENTORY_COLLECTION).doc(tenantId);
  await inventoryRef.set(
    {
      tenantId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      [`items.${productId}`]: admin.firestore.FieldValue.delete(),
    },
    { merge: true }
  );
};

export const syncPublicProductsForTenant = async (
  tenantId: string,
  db: FirebaseFirestore.Firestore
): Promise<number> => {
  const [publishedSnap, legacySnap, existingPublicSnap] = await Promise.all([
    db
      .collection("tenants")
      .doc(tenantId)
      .collection("products")
      .where("publicStatus", "==", "published")
      .get(),
    db
      .collection("tenants")
      .doc(tenantId)
      .collection("products")
      .where("isPublic", "==", true)
      .get(),
    db
      .collection("tenants")
      .doc(tenantId)
      .collection("public_products")
      .get(),
  ]);

  const publishedProducts = new Map<string, admin.firestore.QueryDocumentSnapshot>();
  for (const doc of publishedSnap.docs) {
    publishedProducts.set(doc.id, doc);
  }
  for (const doc of legacySnap.docs) {
    if (!publishedProducts.has(doc.id)) {
      publishedProducts.set(doc.id, doc);
    }
  }

  const publishedIds = new Set<string>();
  const inventoryItems: Record<string, PublicCatalogInventoryItem> = {};

  let syncBatch = db.batch();
  let batchCount = 0;
  let syncedCount = 0;

  for (const productDoc of publishedProducts.values()) {
    const productData = productDoc.data();
    if (!productData || !isProductPublished(productData)) {
      continue;
    }
    publishedIds.add(productDoc.id);
    const productPayload = buildPublicProductPayload(tenantId, productDoc.id, productData);
    inventoryItems[productDoc.id] = toInventoryItem(productPayload);
    const publicRef = db
      .collection("tenants")
      .doc(tenantId)
      .collection("public_products")
      .doc(productDoc.id);
    syncBatch.set(publicRef, productPayload, { merge: true });
    batchCount += 1;
    syncedCount += 1;
    if (batchCount === BATCH_MAX_OPS) {
      await syncBatch.commit();
      syncBatch = db.batch();
      batchCount = 0;
    }
  }

  for (const publicDoc of existingPublicSnap.docs) {
    if (publishedIds.has(publicDoc.id)) {
      continue;
    }
    syncBatch.delete(publicDoc.ref);
    batchCount += 1;
    if (batchCount === BATCH_MAX_OPS) {
      await syncBatch.commit();
      syncBatch = db.batch();
      batchCount = 0;
    }
  }

  if (batchCount > 0) {
    await syncBatch.commit();
  }

  await db
    .collection(PUBLIC_CATALOG_INVENTORY_COLLECTION)
    .doc(tenantId)
    .set(
      {
        tenantId,
        items: inventoryItems,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: false }
    );

  return syncedCount;
};

export const createSyncPublicProductOnWriteHandler = (db: FirebaseFirestore.Firestore) => {
  return async (
    change: functions.Change<FirebaseFirestore.DocumentSnapshot>,
    context: functions.EventContext
  ) => {
    const { tenantId, productId } = context.params as { tenantId: string; productId: string };
    const publicRef = db
      .collection("tenants")
      .doc(tenantId)
      .collection("public_products")
      .doc(productId);

    if (!change.after.exists) {
      await Promise.all([
        publicRef.delete(),
        removeInventoryItem(db, tenantId, productId),
      ]);
      return null;
    }

    const afterData = change.after.data();
    if (!afterData) {
      return null;
    }

    if (!isProductPublished(afterData)) {
      await Promise.all([
        publicRef.delete(),
        removeInventoryItem(db, tenantId, productId),
      ]);
      return null;
    }

    const payload = buildPublicProductPayload(tenantId, productId, afterData);
    await Promise.all([
      publicRef.set(payload, { merge: true }),
      upsertInventoryItem(db, tenantId, productId, payload),
    ]);
    return null;
  };
};

export const createRefreshPublicProductsHandler = (db: FirebaseFirestore.Firestore) => {
  return async () => {
    const tenantsSnapshot = await db.collection("tenants").get();
    const now = Date.now();

    for (const tenantDoc of tenantsSnapshot.docs) {
      const tenantId = tenantDoc.id;
      const configRef = db
        .collection("tenants")
        .doc(tenantId)
        .collection("config")
        .doc("public_store");
      const configSnap = await configRef.get();
      const configData = configSnap.data() || {};
      // publicEnabled es true por defecto; solo se omite si está explícitamente en false
      const enabled = configData.publicEnabled !== false;
      if (!enabled) {
        continue;
      }

      const intervalMinutes = Number(configData.syncIntervalMinutes) || 15;
      const lastSyncedAt = configData.lastSyncedAt?.toDate?.();
      if (lastSyncedAt && now - lastSyncedAt.getTime() < intervalMinutes * 60000) {
        continue;
      }

      await syncPublicProductsForTenant(tenantId, db);
      await configRef.set(
        { lastSyncedAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );
    }
    return null;
  };
};

export const createTriggerStoreProductsSyncHandler = (
  db: FirebaseFirestore.Firestore,
  normalizeString: (value: unknown) => string
) => {
  return async (data: unknown, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }

    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);

    if (!tenantId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId requerido");
    }

    await assertTenantManagePermission(db, context, tenantId, normalizeString);

    const syncedCount = await syncPublicProductsForTenant(tenantId, db);

    await db
      .collection("tenants")
      .doc(tenantId)
      .collection("config")
      .doc("public_store")
      .set(
        {
          publicEnabled: true,
          lastSyncedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    return { ok: true, tenantId, syncedCount };
  };
};

const assertTenantManagePermission = async (
  db: FirebaseFirestore.Firestore,
  context: functions.https.CallableContext,
  tenantId: string,
  normalizeString: (value: unknown) => string
): Promise<void> => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "auth requerido");
  }

  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists) {
    throw new functions.https.HttpsError("permission-denied", "usuario sin perfil");
  }
  const userData = userDoc.data() || {};
  const isSuperAdmin =
    context.auth?.token?.superAdmin === true || userData.isSuperAdmin === true;
  const userTenantId = normalizeString(userData.tenantId);
  const userRole = normalizeString(userData.role).toLowerCase();

  if (!isSuperAdmin && (userTenantId !== tenantId || userRole !== "owner")) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "sin permisos sobre este tenant"
    );
  }
};

export const createGetPublicCatalogSyncDiagnosticsHandler = (
  db: FirebaseFirestore.Firestore,
  normalizeString: (value: unknown) => string
) => {
  return async (data: unknown, context: functions.https.CallableContext) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "auth requerido");
    }
    const payload = (data ?? {}) as Record<string, unknown>;
    const tenantId = normalizeString(payload.tenantId);
    if (!tenantId) {
      throw new functions.https.HttpsError("invalid-argument", "tenantId requerido");
    }
    await assertTenantManagePermission(db, context, tenantId, normalizeString);

    const tenantRef = db.collection("tenants").doc(tenantId);
    const productsRef = tenantRef.collection("products");
    const publicProductsRef = tenantRef.collection("public_products");

    const [publishedSnap, legacyPublishedSnap, draftCountAgg, publicSnap] = await Promise.all([
      productsRef.where("publicStatus", "==", "published").get(),
      productsRef.where("isPublic", "==", true).get(),
      productsRef.where("publicStatus", "==", "draft").count().get(),
      publicProductsRef.get(),
    ]);

    const publishedIds = new Set<string>();
    for (const doc of publishedSnap.docs) {
      if (isProductPublished(doc.data())) {
        publishedIds.add(doc.id);
      }
    }
    for (const doc of legacyPublishedSnap.docs) {
      if (isProductPublished(doc.data())) {
        publishedIds.add(doc.id);
      }
    }
    const publicIds = new Set(publicSnap.docs.map((doc) => doc.id));

    const missingInPublic = [...publishedIds].filter((id) => !publicIds.has(id));
    const orphanInPublic = [...publicIds].filter((id) => !publishedIds.has(id));

    const nowIso = new Date().toISOString();
    return {
      ok: true,
      tenantId,
      analyzedAt: nowIso,
      counts: {
        publishedProducts: publishedIds.size,
        draftProducts: draftCountAgg.data().count,
        publicProducts: publicIds.size,
        missingInPublic: missingInPublic.length,
        orphanInPublic: orphanInPublic.length,
      },
      sample: {
        missingInPublic: missingInPublic.slice(0, 50),
        orphanInPublic: orphanInPublic.slice(0, 50),
      },
      notes: [
        "missingInPublic: productos publicados que no están en public_products",
        "orphanInPublic: productos en public_products que ya no están publicados",
      ],
    };
  };
};
