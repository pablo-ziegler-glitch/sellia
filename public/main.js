const config = window.STORE_CONFIG || {};
const runtimeConfig = window.__STORE_RUNTIME_CONFIG__ || {};
const safeDom = window.SafeDom || {};

const GENERIC_LANDING_CONFIG = {
  brand: {
    name: "Tu Marca",
    tagline: "digitalizá tu negocio y vendé más"
  },
  seo: {
    title: "Tu Marca | Impulsá tu tienda",
    description: "Llevá tu negocio al siguiente nivel con una presencia digital profesional.",
    ogImage: "/assets/og-image.svg"
  },
  hero: {
    title: "Tu negocio, listo para crecer online",
    subtitle: "Mostrá tus productos, captá clientes y centralizá tu operación en una sola plataforma."
  },
  cta: {
    primary: "CONOCÉ LA SOLUCIÓN",
    secondary: "VER DEMO"
  },
  story: {
    title: "De catálogo improvisado a canal digital escalable",
    description:
      "Esta landing es una base para nuevas tiendas: una estructura clara para comunicar valor, generar confianza y convertir visitas en ventas."
  },
  purpose: {
    title: "Nuestra propuesta",
    description:
      "Simplificar la operación comercial con herramientas que ahorran tiempo, mejoran la atención y permiten decisiones basadas en datos."
  }
};

const TENANT_LANDING_PRESETS = {
  floki: {
    brand: {
      name: "FLOKI",
      tagline: "conquista a tu competencia"
    },
    seo: {
      title: "FLOKI | Digitalizá tu negocio y vendé con foco",
      description:
        "FLOKI te ayuda a ordenar catálogo, acelerar ventas y profesionalizar la operación comercial con una experiencia simple y efectiva.",
      ogImage: "/assets/og-image.svg"
    },
    hero: {
      title: "FLOKI: la historia de cómo convertir caos operativo en crecimiento",
      subtitle:
        "Nacimos para resolver el día a día comercial: menos fricción, más control, y una tienda digital que trabaja para vos 24/7."
    },
    cta: {
      primary: "QUIERO DIGITALIZAR MI NEGOCIO",
      secondary: "VER CÓMO FUNCIONA"
    },
    story: {
      title: "Historia FLOKI",
      description:
        "Vimos comercios perdiendo ventas por procesos manuales, catálogos desactualizados y respuestas tardías. FLOKI surge para transformar ese problema en ventaja competitiva."
    },
    purpose: {
      title: "Propósito FLOKI",
      description:
        "Impulsar a cada negocio a operar como una marca moderna: catálogo vivo, comunicación consistente y decisiones comerciales apoyadas en datos."
    }
  }
};

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

function sanitizeText(value, fallback = "") {
  const sanitized = safeDom.sanitizeText ? safeDom.sanitizeText(value) : String(value ?? "");
  const trimmed = sanitized.trim();
  return trimmed || fallback;
}

function sanitizeHttpsUrl(value, fallback = "") {
  const candidate = sanitizeText(value, "");
  if (!candidate) return fallback;
  const safeUrl = safeDom.toAllowedHttpsUrl?.(candidate);
  return safeUrl || fallback;
}

function mergeLandingConfig(baseConfig, overrideConfig) {
  return {
    brand: {
      name: sanitizeText(overrideConfig?.brand?.name, baseConfig.brand.name),
      tagline: sanitizeText(overrideConfig?.brand?.tagline, baseConfig.brand.tagline)
    },
    seo: {
      title: sanitizeText(overrideConfig?.seo?.title, baseConfig.seo.title),
      description: sanitizeText(overrideConfig?.seo?.description, baseConfig.seo.description),
      ogImage: sanitizeHttpsUrl(overrideConfig?.seo?.ogImage, baseConfig.seo.ogImage)
    },
    hero: {
      title: sanitizeText(overrideConfig?.hero?.title, baseConfig.hero.title),
      subtitle: sanitizeText(overrideConfig?.hero?.subtitle, baseConfig.hero.subtitle)
    },
    cta: {
      primary: sanitizeText(overrideConfig?.cta?.primary, baseConfig.cta.primary),
      secondary: sanitizeText(overrideConfig?.cta?.secondary, baseConfig.cta.secondary)
    },
    story: {
      title: sanitizeText(overrideConfig?.story?.title, baseConfig.story.title),
      description: sanitizeText(overrideConfig?.story?.description, baseConfig.story.description)
    },
    purpose: {
      title: sanitizeText(overrideConfig?.purpose?.title, baseConfig.purpose.title),
      description: sanitizeText(overrideConfig?.purpose?.description, baseConfig.purpose.description)
    }
  };
}

function getRuntimeLandingConfig() {
  const runtimeLanding = runtimeConfig.landingMain || runtimeConfig.landing || {};
  const runtimeBrand = runtimeConfig.brand || {};
  const runtimeSeo = runtimeConfig.seo || {};
  return {
    brand: {
      name: runtimeLanding.brand?.name || runtimeBrand.name || runtimeConfig.brandName,
      tagline: runtimeLanding.brand?.tagline || runtimeBrand.tagline || runtimeConfig.brandTagline
    },
    seo: {
      title: runtimeLanding.seo?.title || runtimeSeo.title,
      description: runtimeLanding.seo?.description || runtimeSeo.description,
      ogImage: runtimeLanding.seo?.ogImage || runtimeSeo.ogImage
    },
    hero: {
      title: runtimeLanding.hero?.title,
      subtitle: runtimeLanding.hero?.subtitle
    },
    cta: {
      primary: runtimeLanding.cta?.primary,
      secondary: runtimeLanding.cta?.secondary
    },
    story: {
      title: runtimeLanding.story?.title,
      description: runtimeLanding.story?.description
    },
    purpose: {
      title: runtimeLanding.purpose?.title,
      description: runtimeLanding.purpose?.description
    }
  };
}

function parseFirestoreDocumentMap(fields = {}) {
  const parsed = {};
  Object.entries(fields).forEach(([key, value]) => {
    if (!value || typeof value !== "object") return;
    if (typeof value.stringValue === "string") {
      parsed[key] = value.stringValue;
      return;
    }
    if (value.mapValue?.fields) {
      parsed[key] = parseFirestoreDocumentMap(value.mapValue.fields);
    }
  });
  return parsed;
}

async function fetchPublicLandingConfig() {
  const projectId = config.firebase?.projectId;
  const apiKey = config.firebase?.apiKey;
  const tenantId = config.tenantId;

  if (!projectId || !apiKey || !tenantId) return null;

  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${encodeURIComponent(
      tenantId
    )}/config/landing_main?key=${apiKey}`
  );

  if (!response.ok) return null;

  const payload = await response.json();
  return parseFirestoreDocumentMap(payload?.fields || {});
}

function setTextBySelector(selector, value) {
  document.querySelectorAll(selector).forEach((element) => {
    element.textContent = value;
  });
}

function applyLandingConfig(landingConfig) {
  const brandName = landingConfig.brand.name;
  setTextBySelector("[data-brand]", brandName);

  document.querySelectorAll("[data-landing]").forEach((element) => {
    const path = element.dataset.landing;
    const value = path
      .split(".")
      .reduce((acc, key) => (acc && Object.prototype.hasOwnProperty.call(acc, key) ? acc[key] : ""), landingConfig);
    if (typeof value === "string" && value.trim()) {
      element.textContent = value;
    }
  });

  document.querySelectorAll("[data-landing-seo]").forEach((element) => {
    const seoKey = element.dataset.landingSeo;
    switch (seoKey) {
      case "title":
        document.title = landingConfig.seo.title;
        break;
      case "description":
      case "og:title":
      case "og:description":
      case "twitter:title":
      case "twitter:description":
        element.setAttribute(
          "content",
          seoKey.includes("title") ? landingConfig.seo.title : landingConfig.seo.description
        );
        break;
      case "og:image":
      case "twitter:image":
        element.setAttribute("content", landingConfig.seo.ogImage);
        break;
      default:
        break;
    }
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
  tag.textContent = safeDom.sanitizeText ? safeDom.sanitizeText(product.tag || "Colección") : product.tag || "Colección";

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
  const tenantKey = String(config.tenantId || "").toLowerCase();
  const tenantPreset = TENANT_LANDING_PRESETS[tenantKey] || {};
  const runtimeLandingConfig = getRuntimeLandingConfig();

  let landingConfig = mergeLandingConfig(GENERIC_LANDING_CONFIG, tenantPreset);
  landingConfig = mergeLandingConfig(landingConfig, runtimeLandingConfig);

  try {
    const firestoreLandingConfig = await fetchPublicLandingConfig();
    if (firestoreLandingConfig) {
      landingConfig = mergeLandingConfig(landingConfig, firestoreLandingConfig);
    }
  } catch (error) {
    console.warn("No se pudo cargar tenants/{tenantId}/config/landing_main.", error);
  }

  applyLandingConfig(landingConfig);
  setupContacts();
  setupVideo();
  await renderLandingProducts();
}

bootstrap();
