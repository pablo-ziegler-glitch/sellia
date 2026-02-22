#!/usr/bin/env node

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const ADMIN_PERMISSIONS = [
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
    email: '',
    tenantId: '',
    projectId: '',
    role: 'admin',
    superAdmin: false,
    dryRun: false
  };

  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--email') args.email = argv[++i] || '';
    else if (token === '--tenant') args.tenantId = argv[++i] || '';
    else if (token === '--project') args.projectId = argv[++i] || '';
    else if (token === '--role') args.role = (argv[++i] || 'admin').toLowerCase();
    else if (token === '--super-admin') args.superAdmin = true;
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

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase();
}

function hashEmail(email) {
  return crypto.createHash('sha256').update(email, 'utf8').digest('hex');
}

function maskEmail(email) {
  const [local = '', domain = ''] = email.split('@');
  const localPrefix = local.slice(0, 2);
  const maskedLocal = local.length > 2 ? `${localPrefix}${'*'.repeat(Math.max(local.length - 2, 1))}` : `${localPrefix}*`;
  const [domainName = '', tld = ''] = domain.split('.');
  const maskedDomain = domainName ? `${domainName.slice(0, 1)}***` : '***';
  return `${maskedLocal}@${maskedDomain}${tld ? `.${tld}` : ''}`;
}

function validateInput({ email, tenantId, role }) {
  if (!email) {
    throw new Error('Debés enviar --email <EMAIL>.');
  }
  if (!tenantId) {
    throw new Error('Debés enviar --tenant <TENANT_ID>.');
  }
  if (!['admin', 'owner'].includes(role)) {
    throw new Error('El parámetro --role solo admite admin u owner.');
  }
}

async function main() {
  const args = parseArgs(process.argv);
  args.email = normalizeEmail(args.email);
  args.projectId = resolveProjectId(args.projectId);
  validateInput(args);

  if (!args.projectId) {
    throw new Error('No se pudo resolver el project ID. Usá --project <PROJECT_ID> o definí GCLOUD_PROJECT.');
  }

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: args.projectId });
  }

  const auth = admin.auth();
  const db = admin.firestore();

  const user = await auth.getUserByEmail(args.email);
  const emailHash = hashEmail(args.email);

  const currentClaims = user.customClaims || {};
  const nextClaims = {
    ...currentClaims,
    admin: true,
    role: args.role,
    superAdmin: args.superAdmin || currentClaims.superAdmin === true
  };

  const now = admin.firestore.FieldValue.serverTimestamp();
  const userRef = db.collection('users').doc(user.uid);
  const hashRef = db.collection('admin_email_hashes').doc(emailHash);
  const tenantUserRef = db.collection('tenant_users').doc(`${args.tenantId}_${user.uid}`);

  const userPayload = {
    email: args.email,
    tenantId: args.tenantId,
    role: args.role,
    status: 'active',
    accountType: 'store_owner',
    isActive: true,
    isAdmin: true,
    permissions: ADMIN_PERMISSIONS,
    updatedAt: now,
    updatedBy: 'manage-admin-access-script'
  };

  const tenantUserPayload = {
    tenantId: args.tenantId,
    uid: user.uid,
    email: args.email,
    role: args.role,
    isActive: true,
    permissions: ADMIN_PERMISSIONS,
    updatedAt: now,
    updatedBy: 'manage-admin-access-script'
  };

  const hashPayload = {
    emailHash,
    hashAlgorithm: 'sha256',
    uid: user.uid,
    tenantId: args.tenantId,
    role: args.role,
    maskedEmail: maskEmail(args.email),
    updatedAt: now,
    updatedBy: 'manage-admin-access-script'
  };

  if (!args.dryRun) {
    await auth.setCustomUserClaims(user.uid, nextClaims);
    await Promise.all([
      userRef.set(userPayload, { merge: true }),
      tenantUserRef.set(tenantUserPayload, { merge: true }),
      hashRef.set(hashPayload, { merge: true })
    ]);
  }

  console.log(JSON.stringify({
    status: args.dryRun ? 'dry-run' : 'ok',
    uid: user.uid,
    tenantId: args.tenantId,
    role: args.role,
    superAdmin: nextClaims.superAdmin === true,
    emailHash,
    maskedEmail: maskEmail(args.email),
    note: 'El usuario debe refrescar token (logout/login o getIdToken(true)).'
  }, null, 2));
}

main().catch((error) => {
  const message = String(error?.message || error || 'Error desconocido');
  console.error('[manage-admin-access] error:', message);

  if (message.includes('metadata.google.internal')) {
    console.error('[manage-admin-access] hint: Ejecutá con credenciales ADC locales (gcloud auth application-default login) o definí GOOGLE_APPLICATION_CREDENTIALS con una service account JSON.');
  }

  process.exit(1);
});
