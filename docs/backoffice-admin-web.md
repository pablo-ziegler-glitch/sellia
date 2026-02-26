# Backoffice web separado del sitio público

Se agregó un frontend independiente en `public/admin/` para desacoplar operación interna del catálogo público.

## Objetivos de seguridad y operación

- Autenticación con Firebase Auth (`email/password` + Google opcional).
- Sesión persistente con `browserLocalPersistence`.
- Logout seguro y controlado desde UI.
- Validación de perfil en `users/{uid}` antes de habilitar navegación.
- Guards de rutas por rol alineados a `docs/security/ROLE_PERMISSIONS_MATRIX.md`.
- Hardening básico: expiración por inactividad, refresh de token, estados de acceso denegado y cuenta sin tenant.

## Perfil requerido en Firestore

Documento: `users/{uid}`

Campos mínimos:

```json
{
  "tenantId": "tenant_demo",
  "role": "admin",
  "status": "active"
}
```

Reglas:
- `tenantId`: obligatorio.
- `role`: uno de `owner|admin|manager|cashier` para backoffice.
- `status`: debe ser `active`.

## Navegación inicial (backoffice)

- `#/dashboard`
- `#/settings/pricing`
- `#/settings/marketing`
- `#/settings/users`
- `#/settings/cloud-services`
- `#/settings/integrations` (tokens/secretos con vista enmascarada)
- `#/maintenance`

`#/maintenance` es explícitamente operativa (sin venta/carrito).

## Acceso local

```bash
python3 -m http.server 8080 --directory public
```

Abrir:
- Sitio público: `http://localhost:8080/`
- Backoffice: `http://localhost:8080/admin/`


## Gestión de tokens en backoffice

- El backoffice debe operar como consola principal para alta/rotación/revocación de secretos por tenant.
- Las vistas muestran solo previews enmascarados por defecto.
- Revelado completo solo bajo acción explícita, trazado en auditoría y con expiración corta de UI.
- El almacenamiento del secreto debe resolverse vía Cloud Functions + Secret Manager, evitando persistir token completo en Firestore.


## Política de activación de nuevas tiendas

- Regla vigente: **las nuevas tiendas quedan activas por defecto** (sin aprobación manual).
- Backoffice Admin debe incluir un control global `tenantActivationMode` para cambiar a modo `manual` cuando se requiera aprobación explícita para nuevas altas.
- El cambio de modo no afecta retroactivamente tiendas ya creadas; aplica solo a nuevas cuentas.

## Rollout recomendado por feature flags

Ver `docs/backoffice-rollout-feature-flags.md` para la secuencia de activación por tenant, ventana de observación 24–48h, rollback instantáneo y deshabilitación administrativa en mobile.
