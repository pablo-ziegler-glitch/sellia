import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  browserLocalPersistence,
  setPersistence,
  signInWithEmailAndPassword,
  signInWithPopup,
  onAuthStateChanged,
  signOut
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  collection,
  doc,
  getDoc,
  getFirestore,
  limit,
  onSnapshot,
  orderBy,
  query
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-functions.js";
import { INTERNAL_ROLES, hasRouteAccess, rolePermissions } from "./permissions.js";

const INACTIVITY_LIMIT_MS = 30 * 60 * 1000;
const TOKEN_REFRESH_MS = 50 * 60 * 1000;
const DEFAULT_ROUTE = "#/dashboard";

const el = {
  authPanel: document.getElementById("authPanel"),
  appPanel: document.getElementById("appPanel"),
  loginForm: document.getElementById("loginForm"),
  emailInput: document.getElementById("email"),
  passwordInput: document.getElementById("password"),
  googleBtn: document.getElementById("googleLogin"),
  logoutBtn: document.getElementById("logoutButton"),
  authError: document.getElementById("authError"),
  sessionBanner: document.getElementById("sessionBanner"),
  tenantBadge: document.getElementById("tenantBadge"),
  roleBadge: document.getElementById("roleBadge"),
  statusBadge: document.getElementById("statusBadge"),
  permissionsList: document.getElementById("permissionsList"),
  viewTitle: document.getElementById("viewTitle"),
  viewDescription: document.getElementById("viewDescription"),
  deniedState: document.getElementById("deniedState"),
  missingTenantState: document.getElementById("missingTenantState"),
  backupPanel: document.getElementById("backupPanel"),
  backupReasonInput: document.getElementById("backupReasonInput"),
  requestBackupButton: document.getElementById("requestBackupButton"),
  backupMessage: document.getElementById("backupMessage"),
  backupRequestsBody: document.getElementById("backupRequestsBody"),
  tenantPolicyPanel: document.getElementById("tenantPolicyPanel"),
  tenantActivationModeSelect: document.getElementById("tenantActivationModeSelect"),
  saveTenantPolicyButton: document.getElementById("saveTenantPolicyButton"),
  tenantPolicyMessage: document.getElementById("tenantPolicyMessage")
};

const routeViews = {
  "#/dashboard": {
    title: "Panel de uso",
    description: "Monitoreá métricas operativas del tenant con foco en disponibilidad y costos."
  },
  "#/settings/pricing": {
    title: "Configuración de pricing",
    description: "Definí reglas de precio y márgenes para el equipo interno, sin mezclar con UX pública."
  },
  "#/settings/marketing": {
    title: "Configuración de marketing",
    description: "Gestioná campañas y contenido promocional de forma centralizada para el backoffice."
  },
  "#/settings/users": {
    title: "Gestión de usuarios",
    description: "Administrá altas/bajas de operadores internos y su rol por tenant."
  },
  "#/settings/cloud-services": {
    title: "Servicios cloud",
    description: "Revisá estado de integraciones Firebase, storage y automatizaciones internas."
  },
  "#/maintenance": {
    title: "Mantenimiento",
    description: "Sección reservada para tareas operativas. Sin venta, sin carrito y sin catálogo público."
  }
};

const appState = {
  firebaseAuth: null,
  firestore: null,
  cloudFunctions: null,
  currentUser: null,
  profile: null,
  inactivityTimerId: null,
  refreshTimerId: null,
  backupRequestsUnsubscribe: null
};

bootstrap();

async function bootstrap() {
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  const firebaseConfig = window.STORE_CONFIG?.firebase || {};

  if (!firebaseConfig.apiKey || !firebaseConfig.projectId || !firebaseConfig.appId) {
    setAuthError("Falta configuración de Firebase para iniciar el backoffice.");
    return;
  }

  const app = initializeApp(firebaseConfig, "sellia-admin-web");
  const auth = getAuth(app);
  const db = getFirestore(app);
  const cloudFunctions = getFunctions(app);

  appState.firebaseAuth = auth;
  appState.firestore = db;
  appState.cloudFunctions = cloudFunctions;

  await setPersistence(auth, browserLocalPersistence);

  wireEvents();

  onAuthStateChanged(auth, async (user) => {
    try {
      if (!user) {
        clearSessionState();
        switchToAuth();
        return;
      }

      const profile = await loadProfile(db, user.uid);
      const validation = validateProfile(profile);
      if (!validation.ok) {
        clearSessionState();
        switchToApp();
        showMissingTenantState(validation.message);
        await safeLogout(`Sesión invalidada: ${validation.message}`);
        return;
      }

      appState.currentUser = user;
      appState.profile = profile;

      renderSession(profile);
      await loadTenantOnboardingPolicy();
      switchToApp();
      hideHardStates();
      syncRouteWithPermissions();
      startInactivityGuard();
      startTokenRefresh();
    } catch (error) {
      setAuthError(parseAuthError(error));
    }
  });
}

function wireEvents() {
  el.loginForm.addEventListener("submit", onEmailLogin);
  el.googleBtn.addEventListener("click", onGoogleLogin);
  el.logoutBtn.addEventListener("click", () => safeLogout("Sesión cerrada correctamente."));
  el.requestBackupButton.addEventListener("click", onRequestBackupNow);
  el.saveTenantPolicyButton?.addEventListener("click", onSaveTenantOnboardingPolicy);
  window.addEventListener("hashchange", syncRouteWithPermissions);

  ["mousemove", "keydown", "click", "scroll", "touchstart"].forEach((eventName) => {
    window.addEventListener(eventName, resetInactivityTimer, { passive: true });
  });
}

async function onEmailLogin(event) {
  event.preventDefault();
  setAuthError("");

  const email = el.emailInput.value.trim();
  const password = el.passwordInput.value.trim();
  if (!email || !password) {
    setAuthError("Ingresá email y contraseña.");
    return;
  }

  try {
    await signInWithEmailAndPassword(appState.firebaseAuth, email, password);
  } catch (error) {
    setAuthError(parseAuthError(error));
  }
}

async function onGoogleLogin() {
  setAuthError("");
  try {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(appState.firebaseAuth, provider);
  } catch (error) {
    setAuthError(parseAuthError(error));
  }
}

async function loadProfile(db, uid) {
  const snapshot = await getDoc(doc(db, "users", uid));
  if (!snapshot.exists()) {
    throw new Error("No existe perfil de usuario en users/{uid}.");
  }
  return snapshot.data();
}

function validateProfile(profile) {
  const tenantId = (profile?.tenantId || "").trim();
  const role = (profile?.role || "").trim();
  const status = (profile?.status || "").trim().toLowerCase();

  if (!tenantId) return { ok: false, message: "Usuario sin tenant asignado." };
  if (!INTERNAL_ROLES.has(role)) return { ok: false, message: "Rol no habilitado para backoffice." };
  if (status !== "active") return { ok: false, message: "Usuario inactivo o bloqueado." };

  return { ok: true };
}

function renderSession(profile) {
  el.tenantBadge.textContent = profile.tenantId;
  el.roleBadge.textContent = profile.role;
  el.statusBadge.textContent = profile.status;

  const permissions = rolePermissions(profile.role);
  el.permissionsList.innerHTML = permissions.length
    ? permissions.map((permission) => `<li>${permission}</li>`).join("")
    : "<li>Sin permisos internos.</li>";
}

function syncRouteWithPermissions() {
  if (!appState.profile) return;

  const currentRoute = window.location.hash || DEFAULT_ROUTE;
  const hasAccess = hasRouteAccess(appState.profile.role, currentRoute);
  const view = routeViews[currentRoute] || routeViews[DEFAULT_ROUTE];

  if (!hasAccess) {
    showDeniedState(`No tenés permisos para acceder a ${currentRoute.replace("#/", "")}.`);
    if (!routeViews[currentRoute]) {
      window.location.hash = DEFAULT_ROUTE;
    }
    return;
  }

  hideDeniedState();
  el.viewTitle.textContent = view.title;
  el.viewDescription.textContent = view.description;

  const canManageBackups = ["owner", "admin"].includes(appState.profile.role);
  const isCloudServicesRoute = currentRoute === "#/settings/cloud-services";
  const canManageOnboardingPolicy = appState.profile.role === "owner";
  el.backupPanel.hidden = !(canManageBackups && isCloudServicesRoute);
  el.tenantPolicyPanel.hidden = !(canManageOnboardingPolicy && isCloudServicesRoute);

  if (el.backupPanel.hidden) {
    stopBackupRequestsListener();
  } else {
    startBackupRequestsListener();
  }
}

function startInactivityGuard() {
  clearTimeout(appState.inactivityTimerId);
  appState.inactivityTimerId = setTimeout(() => {
    safeLogout("Sesión expirada por inactividad.");
  }, INACTIVITY_LIMIT_MS);
}

function resetInactivityTimer() {
  if (!appState.currentUser) return;
  startInactivityGuard();
}

function startTokenRefresh() {
  clearInterval(appState.refreshTimerId);
  appState.refreshTimerId = setInterval(async () => {
    if (!appState.currentUser) return;
    try {
      await appState.currentUser.getIdToken(true);
      setSessionBanner("Token renovado automáticamente.");
    } catch (error) {
      await safeLogout("No se pudo renovar token. Iniciá sesión nuevamente.");
    }
  }, TOKEN_REFRESH_MS);
}

async function safeLogout(message) {
  try {
    if (appState.firebaseAuth?.currentUser) {
      await signOut(appState.firebaseAuth);
    }
  } finally {
    clearSessionState();
    setSessionBanner(message);
    switchToAuth();
  }
}

function clearSessionState() {
  clearTimeout(appState.inactivityTimerId);
  clearInterval(appState.refreshTimerId);
  appState.currentUser = null;
  appState.profile = null;
  if (typeof appState.backupRequestsUnsubscribe === "function") {
    appState.backupRequestsUnsubscribe();
  }
  appState.backupRequestsUnsubscribe = null;
  el.permissionsList.innerHTML = "";
  el.backupPanel.hidden = true;
  if (el.tenantPolicyPanel) el.tenantPolicyPanel.hidden = true;
  el.backupRequestsBody.innerHTML = '<tr><td colspan="6">Sin solicitudes recientes.</td></tr>';
  setTenantPolicyMessage("");
}

function showDeniedState(message) {
  el.deniedState.hidden = false;
  el.deniedState.querySelector("p").textContent = message;
}

function hideDeniedState() {
  el.deniedState.hidden = true;
}

function showMissingTenantState(message) {
  el.missingTenantState.hidden = false;
  el.missingTenantState.querySelector("p").textContent = message;
}

function hideHardStates() {
  hideDeniedState();
  el.missingTenantState.hidden = true;
}

function switchToAuth() {
  el.authPanel.hidden = false;
  el.appPanel.hidden = true;
}

function switchToApp() {
  el.authPanel.hidden = true;
  el.appPanel.hidden = false;
}

function setAuthError(message) {
  el.authError.textContent = message;
}

function setSessionBanner(message) {
  el.sessionBanner.textContent = message;
}


function stopBackupRequestsListener() {
  if (typeof appState.backupRequestsUnsubscribe === "function") {
    appState.backupRequestsUnsubscribe();
  }
  appState.backupRequestsUnsubscribe = null;
}

function startBackupRequestsListener() {
  if (appState.backupRequestsUnsubscribe || !appState.profile) return;

  const requestsQuery = query(
    collection(appState.firestore, "tenant_backups", appState.profile.tenantId, "requests"),
    orderBy("createdAt", "desc"),
    limit(12)
  );

  appState.backupRequestsUnsubscribe = onSnapshot(
    requestsQuery,
    (snapshot) => {
      if (snapshot.empty) {
        el.backupRequestsBody.innerHTML = "<tr><td colspan=\"6\">Sin solicitudes recientes.</td></tr>";
        return;
      }

      el.backupRequestsBody.innerHTML = snapshot.docs
        .map((requestDoc) => {
          const row = requestDoc.data();
          const createdAtMillis = row.createdAt?.toMillis?.() || null;
          const createdAtLabel = createdAtMillis
            ? new Date(createdAtMillis).toLocaleString()
            : "-";
          const status = (row.status || "queued").toString();
          const createdByUid = (row.createdByUid || "-").toString();
          const docCount = Number.isFinite(row.docCount) ? row.docCount : "-";
          const errorMessage = (row.errorMessage || "-").toString();

          return `
            <tr>
              <td>${requestDoc.id}</td>
              <td>${status}</td>
              <td>${createdAtLabel}</td>
              <td>${createdByUid}</td>
              <td>${docCount}</td>
              <td>${errorMessage}</td>
            </tr>
          `;
        })
        .join("");
    },
    (error) => {
      setBackupMessage(`No se pudo cargar historial de backups: ${error.message || error}`);
    }
  );
}

async function onRequestBackupNow() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setBackupMessage("Acción permitida solo para owner/admin.");
    return;
  }

  const reason = el.backupReasonInput.value.trim();
  if (reason.length < 6) {
    setBackupMessage("Indicá un motivo de al menos 6 caracteres.");
    return;
  }

  try {
    el.requestBackupButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "requestTenantBackup");
    const response = await callable({
      tenantId: appState.profile.tenantId,
      reason
    });

    const deduplicated = response?.data?.deduplicated === true;
    const requestId = response?.data?.requestId || "-";
    setBackupMessage(
      deduplicated
        ? `Ya existía una solicitud reciente (${requestId}). Se evitó un duplicado.`
        : `Solicitud creada (${requestId}).`
    );
    el.backupReasonInput.value = "";
  } catch (error) {
    setBackupMessage(parseAuthError(error));
  } finally {
    el.requestBackupButton.disabled = false;
  }
}

function setBackupMessage(message) {
  el.backupMessage.textContent = message || "";
}


async function loadTenantOnboardingPolicy() {
  if (!appState.profile || appState.profile.role !== "owner") return;
  try {
    const callable = httpsCallable(appState.cloudFunctions, "getTenantOnboardingPolicy");
    const response = await callable({});
    const mode = response?.data?.tenantActivationMode === "manual" ? "manual" : "auto";
    if (el.tenantActivationModeSelect) {
      el.tenantActivationModeSelect.value = mode;
    }
    setTenantPolicyMessage(
      mode === "manual"
        ? "Modo actual: aprobación manual para nuevas tiendas."
        : "Modo actual: activación automática para nuevas tiendas."
    );
  } catch (error) {
    setTenantPolicyMessage(`No se pudo cargar política: ${parseAuthError(error)}`);
  }
}

async function onSaveTenantOnboardingPolicy() {
  if (!appState.profile || appState.profile.role !== "owner") {
    setTenantPolicyMessage("Solo owner puede cambiar esta política global.");
    return;
  }

  const mode = el.tenantActivationModeSelect?.value === "manual" ? "manual" : "auto";
  try {
    el.saveTenantPolicyButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "setTenantOnboardingPolicy");
    await callable({ tenantActivationMode: mode });
    setTenantPolicyMessage(
      mode === "manual"
        ? "Guardado. Nuevas tiendas requerirán aprobación manual."
        : "Guardado. Nuevas tiendas quedarán activas por defecto."
    );
  } catch (error) {
    setTenantPolicyMessage(parseAuthError(error));
  } finally {
    el.saveTenantPolicyButton.disabled = false;
  }
}

function setTenantPolicyMessage(message) {
  if (!el.tenantPolicyMessage) return;
  el.tenantPolicyMessage.textContent = message || "";
}

function parseAuthError(error) {
  if (!error) return "Error de autenticación desconocido.";

  const map = {
    "auth/invalid-credential": "Credenciales inválidas.",
    "auth/popup-closed-by-user": "Login con Google cancelado.",
    "auth/unauthorized-domain": "Dominio no autorizado en Firebase Auth.",
    "auth/network-request-failed": "Sin conexión de red.",
    "functions/permission-denied": "No tenés permisos para ejecutar esta acción.",
    "functions/invalid-argument": "Faltan datos obligatorios para ejecutar la acción.",
    "functions/unauthenticated": "Tu sesión expiró. Iniciá sesión nuevamente."
  };

  return map[error.code] || error.message || "No se pudo iniciar sesión.";
}
