const CONFIG_PLACEHOLDER = "REEMPLAZAR";

const config = window.STORE_CONFIG || {};
const firebaseConfig = config.firebase || {};
const publicCollection = config.publicProductCollection || "public_products";
const catalogLimit = Number(config.publicCatalogLimit) > 0 ? Number(config.publicCatalogLimit) : 1000;
const catalogTenantId = (config.tenantId || "").trim();

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

function parseFirestoreDocument(doc) {
  if (!doc?.fields) return null;
  const fields = doc.fields;
  const readString = (key) => fields[key]?.stringValue || "";
  const readNumber = (key) => {
    const rawValue = fields[key]?.doubleValue ?? fields[key]?.integerValue;
    if (rawValue === undefined || rawValue === null || rawValue === "") return null;
    const numberValue = Number(rawValue);
    return Number.isFinite(numberValue) ? numberValue : null;
  };

  return {
    id: doc.name?.split("/").pop() || "",
    tenantId: readString("tenantId"),
    storeName: readString("storeName"),
    name: readString("name"),
    sku: readString("sku"),
    code: readString("code"),
    barcode: readString("barcode"),
    listPrice: readNumber("listPrice"),
    cashPrice: readNumber("cashPrice")
  };
}

function buildRunQueryUrl() {
  const projectId = firebaseConfig.projectId;
  const apiKey = firebaseConfig.apiKey;
  if (!isConfiguredValue(projectId) || !isConfiguredValue(apiKey)) return null;
  if (catalogTenantId) {
    return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${encodeURIComponent(
      catalogTenantId
    )}:runQuery?key=${apiKey}`;
  }
  return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery?key=${apiKey}`;
}

async function fetchFirestoreProducts() {
  const runQueryUrl = buildRunQueryUrl();
  if (!runQueryUrl) {
    throw new Error("Falta configurar firebase.projectId o firebase.apiKey en config.js");
  }

  const body = {
    structuredQuery: {
      from: [{ collectionId: publicCollection, allDescendants: !catalogTenantId }],
      select: {
        fields: [
          { fieldPath: "tenantId" },
          { fieldPath: "storeName" },
          { fieldPath: "name" },
          { fieldPath: "sku" },
          { fieldPath: "code" },
          { fieldPath: "barcode" },
          { fieldPath: "listPrice" },
          { fieldPath: "cashPrice" }
        ]
      },
      orderBy: [
        { field: { fieldPath: "tenantId" }, direction: "ASCENDING" },
        { field: { fieldPath: "name" }, direction: "ASCENDING" }
      ],
      limit: catalogLimit
    }
  };

  const response = await fetch(runQueryUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    throw new Error(`Firestore respondió ${response.status}`);
  }

  const rows = await response.json();
  return rows
    .map((row) => parseFirestoreDocument(row.document))
    .filter(Boolean)
    .map((product) => normalizeProduct(product));
}

function renderStoreFilter() {
  if (!elements.storeFilter) return;

  const stores = new Map();
  state.products.forEach((product) => {
    const key = product.tenantId || getStoreLabel(product);
    stores.set(key, getStoreLabel(product));
  });

  elements.storeFilter.innerHTML = '<option value="all">Todas las tiendas</option>';
  [...stores.entries()]
    .sort((a, b) => a[1].localeCompare(b[1], "es"))
    .forEach(([storeId, label]) => {
      const option = document.createElement("option");
      option.value = storeId;
      option.textContent = label;
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
  elements.catalogRows.innerHTML = visibleProducts
    .map(
      (product) => `
        <tr>
          <td>${product.name}</td>
          <td>${product.sku}</td>
          <td>${formatCurrency(product.listPrice)}</td>
          <td>${formatCurrency(product.cashPrice)}</td>
        </tr>
      `
    )
    .join("");

  if (visibleProducts.length === 0) {
    elements.catalogRows.innerHTML =
      '<tr><td colspan="4" class="muted">No hay productos para la tienda seleccionada.</td></tr>';
  }

  elements.catalogCount.textContent = `${visibleProducts.length} producto(s) visibles de ${state.products.length} total.`;
}

async function loadCatalog() {
  try {
    const tenantLabel = catalogTenantId ? ` de ${catalogTenantId}` : "";
    setStatus(`Cargando catálogo${tenantLabel} desde Firestore...`);
    state.products = await fetchFirestoreProducts();
    setStatus(state.products.length ? "" : "No hay productos públicos disponibles.");
    renderStoreFilter();
    renderProducts();
  } catch (error) {
    console.error("No se pudo cargar el catálogo", error);
    setStatus(error.message || "Error al cargar catálogo desde Firestore.", true);
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
