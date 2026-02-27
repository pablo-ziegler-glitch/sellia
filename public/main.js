const config = window.STORE_CONFIG || {};
const safeDom = window.SafeDom || {};

const elements = {
  year: document.getElementById("year"),
  secondarySectionsMount: document.getElementById("secondarySectionsMount"),
  secondarySectionsTemplate: document.getElementById("secondarySectionsTemplate")
};

function isConfigured(value) {
  return typeof value === "string" && value.trim() !== "" && !value.startsWith("REEMPLAZAR");
}

function sanitizeVideoId(value) {
  if (!isConfigured(value)) return "";
  return /^[a-zA-Z0-9_-]{11}$/.test(value) ? value : "";
}

function setupBrandAndYear() {
  const brandName = safeDom.sanitizeText ? safeDom.sanitizeText(config.brandName || "FLOKI") : (config.brandName || "FLOKI");
  document.querySelectorAll("[data-brand]").forEach((el) => {
    el.textContent = `${brandName} — Hecho con pasión.`;
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
  const videoPlaceholder = document.getElementById("videoPlaceholder");
  const videoPoster = document.getElementById("videoPoster");
  const videoPlayButton = document.querySelector(".video-play");

  const videoId = sanitizeVideoId(config.youtubeVideoId);
  if (videoId && videoPoster) {
    safeDom.setSafeUrl?.(videoPoster, "src", `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`);
  }

  if (!videoPlayButton || !videoPlaceholder) return;

  videoPlayButton.addEventListener("click", () => {
    if (!videoId) return;
    const iframe = document.createElement("iframe");
    safeDom.setSafeUrl?.(iframe, "src", `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1`);
    iframe.title = "Video de la historia";
    iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture";
    iframe.allowFullscreen = true;
    iframe.loading = "lazy";
    videoPlaceholder.replaceChildren(iframe);
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
  const productGrid = document.getElementById("productGrid");
  if (!productGrid) return;
  try {
    const response = await fetch("/data/products.json", { cache: "force-cache" });
    if (!response.ok) throw new Error("No se pudo cargar la data local");
    const products = await response.json();

    const fragment = document.createDocumentFragment();
    products.slice(0, 6).forEach((product) => {
      fragment.appendChild(createProductCard(product));
    });
    productGrid.replaceChildren(fragment);
  } catch (error) {
    console.warn("No se pudieron cargar los productos de la landing", error);
    const warning = document.createElement("p");
    warning.className = "muted";
    warning.textContent = "No se pudo cargar la vitrina.";
    productGrid.replaceChildren(warning);
  }
}

function renderSecondarySections() {
  if (!elements.secondarySectionsMount || !elements.secondarySectionsTemplate) return;
  if (elements.secondarySectionsMount.childElementCount > 0) return;
  const secondaryContent = elements.secondarySectionsTemplate.content.cloneNode(true);
  elements.secondarySectionsMount.replaceChildren(secondaryContent);
}

function scheduleSecondaryRender() {
  const run = () => {
    renderSecondarySections();
    setupContacts();
    setupVideo();
    renderLandingProducts();
  };

  if (typeof window.requestIdleCallback === "function") {
    window.requestIdleCallback(run, { timeout: 1500 });
    return;
  }

  setTimeout(run, 0);
}

function reportVital(metricName, value) {
  const payload = {
    metric: metricName,
    value,
    page: window.location.pathname,
    tenantId: config.tenantId || "unknown",
    timestamp: Date.now()
  };

  if (typeof window.gtag === "function") {
    window.gtag("event", "web_vital", {
      event_category: "Web Vitals",
      event_label: metricName,
      value: Math.round(value),
      metric_name: metricName,
      metric_value: value,
      tenant_id: payload.tenantId
    });
  }

  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push({ event: "web_vital", ...payload });

  const analyticsEndpoint = config.analytics?.webVitalsEndpoint;
  if (!isConfigured(analyticsEndpoint)) return;

  const body = JSON.stringify(payload);
  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: "application/json" });
    navigator.sendBeacon(analyticsEndpoint, blob);
    return;
  }

  fetch(analyticsEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true
  }).catch(() => {});
}

function observeWebVitals() {
  let clsValue = 0;
  let inpValue = 0;
  let lcpValue = 0;

  if ("PerformanceObserver" in window) {
    try {
      const clsObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (!entry.hadRecentInput) {
            clsValue += entry.value;
          }
        }
      });
      clsObserver.observe({ type: "layout-shift", buffered: true });

      const lcpObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const lastEntry = entries[entries.length - 1];
        if (lastEntry) {
          lcpValue = lastEntry.startTime;
        }
      });
      lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });

      const inpObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          const latency = entry.processingStart - entry.startTime;
          inpValue = Math.max(inpValue, latency);
        }
      });
      inpObserver.observe({ type: "event", buffered: true, durationThreshold: 40 });

      document.addEventListener("visibilitychange", () => {
        if (document.visibilityState !== "hidden") return;
        reportVital("CLS", Number(clsValue.toFixed(4)));
        if (lcpValue > 0) reportVital("LCP", Number(lcpValue.toFixed(2)));
        if (inpValue > 0) reportVital("INP", Number(inpValue.toFixed(2)));
      });
    } catch (error) {
      console.warn("Web Vitals observer no disponible", error);
    }
  }
}

async function bootstrap() {
  setupBrandAndYear();
  observeWebVitals();
  scheduleSecondaryRender();

  await Promise.resolve(window.__STORE_CONFIG_READY__);

  setupContacts();
  setupVideo();
  renderLandingProducts();
}

bootstrap();
