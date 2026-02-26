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
  paymentsAuditBody: document.getElementById("paymentsAuditBody")
  costDashboardPanel: document.getElementById("costDashboardPanel"),
  budgetTotalValue: document.getElementById("budgetTotalValue"),
  currentCostTotalValue: document.getElementById("currentCostTotalValue"),
  costDeltaValue: document.getElementById("costDeltaValue"),
  costByServiceBody: document.getElementById("costByServiceBody")
};

const routeViews = {
  "#/dashboard": {
    title: "Panel de uso",
    description: "Métricas reales del tenant obtenidas desde backend con control de permisos."
  },
  "#/settings/pricing": { title: "Configuración de pricing", description: "Módulo en migración." },
  "#/settings/marketing": { title: "Configuración de marketing", description: "Módulo en migración." },
  "#/settings/users": { title: "Gestión de usuarios", description: "Módulo en migración." },
  "#/settings/cloud-services": {
    title: "Servicios cloud",
    description: "Gestión de backups y estado cloud del tenant."
  },
  "#/maintenance": {
    title: "Mantenimiento",
    description: "Tareas operativas multi-tenant con auditoría de cambios."
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
  backupRequestsUnsubscribe: null,
  paymentsFlagsUnsubscribe: null,
  paymentsAuditUnsubscribe: null
  maintenanceTasks: []
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
  el.saveTenantPolicyButton?.addEventListener("click", onSaveTenantOnboardingPolicy);
  el.dashboardRetryButton.addEventListener("click", loadDashboard);
  el.dashboardErrorRetryButton.addEventListener("click", loadDashboard);
  el.maintenanceRetryButton.addEventListener("click", loadMaintenanceTasks);
  el.maintenanceErrorRetryButton.addEventListener("click", loadMaintenanceTasks);
  el.maintenanceCreateForm.addEventListener("submit", onCreateMaintenanceTask);
  window.addEventListener("hashchange", syncRouteWithPermissions);
  el.maintenanceBody.addEventListener("click", onMaintenanceActions);

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

  if (currentRoute === "#/dashboard") {
    await loadDashboard();
  }
  if (currentRoute === "#/maintenance") {
    await loadMaintenanceTasks();
  }

  const canManageBackups = ["owner", "admin"].includes(appState.profile.role);
  const canViewCosts = ["owner", "admin", "manager"].includes(appState.profile.role);
  const isCloudServicesRoute = currentRoute === "#/settings/cloud-services";
  const canManageOnboardingPolicy = appState.profile.role === "owner";
  el.backupPanel.hidden = !(canManageBackups && isCloudServicesRoute);
  el.paymentsControlPanel.hidden = !(canManageBackups && isCloudServicesRoute);

  if (el.backupPanel.hidden) {
    stopBackupRequestsListener();
    stopPaymentsListeners();
    return;
  }

  startBackupRequestsListener();
  startPaymentsListeners();
  el.costDashboardPanel.hidden = !(canViewCosts && isCloudServicesRoute);

  if (el.backupPanel.hidden) {
    stopBackupRequestsListener();
  } else {
    startBackupRequestsListener();
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
      setSessionBanner("Token renovado automáticamente.");
    } catch {
      await safeLogout("No se pudo renovar token. Iniciá sesión nuevamente.");
    }
  }, TOKEN_REFRESH_MS);
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
  appState.currentUser = null;
  appState.profile = null;
  appState.maintenanceTasks = [];
  if (typeof appState.backupRequestsUnsubscribe === "function") appState.backupRequestsUnsubscribe();
  appState.backupRequestsUnsubscribe = null;
  stopPaymentsListeners();
  el.permissionsList.innerHTML = "";
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

function formatNumber(value) {
  return Number(value || 0).toLocaleString("es-AR", { maximumFractionDigits: 0 });
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
