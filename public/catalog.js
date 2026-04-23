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
  catalogSearchInput: document.getElementById("catalogSearchInput"),
  catalogGrid: document.getElementById("catalogGrid"),
  catalogRows: document.getElementById("catalogRows"),
  catalogStatus: document.getElementById("catalogStatus"),
  catalogCount: document.getElementById("catalogCount"),
  catalogHeader: document.getElementById("catalogHeader"),
  catalogLogo: document.getElementById("catalogLogo"),
  catalogStoreName: document.getElementById("catalogStoreName"),
  catalogWhatsappTop: document.getElementById("catalogWhatsappTop"),
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
  storeMeta: null,
  searchTerm: ""
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
  elements.catalogStatus.dataset.tone = isError
    ? "error"
    : message.toLowerCase().includes("cargando")
      ? "loading"
      : "ok";
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
    imageUrl: raw.imageUrl || "",
    category: raw.category || "",
    listPrice: raw.listPrice,
    cashPrice: raw.cashPrice
  };
}

function dedupeProducts(products) {
  const unique = new Map();
  products.forEach((product, index) => {
    const normalizedSku = String(product.sku || "")
      .trim()
      .toLowerCase();
    const hasMeaningfulSku =
      normalizedSku !== "" &&
      normalizedSku !== "sin sku" &&
      normalizedSku !== "null" &&
      normalizedSku !== "undefined";

    const normalizedName = String(product.name || "").trim().toLowerCase();
    const normalizedCategory = String(product.category || "").trim().toLowerCase();
    const normalizedList = Number.isFinite(Number(product.listPrice))
      ? Number(product.listPrice)
      : "na";
    const normalizedCash = Number.isFinite(Number(product.cashPrice))
      ? Number(product.cashPrice)
      : "na";

    const businessKey = hasMeaningfulSku
      ? `sku:${normalizedSku}`
      : `name:${normalizedName}|cat:${normalizedCategory}|list:${normalizedList}|cash:${normalizedCash}`;
    const stableId = String(product.id || "").trim().toLowerCase();
    const key = businessKey || stableId || `row-${index}`;

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

function applyContactLinks() {
  const contactMap = {
    whatsapp: config.contact?.whatsapp,
    instagram: config.contact?.instagram,
    maps: config.contact?.maps,
  };
  Object.entries(contactMap).forEach(([key, value]) => {
    const link = document.querySelector(`[data-contact="${key}"]`);
    if (!link) return;
    if (isConfiguredValue(value)) {
      safeDom.setSafeUrl?.(link, "href", value);
      link.hidden = false;
    } else {
      link.hidden = true;
    }
  });
}

async function fetchCatalogProductsFromBackend() {
  const catalogTenantId = getCatalogTenantId();
  if (!catalogTenantId) {
    throw new Error("Se requiere una tienda para consultar el catálogo público");
  }

  const items = [];
  let pageToken = "";
  const seenTokens = new Set();
  const MAX_PAGES = 200;
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
    const nextToken = String(data.nextPageToken || "");
    if (!nextToken) break;
    if (seenTokens.has(nextToken)) {
      console.warn("Se detectó pageToken repetido en catálogo público. Se corta paginación para evitar duplicados.");
      break;
    }
    seenTokens.add(nextToken);
    pageToken = nextToken;
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

  const whatsappCandidate = config.contact?.whatsapp;
  if (elements.catalogWhatsappTop && isConfiguredValue(whatsappCandidate)) {
    elements.catalogWhatsappTop.href = whatsappCandidate;
    elements.catalogWhatsappTop.hidden = false;
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

  applyContactLinks();

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
  const normalizedSearch = state.searchTerm.trim().toLowerCase();
  return state.products.filter((product) => {
    const matchesStore =
      state.activeStore === "all" ||
      (product.tenantId || getStoreLabel(product)) === state.activeStore;
    if (!matchesStore) return false;
    if (!normalizedSearch) return true;
    const haystack = [
      product.name,
      product.sku,
      product.id,
      product.category,
      getStoreLabel(product),
    ]
      .map((value) => String(value || "").toLowerCase())
      .join(" ");
    return haystack.includes(normalizedSearch);
  });
}

function createCatalogCard(product, hidePrices, hideCashPrice) {
  const article = document.createElement("article");
  article.className = "catalog-card";

  const media = document.createElement("div");
  media.className = "catalog-card-media";
  const image = document.createElement("img");
  image.loading = "lazy";
  image.alt = sanitizeText
    ? sanitizeText(product.name || "Producto")
    : String(product.name || "Producto");
  const fallback = "https://images.unsplash.com/photo-1557821552-17105176677c?q=80&w=1200&auto=format&fit=crop";
  image.src = String(product.imageUrl || "").startsWith("https://") ? product.imageUrl : fallback;
  media.appendChild(image);

  const body = document.createElement("div");
  body.className = "catalog-card-body";

  const category = document.createElement("p");
  category.className = "catalog-card-category";
  category.textContent = sanitizeText
    ? sanitizeText(product.category || getStoreLabel(product))
    : String(product.category || getStoreLabel(product));

  const title = document.createElement("h3");
  title.className = "catalog-card-title";
  title.textContent = sanitizeText
    ? sanitizeText(product.name || "Producto")
    : String(product.name || "Producto");

  const sku = document.createElement("p");
  sku.className = "catalog-card-sku";
  sku.textContent = sanitizeText
    ? sanitizeText(product.sku || "Sin SKU")
    : String(product.sku || "Sin SKU");

  const prices = document.createElement("div");
  prices.className = "catalog-card-prices";
  if (hidePrices) {
    const muted = document.createElement("span");
    muted.className = "muted";
    muted.textContent = "Precios ocultos";
    prices.appendChild(muted);
  } else {
    const listPrice = document.createElement("strong");
    listPrice.textContent = formatCurrency(product.listPrice);
    prices.appendChild(listPrice);
    if (!hideCashPrice) {
      const cashPrice = document.createElement("span");
      cashPrice.textContent = `Efectivo: ${formatCurrency(product.cashPrice)}`;
      prices.appendChild(cashPrice);
    }
  }

  body.appendChild(category);
  body.appendChild(title);
  body.appendChild(sku);
  body.appendChild(prices);
  article.appendChild(media);
  article.appendChild(body);
  return article;
}

function renderLoadingCards(count = 8) {
  if (!elements.catalogGrid) return;
  elements.catalogGrid.replaceChildren();
  for (let index = 0; index < count; index += 1) {
    const skeleton = document.createElement("article");
    skeleton.className = "catalog-card catalog-card-skeleton";
    skeleton.innerHTML = `
      <div class="catalog-card-media"></div>
      <div class="catalog-card-body">
        <p class="catalog-card-category">&nbsp;</p>
        <h3 class="catalog-card-title">&nbsp;</h3>
        <p class="catalog-card-sku">&nbsp;</p>
        <div class="catalog-card-prices"><strong>&nbsp;</strong><span>&nbsp;</span></div>
      </div>
    `;
    elements.catalogGrid.appendChild(skeleton);
  }
}

function renderProducts() {
  if (!elements.catalogGrid || !elements.catalogCount) return;

  const meta = state.storeMeta;
  const hidePrices = meta?.showPrices === false;
  const hideCashPrice = meta?.showCashPrice === false;

  const visibleProducts = getVisibleProducts();
  elements.catalogGrid.replaceChildren();

  if (visibleProducts.length === 0) {
    const emptyState = document.createElement("article");
    emptyState.className = "catalog-empty-state";
    emptyState.textContent = state.products.length === 0
      ? "Catálogo en construcción. Los productos estarán disponibles pronto."
      : "No hay productos para el filtro seleccionado.";
    elements.catalogGrid.appendChild(emptyState);
  } else {
    visibleProducts.forEach((product) => {
      elements.catalogGrid.appendChild(createCatalogCard(product, hidePrices, hideCashPrice));
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
    renderLoadingCards();
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

if (elements.catalogSearchInput) {
  elements.catalogSearchInput.addEventListener("input", (event) => {
    state.searchTerm = String(event.target.value || "");
    renderProducts();
  });
}

async function bootstrap() {
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  applyContactLinks();
  await loadCatalog();
}

bootstrap();
