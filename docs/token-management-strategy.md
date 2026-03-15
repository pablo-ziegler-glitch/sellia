# Estrategia de gestión de tokens y secretos (Android + Backoffice + Firebase)

## Contexto operativo

Objetivo: permitir configuración centralizada de integraciones (Firebase, Mercado Pago y futuras) sin romper tiendas productivas, minimizando exposición de secretos y costo operativo.

## Principios de arquitectura (producción)

1. **Nunca exponer secretos completos en clientes** (Android/Web).
2. **Firestore guarda metadatos y referencias**, no secretos en claro.
3. **Secret Manager es la fuente de verdad** para secretos sensibles.
4. **Cloud Functions actúa como plano de control** para crear/rotar/revocar secretos.
5. **UI temporal controlada**: mostrar solo preview enmascarado y opcionalmente valor completo por una única vez con TTL corto.

---

## Modelo recomendado

### 1) Metadatos en Firestore (por tenant)

Ruta: `tenants/{tenantId}/config/integrations`

```json
{
  "schemaVersion": 1,
  "updatedAt": "serverTimestamp",
  "updatedBy": "backoffice_web | android_cloud_services",
  "data": {
    "mercadoPago": {
      "enabled": true,
      "accessTokenRef": "projects/<project>/secrets/mp_access_token_tenant_abc/versions/latest",
      "publicKeyPreview": "APP_USR-****-ABCD",
      "webhookSecretRef": "projects/<project>/secrets/mp_webhook_secret_tenant_abc/versions/latest",
      "health": {
        "lastCheckAt": "timestamp",
        "lastStatus": "ok|warning|error"
      }
    },
    "firebase": {
      "hostingEnabled": true,
      "appCheckEnforced": true
    }
  }
}
```

> En Firestore **no** se persiste el token completo de Mercado Pago ni secretos de webhook.

### 2) Secretos reales en Google Secret Manager

Naming recomendado (multi-tenant):

- `mp_access_token_<tenantId>`
- `mp_webhook_secret_<tenantId>`
- `firebase_admin_json_<tenantId>` (solo si fuera estrictamente necesario)

### 3) Callables para control seguro

- `setTenantIntegrationSecret(tenantId, provider, key, value)`
  - Permisos: `owner|admin` del tenant o `superAdmin`.
  - Guarda secreto en Secret Manager y actualiza referencia en Firestore.
- `getTenantIntegrationSecretPreview(tenantId, provider, key)`
  - Devuelve preview enmascarado + estado.
- `revealTenantIntegrationSecretOnce(tenantId, provider, key)`
  - Devuelve secreto completo solo si el usuario tiene permiso y con auditoría.
  - TTL sugerido: 60 segundos.
- `rotateTenantIntegrationSecret(tenantId, provider, key, newValue)`
  - Crea nueva versión y registra auditoría.

---

## UX propuesta (Android y Backoffice)

## A. Pantalla temporal en App (Configuración > Servicios Cloud)

Finalidad: soporte operativo rápido en campo.

- Ver estado por integración (Activo/Inactivo/Error de validación).
- Ver preview enmascarado (`APP_USR-****-ABCD`).
- Botón "Actualizar token" (abre formulario seguro).
- Botón "Revelar 1 vez" con confirmación fuerte.
- Mensaje claro: "Este valor se oculta en 60 segundos y queda auditado".

## B. Pantalla principal en Backoffice Admin

Finalidad: gestión central multi-tienda.

- Filtro por tenant + proveedor.
- Acciones: alta, rotación, desactivar, test de conectividad.
- Tabla de auditoría (quién cambió qué y cuándo).
- Validación post-guardado (p.ej. ping a API Mercado Pago).

---

## Auditoría obligatoria

Colección: `tenants/{tenantId}/audit_logs/{eventId}`

Campos mínimos:

```json
{
  "eventType": "INTEGRATION_SECRET_SET|REVEAL_ONCE|ROTATE|DISABLE",
  "provider": "mercado_pago|firebase",
  "key": "access_token|webhook_secret",
  "actorUid": "uid",
  "actorRole": "owner|admin|superAdmin",
  "createdAt": "serverTimestamp",
  "result": "success|failure",
  "metadata": {
    "maskedValue": "APP_USR-****-ABCD",
    "reason": "rotacion trimestral"
  }
}
```

---

## Estrategia de permisos Firestore sin romper producción

Dado que hoy existen tiendas productivas, se propone **migración gradual por feature flag**:

1. **Fase 0 (actual):** reglas abiertas para continuidad operativa.
2. **Fase 1:** introducir reglas estrictas solo en nuevas rutas de configuración de integraciones (`tenants/{tenantId}/config/integrations`, `audit_logs`).
3. **Fase 2:** activar enforcement por tenant en módulos de bajo riesgo (mantenimiento, configuraciones administrativas).
4. **Fase 3:** extender a dominio completo cuando métricas de errores y soporte estén estables.

Mecanismos de seguridad gradual:

- Flag de rollout por tenant (`tenants/{tenantId}/config/security.data.enforceStrictRules`).
- Logs de rechazos por reglas + dashboard de incidentes.
- Runbook de rollback: desactivar flag por tenant sin redeploy.

---

## Costos y escalabilidad

- Evitar listeners continuos para secretos/config crítica; preferir fetch on-demand + cache local corta.
- Reusar documento `config/integrations` en lugar de múltiples lecturas dispersas.
- Auditoría con retención (ej. 180 días online + export a BigQuery opcional).
- Backups: metadata en Firestore y payload grande en Storage para bajar costo por documento.

---

## Plan incremental recomendado (2 sprints)

### Sprint 1
- Crear contrato `config/integrations` y auditoría.
- Implementar callables `set...`, `preview...`, `revealOnce...`.
- Pantalla backoffice básica (lista + edición + preview).

### Sprint 2
- Pantalla temporal Android (solo owner/admin).
- Rotación de secretos + healthcheck automático.
- Activar reglas estrictas en rutas de integraciones (sin tocar aún reglas globales productivas).



## Flujo de entrega recomendado (sin impacto en producción)

Para este módulo, los cambios deben integrarse por `sandbox` como rama base del PR.

1. Feature branch -> PR con base `sandbox`.
2. Validación técnica/operativa en `sandbox`.
3. Promoción posterior `sandbox -> main` solo cuando el negocio lo apruebe.

Este flujo está alineado con la necesidad de no romper tiendas productivas mientras se evoluciona seguridad/configuración.
