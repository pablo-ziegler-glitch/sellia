#!/usr/bin/env node
/**
 * admin-integrity-helpers.js
 *
 * Helpers para diagnosticar y reparar problemas de integridad de admins.
 * Proporciona funciones para:
 * - Diagnosticar un admin específico
 * - Sugerir reparaciones
 * - Validar consistencia de datos
 *
 * Uso como módulo:
 *   const helpers = require('./admin-integrity-helpers');
 *   helpers.diagnoseAdmin(auth, db, email);
 */

const admin = require('firebase-admin');

class AdminIntegrityHelpers {
  constructor(auth, db) {
    this.auth = auth;
    this.db = db;
  }

  normalizeRole(value) {
    return String(value || '').trim().toLowerCase();
  }

  formatAuthLookupError(err, email) {
    const code = String(err?.code || '').toLowerCase();
    const message = String(err?.message || 'Error desconocido');

    if (code.includes('user-not-found')) {
      return {
        notFound: true,
        lines: [`❌ Usuario NO encontrado en Firebase Auth: ${email}`]
      };
    }

    const quotaProjectError =
      message.includes('application_default_credentials') ||
      message.includes('requires a quota project');

    if (quotaProjectError) {
      return {
        notFound: false,
        lines: [
          '❌ Error de credenciales ADC: falta quota project para Identity Toolkit.',
          '   Solución recomendada (gcloud):',
          '   1) gcloud auth application-default set-quota-project <PROJECT_ID>',
          '   2) gcloud services enable identitytoolkit.googleapis.com --project <PROJECT_ID>',
          '   3) Reintentar el comando admin:diagnose.'
        ]
      };
    }

    return {
      notFound: false,
      lines: [`❌ Error consultando Firebase Auth (code=${err?.code || 'unknown'}): ${message}`]
    };
  }

  /**
   * Diagnostica un admin específico por email
   */
  async diagnoseAdminByEmail(email) {
    const normalizedEmail = String(email || '').trim().toLowerCase();
    console.log(`\n🔍 Diagnosticando admin: ${normalizedEmail}`);
    console.log('='.repeat(80));

    // 1. Buscar en Auth
    let user;
    try {
      user = await this.auth.getUserByEmail(normalizedEmail);
    } catch (err) {
      const errorInfo = this.formatAuthLookupError(err, normalizedEmail);
      errorInfo.lines.forEach(line => console.log(line));
      return null;
    }

    const diagnosis = {
      uid: user.uid,
      email: normalizedEmail,
      findings: {
        auth: {},
        userDoc: {},
        tenantUserDoc: {},
        issues: []
      }
    };

    // 2. Analizar Auth Claims
    console.log('\n📋 Auth Claims:');
    const claims = user.customClaims || {};
    diagnosis.findings.auth = { claims };

    if (claims.admin !== true && claims.isAdmin !== true) {
      diagnosis.findings.issues.push('❌ Sin claim admin/isAdmin');
    } else {
      console.log('   ✅ admin o isAdmin = true');
    }

    if (!claims.tenantId) {
      diagnosis.findings.issues.push('⚠️  Sin tenantId en claims');
    } else {
      console.log(`   ✅ tenantId: ${claims.tenantId}`);
    }

    if (!claims.role) {
      diagnosis.findings.issues.push('⚠️  Sin role en claims');
    } else {
      console.log(`   ✅ role: ${claims.role}`);
    }

    // 3. Analizar /users/{uid}
    console.log('\n📄 /users/' + user.uid + ':');
    const userRef = this.db.collection('users').doc(user.uid);
    const userSnap = await userRef.get();

    if (!userSnap.exists) {
      diagnosis.findings.issues.push('❌ Documento /users/{uid} NO existe');
      console.log('   ❌ NO EXISTE');
    } else {
      const userData = userSnap.data();
      diagnosis.findings.userDoc = userData;

      // Validar campos críticos
      if (userData.isAdmin !== true) {
        diagnosis.findings.issues.push(`⚠️  isAdmin no es true (es: ${userData.isAdmin})`);
        console.log(`   ⚠️  isAdmin: ${userData.isAdmin} (esperado: true)`);
      } else {
        console.log('   ✅ isAdmin: true');
      }

      if (!userData.role) {
        diagnosis.findings.issues.push('❌ Campo role falta');
        console.log('   ❌ role: falta');
      } else if (!['admin', 'owner'].includes(this.normalizeRole(userData.role))) {
        diagnosis.findings.issues.push(`⚠️  role inválido: ${userData.role}`);
        console.log(`   ⚠️  role: ${userData.role} (esperado: admin|owner)`);
      } else {
        console.log(`   ✅ role: ${userData.role}`);
      }

      if (userData.status !== 'active') {
        diagnosis.findings.issues.push(`⚠️  status no es 'active' (es: ${userData.status})`);
        console.log(`   ⚠️  status: ${userData.status} (esperado: active)`);
      } else {
        console.log('   ✅ status: active');
      }

      if (!userData.tenantId) {
        diagnosis.findings.issues.push('❌ Campo tenantId falta');
        console.log('   ❌ tenantId: falta');
      } else {
        console.log(`   ✅ tenantId: ${userData.tenantId}`);

        // 4. Analizar /tenant_users/{tenantId}_{uid}
        console.log('\n👥 /tenant_users/' + userData.tenantId + '_' + user.uid + ':');
        const tenantUserRef = this.db.collection('tenant_users')
          .doc(`${userData.tenantId}_${user.uid}`);
        const tenantUserSnap = await tenantUserRef.get();

        if (!tenantUserSnap.exists) {
          diagnosis.findings.issues.push(
            `❌ Documento /tenant_users/{tenantId}_{uid} NO existe`
          );
          console.log('   ❌ NO EXISTE');
        } else {
          const tenantUserData = tenantUserSnap.data();
          diagnosis.findings.tenantUserDoc = tenantUserData;

          if (tenantUserData.isActive !== true) {
            diagnosis.findings.issues.push(
              `⚠️  tenant_users.isActive no es true (es: ${tenantUserData.isActive})`
            );
            console.log(`   ⚠️  isActive: ${tenantUserData.isActive} (esperado: true)`);
          } else {
            console.log('   ✅ isActive: true');
          }

          if (!tenantUserData.role) {
            diagnosis.findings.issues.push('⚠️  tenant_users.role falta');
            console.log('   ⚠️  role: falta');
          } else if (this.normalizeRole(tenantUserData.role) !== this.normalizeRole(userData.role)) {
            diagnosis.findings.issues.push(
              `⚠️  tenant_users.role inconsistente (doc=${userData.role}, tenant_users=${tenantUserData.role})`
            );
            console.log(`   ⚠️  role: ${tenantUserData.role} (doc dice: ${userData.role})`);
          } else {
            console.log(`   ✅ role: ${tenantUserData.role}`);
          }
        }
      }
    }

    // Resumen
    console.log('\n' + '='.repeat(80));
    if (diagnosis.findings.issues.length === 0) {
      console.log('✅ Admin íntegro: todos los datos son consistentes');
    } else {
      console.log(`⚠️  Encontrados ${diagnosis.findings.issues.length} problemas:`);
      diagnosis.findings.issues.forEach((issue, idx) => {
        console.log(`   ${idx + 1}. ${issue}`);
      });
      this.suggestFixes(diagnosis, user);
    }

    return diagnosis;
  }

  /**
   * Sugiere pasos para reparar los problemas encontrados
   */
  suggestFixes(diagnosis, user) {
    const { findings } = diagnosis;
    const issues = findings.issues;

    console.log('\n' + '='.repeat(80));
    console.log('💡 SUGERENCIAS DE REPARACIÓN:');
    console.log('='.repeat(80));

    const fixes = [];

    // Problema: Sin documento /users
    if (issues.some(i => i.includes('/users/{uid} NO existe'))) {
      fixes.push({
        priority: 1,
        title: 'Crear documento /users/{uid}',
        command: `npm --prefix functions run admin:grant -- --email ${diagnosis.email} --tenant <TENANT_ID> --role owner`
      });
    }

    // Problema: isAdmin inconsistente
    if (issues.some(i => i.includes('isAdmin'))) {
      fixes.push({
        priority: 2,
        title: 'Actualizar isAdmin en /users/{uid}',
        command: `firebase firestore:set functions/scripts/fix-payloads/users-${user.uid}.json --overwrite`
      });
    }

    // Problema: role inconsistente
    if (issues.some(i => i.includes('role') && !i.includes('tenant_users'))) {
      fixes.push({
        priority: 2,
        title: 'Actualizar role en /users/{uid}',
        command: `firebase firestore:set functions/scripts/fix-payloads/users-${user.uid}.json --overwrite`
      });
    }

    // Problema: status no active
    if (issues.some(i => i.includes('status'))) {
      fixes.push({
        priority: 2,
        title: 'Actualizar status a "active"',
        command: `firebase firestore:update users/${user.uid} status=active`
      });
    }

    // Problema: Sin documento tenant_users
    if (issues.some(i => i.includes('/tenant_users'))) {
      const tenantId = findings.userDoc?.tenantId || '<TENANT_ID>';
      fixes.push({
        priority: 1,
        title: 'Crear documento /tenant_users/{tenantId}_{uid}',
        command: `firebase firestore:set "tenant_users/${tenantId}_${user.uid}" --merge`
      });
    }

    // Ordenar por prioridad
    fixes.sort((a, b) => a.priority - b.priority);

    fixes.forEach((fix, idx) => {
      console.log(`\n${idx + 1}. ${fix.title} (Prioridad: ${fix.priority})`);
      console.log(`   ${fix.command}`);
    });

    console.log('\n💻 Alternativa rápida: usar fix-admin-account.js');
    console.log(`   node functions/scripts/fix-admin-account.js \\`);
    console.log(`     --email ${diagnosis.email} \\`);
    console.log(`     --tenant ${findings.userDoc?.tenantId || '<TENANT_ID>'} \\`);
    console.log(`     --role owner \\`);
    console.log(`     --dry-run`);
  }

  /**
   * Valida múltiples admins y genera un reporte
   */
  async validateMultipleAdmins(emails) {
    console.log(`\n🔍 Validando ${emails.length} administrador(es)...\n`);
    const results = [];

    for (const email of emails) {
      try {
        const result = await this.diagnoseAdminByEmail(email);
        if (result) {
          results.push(result);
        }
      } catch (err) {
        console.error(`Error diagnosticando ${email}:`, err.message);
      }
    }

    return results;
  }

  /**
   * Obtiene un resumen rápido de la salud de todos los admins
   */
  async quickHealthCheck() {
    console.log('\n⚡ Health Check Rápido:\n');

    try {
      const usersSnapshot = await this.db.collection('users')
        .where('isAdmin', '==', true)
        .limit(100)
        .get();

      const admins = [];
      for (const doc of usersSnapshot.docs) {
        const data = doc.data();
        const healthy = data.status === 'active' &&
          data.tenantId &&
          ['admin', 'owner'].includes(this.normalizeRole(data.role));

        admins.push({
          email: data.email,
          uid: doc.id,
          role: data.role,
          tenantId: data.tenantId,
          status: data.status,
          healthy: healthy ? '✅' : '❌'
        });
      }

      // Mostrar tabla
      console.log('EMAIL | ROLE | TENANT | STATUS | HEALTH');
      console.log('-'.repeat(80));
      for (const admin of admins) {
        const email = admin.email.padEnd(30);
        const role = (admin.role || '-').padEnd(10);
        const tenant = (admin.tenantId || '-').slice(0, 15).padEnd(15);
        const status = (admin.status || '-').padEnd(10);
        console.log(`${email} | ${role} | ${tenant} | ${status} | ${admin.healthy}`);
      }

      const healthy = admins.filter(a => a.healthy === '✅').length;
      console.log(`\n${healthy}/${admins.length} admins en estado saludable`);

      return admins;
    } catch (err) {
      console.error('Error en health check:', err.message);
      return [];
    }
  }
}

// ─── CLI INTERFACE ─────────────────────────────────────────────────────────

if (require.main === module) {
  const fs = require('fs');
  const path = require('path');
  const os = require('os');

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
      return admin.credential.cert(json);
    }

    const adcCandidates = [
      process.env.GOOGLE_APPLICATION_CREDENTIALS,
      path.join(os.homedir(), '.config', 'gcloud', 'application_default_credentials.json'),
      process.env.APPDATA && path.join(process.env.APPDATA, 'gcloud', 'application_default_credentials.json')
    ].filter(Boolean);

    if (adcCandidates.some(p => fs.existsSync(p))) {
      return admin.credential.applicationDefault();
    }

    throw new Error('No se encontraron credenciales.');
  }

  async function main() {
    const args = {};
    for (let i = 2; i < process.argv.length; i++) {
      if (process.argv[i] === '--email') args.email = process.argv[++i];
      else if (process.argv[i] === '--service-account') args.serviceAccountPath = process.argv[++i];
      else if (process.argv[i] === '--project') args.projectId = process.argv[++i];
      else if (process.argv[i] === '--quota-project') args.quotaProject = process.argv[++i];
      else if (process.argv[i] === '--health-check') args.healthCheck = true;
    }

    if (args.quotaProject) {
      process.env.GOOGLE_CLOUD_QUOTA_PROJECT = args.quotaProject;
    }

    const projectId = resolveProjectId(args.projectId);
    const credential = resolveCredential(args.serviceAccountPath);

    if (!admin.apps.length) {
      admin.initializeApp({ projectId, credential });
    }

    const auth = admin.auth();
    const db = admin.firestore();
    const helpers = new AdminIntegrityHelpers(auth, db);

    try {
      if (args.healthCheck) {
        await helpers.quickHealthCheck();
      } else if (args.email) {
        await helpers.diagnoseAdminByEmail(args.email);
      } else {
        console.log('Uso:');
        console.log('  node admin-integrity-helpers.js --email <EMAIL>');
        console.log('  node admin-integrity-helpers.js --health-check');
        console.log('Opciones:');
        console.log('  --project <PROJECT_ID>');
        console.log('  --quota-project <PROJECT_ID>');
        console.log('  --service-account <PATH_JSON_SERVICE_ACCOUNT>');
      }
    } catch (err) {
      console.error('❌ Error:', err.message);
      process.exit(1);
    }
  }

  main();
}

module.exports = AdminIntegrityHelpers;
