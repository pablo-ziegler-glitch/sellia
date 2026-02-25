const { readFileSync } = require('node:fs');
const { before, beforeEach, after, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc } = require('firebase/firestore');

const PROJECT_ID = 'sellia-firestore-rules-tests';
const TENANT_A = 'tenant-a';
const TENANT_B = 'tenant-b';

let testEnv;

const ADMIN_ROLES = ['owner', 'admin'];
const NON_ADMIN_ROLES = ['manager', 'cashier', 'viewer'];

function roleUserId(role) {
  return `${role}-uid`;
}

function dbWithClaims(uid, claims = {}) {
  return testEnv.authenticatedContext(uid, claims).firestore();
}

async function seedUser(uid, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'users', uid), {
      email: `${uid}@example.com`,
      tenantId: TENANT_A,
      role: 'viewer',
      status: 'active',
      accountType: 'store_owner',
      isAdmin: false,
      isSuperAdmin: false,
      ...data,
    });
  });
}

async function seedBaseData() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, 'users', 'legacy-owner-uid'), {
      email: 'legacy-owner@example.com',
      tenantId: TENANT_A,
      role: 'owner',
      accountType: 'store_owner',
      isAdmin: false,
      isSuperAdmin: false,
      // legacy doc sin status
    });

    await setDoc(doc(db, 'tenant_users', 'tu-a-1'), {
      tenantId: TENANT_A,
      userId: 'existing-user',
      role: 'cashier',
      status: 'active',
    });

    await setDoc(doc(db, 'tenant_users', 'tu-b-1'), {
      tenantId: TENANT_B,
      userId: 'existing-user-b',
      role: 'cashier',
      status: 'active',
    });

    await setDoc(doc(db, 'account_requests', 'ar-a-1'), {
      tenantId: TENANT_A,
      status: 'pending',
      requestedRole: 'cashier',
      requestedBy: 'requester-a',
    });

    await setDoc(doc(db, 'account_requests', 'ar-b-1'), {
      tenantId: TENANT_B,
      status: 'pending',
      requestedRole: 'cashier',
      requestedBy: 'requester-b',
    });

    await setDoc(doc(db, 'tenants', TENANT_A), { id: TENANT_A, ownerUid: 'owner-uid' });
    await setDoc(doc(db, 'tenants', TENANT_B), { id: TENANT_B, ownerUid: 'other-owner-uid' });
  });
}

describe('firestore.rules - multi-tenant admin policy', () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: readFileSync('firestore.rules', 'utf8'),
      },
    });
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();

    for (const role of [...ADMIN_ROLES, ...NON_ADMIN_ROLES]) {
      await seedUser(roleUserId(role), {
        role,
        tenantId: TENANT_A,
      });
    }

    await seedUser('tenant-b-admin-uid', {
      role: 'admin',
      tenantId: TENANT_B,
    });

    await seedBaseData();
  });

  after(async () => {
    await testEnv.cleanup();
  });

  for (const role of ADMIN_ROLES) {
    it(`${role} can write admin collections inside own tenant`, async () => {
      const db = dbWithClaims(roleUserId(role), { uid: roleUserId(role) });

      await assertSucceeds(
        setDoc(doc(db, 'tenant_users', `${role}-created`), {
          tenantId: TENANT_A,
          userId: `${role}-target`,
          role: 'cashier',
          status: 'active',
        }),
      );

      await assertSucceeds(
        setDoc(doc(db, 'users', `${role}-created-user`), {
          email: `${role}-created@example.com`,
          tenantId: TENANT_A,
          role: 'cashier',
          status: 'active',
          accountType: 'store_owner',
          isAdmin: false,
          isSuperAdmin: false,
        }),
      );

      await assertSucceeds(
        updateDoc(doc(db, 'account_requests', 'ar-a-1'), {
          status: 'approved',
        }),
      );
    });
  }

  for (const role of NON_ADMIN_ROLES) {
    it(`${role} (viewer-like no privilege) cannot perform admin writes`, async () => {
      const db = dbWithClaims(roleUserId(role), { uid: roleUserId(role) });

      await assertFails(
        setDoc(doc(db, 'tenant_users', `${role}-created`), {
          tenantId: TENANT_A,
          userId: `${role}-target`,
          role: 'cashier',
          status: 'active',
        }),
      );

      await assertFails(
        updateDoc(doc(db, 'account_requests', 'ar-a-1'), {
          status: 'approved',
        }),
      );
    });
  }

  it('denies cross-tenant admin write even for admin role', async () => {
    const db = dbWithClaims('admin-uid', { uid: 'admin-uid' });

    await assertFails(
      setDoc(doc(db, 'tenant_users', 'admin-cross-tenant'), {
        tenantId: TENANT_B,
        userId: 'cross-tenant-target',
        role: 'cashier',
        status: 'active',
      }),
    );

    await assertFails(
      updateDoc(doc(db, 'account_requests', 'ar-b-1'), {
        status: 'approved',
      }),
    );
  });

  it('allows superAdmin claim bypass for cross-tenant admin writes', async () => {
    const db = dbWithClaims('super-admin-uid', {
      uid: 'super-admin-uid',
      superAdmin: true,
      tenantId: TENANT_A,
      role: 'viewer',
    });

    await assertSucceeds(
      setDoc(doc(db, 'tenant_users', 'super-cross-tenant'), {
        tenantId: TENANT_B,
        userId: 'cross-tenant-target',
        role: 'cashier',
        status: 'active',
      }),
    );

    await assertSucceeds(
      updateDoc(doc(db, 'account_requests', 'ar-b-1'), {
        status: 'approved',
      }),
    );
  });

  it('allows legacy admin user doc without status field (legacy compatibility)', async () => {
    const db = dbWithClaims('legacy-owner-uid', { uid: 'legacy-owner-uid' });

    await assertSucceeds(
      setDoc(doc(db, 'tenant_users', 'legacy-created'), {
        tenantId: TENANT_A,
        userId: 'legacy-target',
        role: 'cashier',
        status: 'active',
      }),
    );
  });

  it('keeps tenant catalog public read while non-catalog remains private', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenants', TENANT_A, 'public_products', 'sku-1'), { name: 'Test' });
      await setDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-1'), { name: 'Private' });
    });

    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(anonDb, 'tenants', TENANT_A, 'public_products', 'sku-1')));
    await assertFails(getDoc(doc(anonDb, 'tenants', TENANT_A, 'products', 'sku-1')));
  });

  it('denies self-create escalating to isAdmin=true', async () => {
    const uid = 'final-customer-malicious';
    const db = dbWithClaims(uid, { uid });

    await assertFails(
      setDoc(doc(db, 'users', uid), {
        email: 'final-customer-malicious@example.com',
        tenantId: TENANT_A,
        role: 'viewer',
        status: 'active',
        accountType: 'final_customer',
        isAdmin: true,
        isSuperAdmin: false,
      }),
    );
  });

  it('allows valid final customer self create', async () => {
    const uid = 'final-customer-valid';
    const db = dbWithClaims(uid, { uid });

    await assertSucceeds(
      setDoc(doc(db, 'users', uid), {
        email: 'final-customer-valid@example.com',
        tenantId: TENANT_A,
        role: 'viewer',
        status: 'active',
        accountType: 'final_customer',
        isAdmin: false,
        isSuperAdmin: false,
      }),
    );

    assert.ok(true);
  });
});
