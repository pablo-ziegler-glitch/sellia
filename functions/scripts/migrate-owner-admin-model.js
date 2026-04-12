#!/usr/bin/env node

const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const admin = require('firebase-admin');

const OWNER_PERMISSIONS = [
  'MANAGE_USERS',
  'MANAGE_CLOUD_SERVICES',
  'VIEW_USAGE_DASHBOARD',
  'CASH_OPEN',
  'CASH_AUDIT',
  'CASH_MOVEMENT',
  'CASH_CLOSE',
  'VIEW_CASH_REPORT'
];

function parseArgs(argv) {
  const args = {
    inputPath: '',
    projectId: '',
    serviceAccountPath: '',
    dryRun: false
  };

  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--input') args.inputPath = argv[++i] || '';
    else if (token === '--project') args.projectId = argv[++i] || '';
    else if (token === '--service-account') args.serviceAccountPath = argv[++i] || '';
    else if (token === '--dry-run') args.dryRun = true;
  }

  return args;
}

function readFirebaseProjectFromRc() {
  const rcPath = path.resolve(__dirname, '../../.firebaserc');
  if (!fs.existsSync(rcPath)) return '';
  try {
    const rcContent = JSON.parse(fs.readFileSync(rcPath, 'utf8'));
    return String(rcContent?.projects?.default || '').trim();
  } catch {
    return '';
  }
}

function readProjectIdFromFirebaseConfig() {
  if (!process.env.FIREBASE_CONFIG) return '';
  try {
    const parsed = JSON.parse(process.env.FIREBASE_CONFIG);
    return String(parsed?.projectId || '').trim();
  } catch {
    return '';
  }
}

function resolveProjectId(cliProjectId) {
  const byPriority = [
    cliProjectId,
    process.env.GCLOUD_PROJECT,
    process.env.GOOGLE_CLOUD_PROJECT,
    readProjectIdFromFirebaseConfig(),
    readFirebaseProjectFromRc()
  ];
  return byPriority.map((value) => String(value || '').trim()).find(Boolean) || '';
}

function getDefaultAdcPathCandidates() {
  const candidates = [];
  const home = os.homedir();
  if (home) candidates.push(path.join(home, '.config', 'gcloud', 'application_default_credentials.json'));
  const appData = process.env.APPDATA;
  if (appData) candidates.push(path.join(appData, 'gcloud', 'application_default_credentials.json'));
  return candidates;
}

function hasUsableAdcCredentials() {
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    return fs.existsSync(process.env.GOOGLE_APPLICATION_CREDENTIALS);
  }
  return getDefaultAdcPathCandidates().some((candidatePath) => fs.existsSync(candidatePath));
}

function readServiceAccountFromPath(serviceAccountPath) {
  const normalizedPath = String(serviceAccountPath || '').trim();
  if (!normalizedPath) return null;
  const absolutePath = path.resolve(process.cwd(), normalizedPath);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`No existe el archivo de service account: ${absolutePath}`);
  }
  try {
    const content = JSON.parse(fs.readFileSync(absolutePath, 'utf8'));
    return { credential: admin.credential.cert(content), source: absolutePath };
  } catch {
    throw new Error(`No se pudo parsear el JSON de service account: ${absolutePath}`);
  }
}

function resolveCredentialConfig(serviceAccountPath) {
  const fromFile = readServiceAccountFromPath(serviceAccountPath);
  if (fromFile) return fromFile;
  if (hasUsableAdcCredentials()) {
    return { credential: admin.credential.applicationDefault(), source: 'adc' };
  }
  throw new Error('No se encontraron credenciales de Google válidas. Usá --service-account <PATH_JSON>, ejecutá "gcloud auth application-default login" o configurá GOOGLE_APPLICATION_CREDENTIALS.');
}

function hashEmail(email) {
  return crypto.createHash('sha256').update(email, 'utf8').digest('hex');
}

function maskEmail(email) {
  const [local = '', domain = ''] = String(email || '').split('@');
  const localPrefix = local.slice(0, 2);
  const maskedLocal = local.length > 2 ? `${localPrefix}${'*'.repeat(Math.max(local.length - 2, 1))}` : `${localPrefix}*`;
  const [domainName = '', tld = ''] = domain.split('.');
  const maskedDomain = domainName ? `${domainName.slice(0, 1)}***` : '***';
  return `${maskedLocal}@${maskedDomain}${tld ? `.${tld}` : ''}`;
}

function normalizeBoolean(value) {
  const normalized = String(value ?? '').trim().toLowerCase();
  return ['1', 'true', 'yes', 'y', 'si', 'sí'].includes(normalized);
}

function parseInputFile(inputPath) {
  const absolutePath = path.resolve(process.cwd(), inputPath);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`No existe el archivo de entrada: ${absolutePath}`);
  }

  const raw = fs.readFileSync(absolutePath, 'utf8').trim();
  if (!raw) return [];

  if (absolutePath.toLowerCase().endsWith('.json')) {
    const data = JSON.parse(raw);
    if (!Array.isArray(data)) {
      throw new Error('El JSON de entrada debe ser un array.');
    }
    return data.map((row) => ({
      email: String(row.email || '').trim().toLowerCase(),
      tenantId: String(row.tenantId || row.tenant || '').trim(),
      superAdmin: Boolean(row.superAdmin)
    }));
  }

  const lines = raw.split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) {
    throw new Error('El CSV debe incluir cabecera y al menos una fila.');
  }

  const headers = lines[0].split(',').map((h) => h.trim().toLowerCase());
  const emailIndex = headers.indexOf('email');
  const tenantIndex = headers.findIndex((h) => h === 'tenantid' || h === 'tenant');
  const superAdminIndex = headers.indexOf('superadmin');

  if (emailIndex < 0 || tenantIndex < 0) {
    throw new Error('El CSV debe tener columnas: email,tenantId[,superAdmin]');
  }

  const rows = [];
  for (let i = 1; i < lines.length; i += 1) {
    const values = lines[i].split(',').map((v) => v.trim());
    const email = String(values[emailIndex] || '').toLowerCase();
    const tenantId = String(values[tenantIndex] || '');
    const superAdmin = superAdminIndex >= 0 ? normalizeBoolean(values[superAdminIndex]) : false;

    if (!email || !tenantId) continue;
    rows.push({ email, tenantId, superAdmin });
  }
  return rows;
}

function validateRows(rows) {
  if (!rows.length) {
    throw new Error('No hay filas válidas para procesar.');
  }

  const invalidRows = rows.filter((row) => !row.email.includes('@') || !row.tenantId);
  if (invalidRows.length) {
    throw new Error(`Hay filas inválidas en el input: ${invalidRows.length}`);
  }

  const keySet = new Set();
  for (const row of rows) {
    const key = `${row.email}__${row.tenantId}`;
    if (keySet.has(key)) {
      throw new Error(`Fila duplicada detectada para ${row.email} en tenant ${row.tenantId}`);
    }
    keySet.add(key);
  }
}

async function migrateOne({ row, auth, db, dryRun }) {
  const user = await auth.getUserByEmail(row.email);
  const currentClaims = user.customClaims || {};
  const nextClaims = {
    ...currentClaims,
    admin: true,
    isAdmin: true,
    role: 'owner',
    tenantId: row.tenantId,
    superAdmin: row.superAdmin || currentClaims.superAdmin === true
  };

  const now = admin.firestore.FieldValue.serverTimestamp();
  const emailHash = hashEmail(row.email);

  const userPayload = {
    email: row.email,
    tenantId: row.tenantId,
    role: 'owner',
    status: 'active',
    accountType: 'store_owner',
    isActive: true,
    isAdmin: true,
    isSuperAdmin: nextClaims.superAdmin === true,
    permissions: OWNER_PERMISSIONS,
    updatedAt: now,
    updatedBy: 'migrate-owner-admin-model-script'
  };

  const tenantUserPayload = {
    tenantId: row.tenantId,
    uid: user.uid,
    email: row.email,
    role: 'owner',
    isActive: true,
    permissions: OWNER_PERMISSIONS,
    updatedAt: now,
    updatedBy: 'migrate-owner-admin-model-script'
  };

  const hashPayload = {
    emailHash,
    hashAlgorithm: 'sha256',
    uid: user.uid,
    tenantId: row.tenantId,
    role: 'owner',
    maskedEmail: maskEmail(row.email),
    updatedAt: now,
    updatedBy: 'migrate-owner-admin-model-script'
  };

  const tenantPayload = {
    ownerUid: user.uid,
    ownerEmail: row.email,
    ownerUids: [user.uid],
    updatedAt: now,
    updatedBy: 'migrate-owner-admin-model-script'
  };

  if (!dryRun) {
    await auth.setCustomUserClaims(user.uid, nextClaims);
    await Promise.all([
      db.collection('users').doc(user.uid).set(userPayload, { merge: true }),
      db.collection('tenant_users').doc(`${row.tenantId}_${user.uid}`).set(tenantUserPayload, { merge: true }),
      db.collection('admin_email_hashes').doc(emailHash).set(hashPayload, { merge: true }),
      db.collection('tenants').doc(row.tenantId).set(tenantPayload, { merge: true })
    ]);
  }

  return {
    email: row.email,
    maskedEmail: maskEmail(row.email),
    uid: user.uid,
    tenantId: row.tenantId,
    superAdmin: nextClaims.superAdmin === true,
    status: dryRun ? 'dry-run' : 'ok'
  };
}

async function main() {
  const args = parseArgs(process.argv);
  if (!args.inputPath) {
    throw new Error('Debés enviar --input <ruta.csv|ruta.json>');
  }

  args.projectId = resolveProjectId(args.projectId);
  if (!args.projectId) {
    throw new Error('No se pudo resolver el project ID. Usá --project <PROJECT_ID> o definí GCLOUD_PROJECT.');
  }

  const credentialConfig = resolveCredentialConfig(args.serviceAccountPath);
  if (!admin.apps.length) {
    admin.initializeApp({
      projectId: args.projectId,
      credential: credentialConfig.credential
    });
  }

  const rows = parseInputFile(args.inputPath);
  validateRows(rows);

  const auth = admin.auth();
  const db = admin.firestore();

  const report = {
    projectId: args.projectId,
    dryRun: args.dryRun,
    total: rows.length,
    ok: [],
    failed: []
  };

  for (const row of rows) {
    try {
      const result = await migrateOne({ row, auth, db, dryRun: args.dryRun });
      report.ok.push(result);
      console.log(`[OK] ${result.maskedEmail} tenant=${result.tenantId} uid=${result.uid} mode=${result.status}`);
    } catch (error) {
      const message = String(error?.message || error || 'Error desconocido');
      report.failed.push({ email: row.email, tenantId: row.tenantId, error: message });
      console.error(`[FAIL] ${maskEmail(row.email)} tenant=${row.tenantId} error=${message}`);
    }
  }

  console.log('\n=== Resumen migración owner/admin ===');
  console.log(JSON.stringify({
    projectId: report.projectId,
    dryRun: report.dryRun,
    total: report.total,
    ok: report.ok.length,
    failed: report.failed.length,
    note: 'Cada usuario migrado debe refrescar token (logout/login o getIdToken(true)).'
  }, null, 2));

  if (report.failed.length > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  const message = String(error?.message || error || 'Error desconocido');
  console.error('[migrate-owner-admin-model] error:', message);
  process.exit(1);
});
