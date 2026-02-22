#!/usr/bin/env node

/**
 * Backfill de `public_tenant_directory` desde `tenant_directory`.
 *
 * Uso:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/key.json \
 *   node scripts/migrate-public-tenant-directory.js --dry-run
 *
 *   node scripts/migrate-public-tenant-directory.js --apply
 */

const admin = require('firebase-admin');

const isApply = process.argv.includes('--apply');
const isDryRun = !isApply;

admin.initializeApp();
const db = admin.firestore();

function toString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

async function main() {
  const sourceSnap = await db.collection('tenant_directory').get();
  if (sourceSnap.empty) {
    console.log('No hay documentos en tenant_directory.');
    return;
  }

  let prepared = 0;
  let skipped = 0;
  let written = 0;
  let batch = db.batch();
  let batchOps = 0;

  for (const doc of sourceSnap.docs) {
    const data = doc.data() || {};
    const name = toString(data.name);
    if (!name) {
      skipped += 1;
      continue;
    }

    const payload = {
      id: doc.id,
      name,
      publicStoreUrl: toString(data.publicStoreUrl),
      publicDomain: toString(data.publicDomain),
      storeLogoUrl: toString(data.storeLogoUrl),
      createdAt: data.createdAt || admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    prepared += 1;
    if (!isApply) {
      console.log(`[DRY_RUN] upsert public_tenant_directory/${doc.id} -> ${JSON.stringify(payload)}`);
      continue;
    }

    batch.set(db.collection('public_tenant_directory').doc(doc.id), payload, { merge: true });
    batchOps += 1;

    if (batchOps >= 400) {
      await batch.commit();
      written += batchOps;
      batch = db.batch();
      batchOps = 0;
    }
  }

  if (isApply && batchOps > 0) {
    await batch.commit();
    written += batchOps;
  }

  console.log(`Procesados: ${sourceSnap.size}`);
  console.log(`Preparados: ${prepared}`);
  console.log(`Saltados (sin name): ${skipped}`);
  console.log(isApply ? `Escritos: ${written}` : 'Modo dry-run, no se aplicaron cambios.');
}

main().catch((error) => {
  console.error('Error migrando public_tenant_directory:', error);
  process.exitCode = 1;
});
