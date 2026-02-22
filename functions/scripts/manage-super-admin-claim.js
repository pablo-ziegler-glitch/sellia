#!/usr/bin/env node

const admin = require('firebase-admin');

function parseArgs(argv) {
  const args = { action: null, uid: null, email: null };
  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--grant') args.action = 'grant';
    else if (token === '--revoke') args.action = 'revoke';
    else if (token === '--uid') args.uid = argv[++i];
    else if (token === '--email') args.email = argv[++i];
  }
  return args;
}

async function resolveUserIdentifier(auth, uid, email) {
  if (uid) return auth.getUser(uid);
  if (email) return auth.getUserByEmail(email);
  throw new Error('Debés enviar --uid <uid> o --email <email>.');
}

async function main() {
  const { action, uid, email } = parseArgs(process.argv);
  if (!action) {
    throw new Error('Debés indicar --grant o --revoke.');
  }

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const auth = admin.auth();
  const user = await resolveUserIdentifier(auth, uid, email);
  const currentClaims = user.customClaims || {};
  const nextClaims = {
    ...currentClaims,
    superAdmin: action === 'grant',
  };

  await auth.setCustomUserClaims(user.uid, nextClaims);

  console.log(JSON.stringify({
    status: 'ok',
    action,
    uid: user.uid,
    email: user.email || null,
    previousClaims: currentClaims,
    nextClaims,
    note: 'El usuario debe refrescar su ID token para ver el claim aplicado.',
  }, null, 2));
}

main().catch((error) => {
  console.error('[manage-super-admin-claim] error:', error.message || error);
  process.exit(1);
});
