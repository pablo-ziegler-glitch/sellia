# Auditoría de costo y performance Firebase/GCP (2026-04-11)

## Resumen ejecutivo
- Se detectó sobreuso en procesos automáticos (Scheduler/cron) y listeners en backoffice.
- Se implementó gobernanza central de jobs con modo `on_demand` o `automatic` + TTL configurable y ejecución manual auditada.
- Se agregó pantalla admin `Jobs & TTL` para operar sin redeploy.
- Se priorizó reducción de ejecución de background innecesaria: los jobs ahora verifican TTL antes de ejecutar trabajo pesado.

## Hallazgos principales por categoría

### Firestore
- `functions/src/publicStore.ts#createRefreshPublicProductsHandler`: escaneo de `tenants` + lecturas por tenant en cada corrida.
- `functions/src/reportsDashboard.ts#createEvaluateUsageAlertsHandler`: escaneo global de tenants y lecturas múltiples por tenant.
- `functions/src/reportsDashboard.ts#createEvaluateTenantBudgetAlertsHandler`: escaneo global de tenants y lectura doble por tenant.
- `public/admin/app.js`: listeners activos de backups/pagos/maintenance/POS/bulk que incrementan lecturas en sesiones largas.
- `app/src/main/java/com/example/selliaapp/repository/impl/NotificationRepositoryImpl.kt`: listeners en notificaciones y unread count.

### Cloud Functions / Scheduler
- Jobs periódicos con frecuencias fijas y sin control operativo central (antes):  
  `collectUsageMetrics`, `evaluateUsageAlerts`, `refreshPublicProducts`, `reconcilePendingPayments`, `createDailyTenantBackups`, `archiveAndPurgeTenantBackups`, `evaluateTenantBudgetAlerts`.
- Riesgo de ejecución “por si acaso” cuando no hay cambios materiales.

### Hosting / App Hosting / Cloud Run
- `web/apphosting.yaml` ya tiene `minInstances: 0` (correcto para minimizar costo idle).
- `maxInstances: 2`, `cpu: 1`, `memoryMiB: 512`: razonable para tráfico bajo/medio; revisar si hay picos.

### Auth / App Check / Seguridad operativa
- Hay callables con `enforceAppCheck: false`, mitigados parcialmente por `assertAppCheckForInternalCallable`.
- Faltaba panel operativo para gobernanza explícita de jobs (resuelto en esta entrega).

## Implementación realizada

### 1) Gobernanza de jobs (backend)
- Nuevo módulo: `functions/src/backgroundJobs.ts`
- Colección canónica: `background_jobs_config/{jobId}`
- Capacidades:
  - Definición de jobs soportados y defaults
  - Validación de TTL (mínimo 5 minutos, máximo 30 días)
  - Modo `automatic` / `on_demand`
  - Activación/desactivación (`active`)
  - Cálculo de próximo run
  - Registro de ejecución: último run, duración, resultado, error, contador, historial básico

### 2) Control de ejecución en jobs programados
- Actualizados en `functions/src/legacyHandlers.ts` para chequeo TTL previo:
  - `collectUsageMetrics`
  - `evaluateUsageAlerts`
  - `refreshPublicProducts`
  - `reconcilePendingPayments`
  - `createDailyTenantBackups`
  - `archiveAndPurgeTenantBackups`
  - `evaluateTenantBudgetAlerts`
- Si no venció TTL o modo es `on_demand`, el scheduler sale sin ejecutar trabajo pesado.

### 3) Operación segura por callable
- Nuevos callables:
  - `getBackgroundJobsConfig`
  - `setBackgroundJobConfig`
  - `runBackgroundJobNow`
- Protecciones:
  - Auth requerida
  - Guard de App Check interno
  - Rate limiting existente
  - Validación de rol admin/owner/superadmin
  - Audit trail en `audit_logs`

### 4) Pantalla admin de TTL/jobs
- Archivos:
  - `public/admin/index.html`
  - `public/admin/app.js`
  - `public/admin/styles.css`
  - `public/admin/permissions.js`
- Nueva ruta: `#/settings/jobs`
- Funcionalidades:
  - Ver jobs y metadatos operativos
  - Cambiar modo, activo, TTL (valor + unidad)
  - Ejecutar manualmente
  - Desactivar job
  - Refrescar estado on-demand
  - Visualizar duración, entorno e historial corto de ejecuciones

### 5) Reducción adicional de listeners en backoffice
- `public/admin/app.js` migrado de realtime a fetch puntual en:
  - historial de backups
  - flags/auditoría de pagos
  - mantenimiento
  - catálogo POS
  - productos bulk
- Resultado: menor lectura incremental por sesión abierta y menor costo por operador inactivo.

## Ahorro estimado (cualitativo)
- **Alto**: control TTL en jobs scheduler globales evita barridos completos innecesarios.
- **Alto**: posibilidad de pasar jobs a `on_demand` elimina corridas periódicas improductivas.
- **Medio**: reducción de reprocesos manuales inseguro/no auditado al centralizar ejecución manual.
- **Medio**: menor riesgo de write amplification por jobs automáticos sin cambios materiales.

## Riesgos / trade-offs
- Modo `on_demand` puede introducir datos stale si operación no ejecuta manualmente cuando corresponde.
- TTL demasiado largo puede retrasar alertas/visibilidad de métricas.
- TTL demasiado corto puede volver a generar costos altos (se mitiga con validación mínima y panel admin).

## Recomendaciones de siguiente fase (pendientes)
- Migrar listeners costosos del backoffice legado y de paneles no críticos a fetch puntual + refresh manual.
- Agregar idempotency keys explícitas en todos los jobs manuales críticos.
- Incorporar límites por entorno (`dev/staging/prod`) en configuración de jobs.
- Agregar métricas de costo por job (estimación por run) en documento de configuración.
- Evaluar reemplazo de scans globales por colecciones de trabajo incremental (delta queues).
