const config = window.STORE_CONFIG || {};
const safeDom = window.SafeDom || {};

const elements = {
  year: document.getElementById("year"),
  videoPlaceholder: document.getElementById("videoPlaceholder"),
  videoPoster: document.getElementById("videoPoster"),
  videoPlayButton: document.querySelector(".video-play"),
  productGrid: document.getElementById("productGrid")
};

function isConfigured(value) {
  return typeof value === "string" && value.trim() !== "" && !value.startsWith("REEMPLAZAR");
}

function sanitizeVideoId(value) {
  if (!isConfigured(value)) return "";
  return /^[a-zA-Z0-9_-]{11}$/.test(value) ? value : "";
}

function setupBrandAndYear() {
  const brandName = safeDom.sanitizeText ? safeDom.sanitizeText(config.brandName || "Tu tienda") : (config.brandName || "Tu tienda");
  document.querySelectorAll("[data-brand]").forEach((el) => {
    el.textContent = brandName;
  });
  if (elements.year) {
    elements.year.textContent = String(new Date().getFullYear());
  }
}

function setupContacts() {
  const contactMap = {
    whatsapp: config.contact?.whatsapp,
    instagram: config.contact?.instagram,
    maps: config.contact?.maps
  };

  Object.entries(contactMap).forEach(([key, value]) => {
    const link = document.querySelector(`[data-contact="${key}"]`);
    if (!link || !isConfigured(value)) return;
    safeDom.setSafeUrl?.(link, "href", value);
  });
}

function setupVideo() {
  const videoId = sanitizeVideoId(config.youtubeVideoId);
  if (videoId && elements.videoPoster) {
    safeDom.setSafeUrl?.(elements.videoPoster, "src", `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`);
  }

  if (!elements.videoPlayButton || !elements.videoPlaceholder) return;

  elements.videoPlayButton.addEventListener("click", () => {
    if (!videoId) return;
    const iframe = document.createElement("iframe");
    safeDom.setSafeUrl?.(iframe, "src", `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1`);
    iframe.title = "Video de la historia";
    iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture";
    iframe.allowFullscreen = true;
    iframe.loading = "lazy";
    elements.videoPlaceholder.replaceChildren(iframe);
  });
}

function createProductCard(product) {
  const article = document.createElement("article");
  article.className = "product-card";

  const image = document.createElement("img");
  safeDom.setSafeUrl?.(image, "src", product.image);
  image.alt = safeDom.sanitizeText ? safeDom.sanitizeText(product.name) : String(product.name || "Producto");
  image.loading = "lazy";

  const tag = document.createElement("span");
  tag.className = "tag";
  tag.textContent = safeDom.sanitizeText ? safeDom.sanitizeText(product.tag || "Colección") : (product.tag || "Colección");

  const title = document.createElement("h3");
  title.textContent = safeDom.sanitizeText ? safeDom.sanitizeText(product.name) : String(product.name || "Producto");

  const desc = document.createElement("p");
  desc.textContent = safeDom.sanitizeText ? safeDom.sanitizeText(product.desc || "") : String(product.desc || "");

  article.appendChild(image);
  article.appendChild(tag);
  article.appendChild(title);
  article.appendChild(desc);

  return article;
}

async function renderLandingProducts() {
  if (!elements.productGrid) return;
  try {
    const response = await fetch("/data/products.json");
    if (!response.ok) throw new Error("No se pudo cargar la data local");
    const products = await response.json();

    const fragment = document.createDocumentFragment();
    products.slice(0, 6).forEach((product) => {
      fragment.appendChild(createProductCard(product));
    });
    elements.productGrid.replaceChildren(fragment);
  } catch (error) {
    console.warn("No se pudieron cargar los productos de la landing", error);
    const warning = document.createElement("p");
    warning.className = "muted";
    warning.textContent = "No se pudo cargar la vitrina.";
    elements.productGrid.replaceChildren(warning);
  }
}

async function bootstrap() {
  await Promise.resolve(window.__STORE_CONFIG_READY__);
  setupBrandAndYear();
  setupContacts();
  setupVideo();
  await renderLandingProducts();
}

bootstrap();
