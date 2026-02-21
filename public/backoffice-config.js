(function attachBackofficeConfigApi(globalScope) {
  const CONFIG_DOCS = {
    pricing: "pricing",
    marketing: "marketing",
    security: "security",
    cloudServices: "cloud_services",
    developmentOptions: "development_options"
  };

  const SCHEMA_VERSION = 1;

  async function readTenantConfig({ projectId, apiKey, tenantId, docId }) {
    const response = await fetch(
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${encodeURIComponent(
        tenantId
      )}/config/${encodeURIComponent(docId)}?key=${apiKey}`
    );
    if (!response.ok) {
      return null;
    }
    return response.json();
  }

  async function writeTenantConfig({ projectId, apiKey, tenantId, docId, updatedBy, data }) {
    const payload = {
      fields: {
        schemaVersion: { integerValue: String(SCHEMA_VERSION) },
        updatedAt: { timestampValue: new Date().toISOString() },
        updatedBy: { stringValue: updatedBy || "backoffice_web" },
        audit: {
          mapValue: {
            fields: {
              event: { stringValue: `UPSERT_${docId.toUpperCase()}` },
              at: { timestampValue: new Date().toISOString() },
              by: { stringValue: updatedBy || "backoffice_web" }
            }
          }
        },
        data: toFirestoreMap(data || {})
      }
    };

    const response = await fetch(
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/tenants/${encodeURIComponent(
        tenantId
      )}/config/${encodeURIComponent(docId)}?key=${apiKey}`,
      {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      }
    );

    if (!response.ok) {
      throw new Error(`No se pudo guardar ${docId}: ${response.status}`);
    }

    return response.json();
  }

  function toFirestoreMap(value) {
    if (Array.isArray(value)) {
      return {
        arrayValue: {
          values: value.map((item) => toFirestoreValue(item))
        }
      };
    }
    return {
      mapValue: {
        fields: Object.entries(value).reduce((acc, [key, item]) => {
          acc[key] = toFirestoreValue(item);
          return acc;
        }, {})
      }
    };
  }

  function toFirestoreValue(value) {
    if (value === null || value === undefined) return { nullValue: null };
    if (typeof value === "string") return { stringValue: value };
    if (typeof value === "boolean") return { booleanValue: value };
    if (typeof value === "number") {
      if (Number.isInteger(value)) return { integerValue: String(value) };
      return { doubleValue: value };
    }
    if (Array.isArray(value)) return toFirestoreMap(value);
    if (typeof value === "object") return toFirestoreMap(value);
    return { stringValue: String(value) };
  }

  globalScope.BackofficeTenantConfigApi = {
    CONFIG_DOCS,
    SCHEMA_VERSION,
    readTenantConfig,
    writeTenantConfig
  };
})(window);
