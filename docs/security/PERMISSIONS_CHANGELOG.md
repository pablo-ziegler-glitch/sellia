# Permissions Changelog

Registro inmutable de cambios de permisos. Cada versión debe tener:

- Fecha y autor técnico.
- Motivación de negocio/seguridad.
- Impacto en runtime (backend/frontend/docs).
- `Security review: APPROVED` con responsable.

## 2026-02-24

- Date: 2026-02-24
- Author: platform-security
- Change summary:
  - Se consolidó `MODULE_ROLE_POLICIES` como matriz canónica en backend (`functions/src/security/rolePermissionsMatrix.ts`).
  - Se aplicó sincronización obligatoria hacia frontend (`public/admin/permissions.js`) y documentación (`docs/security/ROLE_PERMISSIONS_MATRIX.md`).
  - Se agregó control de drift automatizado y puerta de CI.
  - Se formalizó aprobación temporal para diffs excepcionales vía `permissions-drift-approvals.json`.
- Security review: APPROVED (security-architecture)
