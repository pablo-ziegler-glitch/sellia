const CONFIG_PLACEHOLDER = "REEMPLAZAR";

const config = window.STORE_CONFIG || {};
const firebaseConfig = config.firebase || {};
const catalogApiBaseUrl = (config.publicCatalogApiBaseUrl || "/public/catalog").trim();
const catalogPageSize = Math.min(100, Number(config.publicCatalogPageSize) > 0 ? Number(config.publicCatalogPageSize) : 50);
const catalogSort = String(config.publicCatalogSort || "name_asc").trim();
const FORCED_CATALOG_TENANT_ID = "61eac2a5-f9e7-471e-9dfd-a419486a6369";
const CATALOG_CACHE_TTL_MS = 5 * 60 * 1000;
const CATALOG_CACHE_VERSION = "v1";
const queryParams = new URLSearchParams(window.location.search || "");

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
  const normalized = sanitizeText
    ? sanitizeText(product.storeName || "")
    : String(product.storeName || "");
  return normalized.trim() || "Tienda sin nombre";
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

function dedupeProducts(products) {
  const unique = new Map();
  products.forEach((product, index) => {
    const stableId = String(product.id || "").trim();
    const fallbackKey = `${String(product.name || "").trim().toLowerCase()}|${String(product.sku || "").trim().toLowerCase()}`;
    const key = stableId || fallbackKey || `row-${index}`;
    if (!unique.has(key)) {
      unique.set(key, product);
    }
  });
  return [...unique.values()];
}

function getCatalogTenantId() {
  return FORCED_CATALOG_TENANT_ID;
}

function getCatalogCacheKey() {
  return `public_catalog_cache:${CATALOG_CACHE_VERSION}:${getCatalogTenantId()}:${catalogSort}`;
}

function loadCatalogCache() {
  try {
    const raw = localStorage.getItem(getCatalogCacheKey());
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    if (!Array.isArray(parsed.products)) return null;
    if (typeof parsed.cachedAt !== "number") return null;
    const isFresh = Date.now() - parsed.cachedAt < CATALOG_CACHE_TTL_MS;
    return {
      isFresh,
      products: parsed.products,
      storeMeta: parsed.storeMeta || null
    };
  } catch (_error) {
    return null;
  }
}

function saveCatalogCache(payload) {
  try {
    localStorage.setItem(
      getCatalogCacheKey(),
      JSON.stringify({
        cachedAt: Date.now(),
        products: payload.products || [],
        storeMeta: payload.storeMeta || null
      })
    );
  } catch (_error) {
    // Ignorar errores de cuota/localStorage bloqueado
  }
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
    return "Falta configuración de tienda para consultar el catálogo.";
  }
  if (message.includes("Rate limit")) {
    return "Demasiadas solicitudes al catálogo. Reintentá en unos segundos.";
  }
  if (message.toLowerCase().includes("abort")) {
    return "El catálogo tardó demasiado en responder. Verificá la función /public/catalog.";
  }
  return message || "Error al cargar catálogo público.";
}

async function fetchCatalogProductsFromBackend() {
  const catalogTenantId = getCatalogTenantId();
  if (!catalogTenantId) {
    throw new Error("Se requiere una tienda para consultar el catálogo público");
  }

  const items = [];
  let pageToken = "";
  const MAX_PAGES = 2000;
  let page = 0;

  while (page < MAX_PAGES) {
    page += 1;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 8000);

    const response = await fetch(buildCatalogEndpointUrl(pageToken), {
      method: "GET",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal
    }).finally(() => {
      clearTimeout(timeoutId);
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

  return dedupeProducts(items);
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
    const label = getStoreLabel(product);
    const key = product.tenantId || label.toLowerCase();
    stores.set(key, label);
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
  const cached = loadCatalogCache();
  if (cached?.isFresh) {
    state.products = dedupeProducts(cached.products);
    state.storeMeta = cached.storeMeta;
    applyStoreMeta(state.storeMeta);
    setStatus("");
    renderStoreFilter();
    renderProducts();
    return;
  }

  try {
    setStatus("Cargando catálogo desde backend...");
    state.products = dedupeProducts(await fetchCatalogProductsFromBackend());
    applyStoreMeta(state.storeMeta);
    if (!state.products.length) {
      setStatus("Catálogo en construcción.");
    } else {
      setStatus("");
    }
    saveCatalogCache({
      products: state.products,
      storeMeta: state.storeMeta
    });
    renderStoreFilter();
    renderProducts();
  } catch (error) {
    console.error("No se pudo cargar el catálogo", error);
    if (cached?.products?.length) {
      state.products = dedupeProducts(cached.products);
      state.storeMeta = cached.storeMeta;
      applyStoreMeta(state.storeMeta);
      setStatus("Mostrando catálogo cacheado por falla temporal de red.", false);
    } else {
      setStatus(buildFriendlyCatalogError(error), true);
      state.products = [];
    }
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
