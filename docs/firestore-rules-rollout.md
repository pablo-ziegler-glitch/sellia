# Rollout secuencial de Firestore Rules (anti-lockout)

## Objetivo
Publicar reglas en producción sin bloquear operaciones críticas de app/backoffice.

## Secuencia recomendada

1. **Pre-check local (emulator):**
   - `npm run test:firestore-rules`
2. **Deploy solo reglas Firestore (sin tocar functions):**
   - `firebase deploy --only firestore:rules --project <project-id>`
3. **Smoke test inmediato (5-10 min):**
   - Backoffice: login owner/admin, alta de usuario en tenant.
   - App: login viewer/cashier y validación de que no puede usar flujos admin.
   - Público: lectura de `tenants/{tenantId}/public_products` sin auth.
   - Config: owner/admin guardan `tenants/{tenantId}/config/cloud_services`.
4. **Verificación de backup/restore requests:**
   - owner/admin generan request en su tenant.
   - superAdmin puede aprobar/operar cross-tenant.
5. **Rollback (si lockout):**
   - redeploy de última versión estable de `firestore.rules`.

## Criterio de salida
- Sin errores de permisos para owner/admin en tenant propio.
- Denegación correcta para viewer/manager/cashier en flujos administrativos.
- Lecturas públicas funcionando solo en colecciones de catálogo público.
