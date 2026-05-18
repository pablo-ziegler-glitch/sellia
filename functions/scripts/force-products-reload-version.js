#!/usr/bin/env node

/**
 * Marca una versión remota para forzar recarga completa del catálogo de productos
 * en cada dispositivo (resetea baseline/cursors una sola vez por versión).
 *
 * Uso:
 *   node scripts/force-products-reload-version.js --tenant TENANT_ID
 *   node scripts/force-products-reload-version.js --tenant TENANT_ID --version 2026051801
 */

const admin = require('firebase-admin');

const args = process.argv.slice(2);
const tenantId = readArgValue('--tenant');
const explicitVersion = readArgValue('--version');

if (!tenantId) {
  console.error('Falta --tenant TENANT_ID');
  process.exit(1);
}

const targetVersion = explicitVersion ? Number(explicitVersion) : Date.now();
if (!Number.isFinite(targetVersion) || targetVersion <= 0) {
  console.error('La versión debe ser numérica y mayor a 0.');
  process.exit(1);
}

admin.initializeApp();
const db = admin.firestore();

async function run() {
  const tenantRef = db.collection('tenants').doc(tenantId);
  await tenantRef.set(
    {
      productsForceReloadVersion: targetVersion,
      productsForceReloadUpdatedAtEpochMs: Date.now(),
    },
    { merge: true },
  );
  console.log(
    `[OK] tenant=${tenantId} productsForceReloadVersion=${targetVersion}`
  );
}

function readArgValue(name) {
  const eqArg = args.find((arg) => arg.startsWith(`${name}=`));
  if (eqArg) {
    const value = eqArg.slice(name.length + 1).trim();
    return value || null;
  }
  const idx = args.indexOf(name);
  if (idx === -1) return null;
  return args[idx + 1] || null;
}

run().catch((error) => {
  console.error('[ERROR] No se pudo actualizar productsForceReloadVersion', error);
  process.exit(1);
});

