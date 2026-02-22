const { readFileSync } = require('node:fs');
const { before, beforeEach, after, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, deleteDoc } = require('firebase/firestore');

const PROJECT_ID = 'sellia-firestore-rules-tests';
const TENANT_ID = 'tenant-a';

let testEnv;

const ADMIN_ROLES = ['owner', 'admin'];
const NON_ADMIN_ROLES = ['manager', 'cashier', 'viewer'];

function roleUserId(role) {
  return `${role}-uid`;
}

async function seedRoleUser(role) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'users', roleUserId(role)), {
      email: `${role}@example.com`,
      tenantId: TENANT_ID,
      role,
      status: 'active',
      accountType: 'store_owner',
      isAdmin: false,
      isSuperAdmin: false,
    });
  });
}

async function seedAdminTargets() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, 'tenant_users', 'tu-1'), {
      tenantId: TENANT_ID,
      userId: 'existing-user',
      role: 'cashier',
      status: 'active',
    });

    await setDoc(doc(db, 'users', 'managed-user'), {
      email: 'managed@example.com',
      tenantId: TENANT_ID,
      role: 'cashier',
      status: 'active',
      accountType: 'store_owner',
      isAdmin: false,
      isSuperAdmin: false,
    });

    await setDoc(doc(db, 'account_requests', 'ar-1'), {
      tenantId: TENANT_ID,
      status: 'pending',
      requestedRole: 'cashier',
      requestedBy: 'requester',
    });
  });
}

async function seedTenantForBootstrap(tenantId, ownerUid) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'tenants', tenantId), {
      id: tenantId,
      ownerUid,
    });
  });
}

function roleDb(role) {
  return testEnv
    .authenticatedContext(roleUserId(role), {
      uid: roleUserId(role),
      email: `${role}@example.com`,
    })
    .firestore();
}

describe('firestore.rules - tenant user management policy', () => {
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
      await seedRoleUser(role);
    }
    await seedAdminTargets();
  });

  after(async () => {
    await testEnv.cleanup();
  });

  for (const role of ADMIN_ROLES) {
    it(`${role} can perform administrative writes on /tenant_users, /users and /account_requests`, async () => {
      const db = roleDb(role);

      await assertSucceeds(
        setDoc(doc(db, 'tenant_users', `${role}-created-tu`), {
          tenantId: TENANT_ID,
          userId: `created-by-${role}`,
          role: 'cashier',
          status: 'active',
        }),
      );

      await assertSucceeds(
        setDoc(doc(db, 'users', `${role}-created-user`), {
          email: `${role}-created@example.com`,
          tenantId: TENANT_ID,
          role: 'cashier',
          status: 'active',
          accountType: 'store_owner',
          isAdmin: false,
          isSuperAdmin: false,
        }),
      );

      await assertSucceeds(
        updateDoc(doc(db, 'account_requests', 'ar-1'), {
          status: 'approved',
        }),
      );

      await assertSucceeds(
        deleteDoc(doc(db, 'tenant_users', 'tu-1')),
      );

      await assertSucceeds(
        deleteDoc(doc(db, 'users', 'managed-user')),
      );

      await assertSucceeds(
        deleteDoc(doc(db, 'account_requests', 'ar-1')),
      );
    });
  }

  it('viewer without superAdmin claim cannot perform administrative writes', async () => {
    const db = dbWithClaims('viewer-claimless', { superAdmin: false });

    await assertFails(
      setDoc(doc(db, 'tenant_users', 'viewer-claimless-tu'), {
        tenantId: TENANT_ID,
        userId: 'viewer-claimless-target',
        role: 'cashier',
        status: 'active',
      }),
    );
  });

  it('authenticated user with superAdmin claim can perform administrative writes', async () => {
    const db = dbWithClaims('super-admin-uid', { superAdmin: true });

    await assertSucceeds(
      setDoc(doc(db, 'tenant_users', 'super-admin-tu'), {
        tenantId: TENANT_ID,
        userId: 'super-admin-target',
        role: 'cashier',
        status: 'active',
      }),
    );

    await assertSucceeds(
      setDoc(doc(db, 'users', 'super-admin-created-user'), {
        email: 'super-admin-created@example.com',
        tenantId: TENANT_ID,
        role: 'cashier',
        status: 'active',
        accountType: 'store_owner',
        isAdmin: false,
        isSuperAdmin: false,
      }),
    );

    await assertSucceeds(
      updateDoc(doc(db, 'account_requests', 'ar-1'), {
        status: 'approved',
      }),
    );
  });


  for (const role of NON_ADMIN_ROLES) {
    it(`${role} is denied for administrative writes on /tenant_users, /users and /account_requests`, async () => {
      const db = roleDb(role);

      await assertFails(
        setDoc(doc(db, 'tenant_users', `${role}-created-tu`), {
          tenantId: TENANT_ID,
          userId: `created-by-${role}`,
          role: 'cashier',
          status: 'active',
        }),
      );

      await assertFails(
        setDoc(doc(db, 'users', `${role}-created-user`), {
          email: `${role}-created@example.com`,
          tenantId: TENANT_ID,
          role: 'cashier',
          status: 'active',
          accountType: 'store_owner',
          isAdmin: false,
          isSuperAdmin: false,
        }),
      );

      await assertFails(
        updateDoc(doc(db, 'account_requests', 'ar-1'), {
          status: 'approved',
        }),
      );

      await assertFails(
        deleteDoc(doc(db, 'tenant_users', 'tu-1')),
      );

      await assertFails(
        deleteDoc(doc(db, 'users', 'managed-user')),
      );

      await assertFails(
        deleteDoc(doc(db, 'account_requests', 'ar-1')),
      );

      assert.ok(true);
    });
  }

  it('denies final customer self create with isAdmin=true', async () => {
    const uid = 'final-customer-malicious';
    const db = testEnv
      .authenticatedContext(uid, {
        uid,
        email: 'final-customer-malicious@example.com',
      })
      .firestore();

    await assertFails(
      setDoc(doc(db, 'users', uid), {
        email: 'final-customer-malicious@example.com',
        tenantId: TENANT_ID,
        role: 'viewer',
        status: 'active',
        accountType: 'final_customer',
        isAdmin: true,
        isSuperAdmin: false,
      }),
    );
  });

  it('denies owner bootstrap when trying to set isSuperAdmin=true', async () => {
    const uid = 'owner-bootstrap-malicious';
    const ownerTenantId = 'tenant-owner-bootstrap';
    await seedTenantForBootstrap(ownerTenantId, uid);

    const db = testEnv
      .authenticatedContext(uid, {
        uid,
        email: 'owner-bootstrap-malicious@example.com',
      })
      .firestore();

    await assertFails(
      setDoc(doc(db, 'users', uid), {
        email: 'owner-bootstrap-malicious@example.com',
        tenantId: ownerTenantId,
        role: 'owner',
        status: 'active',
        accountType: 'store_owner',
        isAdmin: false,
        isSuperAdmin: true,
      }),
    );
  });

  it('allows valid final customer self create without sensitive fields', async () => {
    const uid = 'final-customer-valid';
    const db = testEnv
      .authenticatedContext(uid, {
        uid,
        email: 'final-customer-valid@example.com',
      })
      .firestore();

    await assertSucceeds(
      setDoc(doc(db, 'users', uid), {
        email: 'final-customer-valid@example.com',
        tenantId: TENANT_ID,
        role: 'viewer',
        status: 'active',
        accountType: 'final_customer',
        isAdmin: false,
        isSuperAdmin: false,
      }),
    );
  });
});
