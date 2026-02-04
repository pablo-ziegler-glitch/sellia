import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-app.js";
import {
  getFirestore,
  collection,
  query,
  where,
  limit,
  getDocs
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-firestore.js";

const CONFIG = {
  BRAND_NAME: "Valkirja",
  WHATSAPP_URL: "REEMPLAZAR",
  FIREBASE_CONFIG: {
    apiKey: "REEMPLAZAR",
    authDomain: "REEMPLAZAR",
    projectId: "REEMPLAZAR",
    storageBucket: "REEMPLAZAR",
    messagingSenderId: "REEMPLAZAR",
    appId: "REEMPLAZAR"
  }
};

const DEFAULT_PLACEHOLDER = "/assets/placeholder.svg";

const ui = {
  status: document.getElementById("productStatus"),
  layout: document.getElementById("productLayout"),
  galleryMain: document.getElementById("productGalleryMain"),
  galleryThumbs: document.getElementById("productGalleryThumbs"),
  name: document.getElementById("productName"),
  sku: document.getElementById("productSku"),
  tag: document.getElementById("productTag"),
  description: document.getElementById("productDescription"),
  priceList: document.getElementById("priceList"),
  priceCash: document.getElementById("priceCash"),
  sizes: document.getElementById("productSizes"),
  contactButton: document.getElementById("contactButton"),
  contactHint: document.getElementById("contactHint")
};

function setStatus(message) {
  if (!ui.status) return;
  ui.status.textContent = message;
  ui.status.hidden = false;
}

function showLayout() {
  if (ui.layout) {
    ui.layout.hidden = false;
  }
  if (ui.status) {
    ui.status.hidden = true;
  }
}

function normalizeImages(data) {
  if (Array.isArray(data.images) && data.images.length) return data.images;
  if (Array.isArray(data.photos) && data.photos.length) return data.photos;
  if (data.image) return [data.image];
  return [DEFAULT_PLACEHOLDER];
}

function formatPrice(value, currency = "ARS") {
  if (value === undefined || value === null || value === "") {
    return "Consultar";
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return new Intl.NumberFormat("es-AR", {
    style: "currency",
    currency,
    maximumFractionDigits: 0
  }).format(numeric);
}

function renderGallery(images, name) {
  if (!ui.galleryMain || !ui.galleryThumbs) return;

  const [primary, ...rest] = images;
  ui.galleryMain.innerHTML = `
    <img src="${primary}" alt="${name}" loading="lazy" />
  `;

  ui.galleryThumbs.innerHTML = images
    .map(
      (image, index) => `
        <button type="button" class="thumb" data-image-index="${index}">
          <img src="${image}" alt="${name} - vista ${index + 1}" loading="lazy" />
        </button>
      `
    )
    .join("");

  const thumbButtons = ui.galleryThumbs.querySelectorAll("button");
  thumbButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.imageIndex);
      if (Number.isNaN(index)) return;
      const nextImage = images[index];
      ui.galleryMain.innerHTML = `
        <img src="${nextImage}" alt="${name}" loading="lazy" />
      `;
    });
  });
}

function renderSizes(sizes) {
  if (!ui.sizes) return;
  if (!Array.isArray(sizes) || sizes.length === 0) {
    ui.sizes.innerHTML = "<span class=\"size-pill\">Consultar talles</span>";
    return;
  }

  ui.sizes.innerHTML = sizes
    .map((size) => `<span class="size-pill">${size}</span>`)
    .join("");
}

function applyUtmToContact() {
  if (!ui.contactButton) return;

  const utmParams = new URLSearchParams(window.location.search);
  const utm = new URLSearchParams();
  for (const [key, value] of utmParams.entries()) {
    if (key.startsWith("utm_")) {
      utm.set(key, value);
    }
  }

  if ([...utm.keys()].length === 0) return;

  const href = ui.contactButton.getAttribute("href");
  if (!href || href.startsWith("#") || href.startsWith("REEMPLAZAR")) return;
  try {
    const url = new URL(href);
    utm.forEach((value, key) => {
      url.searchParams.set(key, value);
    });
    ui.contactButton.href = url.toString();
  } catch (error) {
    console.warn("No se pudo aplicar UTM", error);
  }
}

function buildWhatsappLink(name, sku) {
  if (!CONFIG.WHATSAPP_URL || CONFIG.WHATSAPP_URL.startsWith("REEMPLAZAR")) {
    return "#";
  }
  try {
    const url = new URL(CONFIG.WHATSAPP_URL);
    const message = `Hola! Quiero consultar por el producto ${name} (SKU ${sku}).`;
    url.searchParams.set("text", message);
    return url.toString();
  } catch (error) {
    console.warn("WhatsApp URL inválida", error);
    return CONFIG.WHATSAPP_URL;
  }
}

function renderProduct(data) {
  if (!data) {
    setStatus("Producto no encontrado.");
    return;
  }

  const images = normalizeImages(data);
  renderGallery(images, data.name);

  if (ui.tag) ui.tag.textContent = data.tag || "Producto";
  if (ui.name) ui.name.textContent = data.name;
  if (ui.sku) ui.sku.textContent = `SKU: ${data.sku}`;
  if (ui.description) ui.description.textContent = data.description || "";
  if (ui.priceList) ui.priceList.textContent = formatPrice(data.priceList, data.currency);
  if (ui.priceCash) ui.priceCash.textContent = formatPrice(data.priceCash, data.currency);

  renderSizes(data.sizes);

  if (ui.contactButton) {
    ui.contactButton.href = buildWhatsappLink(data.name, data.sku);
  }
  if (ui.contactHint) {
    ui.contactHint.textContent = "Respondemos rápido con stock y tiempos de entrega.";
  }

  document.title = `${data.name} | ${CONFIG.BRAND_NAME}`;
  showLayout();
  applyUtmToContact();
}

async function fetchProductBySku(db, sku) {
  const productsRef = collection(db, "products");
  const skuQuery = query(productsRef, where("code", "==", sku), limit(1));
  const snapshot = await getDocs(skuQuery);

  if (!snapshot.empty) {
    return snapshot.docs[0];
  }

  const barcodeQuery = query(productsRef, where("barcode", "==", sku), limit(1));
  const barcodeSnapshot = await getDocs(barcodeQuery);
  if (!barcodeSnapshot.empty) {
    return barcodeSnapshot.docs[0];
  }

  return null;
}

function normalizeProduct(doc, sku) {
  if (!doc) return null;
  const data = doc.data();
  return {
    id: doc.id,
    sku,
    name: data.name || "Producto sin nombre",
    description: data.description || data.desc || "",
    tag: data.tag || "",
    priceList: data.priceList ?? data.price_list ?? null,
    priceCash: data.priceCash ?? data.price_cash ?? null,
    currency: data.currency || "ARS",
    sizes: data.sizes || data.talles || [],
    images: data.images || data.photos || [],
    image: data.image || ""
  };
}

async function init() {
  const searchParams = new URLSearchParams(window.location.search);
  const rawSku = searchParams.get("q");
  const sku = rawSku ? rawSku.trim() : "";

  if (!sku) {
    setStatus("Producto no encontrado. Verificá el SKU.");
    return;
  }

  if (CONFIG.FIREBASE_CONFIG.projectId === "REEMPLAZAR") {
    setStatus("Configurá Firebase para ver productos reales.");
    return;
  }

  setStatus("Buscando producto...");

  try {
    const app = initializeApp(CONFIG.FIREBASE_CONFIG);
    const db = getFirestore(app);
    const doc = await fetchProductBySku(db, sku);
    const product = normalizeProduct(doc, sku);
    if (!product) {
      setStatus("Producto no encontrado.");
      return;
    }
    renderProduct(product);
  } catch (error) {
    console.error("Error al cargar producto", error);
    setStatus("No pudimos cargar el producto. Probá más tarde.");
  }
}

init();
