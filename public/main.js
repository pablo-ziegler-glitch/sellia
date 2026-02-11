const CONFIG_PLACEHOLDER = "REEMPLAZAR";

const CONFIG = {
  brandName: window.SELLIA_CONFIG?.brandName || "Sellia",
  youtubeVideoId: window.SELLIA_CONFIG?.youtubeVideoId || CONFIG_PLACEHOLDER,
  contact: {
    whatsapp: window.SELLIA_CONFIG?.contact?.whatsapp || CONFIG_PLACEHOLDER,
    instagram: window.SELLIA_CONFIG?.contact?.instagram || CONFIG_PLACEHOLDER,
    maps: window.SELLIA_CONFIG?.contact?.maps || CONFIG_PLACEHOLDER
  }
};

function isConfiguredValue(value) {
  return Boolean(value) && !String(value).startsWith(CONFIG_PLACEHOLDER);
}

function validatePublicConfig(config) {
  const requiredKeys = [
    { path: "brandName", value: config.brandName },
    { path: "youtubeVideoId", value: config.youtubeVideoId },
    { path: "contact.whatsapp", value: config.contact?.whatsapp },
    { path: "contact.instagram", value: config.contact?.instagram },
    { path: "contact.maps", value: config.contact?.maps }
  ];

  const missingKeys = requiredKeys.filter(({ value }) => !isConfiguredValue(value)).map(({ path }) => path);

  if (missingKeys.length > 0) {
    console.warn(
      `[config] Faltan valores válidos en SELLIA_CONFIG para: ${missingKeys.join(", ")}. Reemplazá los placeholders en public/config.js.`
    );
  }
}

validatePublicConfig(CONFIG);

const state = {
  products: []
};

const brandTargets = document.querySelectorAll("[data-brand]");
brandTargets.forEach((el) => {
  el.textContent = CONFIG.brandName;
});

const yearEl = document.getElementById("year");
if (yearEl) {
  yearEl.textContent = new Date().getFullYear();
}

const contactLinks = {
  whatsapp: CONFIG.contact.whatsapp,
  instagram: CONFIG.contact.instagram,
  maps: CONFIG.contact.maps
};

Object.entries(contactLinks).forEach(([key, url]) => {
  const link = document.querySelector(`[data-contact="${key}"]`);
  if (link && isConfiguredValue(url)) {
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
    if (!href || href.startsWith("#") || href.startsWith(CONFIG_PLACEHOLDER)) {
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
let modalFocusTrapEnabled = false;

const MODAL_FOCUSABLE_SELECTOR = [
  "a[href]",
  "area[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "iframe",
  "object",
  "embed",
  "[contenteditable]",
  "[tabindex]:not([tabindex='-1'])"
].join(",");

if (isConfiguredValue(CONFIG.youtubeVideoId)) {
  const posterUrl = `https://i.ytimg.com/vi/${CONFIG.youtubeVideoId}/hqdefault.jpg`;
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
    const videoId = CONFIG.youtubeVideoId;
    if (!isConfiguredValue(videoId)) {
      console.warn("Configurá youtubeVideoId en config.js para habilitar el video.");
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
const modalCarousel = document.getElementById("modalCarousel");
const modalCarouselTrack = document.getElementById("modalCarouselTrack");
const modalCarouselIndicators = document.getElementById("modalCarouselIndicators");
const modalCarouselPrev = modal?.querySelector("[data-carousel-prev]");
const modalCarouselNext = modal?.querySelector("[data-carousel-next]");
const modalTitle = document.getElementById("modalTitle");
const modalDescription = document.getElementById("modalDescription");
const modalTag = document.getElementById("modalTag");
const appBackgroundElements = modal
  ? Array.from(document.body.children).filter(
      (element) => element !== modal && element.tagName !== "SCRIPT"
    )
  : [];
const carouselState = {
  images: [],
  index: 0,
  productName: ""
};

function getModalFocusableElements() {
  if (!modal) return [];

  return Array.from(modal.querySelectorAll(MODAL_FOCUSABLE_SELECTOR)).filter((element) => {
    if (!(element instanceof HTMLElement)) return false;
    if (element.hasAttribute("disabled")) return false;
    if (element.getAttribute("aria-hidden") === "true") return false;
    const isVisible = element.offsetParent !== null || element === document.activeElement;
    return isVisible;
  });
}

function setBackgroundInteractivity(isModalOpen) {
  appBackgroundElements.forEach((element) => {
    if ("inert" in element) {
      element.inert = isModalOpen;
      if (!isModalOpen) {
        element.removeAttribute("inert");
      }
      return;
    }

    if (isModalOpen) {
      element.setAttribute("aria-hidden", "true");
    } else {
      element.removeAttribute("aria-hidden");
    }
  });
}

function activateModalFocusTrap() {
  modalFocusTrapEnabled = true;
}

function deactivateModalFocusTrap() {
  modalFocusTrapEnabled = false;
}

function getProductImages(product) {
  if (Array.isArray(product.images) && product.images.length > 0) {
    return product.images;
  }
  if (product.image) {
    return [product.image];
  }
  return [];
}

function updateCarousel() {
  if (!modalCarouselTrack || !modalCarouselIndicators) return;

  modalCarouselTrack.style.transform = `translateX(-${carouselState.index * 100}%)`;

  modalCarouselIndicators.querySelectorAll("button").forEach((button, index) => {
    const isActive = index === carouselState.index;
    button.classList.toggle("is-active", isActive);
    button.setAttribute("aria-current", isActive ? "true" : "false");
  });
}

function goToSlide(index) {
  if (carouselState.images.length === 0) return;
  const maxIndex = carouselState.images.length - 1;
  const nextIndex = Math.max(0, Math.min(index, maxIndex));
  carouselState.index = nextIndex;
  updateCarousel();
}

function stepCarousel(delta) {
  if (carouselState.images.length <= 1) return;
  const maxIndex = carouselState.images.length - 1;
  const nextIndex = (carouselState.index + delta + carouselState.images.length) % carouselState.images.length;
  carouselState.index = Math.max(0, Math.min(nextIndex, maxIndex));
  updateCarousel();
}

function renderCarousel(product) {
  if (!modalCarousel || !modalCarouselTrack || !modalCarouselIndicators) return;

  const images = getProductImages(product);
  carouselState.images = images;
  carouselState.index = 0;
  carouselState.productName = product.name;

  modalCarouselTrack.innerHTML = images
    .map(
      (image, index) => `
        <div class="carousel-slide">
          <img src="${image}" alt="${product.name} - imagen ${index + 1}" loading="lazy" />
        </div>
      `
    )
    .join("");

  modalCarouselIndicators.innerHTML = images
    .map(
      (_, index) => `
        <button type="button" data-carousel-index="${index}" aria-label="Ver imagen ${
          index + 1
        } de ${images.length}"></button>
      `
    )
    .join("");

  const hasMultipleImages = images.length > 1;
  modalCarousel.setAttribute("aria-live", hasMultipleImages ? "polite" : "off");

  if (modalCarouselPrev) {
    modalCarouselPrev.disabled = !hasMultipleImages;
  }
  if (modalCarouselNext) {
    modalCarouselNext.disabled = !hasMultipleImages;
  }

  modalCarouselIndicators.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.carouselIndex);
      if (!Number.isNaN(index)) {
        goToSlide(index);
      }
    });
  });

  updateCarousel();
}

function openModal(id) {
  const product = state.products.find((item) => item.id === id);
  if (!product || !modal) return;

  lastFocusedElement = document.activeElement;
  renderCarousel(product);
  modalTitle.textContent = product.name;
  modalDescription.textContent = product.longDesc || product.desc;
  modalTag.textContent = product.tag || "Colección";

  modal.setAttribute("aria-hidden", "false");
  setBackgroundInteractivity(true);
  activateModalFocusTrap();
  document.body.style.overflow = "hidden";

  trackEvent("product_detail", { id: product.id });

  const [firstFocusable] = getModalFocusableElements();
  firstFocusable?.focus();
}

function closeModal() {
  if (!modal) return;

  deactivateModalFocusTrap();
  setBackgroundInteractivity(false);
  modal.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";

  if (
    lastFocusedElement instanceof HTMLElement &&
    lastFocusedElement.isConnected &&
    !lastFocusedElement.hasAttribute("disabled")
  ) {
    lastFocusedElement.focus();
  }

  lastFocusedElement = null;
}

if (modal) {
  modal.addEventListener("click", (event) => {
    if (event.target.matches("[data-close]")) {
      closeModal();
    }
    if (event.target.matches("[data-carousel-prev]")) {
      stepCarousel(-1);
    }
    if (event.target.matches("[data-carousel-next]")) {
      stepCarousel(1);
    }
  });

  let touchStartX = 0;
  let touchEndX = 0;

  if (modalCarousel) {
    modalCarousel.addEventListener(
      "touchstart",
      (event) => {
        if (event.touches.length === 0) return;
        touchStartX = event.touches[0].clientX;
      },
      { passive: true }
    );

    modalCarousel.addEventListener(
      "touchend",
      (event) => {
        touchEndX = event.changedTouches[0].clientX;
        const delta = touchStartX - touchEndX;
        if (Math.abs(delta) > 40) {
          stepCarousel(delta > 0 ? 1 : -1);
        }
      },
      { passive: true }
    );
  }

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.getAttribute("aria-hidden") === "false") {
      closeModal();
      return;
    }

    if (modal.getAttribute("aria-hidden") === "false") {
      if (event.key === "Tab" && modalFocusTrapEnabled) {
        const focusableElements = getModalFocusableElements();
        if (focusableElements.length === 0) {
          event.preventDefault();
          return;
        }

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];
        const activeElement = document.activeElement;

        if (event.shiftKey && activeElement === firstElement) {
          event.preventDefault();
          lastElement.focus();
        } else if (!event.shiftKey && activeElement === lastElement) {
          event.preventDefault();
          firstElement.focus();
        }
      }

      if (event.key === "ArrowLeft") {
        stepCarousel(-1);
      }
      if (event.key === "ArrowRight") {
        stepCarousel(1);
      }
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
