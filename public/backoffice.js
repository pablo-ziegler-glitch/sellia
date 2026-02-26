import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  addDoc,
  collection,
  getFirestore,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  where
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";
import { getAuth, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-functions.js";

const safeDom = window.SafeDom || {};

const mockTasks = [
  { id: "mt-001", title: "Revisar lector de código", status: "pending", overdue: false, blocker: true, priority: "critical" },
  { id: "mt-002", title: "Mantenimiento preventivo POS", status: "pending", overdue: true, blocker: false, priority: "high" },
  { id: "mt-003", title: "Control de UPS", status: "in_progress", overdue: false, blocker: false, priority: "medium" }
];

const restoreRequests = [
  {
    restoreId: "rr-2026-0007",
    runId: "2026-02-20T00-00-01-000Z",
    scope: "full",
    status: "requested",
    requestedBy: "uid-owner-001",
    approvedBy: null,
    dryRun: false,
    blockedReason: "Requiere doble aprobación de superAdmin"
  },
  {
    restoreId: "rr-2026-0006",
    runId: "2026-02-19T00-00-01-000Z",
    scope: "document",
    status: "approved",
    requestedBy: "uid-admin-002",
    approvedBy: "uid-super-010",
    dryRun: true,
    blockedReason: ""
  },
  {
    restoreId: "rr-2026-0005",
    runId: "2026-02-18T00-00-01-000Z",
    scope: "collection",
    status: "failed",
    requestedBy: "uid-admin-003",
    approvedBy: "uid-super-010",
    dryRun: false,
    blockedReason: "Falló validación de integridad"
  }
];

const filters = document.querySelectorAll(".bo-filters .bo-filter");
const list = document.getElementById("maintenance-list");
const retryBtn = document.getElementById("maintenance-retry-btn");
const createBtn = document.getElementById("maintenance-create-btn");
const statusEl = document.getElementById("maintenance-status");
const restoreHistoryList = document.getElementById("restore-history-list");
const pendingPaymentsList = document.getElementById("pending-payments-list");
const paymentsStatusEl = document.getElementById("payments-status");
const paymentsRetryBtn = document.getElementById("payments-retry-btn");
const restoreConfirmCheck = document.getElementById("restore-confirm-check");
const restoreRequestBtn = document.getElementById("restore-request-btn");

const state = {
  tasks: [],
  activeFilter: "pending",
  loading: false,
  errorMessage: "",
  unsubscribeTasks: null,
  unsubscribePendingPayments: null,
  pendingPayments: [],
  paymentsLoading: false,
  paymentsErrorMessage: "",
  tenantId: "",
  userUid: "",
  useMockData: false,
  firestore: null,
  callables: {
    createMaintenanceTask: null,
    updateMaintenanceTask: null,
    reconcilePendingPayment: null
  }
};

bootstrap();

async function bootstrap() {
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  state.tenantId = sanitizeText(window.STORE_CONFIG?.tenantId || "").trim();
  state.useMockData = shouldUseMockData();

  wireFilters();
  wireRetry();
  wireCreate();
  wirePaymentActions();
  wireRestoreGuards();
  renderRestoreHistory();

  if (state.useMockData) {
    state.tasks = mockTasks;
    render();
    renderPendingPayments();
    setStatus("Modo desarrollo explícito: usando mock local.", "warning");
    setPaymentsStatus("Modo desarrollo: pagos pendientes deshabilitados en mock.", "warning");
    return;
  }

  const firebaseConfig = window.STORE_CONFIG?.firebase || {};
  if (!state.tenantId || !firebaseConfig.apiKey || !firebaseConfig.projectId || !firebaseConfig.appId) {
    state.errorMessage = "Configuración incompleta de Firebase/tenant para cargar mantenimiento.";
    render();
    return;
  }

  try {
    const app = initializeApp(firebaseConfig, "sellia-backoffice-web");
    const auth = getAuth(app);
    const firestore = getFirestore(app);
    const cloudFunctions = getFunctions(app);
    state.firestore = firestore;

    state.callables.createMaintenanceTask = httpsCallable(cloudFunctions, "createMaintenanceTask");
    state.callables.updateMaintenanceTask = httpsCallable(cloudFunctions, "updateMaintenanceTask");
    state.callables.reconcilePendingPayment = httpsCallable(cloudFunctions, "reconcilePendingPayment");

    onAuthStateChanged(auth, (user) => {
      state.userUid = user?.uid || "";
      if (!user) {
        state.errorMessage = "No hay sesión autenticada para mantenimiento. Iniciá sesión en el panel admin.";
        state.loading = false;
        render();
        return;
      }
      subscribeTasks(firestore);
      subscribePendingPayments(firestore);
    });
  } catch (error) {
    state.errorMessage = buildUiError(error);
    render();
  }
}

function shouldUseMockData() {
  const urlParams = new URLSearchParams(window.location.search);
  const explicitFlag = urlParams.get("mockMaintenance") === "1" || localStorage.getItem("sellia:mockMaintenance") === "1";
  const isDev = ["localhost", "127.0.0.1"].includes(window.location.hostname);
  return isDev && explicitFlag;
}

function sanitizeText(value) {
  return safeDom.sanitizeText ? safeDom.sanitizeText(value) : String(value);
}

function toDate(value) {
  if (!value) return null;
  if (typeof value?.toDate === "function") return value.toDate();
  const dateValue = new Date(value);
  return Number.isNaN(dateValue.getTime()) ? null : dateValue;
}

function buildViewTask(task) {
  const dueAtDate = toDate(task.dueAt);
  const now = new Date();
  const overdue = !!dueAtDate && dueAtDate.getTime() < now.getTime() && task.status !== "completed";

  return {
    id: sanitizeText(task.id || "sin-id"),
    title: sanitizeText(task.title || "Sin título"),
    status: sanitizeText(task.status || "pending"),
    priority: sanitizeText(task.priority || "medium"),
    blocker: task.operationalBlocker === true || task.blocker === true,
    overdue,
    raw: task
  };
}

function applyFilter(tasks, mode) {
  return tasks.filter((task) => {
    if (mode === "pending") return task.status === "pending";
    if (mode === "overdue") return task.overdue;
    if (mode === "blocker") return task.blocker;
    return true;
  });
}

function renderTask(task) {
  const item = document.createElement("li");

  const title = document.createElement("strong");
  title.textContent = task.title;

  const details = document.createTextNode(` · prioridad ${task.priority} · #${task.id}`);

  const actions = document.createElement("div");
  actions.className = "bo-task-actions";

  if (!state.useMockData && task.status !== "completed") {
    if (task.status === "pending") {
      actions.appendChild(buildActionButton("Iniciar", () => transitionTask(task, "in_progress")));
    }
    if (["pending", "in_progress", "blocked"].includes(task.status)) {
      actions.appendChild(buildActionButton("Completar", () => transitionTask(task, "completed")));
    }
  }

  item.appendChild(title);
  item.appendChild(details);
  if (actions.childNodes.length) item.appendChild(actions);
  return item;
}

function buildActionButton(label, onClick) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "bo-filter";
  btn.textContent = label;
  btn.addEventListener("click", onClick);
  return btn;
}

function render() {
  const fragment = document.createDocumentFragment();

  if (state.loading) {
    const item = document.createElement("li");
    item.textContent = "Cargando tareas de mantenimiento…";
    fragment.appendChild(item);
    list.replaceChildren(fragment);
    setStatus("Cargando tareas…", "loading");
    return;
  }

  if (state.errorMessage) {
    const item = document.createElement("li");
    item.textContent = `No se pudo cargar mantenimiento: ${state.errorMessage}`;
    fragment.appendChild(item);
    list.replaceChildren(fragment);
    setStatus("Error de carga. Podés reintentar.", "error");
    return;
  }

  const viewTasks = state.tasks.map((task) => buildViewTask(task));
  const filtered = applyFilter(viewTasks, state.activeFilter);

  if (!filtered.length) {
    const item = document.createElement("li");
    item.textContent = "No hay tareas para este filtro.";
    fragment.appendChild(item);
    list.replaceChildren(fragment);
    setStatus("Estado vacío: todavía no hay tareas para mostrar.", "warning");
    return;
  }

  filtered.forEach((task) => {
    fragment.appendChild(renderTask(task));
  });
  list.replaceChildren(fragment);
  setStatus(`${filtered.length} tareas visibles (${state.activeFilter}).`, "ok");
}

function setStatus(message, tone) {
  if (!statusEl) return;
  statusEl.textContent = sanitizeText(message);
  statusEl.dataset.tone = tone;
}

function subscribeTasks(firestore) {
  if (state.unsubscribeTasks) {
    state.unsubscribeTasks();
  }

  state.loading = true;
  state.errorMessage = "";
  render();

  const tasksRef = collection(firestore, "tenants", state.tenantId, "maintenance_tasks");
  const q = query(tasksRef, orderBy("updatedAt", "desc"));

  state.unsubscribeTasks = onSnapshot(
    q,
    (snapshot) => {
      state.loading = false;
      state.errorMessage = "";
      state.tasks = snapshot.docs.map((docSnapshot) => ({ id: docSnapshot.id, ...docSnapshot.data() }));
      render();
    },
    (error) => {
      state.loading = false;
      state.errorMessage = buildUiError(error);
      render();
    }
  );
}

function wireFilters() {
  filters.forEach((btn) => {
    btn.addEventListener("click", () => {
      filters.forEach((node) => node.classList.remove("bo-filter--active"));
      btn.classList.add("bo-filter--active");
      state.activeFilter = btn.dataset.filter || "pending";
      render();
    });
  });
}

function wireRetry() {
  if (!retryBtn) return;
  retryBtn.addEventListener("click", () => {
    if (state.useMockData) {
      state.tasks = mockTasks;
      state.errorMessage = "";
      render();
      return;
    }
    window.location.reload();
  });
}

function wireCreate() {
  if (!createBtn) return;
  createBtn.addEventListener("click", () => createQuickTask());
}

async function createQuickTask() {
  if (state.useMockData) {
    setStatus("No se crea en mock mode. Desactivá mockMaintenance para persistir.", "warning");
    return;
  }

  if (!state.callables.createMaintenanceTask) {
    setStatus("CreateMaintenanceTask no está disponible todavía.", "warning");
    return;
  }

  const title = window.prompt("Título de la tarea (mínimo 3 caracteres):", "Nueva tarea operativa");
  if (!title) return;

  try {
    setStatus("Creando tarea…", "loading");
    const response = await state.callables.createMaintenanceTask({
      tenantId: state.tenantId,
      title,
      description: "Creada desde backoffice web",
      status: "pending",
      priority: "medium",
      operationalBlocker: false
    });

    await logClientAudit({
      action: "create_task",
      taskId: response?.data?.taskId || "unknown",
      change: { title }
    });

    setStatus("Tarea creada correctamente.", "ok");
  } catch (error) {
    setStatus(buildUiError(error), "error");
  }
}

async function transitionTask(task, nextStatus) {
  if (!state.callables.updateMaintenanceTask) {
    setStatus("UpdateMaintenanceTask no está disponible todavía.", "warning");
    return;
  }

  try {
    setStatus(`Actualizando #${task.id}…`, "loading");
    await state.callables.updateMaintenanceTask({
      tenantId: state.tenantId,
      taskId: task.id,
      title: task.title,
      description: task.raw.description || "",
      priority: task.priority,
      status: nextStatus,
      operationalBlocker: task.blocker,
      dueAt: task.raw.dueAt?.toDate ? task.raw.dueAt.toDate().toISOString() : task.raw.dueAt || null
    });

    await logClientAudit({
      action: "update_task_status",
      taskId: task.id,
      change: { from: task.status, to: nextStatus }
    });

    setStatus(`Tarea #${task.id} actualizada a ${nextStatus}.`, "ok");
  } catch (error) {
    setStatus(buildUiError(error), "error");
  }
}

function wirePaymentActions() {
  if (!paymentsRetryBtn) return;
  paymentsRetryBtn.addEventListener("click", () => {
    if (state.useMockData) {
      setPaymentsStatus("Modo mock sin integración de pagos pendientes.", "warning");
      return;
    }
    if (state.firestore) {
      subscribePendingPayments(state.firestore);
    }
  });
}

function subscribePendingPayments(firestore) {
  if (state.unsubscribePendingPayments) {
    state.unsubscribePendingPayments();
  }

  state.paymentsLoading = true;
  state.paymentsErrorMessage = "";
  renderPendingPayments();

  const paymentsRef = collection(firestore, "tenants", state.tenantId, "payments");
  const q = query(paymentsRef, where("status", "==", "PENDING"), orderBy("updatedAt", "desc"), limit(25));

  state.unsubscribePendingPayments = onSnapshot(
    q,
    (snapshot) => {
      state.paymentsLoading = false;
      state.paymentsErrorMessage = "";
      state.pendingPayments = snapshot.docs.map((docSnapshot) => ({ id: docSnapshot.id, ...docSnapshot.data() }));
      renderPendingPayments();
    },
    (error) => {
      state.paymentsLoading = false;
      state.paymentsErrorMessage = buildUiError(error);
      renderPendingPayments();
    }
  );
}

function renderPendingPayments() {
  if (!pendingPaymentsList) return;
  const fragment = document.createDocumentFragment();

  if (state.paymentsLoading) {
    const item = document.createElement("li");
    item.textContent = "Cargando pagos pendientes…";
    fragment.appendChild(item);
    pendingPaymentsList.replaceChildren(fragment);
    setPaymentsStatus("Cargando pagos pendientes…", "loading");
    return;
  }

  if (state.paymentsErrorMessage) {
    const item = document.createElement("li");
    item.textContent = `No se pudo cargar pagos: ${state.paymentsErrorMessage}`;
    fragment.appendChild(item);
    pendingPaymentsList.replaceChildren(fragment);
    setPaymentsStatus("Error al cargar pagos pendientes.", "error");
    return;
  }

  if (!state.pendingPayments.length) {
    const item = document.createElement("li");
    item.textContent = "No hay pagos pendientes para este tenant.";
    fragment.appendChild(item);
    pendingPaymentsList.replaceChildren(fragment);
    setPaymentsStatus("Sin pendientes. Cierre de caja sin bloqueos por conciliación.", "ok");
    return;
  }

  state.pendingPayments.forEach((payment) => {
    const item = document.createElement("li");
    const statusDetail = sanitizeText(payment.raw?.statusDetail || payment.raw?.status_detail || "-");
    const amount = Number(payment.raw?.transactionAmount ?? payment.raw?.transaction_amount ?? 0);
    const currency = sanitizeText(payment.raw?.currencyId || payment.raw?.currency_id || "ARS");

    const title = document.createElement("strong");
    title.textContent = `Pago #${sanitizeText(payment.id)} · ${currency} ${Number.isFinite(amount) ? amount.toFixed(2) : "0.00"}`;

    const detail = document.createElement("p");
    detail.textContent = `Estado provider: ${sanitizeText(payment.status || "PENDING")} · Detalle: ${statusDetail}`;

    const recommendation = document.createElement("p");
    const previousRecommendation = sanitizeText(payment.manualReconciliation?.recommendation || "no_calculada");
    recommendation.textContent = `Recomendación actual: ${previousRecommendation}`;

    const actionButton = buildActionButton("Conciliar con provider", async () => {
      await reconcilePaymentWithProvider(payment.id);
    });

    item.appendChild(title);
    item.appendChild(detail);
    item.appendChild(recommendation);
    item.appendChild(actionButton);
    fragment.appendChild(item);
  });

  pendingPaymentsList.replaceChildren(fragment);
  setPaymentsStatus(`${state.pendingPayments.length} pagos pendientes listos para conciliación manual.`, "warning");
}

async function reconcilePaymentWithProvider(paymentId) {
  if (!state.callables.reconcilePendingPayment) {
    setPaymentsStatus("reconcilePendingPayment no está disponible todavía.", "warning");
    return;
  }

  const reason = window.prompt("Motivo de conciliación manual (mínimo 6 caracteres):", "Validación operativa pre-cierre") || "";
  if (reason.trim().length < 6) {
    setPaymentsStatus("Motivo inválido. Se requiere al menos 6 caracteres.", "warning");
    return;
  }

  try {
    setPaymentsStatus(`Consultando provider para pago #${paymentId}…`, "loading");
    const response = await state.callables.reconcilePendingPayment({
      tenantId: state.tenantId,
      paymentId,
      reason: reason.trim(),
    });

    const recommendation = sanitizeText(response?.data?.recommendation || "escalar");
    const providerStatus = sanitizeText(response?.data?.providerStatus || "PENDING");
    const result = sanitizeText(response?.data?.result || "requires_action");

    setPaymentsStatus(
      `Pago #${paymentId} reconciliado. Estado provider ${providerStatus}. Recomendación: ${recommendation}. Resultado: ${result}.`,
      recommendation === "esperar" ? "ok" : "warning"
    );
  } catch (error) {
    setPaymentsStatus(buildUiError(error), "error");
  }
}

function setPaymentsStatus(message, tone) {
  if (!paymentsStatusEl) return;
  paymentsStatusEl.textContent = sanitizeText(message);
  paymentsStatusEl.dataset.tone = tone;
}

function buildUiError(error) {
  const code = sanitizeText(error?.code || "unknown");
  if (code.includes("permission-denied")) {
    return "No tenés permisos para operar mantenimiento en este tenant.";
  }
  if (code.includes("unauthenticated")) {
    return "Tu sesión no es válida. Iniciá sesión nuevamente.";
  }
  if (code.includes("not-found")) {
    return "La tarea solicitada no existe o ya fue movida.";
  }
  if (code.includes("invalid-argument")) {
    return "Datos inválidos: verificá título, estado y prioridad.";
  }
  return sanitizeText(error?.message || "Error inesperado");
}

async function logClientAudit({ action, taskId, change }) {
  if (!window.STORE_CONFIG?.firebase?.projectId || state.useMockData) return;

  try {
    if (!state.firestore) return;
    await addDoc(collection(state.firestore, "tenants", state.tenantId, "maintenance_audit_ui"), {
      action,
      taskId,
      change,
      actorUid: state.userUid || "anonymous",
      tenantId: state.tenantId,
      source: "backoffice_web",
      at: serverTimestamp()
    });
  } catch (error) {
    console.warn("No se pudo registrar auditoría de UI", error);
  }
}

function renderRestoreHistory() {
  if (!restoreHistoryList) return;
  const fragment = document.createDocumentFragment();

  restoreRequests.forEach((request) => {
    const item = document.createElement("li");
    item.className = "bo-restore-item";

    const title = document.createElement("strong");
    title.textContent = `${sanitizeText(request.scope)} · ${sanitizeText(request.status)} · ${sanitizeText(request.restoreId)}`;

    const meta = document.createElement("p");
    meta.textContent = `runId ${sanitizeText(request.runId)} · dryRun ${request.dryRun ? "sí" : "no"} · requestedBy ${sanitizeText(request.requestedBy)}`;

    const approval = document.createElement("p");
    approval.textContent = request.approvedBy
      ? `approvedBy ${sanitizeText(request.approvedBy)}`
      : "Sin aprobación final";

    const lock = document.createElement("p");
    lock.className = "bo-restore-lock";
    lock.textContent = request.blockedReason
      ? `Bloqueo UX: ${sanitizeText(request.blockedReason)}`
      : "Sin bloqueos";

    item.appendChild(title);
    item.appendChild(meta);
    item.appendChild(approval);
    item.appendChild(lock);
    fragment.appendChild(item);
  });

  restoreHistoryList.replaceChildren(fragment);
}

function wireRestoreGuards() {
  if (!restoreConfirmCheck || !restoreRequestBtn) return;

  restoreConfirmCheck.addEventListener("change", () => {
    restoreRequestBtn.disabled = !restoreConfirmCheck.checked;
  });

  restoreRequestBtn.addEventListener("click", () => {
    window.alert("Se enviará requestTenantRestore (no ejecuta restore directo). Requiere flujo de aprobación.");
  });
}
