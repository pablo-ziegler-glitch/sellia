#!/usr/bin/env node
/**
 * audit-tenant-users-integrity.js
 *
 * Audita la integridad de permisos para DUEÑOS (owner) y CLIENTES FINALES (viewer/final_customer).
 * Detecta inconsistencias entre JWT claims, /users/{uid}, /tenant_users y /tenants/{tenantId}.
 *
 * Para owners el problema más común es que falte el JWT claim `admin: true`
 * (requerido por las reglas Firestore) o que no exista el doc /tenant_users.
 *
 * Uso:
 *   # Auditar todos los owners y clientes del sistema
 *   node scripts/audit-tenant-users-integrity.js
 *
 *   # Auditar un usuario específico por email
 *   node scripts/audit-tenant-users-integrity.js --email usuario@ejemplo.com
 *
 *   # Auditar un usuario específico por UID
 *   node scripts/audit-tenant-users-integrity.js --uid <UID>
 *
 *   # Solo auditar owners
 *   node scripts/audit-tenant-users-integrity.js --roles owner
 *
 *   # Solo auditar clientes finales
 *   node scripts/audit-tenant-users-integrity.js --roles client
 *
 *   # Con service account explícito
 *   node scripts/audit-tenant-users-integrity.js --service-account /ruta/sa.json
 *
 * ROLES auditados:
 *   owner  → necesita: admin claim JWT, isAdmin=true, tenantId consistente,
 *             /tenant_users doc, /tenants doc con ownerUid
 *   client → necesita: isAdmin=false, accountType=final_customer, status=active,
 *             NO debe tener claim admin en JWT
 */

'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const admin = require('firebase-admin');

// ─── HELPERS ──────────────────────────────────────────────────────────────────

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

  const candidates = [
    process.env.GOOGLE_APPLICATION_CREDENTIALS,
    path.join(os.homedir(), '.config', 'gcloud', 'application_default_credentials.json'),
    process.env.APPDATA && path.join(process.env.APPDATA, 'gcloud', 'application_default_credentials.json')
  ].filter(Boolean);

  if (candidates.some(p => fs.existsSync(p))) {
    return { credential: admin.credential.applicationDefault(), source: 'adc' };
  }

  throw new Error(
    'No se encontraron credenciales de Google. Usá --service-account <PATH_JSON> ' +
    'o ejecutá "gcloud auth application-default login".'
  );
}

function norm(value) {
  return String(value || '').trim().toLowerCase();
}

function parseArgs(argv) {
  const args = {
    email: '',
    uid: '',
    roles: ['owner', 'client'],  // por defecto ambos
    serviceAccountPath: '',
    projectId: ''
  };

  for (let i = 2; i < argv.length; i++) {
    const token = argv[i];
    if (token === '--email') args.email = norm(argv[++i] || '');
    else if (token === '--uid') args.uid = String(argv[++i] || '').trim();
    else if (token === '--roles') {
      const raw = String(argv[++i] || '').split(',').map(r => norm(r));
      args.roles = raw;
    }
    else if (token === '--service-account') args.serviceAccountPath = argv[++i] || '';
    else if (token === '--project') args.projectId = argv[++i] || '';
  }

  return args;
}

// ─── AUDIT ENGINE ──────────────────────────────────────────────────────────────

class UserRecord {
  constructor(uid, email, claims) {
    this.uid = uid;
    this.email = email;
    this.claims = claims;
    this.userDoc = null;
    this.tenantUserDoc = null;
    this.tenantDoc = null;
    this.issues = [];
    this.detectedRole = null;  // 'owner' | 'client' | 'other'
  }

  addIssue(severity, message) {
    this.issues.push({ severity, message });
  }

  get status() {
    if (this.issues.some(i => i.severity === 'ERROR')) return 'ERROR';
    if (this.issues.some(i => i.severity === 'WARN')) return 'WARN';
    return 'OK';
  }
}

class TenantUsersAudit {
  constructor(auth, db, args) {
    this.auth = auth;
    this.db = db;
    this.args = args;
    this.records = [];
  }

  // ── 1. Recolección de usuarios ─────────────────────────────────────────────

  async collectUsers() {
    const { email, uid, roles } = this.args;
    const wantOwner = roles.includes('owner');
    const wantClient = roles.includes('client');

    if (email || uid) {
      // Modo usuario específico
      let authUser;
      try {
        authUser = email
          ? await this.auth.getUserByEmail(email)
          : await this.auth.getUser(uid);
      } catch (err) {
        console.error(`\n❌ Usuario no encontrado: ${err.message}`);
        process.exit(1);
      }

      const rec = this._buildRecord(authUser);
      // En modo específico se audita sin importar el rol declarado
      this.records.push(rec);
      console.log(`\n🔎 Modo usuario específico: ${authUser.email || authUser.uid}`);
      return;
    }

    // Modo escaneo completo: lista todos los usuarios de Auth
    console.log('\n📋 [1/5] Escaneando Firebase Auth...');
    let pageToken;
    let totalScanned = 0;

    do {
      const page = await this.auth.listUsers(1000, pageToken);
      for (const u of page.users) {
        totalScanned++;
        const claims = u.customClaims || {};
        const claimRole = norm(claims.role);
        const isOwnerClaim = claimRole === 'owner' || claims.admin === true || claims.isAdmin === true;
        const isClientClaim = claims.admin !== true && claims.isAdmin !== true && !claims.superAdmin;

        if (wantOwner && isOwnerClaim) {
          const rec = this._buildRecord(u);
          rec.detectedRole = 'owner';
          this.records.push(rec);
        } else if (wantClient && isClientClaim) {
          const rec = this._buildRecord(u);
          rec.detectedRole = 'client';
          this.records.push(rec);
        }
      }
      pageToken = page.pageToken;
    } while (pageToken);

    console.log(`   ✅ Escaneados ${totalScanned} usuarios, seleccionados ${this.records.length} para auditar`);
  }

  _buildRecord(authUser) {
    const claims = authUser.customClaims || {};
    return new UserRecord(authUser.uid, authUser.email, {
      admin: claims.admin,
      isAdmin: claims.isAdmin,
      role: claims.role,
      tenantId: claims.tenantId,
      superAdmin: claims.superAdmin
    });
  }

  // ── 2. Leer /users/{uid} ──────────────────────────────────────────────────

  async verifyUserDocs() {
    const label = this.args.email || this.args.uid ? '' : ' [2/5]';
    console.log(`\n🔍${label} Verificando /users/{uid}...`);

    for (const rec of this.records) {
      try {
        const snap = await this.db.collection('users').doc(rec.uid).get();
        if (!snap.exists) {
          rec.addIssue('ERROR', `❌ Documento /users/${rec.uid} NO existe en Firestore`);
          continue;
        }
        rec.userDoc = snap.data() || {};

        // Detectar rol desde el doc si no está en claims
        if (!rec.detectedRole) {
          const docRole = norm(rec.userDoc.role);
          const docAccountType = norm(rec.userDoc.accountType);
          if (docRole === 'owner' || rec.userDoc.isAdmin === true) rec.detectedRole = 'owner';
          else if (docAccountType === 'final_customer' || docRole === 'viewer') rec.detectedRole = 'client';
          else rec.detectedRole = 'other';
        }
      } catch (err) {
        rec.addIssue('ERROR', `❌ Error al leer /users/${rec.uid}: ${err.message}`);
      }
    }

    console.log(`   ✅ ${this.records.length} docs revisados`);
  }

  // ── 3. Validaciones por rol ───────────────────────────────────────────────

  async runRoleChecks() {
    const label = this.args.email || this.args.uid ? '' : ' [3/5]';
    console.log(`\n🔐${label} Ejecutando validaciones por rol...`);

    for (const rec of this.records) {
      if (rec.detectedRole === 'owner') {
        this._checkOwner(rec);
      } else if (rec.detectedRole === 'client') {
        this._checkClient(rec);
      }
    }

    console.log(`   ✅ Validaciones de roles completadas`);
  }

  /**
   * Un OWNER válido necesita:
   * 1. JWT claim `admin: true` → para que isTenantAdmin() funcione en Firestore
   * 2. JWT claim `role: 'owner'` → para normalizedRole() en reglas
   * 3. JWT claim `tenantId` definido → para effectiveTenantId()
   * 4. /users/{uid}.isAdmin === true
   * 5. /users/{uid}.role === 'owner'
   * 6. /users/{uid}.accountType === 'store_owner'
   * 7. /users/{uid}.status === 'active'
   * 8. /users/{uid}.tenantId consistente con claims
   * 9. /tenant_users/{tenantId}_{uid} existe con role=owner e isActive=true
   * 10. /tenants/{tenantId} existe con ownerUid === uid o uid en ownerUids
   */
  _checkOwner(rec) {
    const { claims, userDoc } = rec;

    // ── JWT Claims ──
    if (claims.admin !== true) {
      rec.addIssue('ERROR',
        '❌ JWT claim `admin` NO es true — las reglas Firestore requieren esto para isTenantAdmin(). ' +
        'Ejecutar manage-admin-access.js para corregir.'
      );
    }

    if (norm(claims.role) !== 'owner') {
      rec.addIssue('ERROR',
        `❌ JWT claim \`role\` es "${claims.role || '(vacío)'}" — debe ser "owner". ` +
        'Ejecutar manage-admin-access.js --role owner para corregir.'
      );
    }

    if (!claims.tenantId) {
      rec.addIssue('ERROR',
        '❌ JWT claim `tenantId` está vacío o ausente. ' +
        'Ejecutar manage-admin-access.js --tenant <TENANT_ID> para corregir.'
      );
    }

    // ── /users/{uid} ──
    if (!userDoc) return;  // ya reportado arriba

    if (userDoc.isAdmin !== true) {
      rec.addIssue('ERROR',
        `❌ /users/${rec.uid}.isAdmin NO es true (valor: ${userDoc.isAdmin}). ` +
        'Requerido para hasAdminUserFlag() en Firestore.'
      );
    }

    if (norm(userDoc.role) !== 'owner') {
      rec.addIssue('ERROR',
        `❌ /users/${rec.uid}.role es "${userDoc.role || '(vacío)'}" — debe ser "owner".`
      );
    }

    if (norm(userDoc.accountType) !== 'store_owner') {
      rec.addIssue('WARN',
        `⚠️  /users/${rec.uid}.accountType es "${userDoc.accountType || '(vacío)'}" — se espera "store_owner".`
      );
    }

    if (norm(userDoc.status) !== 'active') {
      rec.addIssue('ERROR',
        `❌ /users/${rec.uid}.status es "${userDoc.status || '(vacío)'}" — debe ser "active".`
      );
    }

    if (!userDoc.tenantId) {
      rec.addIssue('ERROR', `❌ /users/${rec.uid}.tenantId está vacío o ausente.`);
    } else if (claims.tenantId && userDoc.tenantId !== claims.tenantId) {
      rec.addIssue('ERROR',
        `❌ tenantId INCONSISTENTE: JWT="${claims.tenantId}" vs /users="${userDoc.tenantId}". ` +
        'Firestore puede rechazar las operaciones del owner.'
      );
    }

    const expectedPermissions = [
      'MANAGE_USERS', 'MANAGE_CLOUD_SERVICES', 'VIEW_USAGE_DASHBOARD',
      'CASH_OPEN', 'CASH_AUDIT', 'CASH_MOVEMENT', 'CASH_CLOSE', 'VIEW_CASH_REPORT'
    ];
    const docPerms = Array.isArray(userDoc.permissions) ? userDoc.permissions : [];
    const missingPerms = expectedPermissions.filter(p => !docPerms.includes(p));
    if (missingPerms.length > 0) {
      rec.addIssue('WARN',
        `⚠️  /users/${rec.uid}.permissions le faltan: ${missingPerms.join(', ')}.`
      );
    }
  }

  /**
   * Un CLIENTE FINAL válido necesita:
   * 1. NO tener JWT claim `admin: true` (seguridad)
   * 2. /users/{uid}.isAdmin === false o ausente
   * 3. /users/{uid}.accountType === 'final_customer'
   * 4. /users/{uid}.role === 'viewer' o equivalente
   * 5. /users/{uid}.status === 'active'
   */
  _checkClient(rec) {
    const { claims, userDoc } = rec;

    // ── JWT Claims ──
    if (claims.admin === true || claims.isAdmin === true) {
      rec.addIssue('ERROR',
        '❌ Cliente final tiene JWT claim `admin: true` — esto le da permisos de admin incorrectamente.'
      );
    }

    if (claims.superAdmin === true) {
      rec.addIssue('ERROR',
        '❌ Cliente final tiene JWT claim `superAdmin: true` — esto es un problema de seguridad crítico.'
      );
    }

    // ── /users/{uid} ──
    if (!userDoc) return;

    if (userDoc.isAdmin === true) {
      rec.addIssue('ERROR',
        `❌ /users/${rec.uid}.isAdmin es true para un cliente final — debe ser false.`
      );
    }

    if (norm(userDoc.accountType) !== 'final_customer') {
      rec.addIssue('WARN',
        `⚠️  /users/${rec.uid}.accountType es "${userDoc.accountType || '(vacío)'}" — se espera "final_customer".`
      );
    }

    const clientRole = norm(userDoc.role);
    if (clientRole && clientRole !== 'viewer') {
      rec.addIssue('WARN',
        `⚠️  /users/${rec.uid}.role es "${userDoc.role}" — para clientes finales se espera "viewer" o vacío.`
      );
    }

    if (norm(userDoc.status) !== 'active') {
      rec.addIssue('WARN',
        `⚠️  /users/${rec.uid}.status es "${userDoc.status || '(vacío)'}" — se espera "active".`
      );
    }
  }

  // ── 4. Verificar /tenant_users (solo owners) ─────────────────────────────

  async verifyTenantUserDocs() {
    const owners = this.records.filter(r => r.detectedRole === 'owner' && r.userDoc);
    if (owners.length === 0) return;

    const label = this.args.email || this.args.uid ? '' : ' [4/5]';
    console.log(`\n🔍${label} Verificando /tenant_users para ${owners.length} owner(s)...`);

    for (const rec of owners) {
      const tenantId = rec.claims.tenantId || rec.userDoc?.tenantId;
      if (!tenantId) {
        rec.addIssue('ERROR', '❌ No hay tenantId disponible — no se puede verificar /tenant_users.');
        continue;
      }

      const docId = `${tenantId}_${rec.uid}`;
      try {
        const snap = await this.db.collection('tenant_users').doc(docId).get();
        if (!snap.exists) {
          rec.addIssue('ERROR',
            `❌ Documento /tenant_users/${docId} NO existe. ` +
            'Ejecutar manage-admin-access.js para crearlo.'
          );
          continue;
        }

        rec.tenantUserDoc = snap.data() || {};

        if (norm(rec.tenantUserDoc.role) !== 'owner') {
          rec.addIssue('WARN',
            `⚠️  /tenant_users/${docId}.role es "${rec.tenantUserDoc.role || '(vacío)'}" — se espera "owner".`
          );
        }

        if (rec.tenantUserDoc.isActive !== true) {
          rec.addIssue('ERROR',
            `❌ /tenant_users/${docId}.isActive NO es true (valor: ${rec.tenantUserDoc.isActive}). ` +
            'El owner no podrá acceder como miembro del tenant.'
          );
        }
      } catch (err) {
        rec.addIssue('ERROR', `❌ Error al leer /tenant_users/${docId}: ${err.message}`);
      }
    }

    console.log(`   ✅ /tenant_users verificados`);
  }

  // ── 5. Verificar /tenants/{tenantId} (solo owners) ───────────────────────

  async verifyTenantDocs() {
    const owners = this.records.filter(r => r.detectedRole === 'owner' && r.userDoc);
    if (owners.length === 0) return;

    const label = this.args.email || this.args.uid ? '' : ' [5/5]';
    console.log(`\n🔍${label} Verificando /tenants/{tenantId} para ${owners.length} owner(s)...`);

    // Cache de docs de tenant para evitar lecturas duplicadas
    const tenantCache = new Map();

    for (const rec of owners) {
      const tenantId = rec.claims.tenantId || rec.userDoc?.tenantId;
      if (!tenantId) continue;

      try {
        if (!tenantCache.has(tenantId)) {
          const snap = await this.db.collection('tenants').doc(tenantId).get();
          tenantCache.set(tenantId, snap.exists ? snap.data() : null);
        }

        const tenantData = tenantCache.get(tenantId);

        if (!tenantData) {
          rec.addIssue('ERROR',
            `❌ Documento /tenants/${tenantId} NO existe en Firestore. ` +
            'El tenant del owner no está creado o tiene un ID incorrecto.'
          );
          continue;
        }

        rec.tenantDoc = tenantData;

        // Verificar que el UID del owner esté registrado en el tenant
        const ownerUids = Array.isArray(tenantData.ownerUids) ? tenantData.ownerUids : [];
        const ownerUidMatch = tenantData.ownerUid === rec.uid || ownerUids.includes(rec.uid);

        if (!ownerUidMatch) {
          rec.addIssue('ERROR',
            `❌ /tenants/${tenantId}.ownerUid="${tenantData.ownerUid}" NO coincide con uid="${rec.uid}". ` +
            'La regla isTenantOwnerRecord() fallará. Verificar que el tenant tenga el ownerUid correcto.'
          );
        }

        // Verificar nombre del tenant (informativo)
        if (!tenantData.name && !tenantData.displayName) {
          rec.addIssue('WARN', `⚠️  /tenants/${tenantId} no tiene campo "name" o "displayName".`);
        }
      } catch (err) {
        rec.addIssue('ERROR', `❌ Error al leer /tenants/${tenantId}: ${err.message}`);
      }
    }

    console.log(`   ✅ /tenants verificados`);
  }

  // ── Reporte ────────────────────────────────────────────────────────────────

  generateReport() {
    const owners = this.records.filter(r => r.detectedRole === 'owner');
    const clients = this.records.filter(r => r.detectedRole === 'client');
    const others = this.records.filter(r => r.detectedRole === 'other' || !r.detectedRole);

    const allIssues = this.records.flatMap(r => r.issues);
    const errorCount = allIssues.filter(i => i.severity === 'ERROR').length;
    const warnCount = allIssues.filter(i => i.severity === 'WARN').length;
    const okCount = this.records.filter(r => r.status === 'OK').length;

    console.log('\n' + '='.repeat(80));
    console.log('RESUMEN');
    console.log('='.repeat(80));
    console.log(`   👑 Owners auditados   : ${owners.length}`);
    console.log(`   👤 Clientes auditados : ${clients.length}`);
    console.log(`   ✅ Sin problemas      : ${okCount}`);
    console.log(`   ❌ Con ERRORES        : ${this.records.filter(r => r.status === 'ERROR').length}`);
    console.log(`   ⚠️  Con ADVERTENCIAS  : ${this.records.filter(r => r.status === 'WARN').length}`);

    // ── Detalle de problemas ──
    const withIssues = this.records.filter(r => r.issues.length > 0);
    if (withIssues.length > 0) {
      console.log('\n' + '='.repeat(80));
      console.log('PROBLEMAS ENCONTRADOS:');
      console.log('='.repeat(80));

      for (const rec of withIssues) {
        const icon = rec.status === 'ERROR' ? '❌' : '⚠️ ';
        console.log(`\n${icon} ${rec.email || '(sin email)'} [${rec.detectedRole || 'desconocido'}]`);
        console.log(`   UID: ${rec.uid}`);
        for (const issue of rec.issues) {
          const iIcon = issue.severity === 'ERROR' ? '  ❌' : '  ⚠️ ';
          console.log(`${iIcon} ${issue.message}`);
        }
      }
    }

    // ── Detalle completo ──
    console.log('\n' + '='.repeat(80));
    console.log('DETALLE COMPLETO:');
    console.log('='.repeat(80));

    for (const rec of this.records) {
      const icon = rec.status === 'OK' ? '✅' : rec.status === 'ERROR' ? '❌' : '⚠️ ';
      const roleLabel = rec.detectedRole === 'owner' ? '👑 OWNER' : rec.detectedRole === 'client' ? '👤 CLIENTE' : '❓ OTRO';
      console.log(`\n${icon} ${rec.email || rec.uid} — ${roleLabel}`);
      console.log(`   UID     : ${rec.uid}`);
      console.log(`   Claims  : admin=${rec.claims.admin}, role=${rec.claims.role}, tenantId=${rec.claims.tenantId}`);

      if (rec.userDoc) {
        console.log(`   /users/ : role=${rec.userDoc.role}, isAdmin=${rec.userDoc.isAdmin}, accountType=${rec.userDoc.accountType}, status=${rec.userDoc.status}, tenantId=${rec.userDoc.tenantId}`);
      } else {
        console.log(`   /users/ : ❌ No existe`);
      }

      if (rec.detectedRole === 'owner') {
        if (rec.tenantUserDoc) {
          console.log(`   /tenant_users/ : role=${rec.tenantUserDoc.role}, isActive=${rec.tenantUserDoc.isActive}`);
        } else {
          console.log(`   /tenant_users/ : ❌ No existe`);
        }

        if (rec.tenantDoc) {
          const tenantId = rec.claims.tenantId || rec.userDoc?.tenantId;
          console.log(`   /tenants/${tenantId} : ownerUid=${rec.tenantDoc.ownerUid}, nombre="${rec.tenantDoc.name || rec.tenantDoc.displayName || '(sin nombre)'}"`);
        } else {
          console.log(`   /tenants/ : ❌ No existe o no se pudo leer`);
        }
      }

      if (rec.issues.length === 0) {
        console.log(`   ✅ Sin problemas`);
      }
    }

    // ── Acciones recomendadas ──
    const ownersWithErrors = owners.filter(r => r.status === 'ERROR');
    if (ownersWithErrors.length > 0) {
      console.log('\n' + '='.repeat(80));
      console.log('ACCIONES CORRECTIVAS PARA OWNERS CON ERRORES:');
      console.log('='.repeat(80));
      console.log('\nPara cada owner con errores de claim JWT o docs faltantes, ejecutar:');
      for (const rec of ownersWithErrors) {
        const tenantId = rec.claims.tenantId || rec.userDoc?.tenantId || '<TENANT_ID>';
        console.log(`\n  node scripts/manage-admin-access.js \\`);
        console.log(`    --email "${rec.email || ''}" \\`);
        console.log(`    --tenant "${tenantId}" \\`);
        console.log(`    --role owner`);
      }
      console.log('\n⚠️  Luego el usuario debe cerrar sesión y volver a iniciar para refrescar el JWT.');
    }

    // ── JSON report ──
    const report = {
      timestamp: new Date().toISOString(),
      summary: {
        totalAudited: this.records.length,
        owners: owners.length,
        clients: clients.length,
        errors: errorCount,
        warnings: warnCount,
        ok: okCount
      },
      records: this.records.map(rec => ({
        uid: rec.uid,
        email: rec.email,
        detectedRole: rec.detectedRole,
        status: rec.status,
        issues: rec.issues,
        authClaims: rec.claims,
        userDoc: rec.userDoc || null,
        tenantUserDoc: rec.tenantUserDoc || null,
        tenantDoc: rec.tenantDoc ? {
          ownerUid: rec.tenantDoc.ownerUid,
          ownerUids: rec.tenantDoc.ownerUids,
          name: rec.tenantDoc.name || rec.tenantDoc.displayName
        } : null
      }))
    };

    const reportPath = path.join(__dirname, `tenant-users-audit-${Date.now()}.json`);
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    console.log(`\n📄 Reporte JSON guardado en: ${reportPath}`);

    return errorCount;
  }
}

// ─── MAIN ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log('🔍 audit-tenant-users-integrity — Auditor de permisos para Owners y Clientes');
  console.log('='.repeat(80));

  const args = parseArgs(process.argv);
  const projectId = resolveProjectId(args.projectId);
  const credConf = resolveCredential(args.serviceAccountPath);

  if (!projectId) {
    throw new Error(
      'No se pudo resolver el project ID. Usá --project <PROJECT_ID> ' +
      'o definí GCLOUD_PROJECT, o asegurate que .firebaserc tenga "projects.default".'
    );
  }

  console.log(`   Proyecto : ${projectId}`);
  console.log(`   Credencial: ${credConf.source}`);
  console.log(`   Roles    : ${args.roles.join(', ')}`);
  if (args.email) console.log(`   Email    : ${args.email}`);
  if (args.uid) console.log(`   UID      : ${args.uid}`);

  if (!admin.apps.length) {
    admin.initializeApp({ projectId, credential: credConf.credential });
  }

  const auth = admin.auth();
  const db = admin.firestore();

  const audit = new TenantUsersAudit(auth, db, args);

  await audit.collectUsers();
  await audit.verifyUserDocs();
  await audit.runRoleChecks();
  await audit.verifyTenantUserDocs();
  await audit.verifyTenantDocs();

  const errorCount = audit.generateReport();

  process.exit(errorCount > 0 ? 1 : 0);
}

main().catch(err => {
  console.error('\n❌ Error fatal:', err.message);
  process.exit(1);
});
