const CONFIG = {
  BRAND_NAME: "Valkirja",
  YOUTUBE_VIDEO_ID: "REEMPLAZAR",
  WHATSAPP_URL: "REEMPLAZAR",
  INSTAGRAM_URL: "REEMPLAZAR",
  MAPS_URL: "REEMPLAZAR"
};

const state = {
  products: []
};

const brandTargets = document.querySelectorAll("[data-brand]");
brandTargets.forEach((el) => {
  el.textContent = CONFIG.BRAND_NAME;
});

const yearEl = document.getElementById("year");
if (yearEl) {
  yearEl.textContent = new Date().getFullYear();
}

const contactLinks = {
  whatsapp: CONFIG.WHATSAPP_URL,
  instagram: CONFIG.INSTAGRAM_URL,
  maps: CONFIG.MAPS_URL
};

Object.entries(contactLinks).forEach(([key, url]) => {
  const link = document.querySelector(`[data-contact="${key}"]`);
  if (link && url && !url.startsWith("REEMPLAZAR")) {
    link.href = url;
  }
});

const utmParams = new URLSearchParams(window.location.search);
const utm = new URLSearchParams();
for (const [key, value] of utmParams.entries()) {
  if (key.startsWith("utm_")) {
    utm.set(key, value);
  }
}

if ([...utm.keys()].length) {
  document.querySelectorAll("[data-utm-link]").forEach((link) => {
    const href = link.getAttribute("href");
    if (!href || href.startsWith("#") || href.startsWith("REEMPLAZAR")) {
      return;
    }
    try {
      const url = new URL(href);
      utm.forEach((value, key) => {
        url.searchParams.set(key, value);
      });
      link.href = url.toString();
    } catch (error) {
      console.warn("No se pudo aplicar UTM", error);
    }
  });
}

const videoPlaceholder = document.getElementById("videoPlaceholder");
const videoPoster = document.getElementById("videoPoster");
const videoPlayButton = document.querySelector(".video-play");
let lastFocusedElement = null;

if (CONFIG.YOUTUBE_VIDEO_ID !== "REEMPLAZAR") {
  const posterUrl = `https://i.ytimg.com/vi/${CONFIG.YOUTUBE_VIDEO_ID}/hqdefault.jpg`;
  videoPoster.src = posterUrl;
}

function trackEvent(name, detail = {}) {
  if (window.analytics && typeof window.analytics.track === "function") {
    window.analytics.track(name, detail);
  } else {
    console.log(`[analytics] ${name}`, detail);
  }
}

if (videoPlayButton) {
  videoPlayButton.addEventListener("click", () => {
    const videoId = CONFIG.YOUTUBE_VIDEO_ID;
    if (!videoId || videoId === "REEMPLAZAR") {
      console.warn("Configurar YOUTUBE_VIDEO_ID para habilitar el video.");
      return;
    }

    const iframe = document.createElement("iframe");
    iframe.src = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1`;
    iframe.title = "Video de la historia de Valkirja";
    iframe.allow =
      "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture";
    iframe.allowFullscreen = true;
    iframe.loading = "lazy";

    videoPlaceholder.innerHTML = "";
    videoPlaceholder.appendChild(iframe);
    trackEvent("video_play", { videoId });
  });
}

const observer = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.2 }
);

document.querySelectorAll("[data-reveal]").forEach((el) => observer.observe(el));

async function loadProducts() {
  try {
    const response = await fetch("/data/products.json");
    const data = await response.json();
    state.products = data;
    renderProducts();
  } catch (error) {
    console.error("No se pudieron cargar los productos", error);
  }
}

function renderProducts() {
  const grid = document.getElementById("productGrid");
  if (!grid) return;

  grid.innerHTML = state.products
    .map(
      (product) => `
        <article class="product-card" data-reveal>
          <img src="${product.image}" alt="${product.name}" loading="lazy" />
          <span class="tag">${product.tag || "Colección"}</span>
          <h3>${product.name}</h3>
          <p>${product.desc}</p>
          <button class="button ghost" type="button" data-product="${product.id}" aria-label="Ver detalle de ${product.name}">
            Ver detalle
          </button>
        </article>
      `
    )
    .join("");

  grid.querySelectorAll("[data-product]").forEach((button) => {
    button.addEventListener("click", () => openModal(button.dataset.product));
  });

  grid.querySelectorAll("[data-reveal]").forEach((el) => observer.observe(el));
}

const modal = document.getElementById("productModal");
const modalImage = document.getElementById("modalImage");
const modalTitle = document.getElementById("modalTitle");
const modalDescription = document.getElementById("modalDescription");
const modalTag = document.getElementById("modalTag");

function openModal(id) {
  const product = state.products.find((item) => item.id === id);
  if (!product || !modal) return;

  lastFocusedElement = document.activeElement;
  modalImage.src = product.image;
  modalImage.alt = product.name;
  modalTitle.textContent = product.name;
  modalDescription.textContent = product.longDesc || product.desc;
  modalTag.textContent = product.tag || "Colección";

  modal.setAttribute("aria-hidden", "false");
  document.body.style.overflow = "hidden";

  trackEvent("product_detail", { id: product.id });
  modal.querySelector(".modal-close").focus();
}

function closeModal() {
  if (!modal) return;
  modal.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";
  if (lastFocusedElement) {
    lastFocusedElement.focus();
  }
}

if (modal) {
  modal.addEventListener("click", (event) => {
    if (event.target.matches("[data-close]")) {
      closeModal();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.getAttribute("aria-hidden") === "false") {
      closeModal();
    }
  });
}

const whatsappLink = document.querySelector('[data-contact="whatsapp"]');
if (whatsappLink) {
  whatsappLink.addEventListener("click", () => {
    trackEvent("contact_whatsapp", { url: whatsappLink.href });
  });
}

loadProducts();
