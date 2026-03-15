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

const { sanitizeText } = window.SafeDom || {};

const elements = {
  storeFilter: document.getElementById("storeFilter"),
  catalogRows: document.getElementById("catalogRows"),
  catalogStatus: document.getElementById("catalogStatus"),
  catalogCount: document.getElementById("catalogCount"),
  catalogHeader: document.getElementById("catalogHeader"),
  catalogLogo: document.getElementById("catalogLogo"),
  catalogStoreName: document.getElementById("catalogStoreName"),
  catalogHero: document.getElementById("catalogHero"),
  catalogHeroTitle: document.getElementById("catalogHeroTitle"),
  catalogHeroSubtitle: document.getElementById("catalogHeroSubtitle"),
  catalogDefaultHeader: document.getElementById("catalogDefaultHeader"),
  catalogFooter: document.getElementById("catalogFooter"),
  catalogFooterText: document.getElementById("catalogFooterText"),
  colHeaderListPrice: document.getElementById("colHeaderListPrice"),
  colHeaderCashPrice: document.getElementById("colHeaderCashPrice")
};

const state = {
  products: [],
  activeStore: "all",
  storeMeta: null
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

function getCatalogTenantId() {
  if (tenantFromQuery) return tenantFromQuery;
  return (config.tenantId || "").trim();
}

function buildCatalogEndpointUrl(pageToken = "") {
  const catalogTenantId = getCatalogTenantId();
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
  const catalogTenantId = getCatalogTenantId();
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

    if (!state.storeMeta && data.storeMeta) {
      state.storeMeta = data.storeMeta;
    }

    if (!data.nextPageToken) break;
    pageToken = String(data.nextPageToken);
  }

  return items.slice(0, catalogLimit);
}

function applyStoreMeta(meta) {
  if (!meta) return;

  const safe = sanitizeText || ((v) => String(v));

  if (meta.storeName) {
    document.title = `${safe(meta.storeName)} | Catálogo`;
  }

  if (meta.storeName || meta.storeLogoUrl) {
    if (elements.catalogHeader) {
      elements.catalogHeader.hidden = false;
    }
    if (meta.storeLogoUrl && elements.catalogLogo) {
      elements.catalogLogo.src = meta.storeLogoUrl;
      elements.catalogLogo.alt = safe(meta.storeName || "Logo");
      elements.catalogLogo.hidden = false;
    }
    if (meta.storeName && elements.catalogStoreName) {
      elements.catalogStoreName.textContent = safe(meta.storeName);
    }
  }

  if (meta.heroTitle) {
    if (elements.catalogHero) elements.catalogHero.hidden = false;
    if (elements.catalogHeroTitle) elements.catalogHeroTitle.textContent = safe(meta.heroTitle);
    if (meta.heroSubtitle && elements.catalogHeroSubtitle) {
      elements.catalogHeroSubtitle.textContent = safe(meta.heroSubtitle);
    }
    if (elements.catalogDefaultHeader) elements.catalogDefaultHeader.hidden = true;
  }

  if (meta.footerText) {
    if (elements.catalogFooter) elements.catalogFooter.hidden = false;
    if (elements.catalogFooterText) elements.catalogFooterText.textContent = safe(meta.footerText);
  }

  if (meta.palette?.primary) {
    document.documentElement.style.setProperty("--catalog-primary", meta.palette.primary);
  }
  if (meta.palette?.secondary) {
    document.documentElement.style.setProperty("--catalog-secondary", meta.palette.secondary);
  }

  if (meta.showPrices === false) {
    if (elements.colHeaderListPrice) elements.colHeaderListPrice.hidden = true;
    if (elements.colHeaderCashPrice) elements.colHeaderCashPrice.hidden = true;
  } else if (meta.showCashPrice === false) {
    if (elements.colHeaderCashPrice) elements.colHeaderCashPrice.hidden = true;
  }

  const resolvedFromHostname = config._resolvedFromHostname === true;
  if (resolvedFromHostname && elements.storeFilter) {
    elements.storeFilter.closest(".catalog-toolbar")?.querySelector("label")?.remove();
    elements.storeFilter.hidden = true;
  }
}

function renderStoreFilter() {
  if (!elements.storeFilter || elements.storeFilter.hidden) return;

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

  const meta = state.storeMeta;
  const hidePrices = meta?.showPrices === false;
  const hideCashPrice = meta?.showCashPrice === false;

  const visibleProducts = getVisibleProducts();
  elements.catalogRows.replaceChildren();

  if (visibleProducts.length === 0) {
    const emptyRow = document.createElement("tr");
    const emptyCell = document.createElement("td");
    emptyCell.setAttribute("colspan", hidePrices ? "2" : hideCashPrice ? "3" : "4");
    emptyCell.className = "muted";
    emptyCell.textContent = state.products.length === 0
      ? "Catálogo en construcción. Los productos estarán disponibles pronto."
      : "No hay productos para el filtro seleccionado.";
    emptyRow.appendChild(emptyCell);
    elements.catalogRows.appendChild(emptyRow);
  } else {
    visibleProducts.forEach((product) => {
      const row = document.createElement("tr");
      const cells = [
        sanitizeText ? sanitizeText(product.name) : String(product.name),
        sanitizeText ? sanitizeText(product.sku) : String(product.sku)
      ];
      if (!hidePrices) {
        cells.push(formatCurrency(product.listPrice));
        if (!hideCashPrice) {
          cells.push(formatCurrency(product.cashPrice));
        }
      }
      cells.forEach((value) => {
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
    const catalogTenantId = getCatalogTenantId();
    const tenantLabel = catalogTenantId
      ? ` de la tienda ${catalogTenantId}`
      : " de todas las tiendas";
    setStatus(`Cargando catálogo${tenantLabel} desde backend...`);
    state.products = await fetchCatalogProductsFromBackend();
    applyStoreMeta(state.storeMeta);
    if (!state.products.length) {
      setStatus("Catálogo en construcción.");
    } else {
      setStatus("");
    }
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
