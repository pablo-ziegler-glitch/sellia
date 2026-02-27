(function attachStoreConfig(globalScope) {
  const runtimeConfig = globalScope.__STORE_RUNTIME_CONFIG__ || {};
  const runtimeFirebase = runtimeConfig.firebase || {};
  const runtimeContact = runtimeConfig.contact || {};

  const storeConfig = {
    brandName: runtimeConfig.brandName || "FLOKI",
    publicStoreUrl: runtimeConfig.publicStoreUrl || "https://floki.com.ar/product.html",
    tenantId: runtimeConfig.tenantId || "floki",
    productCollection: "products",
    publicProductCollection: "public_products",
    refreshIntervalMs: 300000,
    firebase: {
      apiKey: runtimeFirebase.apiKey || "",
      authDomain: runtimeFirebase.authDomain || "sellia1993.firebaseapp.com",
      projectId: runtimeFirebase.projectId || "sellia1993",
      storageBucket: runtimeFirebase.storageBucket || "sellia1993.firebasestorage.app",
      messagingSenderId: runtimeFirebase.messagingSenderId || "",
      appId: runtimeFirebase.appId || ""
    },
    contact: {
      whatsapp: runtimeContact.whatsapp || "",
      instagram: runtimeContact.instagram || "",
      maps: runtimeContact.maps || ""
    },
    analytics: {
      webVitalsEndpoint: runtimeConfig.analytics?.webVitalsEndpoint || ""
    }
  };

  globalScope.STORE_CONFIG = storeConfig;

  const storeConfigReady = resolveTenantStoreConfig(storeConfig).catch((error) => {
    console.warn("No se pudo resolver la configuración pública por tenant.", error);
  });

  globalScope.__STORE_CONFIG_READY__ = storeConfigReady;

  async function resolveTenantStoreConfig(config) {
    const tenantIdFromUrl = resolveTenantFromUrl();
    if (tenantIdFromUrl) {
      config.tenantId = tenantIdFromUrl;
    }

    const projectId = config.firebase?.projectId;
    const apiKey = config.firebase?.apiKey;
    if (!projectId || !apiKey || !config.tenantId) {
      applyFallbackDomainByTenant(config);
      return;
    }

    const marketingResponse = await fetchWithTimeout(
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${encodeURIComponent(
        config.tenantId
      )}/config/marketing?key=${apiKey}`
    );

    if (marketingResponse.ok) {
      const marketingPayload = await marketingResponse.json();
      const marketingFields = marketingPayload?.fields || {};
      const marketingData = marketingFields.data?.mapValue?.fields || {};
      const publicStoreUrl = marketingData.publicStoreUrl?.stringValue?.trim() || "";
      const publicDomain = marketingData.publicDomain?.stringValue?.trim() || "";
      if (publicStoreUrl) {
        config.publicStoreUrl = normalizeUrl(publicStoreUrl);
        return;
      }
      if (publicDomain) {
        config.publicStoreUrl = buildProductUrl(publicDomain);
        return;
      }
    }

    const directoryResponse = await fetchWithTimeout(
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/public_tenant_directory/${encodeURIComponent(
        config.tenantId
      )}?key=${apiKey}`
    );

    if (!directoryResponse.ok) {
      applyFallbackDomainByTenant(config);
      return;
    }

    const payload = await directoryResponse.json();
    const fields = payload?.fields || {};
    const publicStoreUrl = fields.publicStoreUrl?.stringValue?.trim() || "";
    const publicDomain = fields.publicDomain?.stringValue?.trim() || "";

    if (publicStoreUrl) {
      config.publicStoreUrl = normalizeUrl(publicStoreUrl);
      return;
    }

    if (publicDomain) {
      config.publicStoreUrl = buildProductUrl(publicDomain);
      return;
    }

    applyFallbackDomainByTenant(config);
  }

  async function fetchWithTimeout(url, timeoutMs = 2500) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(url, { signal: controller.signal, cache: "no-store" });
    } finally {
      clearTimeout(timeoutId);
    }
  }

  function resolveTenantFromUrl() {
    const params = new URLSearchParams(globalScope.location.search);
    return (
      params.get("tenantId")?.trim() ||
      params.get("tienda")?.trim() ||
      params.get("TIENDA")?.trim() ||
      ""
    );
  }

  function applyFallbackDomainByTenant(config) {
    if ((config.tenantId || "").toLowerCase() === "floki") {
      config.publicStoreUrl = "https://floki.com.ar/product.html";
    }
  }

  function normalizeUrl(url) {
    if (!url) return "";
    if (url.startsWith("http://") || url.startsWith("https://")) {
      return url;
    }
    return `https://${url}`;
  }

  function buildProductUrl(domain) {
    const normalizedDomain = domain.replace(/^https?:\/\//i, "").replace(/\/$/, "");
    return `https://${normalizedDomain}/product.html`;
  }
})(window);
