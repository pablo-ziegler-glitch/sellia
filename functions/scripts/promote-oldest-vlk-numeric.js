#!/usr/bin/env node

/**
 * Promueve como vigente el doc más antiguo por código (VLK* y numéricos),
 * preservando histórico y sin borrado físico.
 *
 * Reglas:
 * - Grupo por valor de code/barcode.
 * - Solo valores VLK* o numéricos.
 * - Canónico = más antiguo (preferencia por docId numérico menor).
 * - Mantiene nombre/identidad del más antiguo.
 * - Copia quantity (stock) del más actualizado para no perder estado operativo.
 * - Archiva el resto como merged.
 * - Guarda snapshot previo en product_state_history.
 *
 * Uso:
 *   node scripts/promote-oldest-vlk-numeric.js --tenant <TENANT_ID> --dry-run
 *   node scripts/promote-oldest-vlk-numeric.js --tenant <TENANT_ID> --apply
 */

const admin = require('firebase-admin');

const args = process.argv.slice(2);
const tenantId = readArgValue('--tenant');
const isApply = args.includes('--apply');
const isDryRun = !isApply;
const runId = readArgValue('--run-id') || `promote_oldest_${Date.now()}`;

if (!tenantId) {
  console.error('Falta --tenant <TENANT_ID>');
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

function asString(v) {
  return typeof v === 'string' ? v.trim() : '';
}

function isTargetCode(value) {
  return /^\d+$/.test(value) || /^VLK/i.test(value);
}

function toEpoch(v) {
  return Number.isFinite(v) ? Number(v) : 0;
}

function chooseOldest(docs) {
  return [...docs].sort((a, b) => {
    const aNum = /^\d+$/.test(a.docId) ? Number(a.docId) : Number.MAX_SAFE_INTEGER;
    const bNum = /^\d+$/.test(b.docId) ? Number(b.docId) : Number.MAX_SAFE_INTEGER;
    if (aNum !== bNum) return aNum - bNum;
    const aCreated = toEpoch(a.data.createdAtEpochMs);
    const bCreated = toEpoch(b.data.createdAtEpochMs);
    if (aCreated !== bCreated) return aCreated - bCreated;
    const aUpdated = toEpoch(a.data.updatedAtEpochMs);
    const bUpdated = toEpoch(b.data.updatedAtEpochMs);
    if (aUpdated !== bUpdated) return aUpdated - bUpdated;
    return a.docId.localeCompare(b.docId);
  })[0];
}

function chooseMostRecent(docs) {
  return [...docs].sort((a, b) => {
    const aUpdated = toEpoch(a.data.updatedAtEpochMs);
    const bUpdated = toEpoch(b.data.updatedAtEpochMs);
    if (aUpdated !== bUpdated) return bUpdated - aUpdated;
    return b.docId.localeCompare(a.docId);
  })[0];
}

function buildCanonicalPayload(oldest, newest, now, runId) {
  const oldData = { ...(oldest.data || {}) };
  const newData = { ...(newest.data || {}) };

  // Mantiene identidad descriptiva del más antiguo; stock operativo del más reciente.
  const quantityFromNewest = Number.isFinite(newData.quantity) ? Number(newData.quantity) : oldData.quantity;
  const imageUrls = [
    ...new Set([
      ...(Array.isArray(oldData.imageUrls) ? oldData.imageUrls : []),
      ...(Array.isArray(newData.imageUrls) ? newData.imageUrls : []),
    ]),
  ];

  return {
    ...oldData,
    quantity: quantityFromNewest,
    imageUrls,
    visible: true,
    mergeStatus: admin.firestore.FieldValue.delete(),
    mergedIntoProductUuid: admin.firestore.FieldValue.delete(),
    archivedAtEpochMs: admin.firestore.FieldValue.delete(),
    normalizationRunId: runId,
    updatedAtEpochMs: now,
  };
}

async function commitBatched(writes) {
  if (writes.length === 0) return;
  for (let i = 0; i < writes.length; i += MAX_BATCH_OPS) {
    const chunk = writes.slice(i, i + MAX_BATCH_OPS);
    const batch = db.batch();
    for (const w of chunk) {
      batch.set(w.ref, w.data, { merge: true });
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
  const snap = await productsRef.get();
  const docs = snap.docs.map((d) => ({
    docId: d.id,
    ref: d.ref,
    data: d.data() || {},
  }));

  const groups = new Map();
  for (const doc of docs) {
    const candidates = [asString(doc.data.code).toUpperCase(), asString(doc.data.barcode).toUpperCase()].filter(Boolean);
    for (const value of candidates) {
      if (!isTargetCode(value)) continue;
      const list = groups.get(value) || [];
      list.push(doc);
      groups.set(value, list);
    }
  }

  const now = Date.now();
  const writes = [];
  const mapping = [];
  let groupsProcessed = 0;
  let archivedDocuments = 0;
  let canonicalPromoted = 0;

  for (const [codeValue, membersRaw] of groups.entries()) {
    const members = dedupeByDocId(membersRaw);
    if (members.length <= 1) continue;

    const oldest = chooseOldest(members);
    const newest = chooseMostRecent(members);
    const duplicates = members.filter((m) => m.docId !== oldest.docId);

    groupsProcessed += 1;
    mapping.push({
      codeValue,
      canonicalDocId: oldest.docId,
      canonicalName: asString(oldest.data.name),
      sourceDocs: members.map((m) => m.docId),
      mergedCount: duplicates.length,
    });

    for (const member of members) {
      writes.push({
        ref: historyRef.doc(`${runId}_${codeValue}_${member.docId}_${now}`),
        data: {
          runId,
          reason: 'promote_oldest_canonical_by_code',
          codeValue,
          sourceDocId: member.docId,
          snapshot: member.data,
          promotedCanonicalDocId: oldest.docId,
          recordedAtEpochMs: now,
        },
      });
    }

    writes.push({
      ref: oldest.ref,
      data: buildCanonicalPayload(oldest, newest, now, runId),
    });
    canonicalPromoted += 1;

    for (const dup of duplicates) {
      writes.push({
        ref: dup.ref,
        data: {
          mergeStatus: 'merged',
          mergedIntoProductUuid: asString(oldest.data.productUuid) || oldest.docId,
          archivedAtEpochMs: now,
          visible: false,
          normalizationRunId: runId,
          updatedAtEpochMs: now,
        },
      });
      archivedDocuments += 1;
    }
  }

  const report = {
    runId,
    tenantId,
    dryRun: isDryRun,
    strategy: 'promote_oldest_vlk_numeric',
    startedAtEpochMs,
    finishedAtEpochMs: Date.now(),
    productsScanned: docs.length,
    groupsProcessed,
    canonicalPromoted,
    archivedDocuments,
    writesPlanned: writes.length,
    mappingCount: mapping.length,
    mappingSample: mapping.slice(0, 50),
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

function dedupeByDocId(members) {
  const m = new Map();
  for (const item of members) m.set(item.docId, item);
  return [...m.values()];
}

main().catch((e) => {
  console.error('Error ejecutando promote-oldest-vlk-numeric:', e);
  process.exitCode = 1;
});

