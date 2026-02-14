#!/usr/bin/env node

/**
 * Limpia tenants huérfanos con nombre "Cliente público" y accountType "public_customer".
 *
 * Uso:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/key.json \
 *   node scripts/cleanup-orphan-public-customers.js --dry-run
 *
 *   node scripts/cleanup-orphan-public-customers.js --apply
 */

const admin = require('firebase-admin');

const isApply = process.argv.includes('--apply');
const isDryRun = !isApply;

admin.initializeApp();
const db = admin.firestore();

async function main() {
  const tenantsSnap = await db
    .collection('tenants')
    .where('accountType', '==', 'public_customer')
    .get();

  if (tenantsSnap.empty) {
    console.log('No se encontraron tenants public_customer.');
    return;
  }

  let orphanCount = 0;
  for (const doc of tenantsSnap.docs) {
    const tenantId = doc.id;
    const name = (doc.get('name') || '').trim().toLowerCase();
    if (name !== 'cliente público') {
      continue;
    }

    const ownerUid = doc.get('ownerUid');
    const ownerEmail = (doc.get('ownerEmail') || '').trim().toLowerCase();

    const linkedUsersByTenant = await db
      .collection('users')
      .where('tenantId', '==', tenantId)
      .limit(1)
      .get();

    const linkedUsersByStore = await db
      .collection('users')
      .where('storeId', '==', tenantId)
      .limit(1)
      .get();

    const linkedTenantUsers = await db
      .collection('tenant_users')
      .where('tenantId', '==', tenantId)
      .limit(1)
      .get();

    const hasLinks = !linkedUsersByTenant.empty || !linkedUsersByStore.empty || !linkedTenantUsers.empty;
    if (hasLinks) {
      continue;
    }

    orphanCount += 1;
    console.log(`[ORPHAN] tenantId=${tenantId} ownerUid=${ownerUid || '-'} ownerEmail=${ownerEmail || '-'}`);

    if (isApply) {
      const batch = db.batch();
      batch.delete(db.collection('tenants').doc(tenantId));
      batch.delete(db.collection('tenant_directory').doc(tenantId));
      await batch.commit();
      console.log(`  ↳ eliminado`);
    }
  }

  if (orphanCount === 0) {
    console.log('No se detectaron tenants "Cliente público" huérfanos.');
    return;
  }

  if (isDryRun) {
    console.log(`Detectados ${orphanCount} huérfanos. Ejecutá con --apply para limpiar.`);
  } else {
    console.log(`Limpieza completada. Eliminados: ${orphanCount}`);
  }
}

main().catch((error) => {
  console.error('Error ejecutando cleanup:', error);
  process.exitCode = 1;
});
