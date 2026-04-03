(function attachStoreConfig(globalScope) {
  const runtimeConfig = globalScope.__STORE_RUNTIME_CONFIG__ || {};
  const runtimeFirebase = runtimeConfig.firebase || {};
  const runtimeContact = runtimeConfig.contact || {};

  // tenantId se deja vacío si no hay inyección explícita del servidor.
  // El valor "floki" es solo un fallback de último recurso que se aplica
  // después de que falla el lookup por hostname y por directorio público.
  const runtimeTenantId = runtimeConfig.tenantId || "";

  const domainTenantFallbacks = {
    "valkirja.com.ar": "valkirja"
  };

  const storeConfig = {
    brandName: runtimeConfig.brandName || "FLOKI",
    publicStoreUrl: runtimeConfig.publicStoreUrl || "https://floki.com.ar/product.html",
    tenantId: runtimeTenantId,
    productCollection: "products",
    publicProductCollection: "public_products",
    refreshIntervalMs: 300000,
    firebase: {
      apiKey: runtimeFirebase.apiKey || "AIzaSyDyi0skvcP4OPOyqZCeoGfknZM5n-Y0yG8",
      authDomain: runtimeFirebase.authDomain || "sellia1993.firebaseapp.com",
      projectId: runtimeFirebase.projectId || "sellia1993",
      storageBucket: runtimeFirebase.storageBucket || "sellia1993.firebasestorage.app",
      messagingSenderId: runtimeFirebase.messagingSenderId || "218630438552",
      appId: runtimeFirebase.appId || "1:218630438552:web:162de96e3b8fc05b1d9aed"
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

    const hostname = resolveLookupHostname();

    if (!config.tenantId) {
      const fallbackTenant = resolveTenantFromHostnameFallback(hostname);
      if (fallbackTenant) {
        config.tenantId = fallbackTenant;
      }
    }

    if (projectId && apiKey && hostname) {
      try {
        const liveTenant = await resolveTenantFromFirestoreByHostname(projectId, apiKey, hostname);
        if (liveTenant) {
          config.tenantId = liveTenant;
          config._resolvedFromHostname = true;
        }
      } catch (_error) {
        // Domain lookup failed — fallback to existing logic
      }
    }

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

  async function fetchWithTimeout(url, timeoutMs = 2500, init = {}) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(url, {
        cache: "no-store",
        ...init,
        signal: controller.signal
      });
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

  async function resolveTenantFromFirestoreByHostname(projectId, apiKey, hostname) {
    const response = await fetchWithTimeout(
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery?key=${apiKey}`,
      2500,
      {
        method: "POST",
        headers: {
          "content-type": "application/json"
        },
        body: JSON.stringify({
          structuredQuery: {
            from: [{ collectionId: "domain_to_tenant" }],
            where: {
              fieldFilter: {
                field: { fieldPath: "__name__" },
                op: "EQUAL",
                value: {
                  referenceValue: `projects/${projectId}/databases/(default)/documents/domain_to_tenant/${hostname}`
                }
              }
            },
            limit: 1
          }
        })
      }
    );

    if (!response.ok) return "";

    const queryResult = await response.json();
    const firstRow = Array.isArray(queryResult) ? queryResult[0] : null;
    return firstRow?.document?.fields?.tenantId?.stringValue?.trim() || "";
  }

  function resolveLookupHostname() {
    const hostname = globalScope.location.hostname.toLowerCase().replace(/^www\./, "");
    const isFirebaseDefaultDomain = hostname.endsWith(".web.app") || hostname.endsWith(".firebaseapp.com");
    if (!hostname || hostname === "localhost" || hostname === "127.0.0.1" || isFirebaseDefaultDomain) {
      return "";
    }
    return hostname;
  }

  function resolveTenantFromHostnameFallback(hostname) {
    return domainTenantFallbacks[hostname] || "";
  }

  function applyFallbackDomainByTenant(config) {
    if (!config.tenantId) {
      config.tenantId = "floki";
    }
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
