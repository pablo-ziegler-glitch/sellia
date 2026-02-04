const config = window.SELLIA_CONFIG || {};
const brandName = config.brandName || "Sellia";
const refreshIntervalMs = Number(config.refreshIntervalMs) || 300000;
const tenantId = config.tenantId || "";
const publicProductCollection = config.publicProductCollection || "public_products";

const elements = {
  brandName: document.getElementById("brandName"),
  productTitle: document.getElementById("productTitle"),
  productMeta: document.getElementById("productMeta"),
  productGallery: document.getElementById("productGallery"),
  priceGrid: document.getElementById("priceGrid"),
  productDescription: document.getElementById("productDescription"),
  productSizes: document.getElementById("productSizes"),
  statusCard: document.getElementById("statusCard"),
  syncMeta: document.getElementById("syncMeta"),
  ctaWhatsapp: document.getElementById("ctaWhatsapp")
};

const state = {
  sku: "",
  product: null,
  lastSync: null,
  timer: null,
  paused: false
};

function setStatus(message, isError = false) {
  if (!elements.statusCard) return;
  elements.statusCard.textContent = message;
  elements.statusCard.hidden = !message;
  elements.statusCard.classList.toggle("error", isError);
}

function formatCurrency(value) {
  if (value === null || value === undefined || value === "") return "Sin precio";
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return String(value);
  return new Intl.NumberFormat("es-AR", {
    style: "currency",
    currency: "ARS",
    maximumFractionDigits: 0
  }).format(numberValue);
}

function updateSyncMeta() {
  if (!elements.syncMeta) return;
  if (!state.lastSync) {
    elements.syncMeta.textContent = "";
    return;
  }
  const time = state.lastSync.toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit"
  });
  elements.syncMeta.textContent = `Última actualización: ${time}. Se refresca cada ${
    Math.round(refreshIntervalMs / 60000)
  } min.`;
}

function renderProduct(product) {
  if (!product) return;
  elements.productTitle.textContent = product.name || "Producto";
  elements.productDescription.textContent = product.description || "Sin descripción.";

  elements.productMeta.innerHTML = "";
  if (product.sku) {
    const sku = document.createElement("span");
    sku.textContent = `SKU: ${product.sku}`;
    elements.productMeta.appendChild(sku);
  }
  if (product.category) {
    const category = document.createElement("span");
    category.textContent = `Categoría: ${product.category}`;
    elements.productMeta.appendChild(category);
  }
  if (product.updatedAt) {
    const updated = document.createElement("span");
    updated.textContent = `Actualizado: ${product.updatedAt}`;
    elements.productMeta.appendChild(updated);
  }

  elements.productGallery.innerHTML = "";
  const images = product.images?.length
    ? product.images
    : product.imageUrl
      ? [product.imageUrl]
      : ["/assets/placeholder.svg"];
  images.forEach((url) => {
    const img = document.createElement("img");
    img.src = url;
    img.alt = product.name || "Producto";
    img.loading = "lazy";
    elements.productGallery.appendChild(img);
  });

  elements.priceGrid.innerHTML = "";
  const basePrice = product.price ?? product.listPrice ?? product.cashPrice ?? null;
  const priceRows = [
    { label: "Precio lista", value: formatCurrency(product.listPrice ?? basePrice) },
    { label: "Precio efectivo", value: formatCurrency(product.cashPrice ?? basePrice) }
  ];
  if (product.transferPrice !== null && product.transferPrice !== undefined) {
    priceRows.push({ label: "Precio transferencia", value: formatCurrency(product.transferPrice) });
  }
  priceRows.forEach((row) => {
    const line = document.createElement("div");
    line.className = "price-row";
    line.innerHTML = `<span>${row.label}</span><strong>${row.value}</strong>`;
    elements.priceGrid.appendChild(line);
  });

  elements.productSizes.innerHTML = "";
  if (product.sizes?.length) {
    product.sizes.forEach((size) => {
      const pill = document.createElement("span");
      pill.className = "size-pill";
      pill.textContent = size;
      elements.productSizes.appendChild(pill);
    });
  } else {
    const empty = document.createElement("span");
    empty.className = "muted";
    empty.textContent = "Sin talles cargados.";
    elements.productSizes.appendChild(empty);
  }

  if (elements.ctaWhatsapp && config.contact?.whatsapp && !config.contact.whatsapp.startsWith("REEMPLAZAR")) {
    const text = `Hola, quiero consultar por ${product.name} (SKU ${product.sku || state.sku}).`;
    const url = new URL(config.contact.whatsapp);
    url.searchParams.set("text", text);
    elements.ctaWhatsapp.href = url.toString();
  } else if (elements.ctaWhatsapp) {
    elements.ctaWhatsapp.href = "#";
  }
}

function parseFirestoreDocument(doc) {
  if (!doc?.fields) return null;
  const fields = doc.fields;
  const getString = (key) => fields[key]?.stringValue || "";
  const getNumber = (key) =>
    fields[key]?.doubleValue ?? fields[key]?.integerValue ?? "";
  const getArray = (key) =>
    fields[key]?.arrayValue?.values?.map((v) => v.stringValue).filter(Boolean) || [];
  const imageUrl = getString("imageUrl");
  const updatedAt =
    fields.updatedAt?.stringValue ||
    (fields.updatedAt?.timestampValue
      ? new Date(fields.updatedAt.timestampValue).toISOString().slice(0, 10)
      : "");

  return {
    id: doc.name?.split("/").pop() || "",
    sku: getString("code") || getString("barcode"),
    name: getString("name"),
    description: getString("description"),
    category: getString("category"),
    price: getNumber("price"),
    listPrice: getNumber("listPrice"),
    cashPrice: getNumber("cashPrice"),
    transferPrice: getNumber("transferPrice"),
    images: getArray("imageUrls"),
    imageUrl,
    sizes: getArray("sizes"),
    updatedAt
  };
}

function buildPublicCollectionPath() {
  if (!tenantId) return null;
  return `tenants/${tenantId}/${publicProductCollection}`;
}

function buildRunQueryUrl() {
  const projectId = config.firebase?.projectId;
  const apiKey = config.firebase?.apiKey;
  if (!projectId || !apiKey || apiKey === "REEMPLAZAR" || !tenantId) return null;
  return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${tenantId}:runQuery?key=${apiKey}`;
}

async function fetchFirestoreByField(field, value) {
  const url = buildRunQueryUrl();
  if (!url) return null;
  const body = {
    structuredQuery: {
      from: [{ collectionId: publicProductCollection }],
      where: {
        fieldFilter: {
          field: { fieldPath: field },
          op: "EQUAL",
          value: { stringValue: value }
        }
      },
      select: {
        fields: [
          { fieldPath: "code" },
          { fieldPath: "barcode" },
          { fieldPath: "name" },
          { fieldPath: "description" },
          { fieldPath: "category" },
          { fieldPath: "price" },
          { fieldPath: "listPrice" },
          { fieldPath: "cashPrice" },
          { fieldPath: "transferPrice" },
          { fieldPath: "imageUrl" },
          { fieldPath: "imageUrls" },
          { fieldPath: "sizes" },
          { fieldPath: "updatedAt" }
        ]
      },
      limit: 1
    }
  };
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    throw new Error("No se pudo consultar Firestore");
  }
  const data = await response.json();
  const doc = data.find((item) => item.document)?.document;
  return parseFirestoreDocument(doc);
}

async function fetchFirestoreById(id) {
  const projectId = config.firebase?.projectId;
  const apiKey = config.firebase?.apiKey;
  const collectionPath = buildPublicCollectionPath();
  if (!projectId || !apiKey || apiKey === "REEMPLAZAR" || !collectionPath) return null;
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collectionPath}/${encodeURIComponent(
    id
  )}?key=${apiKey}`;
  const response = await fetch(url);
  if (!response.ok) return null;
  const data = await response.json();
  return parseFirestoreDocument(data);
}

async function fetchDemoProduct(sku) {
  try {
    const response = await fetch("/data/products.json");
    const data = await response.json();
    const found = data.find((item) => item.id === sku || item.name === sku);
    if (!found) return null;
    return {
      sku: found.id,
      name: found.name,
      description: found.longDesc || found.desc,
      listPrice: "Consultar",
      cashPrice: "Consultar",
      images: found.images?.length ? found.images : [found.image],
      sizes: ["S", "M", "L"]
    };
  } catch (error) {
    console.warn("No se pudo cargar demo", error);
    return null;
  }
}

async function loadProduct() {
  if (!state.sku) {
    setStatus("Falta el SKU en la URL. Usá ?q=SKU.", true);
    elements.productTitle.textContent = "Producto no encontrado";
    return;
  }

  setStatus("Actualizando precios...", false);
  elements.productTitle.textContent = "Buscando producto...";

  let product = null;
  try {
    product = await fetchFirestoreByField("code", state.sku);
    if (!product) {
      product = await fetchFirestoreByField("barcode", state.sku);
    }
    if (!product) {
      product = await fetchFirestoreById(state.sku);
    }
  } catch (error) {
    console.warn("No se pudo consultar Firestore", error);
  }

  if (!product) {
    product = await fetchDemoProduct(state.sku);
  }

  if (!product) {
    setStatus("No encontramos el producto. Verificá el SKU.", true);
    elements.productTitle.textContent = "Producto no encontrado";
    return;
  }

  state.product = product;
  state.lastSync = new Date();
  setStatus("");
  updateSyncMeta();
  renderProduct(product);
}

function startPolling() {
  if (state.timer || state.paused) return;
  state.timer = setInterval(loadProduct, refreshIntervalMs);
}

function stopPolling() {
  if (!state.timer) return;
  clearInterval(state.timer);
  state.timer = null;
}

function init() {
  elements.brandName.textContent = brandName;
  const params = new URLSearchParams(window.location.search);
  state.sku = params.get("q")?.trim() || "";
  if (!tenantId) {
    setStatus("Falta configurar tenantId en config.js.", true);
  }
  loadProduct();
  startPolling();
  document.addEventListener("visibilitychange", () => {
    state.paused = document.hidden;
    if (state.paused) {
      stopPolling();
    } else {
      loadProduct();
      startPolling();
    }
  });
}

init();
