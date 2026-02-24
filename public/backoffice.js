const safeDom = window.SafeDom || {};

const tasks = [
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

const filters = document.querySelectorAll(".bo-filter");
const list = document.getElementById("maintenance-list");
const restoreHistoryList = document.getElementById("restore-history-list");
const restoreConfirmCheck = document.getElementById("restore-confirm-check");
const restoreRequestBtn = document.getElementById("restore-request-btn");

function sanitizeText(value) {
  return safeDom.sanitizeText ? safeDom.sanitizeText(value) : String(value);
}

function renderTask(task) {
  const item = document.createElement("li");

  const title = document.createElement("strong");
  title.textContent = sanitizeText(task.title);

  const details = document.createTextNode(
    ` · prioridad ${sanitizeText(task.priority)} · #${sanitizeText(task.id)}`
  );

  item.appendChild(title);
  item.appendChild(details);
  return item;
}

const render = (mode) => {
  const filtered = tasks.filter((task) => {
    if (mode === "pending") return task.status === "pending";
    if (mode === "overdue") return task.overdue;
    if (mode === "blocker") return task.blocker;
    return true;
  });

  const fragment = document.createDocumentFragment();
  filtered.forEach((task) => {
    fragment.appendChild(renderTask(task));
  });
  list.replaceChildren(fragment);
};

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

filters.forEach((btn) => {
  btn.addEventListener("click", () => {
    filters.forEach((node) => node.classList.remove("bo-filter--active"));
    btn.classList.add("bo-filter--active");
    render(btn.dataset.filter);
  });
});

wireRestoreGuards();
render("pending");
renderRestoreHistory();
