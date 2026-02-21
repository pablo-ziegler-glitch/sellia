const tasks = [
  { id: "mt-001", title: "Revisar lector de código", status: "pending", overdue: false, blocker: true, priority: "critical" },
  { id: "mt-002", title: "Mantenimiento preventivo POS", status: "pending", overdue: true, blocker: false, priority: "high" },
  { id: "mt-003", title: "Control de UPS", status: "in_progress", overdue: false, blocker: false, priority: "medium" },
];

const filters = document.querySelectorAll(".bo-filter");
const list = document.getElementById("maintenance-list");

const render = (mode) => {
  const filtered = tasks.filter((task) => {
    if (mode === "pending") return task.status === "pending";
    if (mode === "overdue") return task.overdue;
    if (mode === "blocker") return task.blocker;
    return true;
  });

  list.innerHTML = filtered
    .map((task) => `<li><strong>${task.title}</strong> · prioridad ${task.priority} · #${task.id}</li>`)
    .join("");
};

filters.forEach((btn) => {
  btn.addEventListener("click", () => {
    filters.forEach((node) => node.classList.remove("bo-filter--active"));
    btn.classList.add("bo-filter--active");
    render(btn.dataset.filter);
  });
});

render("pending");
