#!/usr/bin/env node

/**
 * Backfill del catálogo público de VALKIRJA.
 *
 * Este script sincroniza todos los productos con publicStatus="published"
 * (o isPublic=true para datos legacy) desde la colección privada
 * `tenants/{tenantId}/products` hacia `tenants/{tenantId}/public_products`.
 *
 * Útil para:
 *   - Primera puesta en marcha (retrocompatibilidad con datos existentes)
 *   - Recuperación ante fallos del trigger automático
 *   - Verificación del estado del catálogo
 *
 * Uso:
 *   # Ver qué se sincronizaría (sin escribir nada):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json \
 *   VALKIRJA_TENANT_ID=tu-tenant-id \
 *   node scripts/backfill-valkirja-catalog.js --dry-run
 *
 *   # Aplicar la sincronización:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json \
 *   VALKIRJA_TENANT_ID=tu-tenant-id \
 *   node scripts/backfill-valkirja-catalog.js --apply
 *
 *   # También se puede pasar el tenantId como argumento:
 *   node scripts/backfill-valkirja-catalog.js --apply --tenant=mi-tienda
 */

const admin = require('firebase-admin');
const crypto = require('crypto');

// ─── Configuración ─────────────────────────────────────────────────────────

const isApply = process.argv.includes('--apply');
const isDryRun = !isApply;

const tenantArg = process.argv.find((a) => a.startsWith('--tenant='));
const TENANT_ID =
  (tenantArg ? tenantArg.split('=')[1] : null) ||
  process.env.VALKIRJA_TENANT_ID ||
  'valkirja';

const BATCH_MAX_OPS = 450;

// ─── Helpers (replicados de publicStore.ts para no importar TS) ─────────────

const FIREBASE_STORAGE_HOST = 'firebasestorage.googleapis.com';
const PUBLIC_PRODUCT_IMAGES_ROOT = 'public_products';

function sanitizePathSegment(value) {
  return (
    value
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '') || 'image'
  );
}

function buildPublicImageVersion(inputUrl) {
  return crypto.createHash('sha1').update(inputUrl).digest('hex').slice(0, 10);
}

function buildPublicStorageMediaUrl(bucketName, objectPath) {
  const encodedPath = encodeURIComponent(objectPath);
  return `https://${FIREBASE_STORAGE_HOST}/v0/b/${bucketName}/o/${encodedPath}?alt=media`;
}

function extractFileNameAndExtension(inputUrl) {
  try {
    const parsedUrl = new URL(inputUrl);
    const pathParts = parsedUrl.pathname.split('/').filter(Boolean);
    const lastSegment = pathParts[pathParts.length - 1] || '';
    const decodedLastSegment = decodeURIComponent(lastSegment);
    const leaf = decodedLastSegment.includes('/')
      ? decodedLastSegment.split('/').filter(Boolean).pop() ?? ''
      : decodedLastSegment;
    const cleanLeaf = leaf.trim();
    if (!cleanLeaf) return { fileName: 'image', extension: 'jpg' };
    const dotIndex = cleanLeaf.lastIndexOf('.');
    if (dotIndex <= 0 || dotIndex === cleanLeaf.length - 1) {
      return { fileName: sanitizePathSegment(cleanLeaf), extension: 'jpg' };
    }
    return {
      fileName: sanitizePathSegment(cleanLeaf.slice(0, dotIndex)),
      extension: sanitizePathSegment(cleanLeaf.slice(dotIndex + 1)) || 'jpg',
    };
  } catch {
    return { fileName: 'image', extension: 'jpg' };
  }
}

function normalizePublicImageUrl(inputUrl, tenantId, productId, index, bucketName) {
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
        decodedPath.slice(decodedPath.indexOf('/o/') + 3)
      );
    }
  } catch {
    // Sigue con normalización normal
  }
  const { fileName, extension } = extractFileNameAndExtension(inputUrl);
  const version = buildPublicImageVersion(inputUrl);
  const normalizedLeaf = `${String(index + 1).padStart(2, '0')}_${fileName}_v${version}.${extension}`;
  const objectPath = [
    'tenants', tenantId, PUBLIC_PRODUCT_IMAGES_ROOT, productId, 'images', normalizedLeaf,
  ].join('/');
  return buildPublicStorageMediaUrl(bucketName, objectPath);
}

function normalizePublicImageUrls(tenantId, productId, data, bucketName) {
  const rawUrls = Array.isArray(data.imageUrls)
    ? data.imageUrls.filter((u) => typeof u === 'string' && u.trim())
    : [];
  const legacyUrl = typeof data.imageUrl === 'string' && data.imageUrl.trim() ? data.imageUrl : null;
  const sourceUrls = [...new Set([legacyUrl, ...rawUrls].filter(Boolean))];
  return sourceUrls.map((url, index) =>
    normalizePublicImageUrl(url, tenantId, productId, index, bucketName)
  );
}

function isProductPublished(data) {
  const publicStatus =
    typeof data.publicStatus === 'string' ? data.publicStatus.toLowerCase() : null;
  if (publicStatus) return publicStatus === 'published';
  return data.isPublic === true;
}

function buildPublicProductPayload(tenantId, productId, data, bucketName) {
  const imageUrls = normalizePublicImageUrls(tenantId, productId, data, bucketName);
  return {
    id: productId,
    tenantId,
    code: data.code ?? null,
    barcode: data.barcode ?? null,
    name: data.name ?? 'Producto',
    sku: data.sku ?? data.code ?? data.barcode ?? null,
    storeName: data.storeName ?? data.tenantName ?? null,
    description: data.description ?? null,
    brand: data.brand ?? null,
    parentCategory: data.parentCategory ?? null,
    category: data.category ?? null,
    color: data.color ?? null,
    sizes: Array.isArray(data.sizes) ? data.sizes.filter((s) => typeof s === 'string') : [],
    listPrice: typeof data.listPrice === 'number' ? data.listPrice : null,
    cashPrice: typeof data.cashPrice === 'number' ? data.cashPrice : null,
    transferPrice: typeof data.transferPrice === 'number' ? data.transferPrice : null,
    imageUrl: imageUrls[0] ?? null,
    imageUrls,
    publicStatus: 'published',
    updatedAt: data.updatedAt ?? admin.firestore.FieldValue.serverTimestamp(),
    publicUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
}

// ─── Main ───────────────────────────────────────────────────────────────────

admin.initializeApp();
const db = admin.firestore();

async function main() {
  console.log('');
  console.log('╔══════════════════════════════════════════╗');
  console.log('║    VALKIRJA — Backfill Catálogo Público  ║');
  console.log('╚══════════════════════════════════════════╝');
  console.log(`  Tenant:   ${TENANT_ID}`);
  console.log(`  Modo:     ${isDryRun ? 'DRY-RUN (sin cambios)' : 'APPLY (escribe en Firestore)'}`);
  console.log('');

  const bucketName = admin.storage().bucket().name;

  // 1. Leer todos los productos del tenant
  const [publishedSnap, legacySnap, existingPublicSnap] = await Promise.all([
    db
      .collection('tenants').doc(TENANT_ID).collection('products')
      .where('publicStatus', '==', 'published').get(),
    db
      .collection('tenants').doc(TENANT_ID).collection('products')
      .where('isPublic', '==', true).get(),
    db
      .collection('tenants').doc(TENANT_ID).collection('public_products').get(),
  ]);

  // Merge: publicStatus="published" tiene prioridad sobre isPublic=true
  const publishedMap = new Map();
  for (const doc of publishedSnap.docs) publishedMap.set(doc.id, doc);
  for (const doc of legacySnap.docs) {
    if (!publishedMap.has(doc.id)) publishedMap.set(doc.id, doc);
  }

  const existingPublicIds = new Set(existingPublicSnap.docs.map((d) => d.id));

  console.log(`  Productos encontrados (publicados):  ${publishedMap.size}`);
  console.log(`  Productos en public_products actual: ${existingPublicIds.size}`);
  console.log('');

  let toSync = 0;
  let toDelete = 0;
  let skipped = 0;

  // 2. Calcular operaciones necesarias
  const publishedIds = new Set();
  const upsertOps = [];

  for (const [id, doc] of publishedMap.entries()) {
    const data = doc.data();
    if (!isProductPublished(data)) {
      skipped++;
      continue;
    }
    publishedIds.add(id);
    const payload = buildPublicProductPayload(TENANT_ID, id, data, bucketName);
    upsertOps.push({ id, payload, ref: db.collection('tenants').doc(TENANT_ID).collection('public_products').doc(id) });
    toSync++;
  }

  const deleteOps = [];
  for (const doc of existingPublicSnap.docs) {
    if (!publishedIds.has(doc.id)) {
      deleteOps.push({ id: doc.id, ref: doc.ref });
      toDelete++;
    }
  }

  console.log(`  A sincronizar (upsert):  ${toSync}`);
  console.log(`  A eliminar (no publicados): ${toDelete}`);
  console.log(`  Omitidos (no publicados): ${skipped}`);
  console.log('');

  if (isDryRun) {
    console.log('── DRY-RUN: productos que se sincronizarían ──────────────────');
    for (const { id, payload } of upsertOps) {
      console.log(`  [UPSERT] ${id} — "${payload.name}" (cat: ${payload.category ?? 'sin categoría'})`);
    }
    if (deleteOps.length > 0) {
      console.log('');
      console.log('── DRY-RUN: productos que se eliminarían del catálogo público ──');
      for (const { id } of deleteOps) {
        console.log(`  [DELETE] ${id}`);
      }
    }
    console.log('');
    console.log('Para aplicar los cambios, ejecutá con --apply');
    return;
  }

  // 3. Ejecutar en batches
  let batch = db.batch();
  let batchCount = 0;
  let committed = 0;

  for (const { ref, payload } of upsertOps) {
    batch.set(ref, payload, { merge: true });
    batchCount++;
    if (batchCount === BATCH_MAX_OPS) {
      await batch.commit();
      committed += batchCount;
      process.stdout.write(`  Progreso: ${committed}/${toSync} productos...  \r`);
      batch = db.batch();
      batchCount = 0;
    }
  }

  for (const { ref } of deleteOps) {
    batch.delete(ref);
    batchCount++;
    if (batchCount === BATCH_MAX_OPS) {
      await batch.commit();
      batch = db.batch();
      batchCount = 0;
    }
  }

  if (batchCount > 0) {
    await batch.commit();
  }

  // 4. Actualizar lastSyncedAt en config/public_store
  await db
    .collection('tenants').doc(TENANT_ID).collection('config').doc('public_store')
    .set(
      { publicEnabled: true, lastSyncedAt: admin.firestore.FieldValue.serverTimestamp() },
      { merge: true }
    );

  console.log('');
  console.log('✓ Sincronización completada:');
  console.log(`  Productos sincronizados: ${toSync}`);
  console.log(`  Productos eliminados:    ${toDelete}`);
  console.log('');
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
