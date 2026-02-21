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
import { doc, getDoc, getFirestore } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";
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
  missingTenantState: document.getElementById("missingTenantState")
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
  currentUser: null,
  profile: null,
  inactivityTimerId: null,
  refreshTimerId: null
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

  appState.firebaseAuth = auth;
  appState.firestore = db;

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
  el.permissionsList.innerHTML = "";
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

function parseAuthError(error) {
  if (!error) return "Error de autenticación desconocido.";

  const map = {
    "auth/invalid-credential": "Credenciales inválidas.",
    "auth/popup-closed-by-user": "Login con Google cancelado.",
    "auth/unauthorized-domain": "Dominio no autorizado en Firebase Auth.",
    "auth/network-request-failed": "Sin conexión de red."
  };

  return map[error.code] || error.message || "No se pudo iniciar sesión.";
}
