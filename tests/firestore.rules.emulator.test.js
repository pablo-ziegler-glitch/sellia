const { readFileSync } = require('node:fs');
const { before, beforeEach, after, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, collection, query, getDocs } = require('firebase/firestore');

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

  it('denies admin user flag in /users doc for cross-tenant admin writes without membership', async () => {
    await seedUser('legacy-admin-flag-uid', {
      role: 'viewer',
      tenantId: TENANT_A,
      isAdmin: true,
      isSuperAdmin: false,
    });

    const db = dbWithClaims('legacy-admin-flag-uid', {
      uid: 'legacy-admin-flag-uid',
      role: 'viewer',
      tenantId: TENANT_A,
    });

    await assertFails(
      setDoc(doc(db, 'tenant_users', 'legacy-admin-cross-tenant'), {
        tenantId: TENANT_B,
        userId: 'cross-tenant-target',
        role: 'cashier',
        status: 'active',
      }),
    );
  });

  it('allows super admin user flag in /users doc to update store requests without custom claims', async () => {
    await seedUser('legacy-super-admin-flag-uid', {
      role: 'viewer',
      tenantId: TENANT_A,
      isAdmin: false,
      isSuperAdmin: true,
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'store_requests', 'sr-1'), {
        userId: 'some-user',
        status: 'pending',
      });
    });

    const db = dbWithClaims('legacy-super-admin-flag-uid', {
      uid: 'legacy-super-admin-flag-uid',
      role: 'viewer',
      tenantId: TENANT_A,
    });

    await assertSucceeds(
      updateDoc(doc(db, 'store_requests', 'sr-1'), {
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

  it('denies admin claim reading tenant config query without explicit tenant membership', async () => {
    await seedUser('claim-admin-uid', {
      role: 'viewer',
      tenantId: '',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenants', TENANT_A, 'config', 'development_options'), {
        enabled: true,
      });
    });

    const db = dbWithClaims('claim-admin-uid', {
      uid: 'claim-admin-uid',
      admin: true,
      role: 'viewer',
    });

    const configQuery = query(collection(db, 'tenants', TENANT_A, 'config'));
    await assertFails(getDocs(configQuery));
  });

  it('allows owner role from /users doc to perform tenant admin writes without admin claim', async () => {
    await seedUser('owner-no-claim-uid', {
      role: 'owner',
      tenantId: TENANT_A,
      isAdmin: false,
      isSuperAdmin: false,
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenant_users', `${TENANT_A}_owner-no-claim-uid`), {
        tenantId: TENANT_A,
        userId: 'owner-no-claim-uid',
        role: 'owner',
        status: 'active',
      });
    });

    const db = dbWithClaims('owner-no-claim-uid', {
      uid: 'owner-no-claim-uid',
      role: 'viewer',
      tenantId: TENANT_A,
    });

    await assertSucceeds(
      setDoc(doc(db, 'tenant_users', 'owner-no-claim-created'), {
        tenantId: TENANT_A,
        userId: 'owner-managed-user',
        role: 'cashier',
        status: 'active',
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



  it('allows tenant membership by email-based tenant_users id fallback', async () => {
    await seedUser('email-member-uid', {
      role: 'viewer',
      tenantId: '',
      email: 'email-member@example.com',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenant_users', `${TENANT_A}_email-member@example.com`), {
        tenantId: TENANT_A,
        email: 'email-member@example.com',
        role: 'viewer',
        status: 'active',
      });
      await setDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-email-member'), { name: 'Private' });
    });

    const db = dbWithClaims('email-member-uid', {
      uid: 'email-member-uid',
      email: 'email-member@example.com',
      role: 'viewer',
    });

    await assertSucceeds(getDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-email-member')));
  });

  it('allows tenant membership by legacy bare uid tenant_users id', async () => {
    await seedUser('legacy-bare-uid-member', {
      role: 'viewer',
      tenantId: '',
      email: 'legacy-bare-uid@example.com',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenant_users', 'legacy-bare-uid-member'), {
        tenantId: TENANT_A,
        uid: 'legacy-bare-uid-member',
        role: 'viewer',
        status: 'active',
      });
      await setDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-legacy-bare-uid'), { name: 'Private legacy uid' });
    });

    const db = dbWithClaims('legacy-bare-uid-member', {
      uid: 'legacy-bare-uid-member',
      email: 'legacy-bare-uid@example.com',
      role: 'viewer',
    });

    await assertSucceeds(getDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-legacy-bare-uid')));
  });

  it('allows tenant membership by legacy bare email tenant_users id', async () => {
    await seedUser('legacy-bare-email-member', {
      role: 'viewer',
      tenantId: '',
      email: 'legacy-bare-email@example.com',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenant_users', 'legacy-bare-email@example.com'), {
        tenantId: TENANT_A,
        email: 'legacy-bare-email@example.com',
        role: 'viewer',
        status: 'active',
      });
      await setDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-legacy-bare-email'), { name: 'Private legacy email' });
    });

    const db = dbWithClaims('legacy-bare-email-member', {
      uid: 'legacy-bare-email-member',
      email: 'legacy-bare-email@example.com',
      role: 'viewer',
    });

    await assertSucceeds(getDoc(doc(db, 'tenants', TENANT_A, 'products', 'sku-legacy-bare-email')));
  });

  it('allows tenant membership when tenantId exists in users.tenantIds array', async () => {
    await seedUser('multi-tenant-uid', {
      role: 'viewer',
      tenantId: '',
      tenantIds: [TENANT_A, TENANT_B],
      email: 'multi-tenant@example.com',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenants', TENANT_B, 'products', 'sku-multi-tenant'), { name: 'Private B' });
    });

    const db = dbWithClaims('multi-tenant-uid', {
      uid: 'multi-tenant-uid',
      email: 'multi-tenant@example.com',
      role: 'viewer',
    });

    await assertSucceeds(getDoc(doc(db, 'tenants', TENANT_B, 'products', 'sku-multi-tenant')));
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

  it('allows tenant ownerUid to read own tenant doc even without tenant_users membership', async () => {
    await seedUser('owner-direct-uid', {
      role: 'viewer',
      tenantId: '',
      email: 'owner-direct@example.com',
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'tenants', 'tenant-owner-direct'), {
        id: 'tenant-owner-direct',
        ownerUid: 'owner-direct-uid',
      });
    });

    const db = dbWithClaims('owner-direct-uid', {
      uid: 'owner-direct-uid',
      role: 'viewer',
      email: 'owner-direct@example.com',
    });

    await assertSucceeds(getDoc(doc(db, 'tenants', 'tenant-owner-direct')));
  });
});
