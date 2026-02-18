(function attachStoreConfig(globalScope) {
  const runtimeConfig = globalScope.__STORE_RUNTIME_CONFIG__ || {};
  const runtimeFirebase = runtimeConfig.firebase || {};
  const runtimeContact = runtimeConfig.contact || {};

  globalScope.STORE_CONFIG = {
    brandName: runtimeConfig.brandName || "Valkirja",
    publicStoreUrl: runtimeConfig.publicStoreUrl || "https://sellia1993.web.app/product.html",
    tenantId: runtimeConfig.tenantId || "valkirja",
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
    }
  };
})(window);
