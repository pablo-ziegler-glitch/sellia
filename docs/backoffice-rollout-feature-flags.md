# Plan de rollout del Backoffice (feature flags por módulo)

## Objetivo

Mantener la estrategia de entrega rápida en branch, minimizando riesgo operativo mediante activación progresiva por módulo de Backoffice (BO), con reversión inmediata por feature flags.

## Principios de despliegue

1. **Separación por módulo BO**: cada módulo (`configuracion`, `usuarios`, `mantenimiento`, `reportes`) se controla con un flag independiente.
2. **Activación por tenant**: primero se habilita para un tenant interno de validación, no para todo el tráfico.
3. **Observabilidad previa a expansión**: no se avanza sin una ventana de 24–48h con métricas estables.
4. **Rollback instantáneo**: cualquier degradación crítica se mitiga apagando el flag sin redeploy.
5. **Paridad funcional por canal**: al confirmar estabilidad en web, se deshabilitan funciones administrativas en mobile para reducir superficie operativa y de seguridad.


## Política de activación de nuevas tiendas (default ON)

### Regla general (nuevo default)

- Toda **nueva tienda se crea activa por defecto** (`isActive=true`).
- No requiere aprobación manual en el flujo estándar de onboarding.

### Override operativo desde Backoffice Admin

Backoffice Admin debe exponer una configuración global de plataforma para cambiar el comportamiento de altas futuras:

- `tenantActivationMode = "auto"` (default): altas nuevas activas automáticamente.
- `tenantActivationMode = "manual"`: altas nuevas quedan en `pending_approval` y requieren aprobación explícita por operador autorizado.

> Esta configuración aplica **solo a nuevas cuentas/tiendas**; no debe mutar retroactivamente el estado de tiendas ya creadas.

### Contrato recomendado de configuración

Documento global (scope plataforma, no por tenant):

- `platform/config/tenant_onboarding`

Payload sugerido:

```json
{
  "schemaVersion": 1,
  "tenantActivationMode": "auto",
  "updatedAt": "serverTimestamp",
  "updatedBy": "backoffice_admin"
}
```

### Reglas de creación de tenant

Al crear una tienda:

1. Leer `tenantActivationMode` (con cache corta e invalidación).
2. Si modo `auto` o no existe config (fallback seguro para continuidad): crear `tenants/{tenantId}.status = "active"`.
3. Si modo `manual`: crear `status = "pending_approval"` + evento de auditoría.
4. Exigir validación server-side; el cliente nunca define el estado final de activación.

### Aprobación manual (cuando modo manual está activo)

- Acción disponible solo para `superAdmin`/rol equivalente en BO administrativo.
- Transición permitida: `pending_approval -> active` (y opcionalmente `rejected` con motivo).
- Registrar auditoría obligatoria: `actor`, `tenantId`, `fromStatus`, `toStatus`, `reason`, `requestId`.

## Diseño de flags (control plane)

### Convención recomendada

- `bo.module.configuracion.enabled`
- `bo.module.usuarios.enabled`
- `bo.module.mantenimiento.enabled`
- `bo.module.reportes.enabled`
- `bo.admin.global.enabled` (kill switch global)
- `bo.mobile.admin.enabled` (desactivación posterior en mobile)

### Reglas de evaluación

Para exponer una pantalla/mutación de BO:

1. `bo.admin.global.enabled == true`
2. flag de módulo `== true`
3. tenant en allowlist (durante fase de validación)
4. usuario con rol permitido en `ROLE_PERMISSIONS_MATRIX`

Si cualquiera falla: UI en estado “no disponible” + backend responde `403/feature_disabled`.

## Secuencia de rollout

### Fase 0 — Preparación

- Instrumentar métricas y logs por módulo y por tenant.
- Asegurar auditoría de operaciones administrativas (quién, qué, cuándo, tenant, resultado).
- Definir umbrales de rollback por SLO/SLI.

### Fase 1 — Activación controlada (tenant interno)

- Habilitar `bo.admin.global.enabled=true` solo para tenant de validación interna.
- Activar módulos BO de forma secuencial (no simultánea), empezando por menor criticidad.
- Ejecutar smoke tests manuales + automáticos sobre rutas y mutaciones administrativas.

### Fase 2 — Observación 24–48h

Monitorear mínimo:

- tasa de error por endpoint/módulo (`5xx`, `4xx`, timeout)
- latencia p95/p99 en operaciones críticas
- denegaciones por permisos (esperadas vs anómalas)
- errores de autorización (`permission_denied`, `insufficient_role`, `tenant_mismatch`)
- health de dependencias (DB, auth, colas, functions)

No avanzar si hay tendencia regresiva o p95/p99 fuera de umbral.

### Fase 3 — Activación global con rollback inmediato

- Quitar allowlist por tenant y habilitar por defecto.
- Mantener `bo.admin.global.enabled` como **kill switch** para desactivar toda la superficie BO.
- Runbook de incidente: detección → decisión → apagado de flag → verificación de recuperación → postmortem.

### Fase 4 — Consolidación de canal

- Confirmada estabilidad global, poner `bo.mobile.admin.enabled=false`.
- Mobile conserva solo capacidades no administrativas.
- Comunicar cambio a soporte/ops y actualizar material de ayuda.

## Guardrails técnicos obligatorios

- **Backend primero**: todo control de permisos/flags debe validarse server-side (no confiar en ocultar UI).
- **Fail-safe**: ante fallo del proveedor de flags, default a deny para rutas administrativas.
- **Consistencia de cache**: TTL corto o invalidación activa de configuración de flags.
- **Idempotencia** en mutaciones críticas de BO para evitar dobles ejecuciones por reintentos.
- **Trazabilidad**: correlación con `requestId`, `tenantId`, `uid`, `module`, `flagSnapshot`.

## Métricas y umbrales sugeridos de go/no-go

- Error rate BO (5xx): `< 1%` sostenido.
- `permission_denied` inesperado: sin incremento estadísticamente significativo.
- p95 de operaciones administrativas: dentro de +20% del baseline.
- Incidentes Sev1/Sev2 atribuibles a BO: `0` durante ventana de observación.

## Checklist de liberación

- [ ] Flags por módulo creados y auditables.
- [ ] Kill switch global probado en staging y producción.
- [ ] Tenant interno configurado en allowlist.
- [ ] Dashboards + alertas de error/permisos activos.
- [ ] Runbook de rollback validado en simulacro.
- [ ] Validación 24–48h completada y documentada.
- [ ] Activación global ejecutada con monitoreo en tiempo real.
- [ ] Funciones administrativas mobile deshabilitadas tras estabilidad.
