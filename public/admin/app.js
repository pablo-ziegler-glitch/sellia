// NOTE: ES module imports no soportan el atributo integrity (SRI) de forma nativa.
// Para agregar SRI a estas dependencias, migrar a un bundler (Vite/Rollup) o self-hostear los SDK.
// Versión fijada deliberadamente: firebase@10.12.5. Actualizar junto con la integridad del hash.
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
import { hasRouteAccess, isInternalRole, normalizeInternalRole, rolePermissions } from "./permissions.js";

const INACTIVITY_LIMIT_MS = 30 * 60 * 1000;
const TOKEN_REFRESH_MS = 50 * 60 * 1000;
// Límite absoluto de sesión. El admin puede configurarlo en Firestore
// (platform/config → session.maxSessionHours, rango 1–24h). Default: 8h.
// Una vez vencido este límite, el usuario debe autenticarse nuevamente
// independientemente de si estuvo activo o no (inactivity timer aparte).
const DEFAULT_SESSION_MAX_MS = 8 * 60 * 60 * 1000;
const SESSION_CHECK_INTERVAL_MS = 60 * 1000; // verificar cada 60 segundos
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
  defaultAdminPermissions: document.getElementById("defaultAdminPermissions"),
  sessionBanner: document.getElementById("sessionBanner"),
  tenantBadge: document.getElementById("tenantBadge"),
  roleBadge: document.getElementById("roleBadge"),
  statusBadge: document.getElementById("statusBadge"),
  permissionsPanel: document.getElementById("permissionsPanel"),
  permissionsRoleBadge: document.getElementById("permissionsRoleBadge"),
  permissionsTagList: document.getElementById("permissionsTagList"),
  viewTitle: document.getElementById("viewTitle"),
  viewDescription: document.getElementById("viewDescription"),
  deniedState: document.getElementById("deniedState"),
  missingTenantState: document.getElementById("missingTenantState"),
  dashboardPanel: document.getElementById("dashboardPanel"),
  dashboardRetryButton: document.getElementById("dashboardRetryButton"),
  dashboardErrorRetryButton: document.getElementById("dashboardErrorRetryButton"),
  dashboardLoading: document.getElementById("dashboardLoading"),
  dashboardEmpty: document.getElementById("dashboardEmpty"),
  dashboardError: document.getElementById("dashboardError"),
  dashboardFeedback: document.getElementById("dashboardFeedback"),
  dashboardContent: document.getElementById("dashboardContent"),
  dashboardPeriod: document.getElementById("dashboardPeriod"),
  dashboardReads: document.getElementById("dashboardReads"),
  dashboardWrites: document.getElementById("dashboardWrites"),
  dashboardStorage: document.getElementById("dashboardStorage"),
  dashboardFunctions: document.getElementById("dashboardFunctions"),
  dashboardErrors: document.getElementById("dashboardErrors"),
  maintenancePanel: document.getElementById("maintenancePanel"),
  maintenanceRetryButton: document.getElementById("maintenanceRetryButton"),
  maintenanceErrorRetryButton: document.getElementById("maintenanceErrorRetryButton"),
  maintenanceLoading: document.getElementById("maintenanceLoading"),
  maintenanceEmpty: document.getElementById("maintenanceEmpty"),
  maintenanceError: document.getElementById("maintenanceError"),
  maintenanceFeedback: document.getElementById("maintenanceFeedback"),
  maintenanceCreateForm: document.getElementById("maintenanceCreateForm"),
  maintenanceCreateButton: document.getElementById("maintenanceCreateButton"),
  maintenanceTitleInput: document.getElementById("maintenanceTitleInput"),
  maintenancePriorityInput: document.getElementById("maintenancePriorityInput"),
  maintenanceBody: document.getElementById("maintenanceBody"),
  backupPanel: document.getElementById("backupPanel"),
  backupReasonInput: document.getElementById("backupReasonInput"),
  requestBackupButton: document.getElementById("requestBackupButton"),
  backupMessage: document.getElementById("backupMessage"),
  backupRequestsBody: document.getElementById("backupRequestsBody"),
  paymentsControlPanel: document.getElementById("paymentsControlPanel"),
  paymentsFlagsBody: document.getElementById("paymentsFlagsBody"),
  paymentsToggleScope: document.getElementById("paymentsToggleScope"),
  paymentsToggleEnabled: document.getElementById("paymentsToggleEnabled"),
  paymentsToggleReason: document.getElementById("paymentsToggleReason"),
  paymentsToggleButton: document.getElementById("paymentsToggleButton"),
  paymentsToggleMessage: document.getElementById("paymentsToggleMessage"),
  paymentsAuditBody: document.getElementById("paymentsAuditBody"),
  costDashboardPanel: document.getElementById("costDashboardPanel"),
  budgetTotalValue: document.getElementById("budgetTotalValue"),
  currentCostTotalValue: document.getElementById("currentCostTotalValue"),
  costDeltaValue: document.getElementById("costDeltaValue"),
  costByServiceBody: document.getElementById("costByServiceBody"),
  tenantPolicyPanel: document.getElementById("tenantPolicyPanel"),
  tenantActivationModeSelect: document.getElementById("tenantActivationModeSelect"),
  saveTenantPolicyButton: document.getElementById("saveTenantPolicyButton"),
  tenantPolicyMessage: document.getElementById("tenantPolicyMessage"),
  storeConfigPanel: document.getElementById("storeConfigPanel"),
  storeCurrentDomain: document.getElementById("storeCurrentDomain"),
  storeDomainInput: document.getElementById("storeDomainInput"),
  setStoreDomainButton: document.getElementById("setStoreDomainButton"),
  removeStoreDomainButton: document.getElementById("removeStoreDomainButton"),
  storeDomainMessage: document.getElementById("storeDomainMessage"),
  syncProductsButton: document.getElementById("syncProductsButton"),
  syncProductsMessage: document.getElementById("syncProductsMessage")
};

const routeViews = {
  "#/dashboard": {
    title: "Panel de uso",
    description: "Métricas reales del tenant obtenidas desde backend con control de permisos."
  },
  "#/settings/pricing": { title: "Configuración de pricing", description: "Módulo en migración." },
  "#/settings/marketing": { title: "Configuración de marketing", description: "Módulo en migración." },
  "#/settings/users": { title: "Gestión de usuarios", description: "Módulo en migración." },
  "#/settings/store": {
    title: "Tienda",
    description: "Dominio personalizado y sincronización del catálogo público."
  },
  "#/settings/cloud-services": {
    title: "Servicios cloud",
    description: "Gestión de backups y estado cloud del tenant."
  },
  "#/maintenance": {
    title: "Mantenimiento",
    description: "Tareas operativas multi-tenant con auditoría de cambios."
  },
  "#/permissions": {
    title: "Mis permisos",
    description: "Permisos activos asignados a tu perfil."
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
  // Watchdog del límite absoluto de sesión (8h por default, configurable por admin).
  absoluteSessionTimerId: null,
  // Duración máxima de sesión cargada desde Firestore. Default: 8h.
  sessionMaxMs: DEFAULT_SESSION_MAX_MS,
  backupRequestsUnsubscribe: null,
  paymentsFlagsUnsubscribe: null,
  paymentsAuditUnsubscribe: null,
  maintenanceTasksUnsubscribe: null,
  maintenanceTasks: []
};

bootstrap();

async function bootstrap() {
  renderDefaultAdminPermissions();
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  const firebaseConfig = window.STORE_CONFIG?.firebase || {};
  if (!firebaseConfig.apiKey || !firebaseConfig.projectId || !firebaseConfig.appId) {
    setAuthError("Falta configuración de Firebase para iniciar el backoffice.");
    return;
  }

  const app = initializeApp(firebaseConfig, "sellia-admin-web");
  appState.firebaseAuth = getAuth(app);
  appState.firestore = getFirestore(app);
  appState.cloudFunctions = getFunctions(app);
  await setPersistence(appState.firebaseAuth, browserLocalPersistence);
  wireEvents();

  onAuthStateChanged(appState.firebaseAuth, async (user) => {
    try {
      if (!user) {
        clearSessionState();
        switchToAuth();
        return;
      }

      // ── Verificar límite absoluto de sesión ─────────────────────────────────
      // lastSignInTime: timestamp real del último sign-in (no se actualiza con
      // token refreshes). Si el tiempo transcurrido supera sessionMaxMs, forzamos
      // logout para garantizar que el usuario re-autentique cada N horas.
      const lastSignInMs = user.metadata?.lastSignInTime
        ? new Date(user.metadata.lastSignInTime).getTime()
        : Date.now();
      const sessionAgeMs = Date.now() - lastSignInMs;

      // Cargar política de sesión configurada por el admin (una sola vez por login).
      if (!appState.currentUser) {
        await loadSessionPolicy(appState.firestore);
      }

      if (sessionAgeMs > appState.sessionMaxMs) {
        const hours = Math.round(appState.sessionMaxMs / (1000 * 60 * 60));
        await safeLogout(`Tu sesión expiró (límite de ${hours}h). Por favor, iniciá sesión nuevamente.`);
        return;
      }

      const profile = await loadProfile(appState.firestore, user.uid);
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
      await syncRouteWithPermissions();
      startInactivityGuard();
      startTokenRefresh();
      startAbsoluteSessionGuard(lastSignInMs);
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
  el.paymentsToggleButton.addEventListener("click", onApplyPaymentsToggle);
  el.setStoreDomainButton?.addEventListener("click", onSetStoreDomain);
  el.removeStoreDomainButton?.addEventListener("click", onRemoveStoreDomain);
  el.syncProductsButton?.addEventListener("click", onSyncProducts);
  el.saveTenantPolicyButton?.addEventListener("click", onSaveTenantOnboardingPolicy);
  el.dashboardRetryButton?.addEventListener("click", loadDashboard);
  el.dashboardErrorRetryButton?.addEventListener("click", loadDashboard);
  el.maintenanceRetryButton?.addEventListener("click", loadMaintenanceTasks);
  el.maintenanceErrorRetryButton?.addEventListener("click", loadMaintenanceTasks);
  el.maintenanceCreateForm?.addEventListener("submit", onCreateMaintenanceTask);
  window.addEventListener("hashchange", syncRouteWithPermissions);
  el.maintenanceBody?.addEventListener("click", onMaintenanceActions);

  ["mousemove", "keydown", "click", "scroll", "touchstart"].forEach((eventName) => {
    window.addEventListener(eventName, resetInactivityTimer, { passive: true });
  });
}

async function onEmailLogin(event) {
  event.preventDefault();
  setAuthError("");
  const email = el.emailInput.value.trim();
  const password = el.passwordInput.value.trim();
  if (!email || !password) return setAuthError("Ingresá email y contraseña.");
  try {
    await signInWithEmailAndPassword(appState.firebaseAuth, email, password);
  } catch (error) {
    setAuthError(parseAuthError(error));
  }
}

async function onGoogleLogin() {
  setAuthError("");
  try {
    await signInWithPopup(appState.firebaseAuth, new GoogleAuthProvider());
  } catch (error) {
    setAuthError(parseAuthError(error));
  }
}

async function loadProfile(db, uid) {
  const snapshot = await getDoc(doc(db, "users", uid));
  if (!snapshot.exists()) throw new Error("No existe perfil de usuario en users/{uid}.");
  return snapshot.data();
}

function validateProfile(profile) {
  const tenantId = (profile?.tenantId || "").trim();
  const role = normalizeInternalRole(profile?.role);
  const status = (profile?.status || "").trim().toLowerCase();
  if (!tenantId) return { ok: false, message: "Usuario sin tenant asignado." };
  if (!isInternalRole(role)) return { ok: false, message: "Rol no habilitado para backoffice." };
  if (status !== "active") return { ok: false, message: "Usuario inactivo o bloqueado." };
  return { ok: true };
}

function renderDefaultAdminPermissions() {
  if (!el.defaultAdminPermissions) return;
  const adminPermissions = rolePermissions("admin");
  el.defaultAdminPermissions.innerHTML = adminPermissions.length
    ? adminPermissions.map((permission) => `<li>${permission}</li>`).join("")
    : "<li>Sin permisos configurados.</li>";
}

function renderSession(profile) {
  el.tenantBadge.textContent = profile.tenantId;
  const normalizedRole = normalizeInternalRole(profile?.role);
  el.roleBadge.textContent = normalizedRole || "-";
  el.statusBadge.textContent = profile.status;
  const permissions = rolePermissions(normalizedRole);
  if (el.permissionsRoleBadge) {
    el.permissionsRoleBadge.textContent = normalizedRole || "-";
  }
  if (el.permissionsTagList) {
    el.permissionsTagList.innerHTML = permissions.length
      ? permissions.map((p) => `<span class="perm-tag">${p}</span>`).join("")
      : '<span class="perm-empty">Sin permisos asignados.</span>';
  }
}

async function syncRouteWithPermissions() {
  if (!appState.profile) return;
  const currentRoute = window.location.hash || DEFAULT_ROUTE;
  const hasAccess = hasRouteAccess(appState.profile.role, currentRoute);
  const view = routeViews[currentRoute] || routeViews[DEFAULT_ROUTE];

  if (!hasAccess) {
    showDeniedState(`No tenés permisos para acceder a ${currentRoute.replace("#/", "")}.`);
    if (!routeViews[currentRoute]) window.location.hash = DEFAULT_ROUTE;
    return;
  }

  hideDeniedState();
  el.viewTitle.textContent = view.title;
  el.viewDescription.textContent = view.description;
  toggleModulePanels(currentRoute);
  document.querySelectorAll(".nav-item[data-route]").forEach((link) => {
    link.classList.toggle("active", link.dataset.route === currentRoute);
  });

  if (currentRoute !== "#/maintenance") {
    stopMaintenanceTasksListener();
  }

  if (currentRoute === "#/dashboard") {
    await loadDashboard();
  }
  if (currentRoute === "#/maintenance") {
    await loadMaintenanceTasks();
  }
  if (currentRoute === "#/settings/store") {
    await loadStoreConfig();
  }

  const canManageBackups = ["owner", "admin"].includes(appState.profile.role);
  const canViewCosts = ["owner", "admin", "manager"].includes(appState.profile.role);
  const isCloudServicesRoute = currentRoute === "#/settings/cloud-services";
  const isStoreRoute = currentRoute === "#/settings/store";
  el.backupPanel.hidden = !(canManageBackups && isCloudServicesRoute);
  el.paymentsControlPanel.hidden = !(canManageBackups && isCloudServicesRoute);
  el.costDashboardPanel.hidden = !(canViewCosts && isCloudServicesRoute);
  if (el.storeConfigPanel) {
    el.storeConfigPanel.hidden = !(canManageBackups && isStoreRoute);
  }

  if (el.backupPanel.hidden) {
    stopBackupRequestsListener();
    stopPaymentsListeners();
  } else {
    startBackupRequestsListener();
    startPaymentsListeners();
  }

  if (!el.costDashboardPanel.hidden) {
    void loadCostDashboard();
  }
}

function startInactivityGuard() {
  clearTimeout(appState.inactivityTimerId);
  appState.inactivityTimerId = setTimeout(() => safeLogout("Sesión expirada por inactividad."), INACTIVITY_LIMIT_MS);
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
      // No mostrar banner en refresh automático para no interrumpir al usuario.
    } catch {
      await safeLogout("No se pudo renovar token. Iniciá sesión nuevamente.");
    }
  }, TOKEN_REFRESH_MS);
}

/**
 * Watchdog del límite absoluto de sesión.
 * Verifica cada minuto si el tiempo transcurrido desde el último sign-in
 * supera sessionMaxMs. Si es así, fuerza el logout independientemente de la actividad.
 */
function startAbsoluteSessionGuard(lastSignInMs) {
  clearInterval(appState.absoluteSessionTimerId);
  appState.absoluteSessionTimerId = setInterval(async () => {
    if (!appState.currentUser) return;
    const elapsed = Date.now() - lastSignInMs;
    if (elapsed > appState.sessionMaxMs) {
      const hours = Math.round(appState.sessionMaxMs / (1000 * 60 * 60));
      await safeLogout(`Tu sesión expiró (límite de ${hours}h). Por favor, iniciá sesión nuevamente.`);
    }
  }, SESSION_CHECK_INTERVAL_MS);
}

/**
 * Carga la política de sesión desde Firestore (platform/config → session.maxSessionHours).
 * Si el documento no existe o el campo no está configurado, mantiene el default (8h).
 * El admin puede modificar este valor sin redesplegar la aplicación.
 */
async function loadSessionPolicy(db) {
  try {
    const snap = await getDoc(doc(db, "platform", "config"));
    if (!snap.exists()) return;
    const data = snap.data() || {};
    const configuredHours = data?.session?.maxSessionHours;
    if (typeof configuredHours === "number" && configuredHours > 0) {
      const clampedMs = Math.round(
        Math.min(Math.max(configuredHours, 1), 24) * 60 * 60 * 1000
      );
      appState.sessionMaxMs = clampedMs;
    }
  } catch {
    // Sin permisos o sin conexión: usar el default configurado.
  }
}

async function safeLogout(message) {
  try {
    if (appState.firebaseAuth?.currentUser) await signOut(appState.firebaseAuth);
  } finally {
    clearSessionState();
    setSessionBanner(message);
    switchToAuth();
  }
}

function clearSessionState() {
  clearTimeout(appState.inactivityTimerId);
  clearInterval(appState.refreshTimerId);
  // Detener el watchdog de expiración absoluta de sesión.
  clearInterval(appState.absoluteSessionTimerId);
  appState.absoluteSessionTimerId = null;
  appState.currentUser = null;
  appState.profile = null;
  appState.sessionMaxMs = DEFAULT_SESSION_MAX_MS; // resetear al default
  appState.maintenanceTasks = [];
  stopMaintenanceTasksListener();
  if (typeof appState.backupRequestsUnsubscribe === "function") appState.backupRequestsUnsubscribe();
  appState.backupRequestsUnsubscribe = null;
  stopPaymentsListeners();
  if (el.permissionsTagList) el.permissionsTagList.innerHTML = "";
  el.backupPanel.hidden = true;
  el.costDashboardPanel.hidden = true;
  el.backupRequestsBody.innerHTML = '<tr><td colspan="6">Sin solicitudes recientes.</td></tr>';
  el.costByServiceBody.innerHTML = '<tr><td colspan="4">Sin datos de costo.</td></tr>';
  el.budgetTotalValue.textContent = '-';
  el.currentCostTotalValue.textContent = '-';
  el.costDeltaValue.textContent = '-';
}

function showDeniedState(message) {
  el.deniedState.hidden = false;
  el.deniedState.querySelector("p").textContent = message;
}
function hideDeniedState() { el.deniedState.hidden = true; }
function showMissingTenantState(message) {
  el.missingTenantState.hidden = false;
  el.missingTenantState.querySelector("p").textContent = message;
}
function hideHardStates() { hideDeniedState(); el.missingTenantState.hidden = true; }
function switchToAuth() { el.authPanel.hidden = false; el.appPanel.hidden = true; }
function switchToApp() { el.authPanel.hidden = true; el.appPanel.hidden = false; }
function setAuthError(message) { el.authError.textContent = message; }
function setSessionBanner(message) { el.sessionBanner.textContent = message; }

function stopBackupRequestsListener() {
  if (typeof appState.backupRequestsUnsubscribe === "function") appState.backupRequestsUnsubscribe();
  appState.backupRequestsUnsubscribe = null;
  stopPaymentsListeners();
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
      el.backupRequestsBody.innerHTML = snapshot.docs.map((requestDoc) => {
        const row = requestDoc.data();
        const createdAtMillis = row.createdAt?.toMillis?.() || null;
        return `<tr>
          <td>${requestDoc.id}</td><td>${row.status || "queued"}</td>
          <td>${createdAtMillis ? new Date(createdAtMillis).toLocaleString() : "-"}</td>
          <td>${row.createdByUid || "-"}</td><td>${Number.isFinite(row.docCount) ? row.docCount : "-"}</td><td>${row.errorMessage || "-"}</td>
        </tr>`;
      }).join("");
    },
    (error) => setBackupMessage(`No se pudo cargar historial de backups: ${error.message || error}`)
  );
}

async function onRequestBackupNow() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setBackupMessage("Acción permitida solo para owner/admin.");
    return;
  }

  const reason = el.backupReasonInput.value.trim();
  if (reason.length < 6) return setBackupMessage("Indicá un motivo de al menos 6 caracteres.");

  try {
    el.requestBackupButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "requestTenantBackup");
    const response = await callable({ tenantId: appState.profile.tenantId, reason });
    const deduplicated = response?.data?.deduplicated === true;
    const requestId = response?.data?.requestId || "-";
    setBackupMessage(deduplicated ? `Ya existía una solicitud reciente (${requestId}).` : `Solicitud creada (${requestId}).`);
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


function stopPaymentsListeners() {
  if (typeof appState.paymentsFlagsUnsubscribe === "function") {
    appState.paymentsFlagsUnsubscribe();
  }
  if (typeof appState.paymentsAuditUnsubscribe === "function") {
    appState.paymentsAuditUnsubscribe();
  }
  appState.paymentsFlagsUnsubscribe = null;
  appState.paymentsAuditUnsubscribe = null;
}

function startPaymentsListeners() {
  if (!appState.profile) return;
  if (!appState.paymentsFlagsUnsubscribe) {
    const tenantFlagsRef = doc(appState.firestore, "tenants", appState.profile.tenantId, "config", "runtime_flags");
    const globalFlagsRef = doc(appState.firestore, "config", "runtime_flags");

    appState.paymentsFlagsUnsubscribe = onSnapshot(
      query(
        collection(appState.firestore, "tenants", appState.profile.tenantId, "audit_logs"),
        orderBy("createdAt", "desc"),
        limit(1)
      ),
      async () => {
        const [tenantSnap, globalSnap] = await Promise.all([getDoc(tenantFlagsRef), getDoc(globalFlagsRef)]);
        renderPaymentsFlags(globalSnap.data() || {}, tenantSnap.data() || {});
      }
    );
  }

  if (!appState.paymentsAuditUnsubscribe) {
    const auditQuery = query(
      collection(appState.firestore, "tenants", appState.profile.tenantId, "audit_logs"),
      orderBy("createdAt", "desc"),
      limit(12)
    );

    appState.paymentsAuditUnsubscribe = onSnapshot(
      auditQuery,
      (snapshot) => {
        const rows = snapshot.docs
          .map((docSnap) => ({ id: docSnap.id, ...docSnap.data() }))
          .filter((row) => row.action === "toggle_mercadopago");

        if (!rows.length) {
          el.paymentsAuditBody.innerHTML = '<tr><td colspan="5">Sin eventos recientes.</td></tr>';
          return;
        }

        el.paymentsAuditBody.innerHTML = rows
          .map((row) => {
            const createdAtMillis = row.createdAt?.toMillis?.() || null;
            const createdAtLabel = createdAtMillis ? new Date(createdAtMillis).toLocaleString() : "-";
            return `
              <tr>
                <td>${createdAtLabel}</td>
                <td>${(row.scope || "tenant").toString()}</td>
                <td>${row.enabled === true ? "habilitado" : "deshabilitado"}</td>
                <td>${(row.actorUid || "-").toString()}</td>
                <td>${(row.reason || "-").toString()}</td>
              </tr>
            `;
          })
          .join("");
      },
      (error) => {
        setPaymentsToggleMessage(`No se pudo cargar auditoría: ${error.message || error}`);
      }
    );
  }
}

function readFlag(source, path) {
  const fromNested = path.split(".").reduce((acc, segment) => (acc && typeof acc === "object" ? acc[segment] : undefined), source);
  if (typeof fromNested === "boolean") return fromNested;
  if (typeof source?.[path] === "boolean") return source[path];
  return true;
}

function renderPaymentsFlags(globalFlags, tenantFlags) {
  const globalEnabled = readFlag(globalFlags, "payments.mp.enabled");
  const tenantEnabled = readFlag(tenantFlags, "tenant.payments.mp.enabled");

  el.paymentsFlagsBody.innerHTML = `
    <tr>
      <td>payments.mp.enabled (global)</td>
      <td>${globalEnabled ? "ON" : "OFF"}</td>
      <td>${formatTimestamp(globalFlags.updatedAt)}</td>
      <td>${(globalFlags.updatedBy || "-").toString()}</td>
    </tr>
    <tr>
      <td>tenant.payments.mp.enabled (tenant)</td>
      <td>${tenantEnabled ? "ON" : "OFF"}</td>
      <td>${formatTimestamp(tenantFlags.updatedAt)}</td>
      <td>${(tenantFlags.updatedBy || "-").toString()}</td>
    </tr>
    <tr>
      <td>effective</td>
      <td>${globalEnabled && tenantEnabled ? "ON" : "OFF"}</td>
      <td>-</td>
      <td>-</td>
    </tr>
  `;
}

function formatTimestamp(ts) {
  const millis = ts?.toMillis?.();
  if (!millis) return "-";
  return new Date(millis).toLocaleString();
}

async function onApplyPaymentsToggle() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setPaymentsToggleMessage("Acción permitida solo para owner/admin.");
    return;
  }

  const reason = el.paymentsToggleReason.value.trim();
  if (reason.length < 8) {
    setPaymentsToggleMessage("Indicá un motivo de al menos 8 caracteres.");
    return;
  }

  const scope = el.paymentsToggleScope.value;
  const enabled = el.paymentsToggleEnabled.value === "true";

  try {
    el.paymentsToggleButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "setMercadoPagoToggle");
    const response = await callable({
      tenantId: appState.profile.tenantId,
      scope,
      enabled,
      reason
    });

    const effectiveEnabled = response?.data?.paymentToggleState?.effectiveEnabled;
    setPaymentsToggleMessage(
      `Toggle aplicado. MP efectivo: ${effectiveEnabled === true ? "habilitado" : "deshabilitado"}.`
    );
    el.paymentsToggleReason.value = "";
  } catch (error) {
    setPaymentsToggleMessage(parseAuthError(error));
  } finally {
    el.paymentsToggleButton.disabled = false;
  }
}

function setPaymentsToggleMessage(message) {
  el.paymentsToggleMessage.textContent = message || "";
}

async function loadCostDashboard() {
  if (!appState.profile) return;

  try {
    const callable = httpsCallable(appState.cloudFunctions, "getTenantCostDashboard");
    const response = await callable({ tenantId: appState.profile.tenantId });
    const data = response?.data || {};
    const budgetTotal = Number(data?.budget?.total || 0);
    const currentTotal = Number(data?.currentCost?.total || 0);
    const deltaPercent = budgetTotal > 0 ? Math.round((currentTotal / budgetTotal) * 100) : 0;

    el.budgetTotalValue.textContent = formatMoney(budgetTotal);
    el.currentCostTotalValue.textContent = formatMoney(currentTotal);
    el.costDeltaValue.textContent = budgetTotal > 0 ? `${deltaPercent}%` : "-";

    const budgetByService = data?.budget?.byService || {};
    const costByService = data?.currentCost?.byService || {};
    const services = new Set([...Object.keys(budgetByService), ...Object.keys(costByService)]);

    if (!services.size) {
      el.costByServiceBody.innerHTML = '<tr><td colspan="4">Sin datos de costo.</td></tr>';
      return;
    }

    el.costByServiceBody.innerHTML = [...services]
      .sort((a, b) => a.localeCompare(b))
      .map((service) => {
        const budget = Number(budgetByService[service] || 0);
        const current = Number(costByService[service] || 0);
        const usage = budget > 0 ? `${Math.round((current / budget) * 100)}%` : "-";

        return `
          <tr>
            <td>${service}</td>
            <td>${formatMoney(current)}</td>
            <td>${formatMoney(budget)}</td>
            <td>${usage}</td>
          </tr>
        `;
      })
      .join("");
  } catch (error) {
    el.costByServiceBody.innerHTML = '<tr><td colspan="4">No se pudo cargar dashboard de costos.</td></tr>';
    setBackupMessage(`No se pudo cargar dashboard de costos: ${parseAuthError(error)}`);
  }
}

function formatMoney(value) {
  if (!Number.isFinite(value)) return "-";
  return new Intl.NumberFormat("es-AR", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(value);
}


function toggleModulePanels(currentRoute) {
  if (el.dashboardPanel) el.dashboardPanel.hidden = currentRoute !== "#/dashboard";
  if (el.maintenancePanel) el.maintenancePanel.hidden = currentRoute !== "#/maintenance";
  if (el.tenantPolicyPanel) el.tenantPolicyPanel.hidden = currentRoute !== "#/settings/cloud-services";
  if (el.storeConfigPanel) el.storeConfigPanel.hidden = currentRoute !== "#/settings/store";
  if (el.permissionsPanel) el.permissionsPanel.hidden = currentRoute !== "#/permissions";
}

async function loadTenantOnboardingPolicy() {
  if (!el.tenantPolicyPanel) return;
  const canRead = ["owner", "admin"].includes(appState.profile?.role);
  if (!canRead) return;

  try {
    const callable = httpsCallable(appState.cloudFunctions, "getTenantOnboardingPolicy");
    const response = await callable({});
    const mode = response?.data?.tenantActivationMode === "manual" ? "manual" : "auto";
    if (el.tenantActivationModeSelect) el.tenantActivationModeSelect.value = mode;
    setTenantPolicyMessage("");
  } catch (error) {
    setTenantPolicyMessage(`No se pudo cargar política: ${parseAuthError(error)}`);
  }
}

async function onSaveTenantOnboardingPolicy() {
  if (!appState.profile || appState.profile.role !== "owner") {
    setTenantPolicyMessage("Solo el owner puede modificar la política de activación.");
    return;
  }
  const mode = el.tenantActivationModeSelect?.value || "auto";
  try {
    if (el.saveTenantPolicyButton) el.saveTenantPolicyButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "setTenantOnboardingPolicy");
    await callable({ tenantActivationMode: mode });
    setTenantPolicyMessage("Política guardada correctamente.");
  } catch (error) {
    setTenantPolicyMessage(parseAuthError(error));
  } finally {
    if (el.saveTenantPolicyButton) el.saveTenantPolicyButton.disabled = false;
  }
}

async function loadDashboard() {
  if (!appState.profile || !el.dashboardPanel) return;
  setDashboardState("loading");
  try {
    const callable = httpsCallable(appState.cloudFunctions, "getUsageMetricsHistory");
    const response = await callable({ tenantId: appState.profile.tenantId, limit: 1 });
    const items = response?.data?.items || [];
    if (!items.length) {
      setDashboardState("empty");
      return;
    }
    const item = items[0];
    const overview = item.overview || {};
    const period = item.period || {};
    if (el.dashboardPeriod) {
      const start = period.start ? new Date(period.start).toLocaleDateString("es-AR") : "-";
      const end = period.end ? new Date(period.end).toLocaleDateString("es-AR") : "-";
      el.dashboardPeriod.textContent = `${start} — ${end}`;
    }
    if (el.dashboardReads) el.dashboardReads.textContent = formatNumber(overview.firestore?.count ?? 0);
    if (el.dashboardWrites) el.dashboardWrites.textContent = formatNumber(overview.auth?.count ?? 0);
    if (el.dashboardStorage) el.dashboardStorage.textContent = formatBytes(overview.storage?.bytes ?? 0);
    if (el.dashboardFunctions) el.dashboardFunctions.textContent = formatNumber(overview.functions?.count ?? 0);
    if (el.dashboardErrors) el.dashboardErrors.textContent = formatNumber(item.errors?.count ?? 0);
    setDashboardState("content");
  } catch (error) {
    setDashboardState("error");
    if (el.dashboardFeedback) el.dashboardFeedback.textContent = parseAuthError(error);
  }
}

function setDashboardState(state) {
  if (el.dashboardLoading) el.dashboardLoading.hidden = state !== "loading";
  if (el.dashboardEmpty) el.dashboardEmpty.hidden = state !== "empty";
  if (el.dashboardError) el.dashboardError.hidden = state !== "error";
  if (el.dashboardContent) el.dashboardContent.hidden = state !== "content";
}

async function loadMaintenanceTasks() {
  if (!appState.profile || !el.maintenancePanel) return;
  stopMaintenanceTasksListener();
  setMaintenanceState("loading");
  const q = query(
    collection(appState.firestore, "tenants", appState.profile.tenantId, "maintenance_tasks"),
    orderBy("createdAt", "desc"),
    limit(20)
  );
  appState.maintenanceTasksUnsubscribe = onSnapshot(
    q,
    (snapshot) => {
      appState.maintenanceTasks = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      if (snapshot.empty) {
        setMaintenanceState("empty");
        return;
      }
      setMaintenanceState("content");
      renderMaintenanceTasks(appState.maintenanceTasks);
    },
    (error) => {
      setMaintenanceState("error", parseAuthError(error));
    }
  );
}

function stopMaintenanceTasksListener() {
  if (typeof appState.maintenanceTasksUnsubscribe === "function") {
    appState.maintenanceTasksUnsubscribe();
  }
  appState.maintenanceTasksUnsubscribe = null;
}

function setMaintenanceState(state, message) {
  if (el.maintenanceLoading) el.maintenanceLoading.hidden = state !== "loading";
  if (el.maintenanceEmpty) el.maintenanceEmpty.hidden = state !== "empty";
  if (el.maintenanceError) el.maintenanceError.hidden = state !== "error";
  if (el.maintenanceFeedback && message !== undefined) el.maintenanceFeedback.textContent = message;
}

function renderMaintenanceTasks(tasks) {
  if (!el.maintenanceBody) return;
  el.maintenanceBody.innerHTML = tasks.map((task) => {
    const createdAtMs = task.createdAt?.toMillis?.();
    const createdAt = createdAtMs ? new Date(createdAtMs).toLocaleString() : "-";
    const isDone = task.status === "completed" || task.status === "cancelled";
    const actions = isDone
      ? "-"
      : `<button class="secondary" data-action="complete" data-task-id="${task.id}">Completar</button>
         <button class="secondary" data-action="cancel" data-task-id="${task.id}">Cancelar</button>`;
    return `<tr>
      <td>${escapeHtml(task.title || "-")}</td>
      <td>${escapeHtml(task.status || "-")}</td>
      <td>${escapeHtml(task.priority || "-")}</td>
      <td>${task.operationalBlocker ? "Sí" : "No"}</td>
      <td>${createdAt}</td>
      <td>${actions}</td>
    </tr>`;
  }).join("");
}

async function onCreateMaintenanceTask(event) {
  event?.preventDefault?.();
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = "Solo owner/admin pueden crear tareas.";
    return;
  }
  const title = (el.maintenanceTitleInput?.value || "").trim();
  const priority = el.maintenancePriorityInput?.value || "medium";
  if (title.length < 3) {
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = "El título debe tener al menos 3 caracteres.";
    return;
  }
  try {
    if (el.maintenanceCreateButton) el.maintenanceCreateButton.disabled = true;
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = "";
    const callable = httpsCallable(appState.cloudFunctions, "createMaintenanceTask");
    await callable({ tenantId: appState.profile.tenantId, title, priority });
    if (el.maintenanceTitleInput) el.maintenanceTitleInput.value = "";
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = "Tarea creada correctamente.";
  } catch (error) {
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = parseAuthError(error);
  } finally {
    if (el.maintenanceCreateButton) el.maintenanceCreateButton.disabled = false;
  }
}

async function onMaintenanceActions(event) {
  const button = event.target.closest("button[data-action]");
  if (!button || !appState.profile) return;
  const action = button.dataset.action;
  const taskId = button.dataset.taskId;
  if (!taskId) return;
  const statusMap = { complete: "completed", cancel: "cancelled" };
  const newStatus = statusMap[action];
  if (!newStatus) return;
  try {
    button.disabled = true;
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = "";
    const callable = httpsCallable(appState.cloudFunctions, "updateMaintenanceTask");
    await callable({ tenantId: appState.profile.tenantId, taskId, status: newStatus });
  } catch (error) {
    if (el.maintenanceFeedback) el.maintenanceFeedback.textContent = parseAuthError(error);
    button.disabled = false;
  }
}

function setTenantPolicyMessage(message) {
  if (el.tenantPolicyMessage) {
    el.tenantPolicyMessage.textContent = message || "";
  }
}

// ---------------------------------------------------------------------------
// Tienda: dominio personalizado y sincronización de catálogo
// ---------------------------------------------------------------------------

async function loadStoreConfig() {
  if (!appState.profile || !el.storeCurrentDomain) return;
  try {
    const snap = await getDoc(
      doc(appState.firestore, "tenants", appState.profile.tenantId, "config", "public_store")
    );
    const data = snap.exists() ? snap.data() : {};
    const domain = data?.publicDomain || "";
    el.storeCurrentDomain.textContent = domain
      ? `Dominio actual: ${domain}`
      : "Sin dominio personalizado configurado.";
    if (el.storeDomainInput) {
      el.storeDomainInput.value = domain;
    }
  } catch {
    el.storeCurrentDomain.textContent = "No se pudo cargar la configuración de tienda.";
  }
}

async function onSetStoreDomain() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setStoreDomainMessage("Solo owner/admin pueden configurar el dominio.");
    return;
  }
  const domain = (el.storeDomainInput?.value || "").trim().toLowerCase();
  if (!domain) {
    setStoreDomainMessage("Ingresá un dominio válido.");
    return;
  }
  try {
    el.setStoreDomainButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "setStoreDomain");
    const response = await callable({ tenantId: appState.profile.tenantId, domain });
    const result = response?.data || {};
    el.storeCurrentDomain.textContent = `Dominio actual: ${result.domain}`;
    setStoreDomainMessage(
      `Dominio vinculado: ${result.domain}. Próximo paso: agregalo en Firebase Console → Hosting y cargá los registros DNS en tu proveedor.`
    );
  } catch (error) {
    setStoreDomainMessage(parseAuthError(error));
  } finally {
    el.setStoreDomainButton.disabled = false;
  }
}

async function onRemoveStoreDomain() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setStoreDomainMessage("Solo owner/admin pueden quitar el dominio.");
    return;
  }
  if (!window.confirm("¿Confirmás que querés quitar el dominio personalizado de esta tienda?")) return;
  try {
    el.removeStoreDomainButton.disabled = true;
    const callable = httpsCallable(appState.cloudFunctions, "removeStoreDomain");
    const response = await callable({ tenantId: appState.profile.tenantId });
    const removed = response?.data?.removed === true;
    el.storeCurrentDomain.textContent = "Sin dominio personalizado configurado.";
    if (el.storeDomainInput) el.storeDomainInput.value = "";
    setStoreDomainMessage(removed ? "Dominio eliminado correctamente." : "No había dominio configurado.");
  } catch (error) {
    setStoreDomainMessage(parseAuthError(error));
  } finally {
    el.removeStoreDomainButton.disabled = false;
  }
}

async function onSyncProducts() {
  if (!appState.profile || !["owner", "admin"].includes(appState.profile.role)) {
    setSyncProductsMessage("Solo owner/admin pueden sincronizar el catálogo.");
    return;
  }
  try {
    el.syncProductsButton.disabled = true;
    setSyncProductsMessage("Sincronizando...");
    const callable = httpsCallable(appState.cloudFunctions, "triggerStoreProductsSync");
    const response = await callable({ tenantId: appState.profile.tenantId });
    const count = response?.data?.syncedCount ?? 0;
    setSyncProductsMessage(`Sincronización completada. ${count} producto(s) publicados en el catálogo.`);
  } catch (error) {
    setSyncProductsMessage(parseAuthError(error));
  } finally {
    el.syncProductsButton.disabled = false;
  }
}

function setStoreDomainMessage(message) {
  if (el.storeDomainMessage) el.storeDomainMessage.textContent = message || "";
}

function setSyncProductsMessage(message) {
  if (el.syncProductsMessage) el.syncProductsMessage.textContent = message || "";
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
    "functions/unauthenticated": "Tu sesión expiró. Iniciá sesión nuevamente.",
    "functions/internal": "Error interno del servidor. Intentá de nuevo más tarde.",
    "functions/unavailable": "Servicio temporalmente no disponible. Intentá de nuevo.",
    "functions/deadline-exceeded": "La operación tardó demasiado. Intentá de nuevo.",
    "functions/not-found": "Recurso no encontrado.",
    "functions/already-exists": "El recurso ya existe.",
    "functions/resource-exhausted": "Límite de operaciones alcanzado. Intentá más tarde."
  };
  return map[error.code] || (error.message && error.message !== "internal" ? error.message : "Ocurrió un error inesperado. Intentá de nuevo.");
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString("es-AR", { maximumFractionDigits: 0 });
}

function formatBytes(bytes) {
  const n = Number(bytes || 0);
  if (!Number.isFinite(n) || n === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(Math.floor(Math.log2(n) / 10), units.length - 1);
  return `${(n / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
