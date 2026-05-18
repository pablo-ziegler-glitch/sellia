#!/usr/bin/env node

/**
 * Normaliza productos duplicados en Firestore conservando siempre el más reciente.
 *
 * Reglas:
 * - No borra documentos físicamente.
 * - Archiva duplicados con merge lógico.
 * - Guarda snapshots históricos por cada estado previo.
 * - dry-run por defecto.
 *
 * Uso:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/key.json \
 *   node scripts/normalize-products-by-recency.js --tenant TENANT_ID --dry-run
 *
 *   node scripts/normalize-products-by-recency.js --tenant TENANT_ID --apply
 */

const admin = require('firebase-admin');
const crypto = require('crypto');

const args = process.argv.slice(2);
const tenantId = readArgValue('--tenant');
const isApply = args.includes('--apply');
const isDryRun = !isApply;
const runId = readArgValue('--run-id') || `products_norm_${Date.now()}`;
const maxReportItems = Number(readArgValue('--max-report-items') || '200');
const maxGroupIds = Number(readArgValue('--max-group-ids') || '100');

if (!tenantId) {
  console.error('Falta --tenant TENANT_ID');
  process.exit(1);
}

admin.initializeApp();
const db = admin.firestore();

const MAX_BATCH_OPS = 420;

function readArgValue(name) {
  const eqArg = args.find((arg) => arg.startsWith(`${name}=`));
  if (eqArg) {
    const value = eqArg.slice(name.length + 1).trim();
    return value || null;
  }
  const index = args.indexOf(name);
  if (index === -1) return null;
  return args[index + 1] || null;
}

function normalizeText(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return trimmed
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ');
}

function normalizeCode(value) {
  return normalizeText(value);
}

function normalizeBarcode(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed || null;
}

function fallbackUuidFromSeed(seed) {
  const digest = crypto.createHash('sha1').update(seed).digest('hex');
  return `${digest.slice(0, 8)}-${digest.slice(8, 12)}-${digest.slice(12, 16)}-${digest.slice(16, 20)}-${digest.slice(20, 32)}`;
}

function productUuidFor(docId, data) {
  const explicit = typeof data.productUuid === 'string' ? data.productUuid.trim() : '';
  if (explicit) return explicit;
  if (/^[0-9a-fA-F-]{36}$/.test(docId)) return docId;
  if (/^\d+$/.test(docId)) return fallbackUuidFromSeed(`legacy-local-${docId}`);
  const seed = [data.code, data.barcode, data.name].map((x) => (x == null ? '' : String(x))).join('|');
  return fallbackUuidFromSeed(seed || `doc-${docId}`);
}

function productScore(product) {
  let score = 0;
  score += (product.updatedAtEpochMs || 0) * 1000;
  if (product.productUuid) score += 100;
  if (product.imageUrls.length > 0) score += 50;
  if (product.code) score += 25;
  if (product.barcode) score += 25;
  if (product.quantity > 0) score += 10;
  if (product.publicStatus === 'published') score += 5;
  return score;
}

async function fetchAllProducts(productsRef) {
  const docs = [];
  let lastDoc = null;
  while (true) {
    let query = productsRef.orderBy(admin.firestore.FieldPath.documentId()).limit(500);
    if (lastDoc) query = query.startAfter(lastDoc);
    const snap = await query.get();
    if (snap.empty) break;
    docs.push(...snap.docs);
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < 500) break;
  }
  return docs;
}

function toProduct(doc) {
  const data = doc.data() || {};
  const productUuid = productUuidFor(doc.id, data);
  const legacyLocalId = Number.isFinite(data.legacyLocalId) ? Number(data.legacyLocalId) : (/^\d+$/.test(doc.id) ? Number(doc.id) : null);
  const code = typeof data.code === 'string' ? data.code.trim() : null;
  const barcode = typeof data.barcode === 'string' ? data.barcode.trim() : null;
  return {
    docId: doc.id,
    ref: doc.ref,
    data,
    productUuid,
    legacyLocalId,
    code,
    barcode,
    name: typeof data.name === 'string' ? data.name : '',
    brand: typeof data.brand === 'string' ? data.brand : null,
    providerSku: typeof data.providerSku === 'string' ? data.providerSku : null,
    providerName: typeof data.providerName === 'string' ? data.providerName : null,
    category: typeof data.category === 'string' ? data.category : null,
    imageUrls: Array.isArray(data.imageUrls) ? data.imageUrls.filter((x) => typeof x === 'string') : [],
    quantity: Number.isFinite(data.quantity) ? Number(data.quantity) : 0,
    updatedAtEpochMs: Number.isFinite(data.updatedAtEpochMs) ? Number(data.updatedAtEpochMs) : 0,
    createdAtEpochMs: Number.isFinite(data.createdAtEpochMs) ? Number(data.createdAtEpochMs) : 0,
    publicStatus: typeof data.publicStatus === 'string' ? data.publicStatus : null,
    mergeStatus: typeof data.mergeStatus === 'string' ? data.mergeStatus : null,
    mergedIntoProductUuid: typeof data.mergedIntoProductUuid === 'string' ? data.mergedIntoProductUuid : null,
    visible: data.visible === false ? false : true,
  };
}

function buildDuplicateGroups(products) {
  const parent = new Map(products.map((p) => [p.docId, p.docId]));

  function find(x) {
    const p = parent.get(x);
    if (p === x) return x;
    const root = find(p);
    parent.set(x, root);
    return root;
  }

  function union(a, b) {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent.set(rb, ra);
  }

  const keyBuckets = new Map();

  function addKey(key, docId) {
    if (!key) return;
    const list = keyBuckets.get(key) || [];
    list.push(docId);
    keyBuckets.set(key, list);
  }

  for (const p of products) {
    addKey(`uuid:${p.productUuid}`, p.docId);
    if (p.legacyLocalId != null) addKey(`legacy:${p.legacyLocalId}`, p.docId);
    const nCode = normalizeCode(p.code);
    const nBarcode = normalizeBarcode(p.barcode);
    if (nCode) addKey(`code:${nCode}`, p.docId);
    if (nBarcode) addKey(`barcode:${nBarcode}`, p.docId);
    const providerKey = [normalizeText(p.providerSku), normalizeText(p.providerName)].filter(Boolean).join('|');
    if (providerKey.includes('|')) addKey(`provider:${providerKey}`, p.docId);
    const nbk = [normalizeText(p.name), normalizeText(p.brand), normalizeText(p.category)].filter(Boolean).join('|');
    if (nbk.split('|').length >= 2) addKey(`nameBrandCat:${nbk}`, p.docId);
  }

  for (const docs of keyBuckets.values()) {
    for (let i = 1; i < docs.length; i += 1) {
      union(docs[0], docs[i]);
    }
  }

  const groups = new Map();
  for (const p of products) {
    const root = find(p.docId);
    const list = groups.get(root) || [];
    list.push(p);
    groups.set(root, list);
  }

  return [...groups.values()].filter((group) => group.length > 1);
}

function detectAmbiguity(group) {
  const distinctBarcodes = new Set(group.map((p) => normalizeBarcode(p.barcode)).filter(Boolean));
  if (distinctBarcodes.size > 1) return 'different_non_empty_barcodes';
  const distinctCodes = new Set(group.map((p) => normalizeCode(p.code)).filter(Boolean));
  if (distinctCodes.size > 1) return 'different_non_empty_codes';
  return null;
}

async function commitBatched(writes) {
  if (writes.length === 0) return;
  for (let i = 0; i < writes.length; i += MAX_BATCH_OPS) {
    const chunk = writes.slice(i, i + MAX_BATCH_OPS);
    const batch = db.batch();
    for (const write of chunk) {
      batch.set(write.ref, write.data, { merge: true });
    }
    await batch.commit();
  }
}

async function main() {
  const tenantRef = db.collection('tenants').doc(tenantId);
  const productsRef = tenantRef.collection('products');
  const historyRef = tenantRef.collection('product_state_history');
  const reportRef = tenantRef.collection('migration_reports').doc(runId);

  const startedAtEpochMs = Date.now();
  const productDocs = await fetchAllProducts(productsRef);
  const products = productDocs.map(toProduct);
  const groups = buildDuplicateGroups(products);

  let autoMergedGroups = 0;
  let manualReviewGroups = 0;
  let archivedDocuments = 0;
  let canonicalDocumentsUpdated = 0;
  const conflicts = [];
  const mapping = [];
  const writes = [];

  for (const group of groups) {
    const ambiguity = detectAmbiguity(group);
    if (ambiguity) {
      manualReviewGroups += 1;
      conflicts.push({
        reason: ambiguity,
        groupDocIdsCount: group.length,
        groupDocIdsSample: group.map((g) => g.docId).slice(0, maxGroupIds),
        groupProductUuidsSample: group.map((g) => g.productUuid).slice(0, maxGroupIds),
      });
      continue;
    }

    autoMergedGroups += 1;
    const canonical = [...group].sort((a, b) => productScore(b) - productScore(a))[0];
    const canonicalTargetDoc = group.find((p) => p.docId === canonical.productUuid);
    const duplicates = group.filter((p) => p.docId !== canonical.docId && p.docId !== canonical.productUuid);
    const mergedGroupMembers = group.filter((p) => p.docId !== canonical.docId);
    if (mergedGroupMembers.length === 0) continue;

    const now = Date.now();
    const legacyLocalIds = [...new Set([canonical.legacyLocalId, ...mergedGroupMembers.map((d) => d.legacyLocalId)].filter((x) => x != null))];
    const legacyCodes = [...new Set([canonical.code, ...mergedGroupMembers.map((d) => d.code)].map(normalizeCode).filter(Boolean))];
    const legacyBarcodes = [...new Set([canonical.barcode, ...mergedGroupMembers.map((d) => d.barcode)].map(normalizeBarcode).filter(Boolean))];
    const legacyRemoteDocIds = [...new Set([canonical.docId, ...mergedGroupMembers.map((d) => d.docId)])];
    const mergedFromProductUuids = [...new Set(mergedGroupMembers.map((d) => d.productUuid))];
    const mergedImages = [...new Set([...canonical.imageUrls, ...mergedGroupMembers.flatMap((d) => d.imageUrls)])];

    mapping.push({
      canonicalDocId: canonical.docId,
      canonicalProductUuid: canonical.productUuid,
      mergedCount: mergedGroupMembers.length,
      mergedDocIdsSample: mergedGroupMembers.map((d) => d.docId).slice(0, maxGroupIds),
      mergedProductUuidsSample: mergedGroupMembers.map((d) => d.productUuid).slice(0, maxGroupIds),
    });

    const canonicalRef = productsRef.doc(canonical.productUuid);
    writes.push({
      ref: canonicalRef,
      data: {
        productUuid: canonical.productUuid,
        legacyLocalIds,
        legacyCodes,
        legacyBarcodes,
        legacyRemoteDocIds,
        mergedFromProductUuids,
        normalizationRunIds: admin.firestore.FieldValue.arrayUnion(runId),
        imageUrls: mergedImages,
        updatedAtEpochMs: now,
        syncStatus: 'SYNCED',
      },
    });
    canonicalDocumentsUpdated += 1;

    if (canonicalTargetDoc && canonicalTargetDoc.docId !== canonical.docId) {
      writes.push({
        ref: historyRef.doc(`${runId}_${canonical.productUuid}_${canonicalTargetDoc.docId}_${now}`),
        data: {
          runId,
          productUuid: canonical.productUuid,
          sourceDocId: canonicalTargetDoc.docId,
          legacyLocalId: canonicalTargetDoc.legacyLocalId ?? null,
          snapshot: canonicalTargetDoc.data,
          reason: 'canonical_target_overwritten_with_most_recent',
          supersededByProductUuid: canonical.productUuid,
          recordedAtEpochMs: now,
        },
      });
    }

    if (canonical.docId !== canonical.productUuid) {
      writes.push({
        ref: historyRef.doc(`${runId}_${canonical.productUuid}_${canonical.docId}_${now}`),
        data: {
          runId,
          productUuid: canonical.productUuid,
          sourceDocId: canonical.docId,
          legacyLocalId: canonical.legacyLocalId ?? null,
          snapshot: canonical.data,
          reason: 'canonical_migrated_to_product_uuid_doc',
          supersededByProductUuid: canonical.productUuid,
          recordedAtEpochMs: now,
        },
      });
      writes.push({
        ref: canonical.ref,
        data: {
          mergeStatus: 'merged',
          mergedIntoProductUuid: canonical.productUuid,
          archivedAtEpochMs: now,
          visible: false,
          syncStatus: 'MERGED',
          normalizationRunId: runId,
          updatedAtEpochMs: now,
        },
      });
      archivedDocuments += 1;
    }

    for (const dup of duplicates) {
      writes.push({
        ref: historyRef.doc(`${runId}_${dup.productUuid}_${dup.docId}_${now}`),
        data: {
          runId,
          productUuid: dup.productUuid,
          sourceDocId: dup.docId,
          legacyLocalId: dup.legacyLocalId ?? null,
          snapshot: dup.data,
          reason: 'duplicate_merged_keep_most_recent',
          supersededByProductUuid: canonical.productUuid,
          recordedAtEpochMs: now,
        },
      });

      if (!(dup.mergeStatus === 'merged' && dup.mergedIntoProductUuid === canonical.productUuid && dup.visible === false)) {
        writes.push({
          ref: dup.ref,
          data: {
            mergeStatus: 'merged',
            mergedIntoProductUuid: canonical.productUuid,
            archivedAtEpochMs: now,
            visible: false,
            syncStatus: 'MERGED',
            normalizationRunId: runId,
            updatedAtEpochMs: now,
          },
        });
        archivedDocuments += 1;
      }
    }
  }

  const finishedAtEpochMs = Date.now();
  const report = {
    runId,
    dryRun: isDryRun,
    tenantId,
    startedAtEpochMs,
    finishedAtEpochMs,
    productsScanned: products.length,
    duplicateGroups: groups.length,
    autoMergedGroups,
    manualReviewGroups,
    archivedDocuments,
    canonicalDocumentsCreatedOrUpdated: canonicalDocumentsUpdated,
    conflictsCount: conflicts.length,
    mappingCount: mapping.length,
    conflicts: conflicts.slice(0, maxReportItems),
    mapping: mapping.slice(0, maxReportItems),
    reportTruncated: conflicts.length > maxReportItems || mapping.length > maxReportItems,
  };

  if (isDryRun) {
    console.log(JSON.stringify(report, null, 2));
    console.log(`DRY RUN. No se aplicaron writes. Writes planificados: ${writes.length}`);
    return;
  }

  await commitBatched(writes);
  await reportRef.set(report, { merge: true });

  console.log(JSON.stringify(report, null, 2));
  console.log(`APPLY OK. Writes aplicados: ${writes.length} + reporte.`);
}

main().catch((error) => {
  console.error('Error normalizando productos:', error);
  process.exitCode = 1;
});
