const CONFIG_PLACEHOLDER = "REEMPLAZAR";

const config = window.STORE_CONFIG || {};
const firebaseConfig = config.firebase || {};
const catalogApiBaseUrl = (config.publicCatalogApiBaseUrl || "/public/catalog").trim();
const catalogLimit = Number(config.publicCatalogLimit) > 0 ? Number(config.publicCatalogLimit) : 1000;
const catalogPageSize = Math.min(100, Number(config.publicCatalogPageSize) > 0 ? Number(config.publicCatalogPageSize) : 50);
const catalogSort = String(config.publicCatalogSort || "name_asc").trim();
const queryParams = new URLSearchParams(window.location.search || "");
const tenantFromQuery = (
  queryParams.get("tenantId") ||
  queryParams.get("tienda") ||
  queryParams.get("TIENDA") ||
  ""
).trim();
const catalogTenantId = tenantFromQuery;

const { sanitizeText } = window.SafeDom || {};

const elements = {
  storeFilter: document.getElementById("storeFilter"),
  catalogRows: document.getElementById("catalogRows"),
  catalogStatus: document.getElementById("catalogStatus"),
  catalogCount: document.getElementById("catalogCount")
};

const state = {
  products: [],
  activeStore: "all"
};

function isConfiguredValue(value) {
  return Boolean(value && typeof value === "string" && !value.startsWith(CONFIG_PLACEHOLDER));
}

function formatCurrency(value) {
  const parsedValue = Number(value);
  if (!Number.isFinite(parsedValue)) return "Sin precio";
  return new Intl.NumberFormat("es-AR", {
    style: "currency",
    currency: "ARS",
    maximumFractionDigits: 0
  }).format(parsedValue);
}

function setStatus(message, isError = false) {
  if (!elements.catalogStatus) return;
  elements.catalogStatus.textContent = message;
  elements.catalogStatus.classList.toggle("error", isError);
}

function getStoreLabel(product) {
  return product.storeName || product.tenantId || "Sin tienda";
}

function normalizeProduct(raw) {
  const sku = raw.sku || raw.code || raw.barcode || "Sin SKU";
  return {
    id: raw.id || "",
    tenantId: raw.tenantId || "",
    storeName: raw.storeName || "",
    name: raw.name || "Producto sin nombre",
    sku,
    listPrice: raw.listPrice,
    cashPrice: raw.cashPrice
  };
}

function buildCatalogEndpointUrl(pageToken = "") {
  const baseUrl = catalogApiBaseUrl || "/public/catalog";
  const query = new URLSearchParams();
  if (catalogTenantId) query.set("tenantId", catalogTenantId);
  query.set("pageSize", String(catalogPageSize));
  query.set("sort", catalogSort);
  if (pageToken) query.set("pageToken", pageToken);

  const hasQuery = baseUrl.includes("?");
  return `${baseUrl}${hasQuery ? "&" : "?"}${query.toString()}`;
}

function buildFriendlyCatalogError(error) {
  const message = String(error?.message || "");
  if (message.includes("tenantId es requerido")) {
    return "Falta tenantId en la URL. Usá ?tenantId=<id_tienda>.";
  }
  if (message.includes("Rate limit")) {
    return "Demasiadas solicitudes al catálogo. Reintentá en unos segundos.";
  }
  return message || "Error al cargar catálogo público.";
}

async function fetchCatalogProductsFromBackend() {
  if (!catalogTenantId) {
    throw new Error("tenantId es requerido para consultar el catálogo público");
  }

  const items = [];
  let pageToken = "";

  while (items.length < catalogLimit) {
    const response = await fetch(buildCatalogEndpointUrl(pageToken), {
      method: "GET",
      headers: { "Content-Type": "application/json" }
    });

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || `Backend catálogo respondió ${response.status}`);
    }

    const data = await response.json();
    const pageItems = Array.isArray(data.items) ? data.items : [];
    items.push(...pageItems.map((product) => normalizeProduct(product)));

    if (!data.nextPageToken) break;
    pageToken = String(data.nextPageToken);
  }

  return items.slice(0, catalogLimit);
}

function renderStoreFilter() {
  if (!elements.storeFilter) return;

  const stores = new Map();
  state.products.forEach((product) => {
    const key = product.tenantId || getStoreLabel(product);
    stores.set(key, getStoreLabel(product));
  });

  elements.storeFilter.replaceChildren();

  const defaultOption = document.createElement("option");
  defaultOption.value = "all";
  defaultOption.textContent = "Todas las tiendas";
  elements.storeFilter.appendChild(defaultOption);

  [...stores.entries()]
    .sort((a, b) => a[1].localeCompare(b[1], "es"))
    .forEach(([storeId, label]) => {
      const option = document.createElement("option");
      option.value = storeId;
      option.textContent = sanitizeText ? sanitizeText(label) : String(label);
      elements.storeFilter.appendChild(option);
    });
}

function getVisibleProducts() {
  if (state.activeStore === "all") return state.products;
  return state.products.filter((product) => (product.tenantId || getStoreLabel(product)) === state.activeStore);
}

function renderProducts() {
  if (!elements.catalogRows || !elements.catalogCount) return;

  const visibleProducts = getVisibleProducts();
  elements.catalogRows.replaceChildren();

  if (visibleProducts.length === 0) {
    const emptyRow = document.createElement("tr");
    const emptyCell = document.createElement("td");
    emptyCell.setAttribute("colspan", "4");
    emptyCell.className = "muted";
    emptyCell.textContent = "No hay productos para la tienda seleccionada.";
    emptyRow.appendChild(emptyCell);
    elements.catalogRows.appendChild(emptyRow);
  } else {
    visibleProducts.forEach((product) => {
      const row = document.createElement("tr");
      [
        sanitizeText ? sanitizeText(product.name) : String(product.name),
        sanitizeText ? sanitizeText(product.sku) : String(product.sku),
        formatCurrency(product.listPrice),
        formatCurrency(product.cashPrice)
      ].forEach((value) => {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.appendChild(cell);
      });
      elements.catalogRows.appendChild(row);
    });
  }

  elements.catalogCount.textContent = `${visibleProducts.length} producto(s) visibles de ${state.products.length} total.`;
}

async function loadCatalog() {
  try {
    const tenantLabel = catalogTenantId
      ? ` de la tienda ${catalogTenantId}`
      : " de todas las tiendas";
    setStatus(`Cargando catálogo${tenantLabel} desde backend...`);
    state.products = await fetchCatalogProductsFromBackend();
    setStatus(state.products.length ? "" : "No hay productos públicos disponibles.");
    renderStoreFilter();
    renderProducts();
  } catch (error) {
    console.error("No se pudo cargar el catálogo", error);
    setStatus(buildFriendlyCatalogError(error), true);
    state.products = [];
    renderStoreFilter();
    renderProducts();
  }
}

if (elements.storeFilter) {
  elements.storeFilter.addEventListener("change", (event) => {
    state.activeStore = event.target.value || "all";
    renderProducts();
  });
}

async function bootstrap() {
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  await loadCatalog();
}

bootstrap();
