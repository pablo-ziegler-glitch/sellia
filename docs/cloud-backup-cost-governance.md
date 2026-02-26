# Gobernanza de backups y costos cloud por tenant

## 1) Retención por criticidad
Cada tenant puede definir `backupCriticality` (`hot|warm|cold`) y opcionalmente `backupPolicy` custom en `tenants/{tenantId}`.

Perfiles por defecto:
- **hot**: retención 14 runs, archive a 3 días (NEARLINE), purge a 30 días.
- **warm**: retención 10 runs, archive a 7 días (COLDLINE), purge a 60 días.
- **cold**: retención 6 runs, archive a 14 días (ARCHIVE), purge a 120 días.

## 2) Purge automático + archivado económico
La tarea programada `archiveAndPurgeTenantBackups` recorre los tenants cada 24h y:
- mueve artefactos maduros a clase de almacenamiento económica (`NEARLINE/COLDLINE/ARCHIVE`),
- purga runs vencidos por política.

## 3) Compresión y deduplicación
Cada backup:
- serializa payload JSON,
- comprime a `json.gz`,
- calcula `payloadHash` SHA-256 del contenido comprimido,
- deduplica contra las últimas ejecuciones completadas (si hash igual, marca `status=deduplicated` y evita subir artefacto/chunks).

## 4) Dashboard de costo por tenant y servicio
Callable `getTenantCostDashboard` retorna:
- presupuesto mensual total y por servicio,
- costo actual total y por servicio,
- historial mensual consolidado de métricas de uso.

La UI de Backoffice (`#/settings/cloud-services`) muestra el resumen por servicio.

## 5) Alerta de desvío contra presupuesto mensual
La tarea `evaluateTenantBudgetAlerts` corre cada 24h y crea alertas en
`tenants/{tenantId}/budget_alerts/{alertId}` cuando el costo acumulado llega al 80% o supera 100% del presupuesto.

## Configuración de presupuesto sugerida
Documento: `tenants/{tenantId}/cost_budgets/monthly`

```json
{
  "totalBudget": 120,
  "budgetByService": {
    "firestore": 30,
    "storage": 20,
    "functions": 40,
    "hosting": 10,
    "auth": 5,
    "other": 15
  }
}
```
