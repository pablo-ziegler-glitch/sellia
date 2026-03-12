#!/usr/bin/env node
/**
 * fix-admin-account.js
 *
 * Restaura y migra la cuenta de admin en Firebase Auth + Firestore.
 * Uso:
 *   node scripts/fix-admin-account.js \
 *     --email valkirja.comercial@gmail.com \
 *     --tenant <TENANT_ID> \
 *     [--role owner|admin] \
 *     [--super-admin] \
 *     [--service-account /ruta/service-account.json] \
 *     [--dry-run]
 */

const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const admin = require('firebase-admin');

// ─── Argparse ────────────────────────────────────────────────────────────────

function parseArgs(argv) {
  const args = {
    email: '',
    tenantId: '',
    projectId: '',
    serviceAccountPath: '',
    role: 'owner',
    superAdmin: false,
    dryRun: false
  };
  for (let i = 2; i < argv.length; i++) {
    const t = argv[i];
    if (t === '--email')           args.email              = argv[++i] || '';
    else if (t === '--tenant')     args.tenantId           = argv[++i] || '';
    else if (t === '--project')    args.projectId          = argv[++i] || '';
    else if (t === '--service-account') args.serviceAccountPath = argv[++i] || '';
    else if (t === '--role')       args.role               = (argv[++i] || 'owner').toLowerCase();
    else if (t === '--super-admin') args.superAdmin        = true;
    else if (t === '--dry-run')    args.dryRun             = true;
  }
  return args;
}

// ─── Credentials ─────────────────────────────────────────────────────────────

function readProjectId() {
  const rcPath = path.resolve(__dirname, '../../.firebaserc');
  try {
    const rc = JSON.parse(fs.readFileSync(rcPath, 'utf8'));
    return String(rc?.projects?.default || '').trim();
  } catch { return ''; }
}

function resolveProjectId(cliId) {
  return [cliId, process.env.GCLOUD_PROJECT, process.env.GOOGLE_CLOUD_PROJECT, readProjectId()]
    .map(v => String(v || '').trim()).find(Boolean) || '';
}

function resolveCredential(serviceAccountPath) {
  if (serviceAccountPath) {
    const abs = path.resolve(process.cwd(), serviceAccountPath);
    if (!fs.existsSync(abs)) throw new Error(`No existe el archivo: ${abs}`);
    const json = JSON.parse(fs.readFileSync(abs, 'utf8'));
    return { credential: admin.credential.cert(json), source: abs };
  }

  // ADC fallback
  const adcCandidates = [
    process.env.GOOGLE_APPLICATION_CREDENTIALS,
    path.join(os.homedir(), '.config', 'gcloud', 'application_default_credentials.json'),
    process.env.APPDATA && path.join(process.env.APPDATA, 'gcloud', 'application_default_credentials.json')
  ].filter(Boolean);

  if (adcCandidates.some(p => fs.existsSync(p))) {
    return { credential: admin.credential.applicationDefault(), source: 'adc' };
  }

  throw new Error(
    'No se encontraron credenciales. Opciones:\n' +
    '  1. --service-account /ruta/service-account.json\n' +
    '  2. gcloud auth application-default login\n' +
    '  3. export GOOGLE_APPLICATION_CREDENTIALS=/ruta/service-account.json'
  );
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

const hashEmail = email => crypto.createHash('sha256').update(email, 'utf8').digest('hex');

function maskEmail(email) {
  const [local = '', domain = ''] = email.split('@');
  const prefix = local.slice(0, 2);
  const masked = local.length > 2 ? `${prefix}${'*'.repeat(local.length - 2)}` : `${prefix}*`;
  const [dname = '', tld = ''] = domain.split('.');
  return `${masked}@${dname.slice(0, 1)}***${tld ? '.' + tld : ''}`;
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const args = parseArgs(process.argv);
  args.email = String(args.email || '').trim().toLowerCase();
  args.projectId = resolveProjectId(args.projectId);

  if (!args.email)    throw new Error('Falta --email');
  if (!args.tenantId) throw new Error('Falta --tenant <TENANT_ID>');
  if (!['admin', 'owner'].includes(args.role)) throw new Error('--role debe ser admin u owner');
  if (!args.projectId) throw new Error('No se pudo resolver el project ID. Usá --project <ID>');

  const credConf = resolveCredential(args.serviceAccountPath);

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: args.projectId, credential: credConf.credential });
  }

  const auth = admin.auth();
  const db   = admin.firestore();

  // 1. Buscar usuario en Firebase Auth
  console.log(`\n[1/5] Buscando usuario: ${args.email}`);
  const user = await auth.getUserByEmail(args.email);
  console.log(`      UID: ${user.uid}`);
  console.log(`      Email verificado: ${user.emailVerified}`);
  console.log(`      Claims actuales: ${JSON.stringify(user.customClaims || {})}`);

  // 2. Verificar documento de tenant
  console.log(`\n[2/5] Verificando tenant: ${args.tenantId}`);
  const tenantRef = db.collection('tenants').doc(args.tenantId);
  const tenantSnap = await tenantRef.get();
  if (!tenantSnap.exists) {
    console.warn(`      ⚠️  El tenant '${args.tenantId}' NO existe en Firestore.`);
    console.warn(`         Se continuará de todas formas, pero verificá el tenant ID.`);
  } else {
    const td = tenantSnap.data();
    console.log(`      Tenant encontrado: ${td.name || td.storeName || '(sin nombre)'}`);
    if (td.ownerUid && td.ownerUid !== user.uid) {
      console.warn(`      ⚠️  El tenant tiene ownerUid=${td.ownerUid}, distinto al UID del usuario (${user.uid}).`);
    }
  }

  // 3. Construir payloads
  const emailHash = hashEmail(args.email);
  const now = admin.firestore.FieldValue.serverTimestamp();

  const nextClaims = {
    ...(user.customClaims || {}),
    admin: true,
    role: args.role,
    tenantId: args.tenantId,
    superAdmin: args.superAdmin || (user.customClaims?.superAdmin === true)
  };

  const userPayload = {
    email: args.email,
    tenantId: args.tenantId,
    role: args.role,
    status: 'active',
    accountType: 'store_owner',
    isActive: true,
    isAdmin: true,
    isSuperAdmin: nextClaims.superAdmin,
    permissions: [
      'MANAGE_USERS', 'MANAGE_CLOUD_SERVICES', 'VIEW_USAGE_DASHBOARD',
      'CASH_OPEN', 'CASH_AUDIT', 'CASH_MOVEMENT', 'CASH_CLOSE', 'VIEW_CASH_REPORT'
    ],
    updatedAt: now,
    updatedBy: 'fix-admin-account-script'
  };

  const tenantUserPayload = {
    tenantId: args.tenantId,
    uid: user.uid,
    email: args.email,
    role: args.role,
    isActive: true,
    permissions: userPayload.permissions,
    updatedAt: now,
    updatedBy: 'fix-admin-account-script'
  };

  const hashPayload = {
    emailHash,
    hashAlgorithm: 'sha256',
    uid: user.uid,
    tenantId: args.tenantId,
    role: args.role,
    maskedEmail: maskEmail(args.email),
    updatedAt: now,
    updatedBy: 'fix-admin-account-script'
  };

  // 4. Preview
  console.log('\n[3/5] Cambios a aplicar:');
  console.log('      Custom claims:');
  console.log('      ', JSON.stringify(nextClaims, null, 6).replace(/\n/g, '\n      '));
  console.log('\n      /users/' + user.uid + ':');
  const previewPayload = { ...userPayload, updatedAt: '<serverTimestamp>' };
  console.log('      ', JSON.stringify(previewPayload, null, 6).replace(/\n/g, '\n      '));
  console.log('\n      /tenant_users/' + args.tenantId + '_' + user.uid + ':');
  const previewTU = { ...tenantUserPayload, updatedAt: '<serverTimestamp>' };
  console.log('      ', JSON.stringify(previewTU, null, 6).replace(/\n/g, '\n      '));

  if (args.dryRun) {
    console.log('\n✅ DRY-RUN completado. No se aplicaron cambios.');
    return;
  }

  // 5. Aplicar
  console.log('\n[4/5] Aplicando cambios...');
  await auth.setCustomUserClaims(user.uid, nextClaims);
  console.log('      ✅ Custom claims actualizados');

  const userRef       = db.collection('users').doc(user.uid);
  const tenantUserRef = db.collection('tenant_users').doc(`${args.tenantId}_${user.uid}`);
  const hashRef       = db.collection('admin_email_hashes').doc(emailHash);

  await Promise.all([
    userRef.set(userPayload, { merge: true }),
    tenantUserRef.set(tenantUserPayload, { merge: true }),
    hashRef.set(hashPayload, { merge: true })
  ]);
  console.log('      ✅ Documentos Firestore actualizados');

  // 6. Resultado final
  console.log('\n[5/5] Resultado:');
  console.log(JSON.stringify({
    status: 'ok',
    uid: user.uid,
    email: maskEmail(args.email),
    tenantId: args.tenantId,
    role: args.role,
    superAdmin: nextClaims.superAdmin,
    credentialSource: credConf.source,
    accion_requerida: 'El usuario debe cerrar sesión y volver a entrar para refrescar el token JWT.'
  }, null, 2));
}

main().catch(err => {
  console.error('\n❌ Error:', err.message || err);
  if (String(err.message).includes('metadata.google.internal')) {
    console.error('   Hint: Ejecutá con credenciales locales (gcloud auth application-default login).');
  }
  process.exit(1);
});
