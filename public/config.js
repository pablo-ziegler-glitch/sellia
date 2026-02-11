const DEFAULT_STORE_NAME = "";
const DEFAULT_SITE_BASE_URL = window.location.origin;
const PLACEHOLDER_SELLIA_BASE_URL = "__SELLIA_BASE_URL__";
const SELLIA_BASE_URL =
  typeof window.__SELLIA_BASE_URL__ == "string" && window.__SELLIA_BASE_URL__.trim()
    ? window.__SELLIA_BASE_URL__.trim()
    : PLACEHOLDER_SELLIA_BASE_URL !== "__SELLIA_BASE_URL__"
      ? PLACEHOLDER_SELLIA_BASE_URL
      : DEFAULT_SITE_BASE_URL;

window.SELLIA_CONFIG = {
  brandName: DEFAULT_STORE_NAME,
  siteBaseUrl: SELLIA_BASE_URL,
  publicStoreUrl: `${SELLIA_BASE_URL}/product.html`,
  deepLinkScheme: "store",
  tenantId: "REEMPLAZAR_TENANT",
  productCollection: "products",
  publicProductCollection: "public_products",
  refreshIntervalMs: 300000,
  firebase: {
    apiKey: "REEMPLAZAR",
    authDomain: "REEMPLAZAR",
    projectId: "REEMPLAZAR",
    storageBucket: "REEMPLAZAR",
    messagingSenderId: "REEMPLAZAR",
    appId: "REEMPLAZAR"
  },
  contact: {
    whatsapp: "REEMPLAZAR",
    instagram: "REEMPLAZAR",
    maps: "REEMPLAZAR"
  }
};
