# Gobernanza de configuración (Android + Backoffice + Firestore)

## Inventario actual de configuraciones editables en Android

| Configuración | Dónde se edita hoy | Alcance | Contrato canónico |
|---|---|---|---|
| Pricing | `PricingConfigViewModel` + `PricingConfigRepository` | **Tenant** (impacta reglas de precio compartidas) | `tenants/{tenantId}/config/pricing` |
| Marketing | `MarketingConfigViewModel` + `MarketingConfigRepository` | **Tenant** (web pública y contacto comercial) | `tenants/{tenantId}/config/marketing` |
| Security | `SecuritySettingsViewModel` + `SecurityConfigRepository` | **Tenant** (política admin y acceso) | `tenants/{tenantId}/config/security` |
| Cloud services | `CloudServicesAdminViewModel` + `CloudServiceConfigRepository` | **Tenant** (habilitación de servicios Firebase por owner) | `tenants/{tenantId}/config/cloud_services` |
| Development options | `DevelopmentOptionsViewModel` + `DevelopmentOptionsRepository` | **Tenant** (feature flags operativas compartidas) | `tenants/{tenantId}/config/development_options` |

## Configuraciones solo dispositivo (no compartidas)

- Cachés locales Room/DataStore usados como soporte offline para las configuraciones anteriores.
- Preferencias de sesión temporal/UI y estado efímero de pantalla.
- Cualquier dato de sincronización encolada (`outbox`) mientras no llega a cloud.

## Contrato canónico obligatorio

Todos los documentos en `tenants/{tenantId}/config/*` deben incluir:

```json
{
  "schemaVersion": 1,
  "updatedAt": "serverTimestamp",
  "updatedBy": "android_marketing | android_security | android_cloud_services | android_development_options | backoffice_web",
  "audit": {
    "event": "UPSERT_*",
    "at": "serverTimestamp",
    "by": "actor"
  },
  "data": {
    "...": "payload de dominio"
  }
}
```

## Reglas de permisos por documento

- `pricing`: `owner`, `admin`.
- `marketing`: `owner`, `admin`, `manager`.
- `security`: `owner`, `admin` (`MANAGE_USERS`).
- `cloud_services`: `owner`, `admin` (`MANAGE_CLOUD_SERVICES`).
- `development_options`: `owner`, `admin`.

Esta matriz alinea Android, backoffice web y Firestore Rules para evitar divergencias funcionales.
