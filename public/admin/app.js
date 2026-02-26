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
  backupRequestsBody: document.getElementById("backupRequestsBody")
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
  const isCloudServicesRoute = currentRoute === "#/settings/cloud-services";
  el.backupPanel.hidden = !(canManageBackups && isCloudServicesRoute);
  if (el.backupPanel.hidden) {
    stopBackupRequestsListener();
  } else {
    startBackupRequestsListener();
  }
}

function toggleModulePanels(route) {
  el.dashboardPanel.hidden = route !== "#/dashboard";
  el.maintenancePanel.hidden = route !== "#/maintenance";
}

async function loadDashboard() {
  if (!appState.profile) return;
  setDashboardState({ loading: true, empty: false, error: false, content: false, feedback: "" });
  try {
    const callable = httpsCallable(appState.cloudFunctions, "getUsageMetrics");
    const response = await callable({ tenantId: appState.profile.tenantId });
    const payload = response?.data || {};
    const overview = payload.overview || {};
    const hasData = Number(overview.totalUsageValue || 0) > 0 || Number(payload.errors?.count || 0) > 0;
    if (!hasData) {
      setDashboardState({ loading: false, empty: true, error: false, content: false, feedback: "" });
      return;
    }

    el.dashboardPeriod.textContent = payload.periodKey || "-";
    el.dashboardReads.textContent = formatNumber((overview.serviceTotals || {}).firestore || 0);
    el.dashboardWrites.textContent = formatNumber((payload.services || []).find((s) => s.metricType?.includes("write_count"))?.value || 0);
    el.dashboardStorage.textContent = formatNumber((overview.serviceTotals || {}).storage || 0);
    el.dashboardFunctions.textContent = formatNumber((overview.serviceTotals || {}).functions || 0);
    el.dashboardErrors.textContent = String(payload.errors?.count || 0);
    setDashboardState({ loading: false, empty: false, error: false, content: true, feedback: "Datos actualizados." });
  } catch (error) {
    setDashboardState({ loading: false, empty: false, error: true, content: false, feedback: parseAuthError(error) });
  }
}

function setDashboardState({ loading, empty, error, content, feedback }) {
  el.dashboardLoading.hidden = !loading;
  el.dashboardEmpty.hidden = !empty;
  el.dashboardError.hidden = !error;
  el.dashboardContent.hidden = !content;
  el.dashboardFeedback.textContent = feedback || "";
}

async function loadMaintenanceTasks() {
  if (!appState.profile) return;
  setMaintenanceState({ loading: true, empty: false, error: false, feedback: "" });
  try {
    const callable = httpsCallable(appState.cloudFunctions, "getMaintenanceTasks");
    const response = await callable({ tenantId: appState.profile.tenantId, pageSize: 25 });
    appState.maintenanceTasks = Array.isArray(response?.data?.tasks) ? response.data.tasks : [];
    if (!appState.maintenanceTasks.length) {
      el.maintenanceBody.innerHTML = '<tr><td colspan="6">Sin tareas.</td></tr>';
      setMaintenanceState({ loading: false, empty: true, error: false, feedback: "" });
      return;
    }

    el.maintenanceBody.innerHTML = appState.maintenanceTasks
      .map((task) => {
        const updatedAt = task.updatedAtMillis ? new Date(task.updatedAtMillis).toLocaleString() : "-";
        return `<tr>
          <td>${task.id}</td>
          <td>${escapeHtml(task.title || "-")}</td>
          <td>${escapeHtml(task.status || "pending")}</td>
          <td>${escapeHtml(task.priority || "medium")}</td>
          <td>${updatedAt}</td>
          <td>
            <button data-action="complete" data-task-id="${task.id}" class="secondary" type="button">Completar</button>
            <button data-action="delete" data-task-id="${task.id}" type="button">Eliminar</button>
          </td>
        </tr>`;
      })
      .join("");
    setMaintenanceState({ loading: false, empty: false, error: false, feedback: "Tareas sincronizadas." });
  } catch (error) {
    setMaintenanceState({ loading: false, empty: false, error: true, feedback: parseAuthError(error) });
  }
}

function setMaintenanceState({ loading, empty, error, feedback }) {
  el.maintenanceLoading.hidden = !loading;
  el.maintenanceEmpty.hidden = !empty;
  el.maintenanceError.hidden = !error;
  el.maintenanceFeedback.textContent = feedback || "";
}

async function onCreateMaintenanceTask(event) {
  event.preventDefault();
  if (!appState.profile) return;
  const title = el.maintenanceTitleInput.value.trim();
  if (title.length < 3) {
    setMaintenanceState({ loading: false, empty: false, error: true, feedback: "Título demasiado corto." });
    return;
  }

  el.maintenanceCreateButton.disabled = true;
  try {
    const callable = httpsCallable(appState.cloudFunctions, "createMaintenanceTask");
    await callable({ tenantId: appState.profile.tenantId, title, priority: el.maintenancePriorityInput.value });
    el.maintenanceTitleInput.value = "";
    setMaintenanceState({ loading: false, empty: false, error: false, feedback: "Tarea creada correctamente." });
    await loadMaintenanceTasks();
  } catch (error) {
    setMaintenanceState({ loading: false, empty: false, error: true, feedback: parseAuthError(error) });
  } finally {
    el.maintenanceCreateButton.disabled = false;
  }
}

async function onMaintenanceActions(event) {
  const button = event.target.closest("button[data-action]");
  if (!button || !appState.profile) return;
  const taskId = button.dataset.taskId;
  const action = button.dataset.action;
  if (!taskId || !action) return;

  if (action === "complete") {
    await updateMaintenanceTask(taskId, { status: "completed" }, "Tarea marcada como completada.");
    return;
  }

  if (action === "delete") {
    const task = appState.maintenanceTasks.find((item) => item.id === taskId);
    if (!window.confirm(`Vas a eliminar la tarea ${taskId}. Esta acción no se puede deshacer.`)) return;
    const challenge = window.prompt("Doble validación: escribí el título exacto de la tarea para confirmar.");
    if ((challenge || "").trim() !== (task?.title || "").trim()) {
      setMaintenanceState({ loading: false, empty: false, error: true, feedback: "Validación fallida. La tarea no fue eliminada." });
      return;
    }
    try {
      const callable = httpsCallable(appState.cloudFunctions, "deleteMaintenanceTask");
      await callable({ tenantId: appState.profile.tenantId, taskId, confirmationText: challenge });
      setMaintenanceState({ loading: false, empty: false, error: false, feedback: "Tarea eliminada correctamente." });
      await loadMaintenanceTasks();
    } catch (error) {
      setMaintenanceState({ loading: false, empty: false, error: true, feedback: parseAuthError(error) });
    }
  }
}

async function updateMaintenanceTask(taskId, changes, successMessage) {
  try {
    const callable = httpsCallable(appState.cloudFunctions, "updateMaintenanceTask");
    await callable({ tenantId: appState.profile.tenantId, taskId, ...changes });
    setMaintenanceState({ loading: false, empty: false, error: false, feedback: successMessage });
    await loadMaintenanceTasks();
  } catch (error) {
    setMaintenanceState({ loading: false, empty: false, error: true, feedback: parseAuthError(error) });
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
  el.permissionsList.innerHTML = "";
  el.backupPanel.hidden = true;
  el.dashboardPanel.hidden = true;
  el.maintenancePanel.hidden = true;
  el.backupRequestsBody.innerHTML = '<tr><td colspan="6">Sin solicitudes recientes.</td></tr>';
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

function setBackupMessage(message) { el.backupMessage.textContent = message || ""; }

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
