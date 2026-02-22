const safeDom = window.SafeDom || {};

const tasks = [
  { id: "mt-001", title: "Revisar lector de código", status: "pending", overdue: false, blocker: true, priority: "critical" },
  { id: "mt-002", title: "Mantenimiento preventivo POS", status: "pending", overdue: true, blocker: false, priority: "high" },
  { id: "mt-003", title: "Control de UPS", status: "in_progress", overdue: false, blocker: false, priority: "medium" }
];

const filters = document.querySelectorAll(".bo-filter");
const list = document.getElementById("maintenance-list");

function renderTask(task) {
  const item = document.createElement("li");

  const title = document.createElement("strong");
  title.textContent = safeDom.sanitizeText ? safeDom.sanitizeText(task.title) : String(task.title);

  const details = document.createTextNode(
    ` · prioridad ${safeDom.sanitizeText ? safeDom.sanitizeText(task.priority) : String(task.priority)} · #${safeDom.sanitizeText ? safeDom.sanitizeText(task.id) : String(task.id)}`
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

filters.forEach((btn) => {
  btn.addEventListener("click", () => {
    filters.forEach((node) => node.classList.remove("bo-filter--active"));
    btn.classList.add("bo-filter--active");
    render(btn.dataset.filter);
  });
});

render("pending");
