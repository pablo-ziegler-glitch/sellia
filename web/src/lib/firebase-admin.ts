import { initializeApp, getApps, cert, type App } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

function getApp(): App {
  if (getApps().length > 0) return getApps()[0];

  // In Firebase App Hosting the default credentials are auto-provided.
  // For local dev set GOOGLE_APPLICATION_CREDENTIALS env var.
  return initializeApp();
}

export function getDb(): Firestore {
  return getFirestore(getApp());
}
