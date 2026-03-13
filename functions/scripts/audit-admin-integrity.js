#!/usr/bin/env node
/**
 * audit-admin-integrity.js
 *
 * Script de auditoría completo para verificar integridad de admins.
 * Identifica inconsistencias entre JWT claims, Firestore user docs, y tenant_users.
 *
 * Uso:
 *   node scripts/audit-admin-integrity.js [--service-account /ruta/sa.json]
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const admin = require('firebase-admin');

// ─── HELPERS ──────────────────────────────────────────────────────────────

function readProjectId() {
  const rcPath = path.resolve(__dirname, '../../.firebaserc');
  try {
    const rc = JSON.parse(fs.readFileSync(rcPath, 'utf8'));
    return String(rc?.projects?.default || '').trim();
  } catch {
    return '';
  }
}

function resolveProjectId(cliId) {
  return [
    cliId,
    process.env.GCLOUD_PROJECT,
    process.env.GOOGLE_CLOUD_PROJECT,
    readProjectId()
  ]
    .map(v => String(v || '').trim())
    .find(Boolean) || '';
}

function resolveCredential(serviceAccountPath) {
  if (serviceAccountPath) {
    const abs = path.resolve(process.cwd(), serviceAccountPath);
    if (!fs.existsSync(abs)) throw new Error(`No existe: ${abs}`);
    const json = JSON.parse(fs.readFileSync(abs, 'utf8'));
    return { credential: admin.credential.cert(json), source: abs };
  }

  const adcCandidates = [
    process.env.GOOGLE_APPLICATION_CREDENTIALS,
    path.join(os.homedir(), '.config', 'gcloud', 'application_default_credentials.json'),
    process.env.APPDATA && path.join(process.env.APPDATA, 'gcloud', 'application_default_credentials.json')
  ].filter(Boolean);

  if (adcCandidates.some(p => fs.existsSync(p))) {
    return { credential: admin.credential.applicationDefault(), source: 'adc' };
  }

  throw new Error('No se encontraron credenciales.');
}

function normalizeRole(value) {
  return String(value || '').trim().toLowerCase();
}

// ─── AUDIT CHECKS ─────────────────────────────────────────────────────────

class AdminAudit {
  constructor(auth, db) {
    this.auth = auth;
    this.db = db;
    this.issues = [];
    this.admins = [];
  }

  addIssue(uid, severity, message) {
    this.issues.push({ uid, severity, message });
  }

  // 1. Obtener todos los admins desde Firebase Auth
  async scanAuthClaims() {
    console.log('\n📋 [1/4] Escaneando Custom Claims en Firebase Auth...');
    const maxResults = 1000;
    let pageToken;
    let count = 0;

    do {
      const listResult = await this.auth.listUsers(maxResults, pageToken);
      for (const user of listResult.users) {
        count++;
        const claims = user.customClaims || {};

        // Buscar admins: admin == true O isAdmin == true
        if (claims.admin === true || claims.isAdmin === true) {
          this.admins.push({
            uid: user.uid,
            email: user.email,
            claims: {
              admin: claims.admin,
              isAdmin: claims.isAdmin,
              role: claims.role,
              tenantId: claims.tenantId,
              superAdmin: claims.superAdmin
            },
            emailVerified: user.emailVerified
          });
        }
      }
      pageToken = listResult.pageToken;
    } while (pageToken);

    console.log(`   ✅ Procesados ${count} usuarios, encontrados ${this.admins.length} admins`);
  }

  // 2. Verificar documentos Firestore /users/{uid}
  async verifyUserDocs() {
    console.log('\n🔍 [2/4] Verificando documentos /users/{uid}...');

    for (const admin of this.admins) {
      try {
        const userDoc = await this.db.collection('users').doc(admin.uid).get();

        if (!userDoc.exists) {
          this.addIssue(
            admin.uid,
            'ERROR',
            `❌ Documento /users/${admin.uid} NO existe en Firestore (solo en Auth)`
          );
          continue;
        }

        const data = userDoc.data() || {};
        admin.userDoc = data;

        // Validaciones
        const issues = [];

        // Validar tenantId
        if (!data.tenantId) {
          issues.push('❌ Campo tenantId falta o vacío');
        } else if (data.tenantId !== admin.claims.tenantId && admin.claims.tenantId) {
          issues.push(`⚠️  tenantId inconsistente: Auth=${admin.claims.tenantId}, Doc=${data.tenantId}`);
        }

        // Validar role
        if (!data.role) {
          issues.push('❌ Campo role falta o vacío');
        } else if (normalizeRole(data.role) !== normalizeRole(admin.claims.role) && admin.claims.role) {
          issues.push(`⚠️  role inconsistente: Auth=${admin.claims.role}, Doc=${data.role}`);
        }

        // Validar status
        if (data.status && normalizeRole(data.status) !== 'active') {
          issues.push(`⚠️  status NO es 'active': ${data.status}`);
        }

        // Validar isAdmin
        if (data.isAdmin !== true) {
          issues.push(`⚠️  isAdmin no es true: ${data.isAdmin}`);
        }

        if (issues.length > 0) {
          issues.forEach(issue => this.addIssue(admin.uid, 'WARN', issue));
        }
      } catch (err) {
        this.addIssue(admin.uid, 'ERROR', `❌ Error al leer /users/${admin.uid}: ${err.message}`);
      }
    }

    console.log(`   ✅ Verificados ${this.admins.length} documentos /users/{uid}`);
  }

  // 3. Verificar documentos /tenant_users/{tenantId}_{uid}
  async verifyTenantUserDocs() {
    console.log('\n🔍 [3/4] Verificando documentos /tenant_users...');

    for (const admin of this.admins) {
      try {
        const tenantId = admin.claims.tenantId || admin.userDoc?.tenantId;
        if (!tenantId) {
          this.addIssue(admin.uid, 'ERROR', '❌ No hay tenantId para verificar tenant_users');
          continue;
        }

        const docId = `${tenantId}_${admin.uid}`;
        const tenantUserDoc = await this.db.collection('tenant_users').doc(docId).get();

        if (!tenantUserDoc.exists) {
          this.addIssue(
            admin.uid,
            'ERROR',
            `❌ Documento /tenant_users/${docId} NO existe`
          );
          continue;
        }

        const data = tenantUserDoc.data() || {};
        admin.tenantUserDoc = data;

        const issues = [];

        if (data.role && normalizeRole(data.role) !== normalizeRole(admin.claims.role)) {
          issues.push(`⚠️  tenant_users.role diferente: Auth=${admin.claims.role}, TenantUser=${data.role}`);
        }

        if (data.isActive !== true && data.isActive !== undefined) {
          issues.push(`⚠️  tenant_users.isActive no es true: ${data.isActive}`);
        }

        if (issues.length > 0) {
          issues.forEach(issue => this.addIssue(admin.uid, 'WARN', issue));
        }
      } catch (err) {
        this.addIssue(admin.uid, 'ERROR', `❌ Error en tenant_users: ${err.message}`);
      }
    }

    console.log(`   ✅ Verificados tenant_users para ${this.admins.length} admins`);
  }

  // 4. Generar reporte final
  async generateReport() {
    console.log('\n📊 [4/4] Generando reporte...');

    const errorCount = this.issues.filter(i => i.severity === 'ERROR').length;
    const warnCount = this.issues.filter(i => i.severity === 'WARN').length;

    console.log(`\n${errorCount > 0 ? '❌' : '✅'} ERRORES: ${errorCount}`);
    console.log(`${warnCount > 0 ? '⚠️ ' : '✅'} ADVERTENCIAS: ${warnCount}`);
    console.log(`✅ ADMINS SANOS: ${this.admins.length - Math.max(...new Set(this.issues.map(i => i.uid)).size, 0)}`);

    if (this.issues.length > 0) {
      console.log('\n' + '='.repeat(80));
      console.log('PROBLEMAS ENCONTRADOS:');
      console.log('='.repeat(80));

      const byUid = {};
      for (const issue of this.issues) {
        if (!byUid[issue.uid]) byUid[issue.uid] = [];
        byUid[issue.uid].push(issue);
      }

      for (const [uid, adminIssues] of Object.entries(byUid)) {
        const admin = this.admins.find(a => a.uid === uid);
        console.log(`\n👤 ${admin?.email || uid}`);
        console.log(`   UID: ${uid}`);
        adminIssues.forEach(issue => {
          console.log(`   ${issue.severity === 'ERROR' ? '❌' : '⚠️ '} ${issue.message}`);
        });
      }
    }

    console.log('\n' + '='.repeat(80));
    console.log('DETALLES DE ADMINS ENCONTRADOS:');
    console.log('='.repeat(80));

    for (const admin of this.admins) {
      const issues = this.issues.filter(i => i.uid === admin.uid);
      const status = issues.some(i => i.severity === 'ERROR') ? '❌' : issues.length > 0 ? '⚠️ ' : '✅';

      console.log(`\n${status} ${admin.email}`);
      console.log(`   UID: ${admin.uid}`);
      console.log(`   Auth Claims: ${JSON.stringify(admin.claims)}`);
      if (admin.userDoc) {
        console.log(`   /users/: tenantId=${admin.userDoc.tenantId}, role=${admin.userDoc.role}, status=${admin.userDoc.status}, isAdmin=${admin.userDoc.isAdmin}`);
      }
      if (admin.tenantUserDoc) {
        console.log(`   /tenant_users/: role=${admin.tenantUserDoc.role}, isActive=${admin.tenantUserDoc.isActive}`);
      }
    }

    // JSON report
    const report = {
      timestamp: new Date().toISOString(),
      summary: {
        totalAdmins: this.admins.length,
        errors: errorCount,
        warnings: warnCount
      },
      admins: this.admins.map(admin => ({
        uid: admin.uid,
        email: admin.email,
        issues: this.issues.filter(i => i.uid === admin.uid).map(i => ({
          severity: i.severity,
          message: i.message
        })),
        authClaims: admin.claims,
        userDoc: admin.userDoc || null,
        tenantUserDoc: admin.tenantUserDoc || null
      }))
    };

    const reportPath = path.join(__dirname, `audit-report-${Date.now()}.json`);
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    console.log(`\n📄 Reporte guardado en: ${reportPath}`);

    return { errorCount, warnCount, reportPath };
  }
}

// ─── MAIN ─────────────────────────────────────────────────────────────────

async function main() {
  const args = {};
  for (let i = 2; i < process.argv.length; i++) {
    if (process.argv[i] === '--service-account') args.serviceAccountPath = process.argv[++i];
  }

  const projectId = resolveProjectId(args.projectId);
  const credConf = resolveCredential(args.serviceAccountPath);

  if (!admin.apps.length) {
    admin.initializeApp({ projectId, credential: credConf.credential });
  }

  const auth = admin.auth();
  const db = admin.firestore();

  const audit = new AdminAudit(auth, db);

  try {
    await audit.scanAuthClaims();
    await audit.verifyUserDocs();
    await audit.verifyTenantUserDocs();
    const { errorCount, warnCount } = await audit.generateReport();

    process.exit(errorCount > 0 ? 1 : 0);
  } catch (err) {
    console.error('\n❌ Error:', err.message);
    process.exit(1);
  }
}

main();
