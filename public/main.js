const config = window.STORE_CONFIG || {};

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

function setupBrandAndYear() {
  const brandName = config.brandName || "Tu tienda";
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
    link.href = value;
  });
}

function setupVideo() {
  const videoId = config.youtubeVideoId;
  if (isConfigured(videoId) && elements.videoPoster) {
    elements.videoPoster.src = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
  }

  if (!elements.videoPlayButton || !elements.videoPlaceholder) return;

  elements.videoPlayButton.addEventListener("click", () => {
    if (!isConfigured(videoId)) return;
    const iframe = document.createElement("iframe");
    iframe.src = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1`;
    iframe.title = "Video de la historia";
    iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture";
    iframe.allowFullscreen = true;
    iframe.loading = "lazy";
    elements.videoPlaceholder.innerHTML = "";
    elements.videoPlaceholder.appendChild(iframe);
  });
}

async function renderLandingProducts() {
  if (!elements.productGrid) return;
  try {
    const response = await fetch("/data/products.json");
    if (!response.ok) throw new Error("No se pudo cargar la data local");
    const products = await response.json();
    elements.productGrid.innerHTML = products
      .slice(0, 6)
      .map(
        (product) => `
          <article class="product-card">
            <img src="${product.image}" alt="${product.name}" loading="lazy" />
            <span class="tag">${product.tag || "Colección"}</span>
            <h3>${product.name}</h3>
            <p>${product.desc || ""}</p>
          </article>
        `
      )
      .join("");
  } catch (error) {
    console.warn("No se pudieron cargar los productos de la landing", error);
    elements.productGrid.innerHTML = "<p class='muted'>No se pudo cargar la vitrina.</p>";
  }
}

setupBrandAndYear();
setupContacts();
setupVideo();
renderLandingProducts();
