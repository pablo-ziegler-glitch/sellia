# Plan de optimización de queries BO/Mobile y dashboards

## 1) Catálogo de queries de mayor frecuencia

### Backoffice Web (`public/backoffice.js`)

1. **Listado de mantenimiento por tenant**
   - Query: `tenants/{tenantId}/maintenance_tasks` ordenado por `updatedAt DESC`.
   - Uso: carga inicial + refresco en tiempo real.
   - Riesgo: sin paginación puede crecer linealmente con el histórico.
2. **Paginación incremental de mantenimiento**
   - Query: misma colección, `startAfter(lastVisible)` + `limit(25)`.
   - Uso: "Cargar más" controlado por usuario.
   - Beneficio: evita scans globales y spikes de lectura en clientes lentos.

### Cloud Functions (`functions/src/index.ts`)

3. **Búsqueda puntual de orden por `orderId`**
   - Query: `collectionGroup("orders")` + `documentId == orderId` + `limit(1)`.
   - Uso: reconciliación / webhook y lookup transaccional.
4. **Listado de corridas de backup**
   - Query: `runsRef.orderBy("createdAt", "desc")`.
   - Acción recomendada: paginar con límite explícito para evitar crecimiento ilimitado.
5. **Lectura de usuarios por tenant**
   - Query: `users.where("tenantId", "==", tenantId)`.
   - Acción recomendada: limitar cuando el caso de uso no requiera barrido completo.

### Mobile app (Kotlin)

6. **Alertas de uso**
   - Query: orden por `createdAt DESC` con `limit` configurable.
7. **Versionado de app**
   - Query: orden por `updatedAt DESC` con `limit`.
8. **Breakdowns de uso**
   - Query: orden por métricas agregadas con límites máximos (`MAX_SERVICES`, `MAX_TENANTS_FOR_BREAKDOWN`).

---

## 2) Índices compuestos requeridos + validación en staging

## Índices definidos/requeridos

En `firestore.indexes.json` se mantienen los siguientes compuestos para `maintenance_tasks` (scope `COLLECTION_GROUP`):

- `(status ASC, dueAt ASC)`
- `(operationalBlocker DESC, priority DESC, dueAt ASC)`
- `(assigneeUid ASC, status ASC, updatedAt DESC)`

> Si los dashboards pasan a `collectionGroup("maintenance_tasks")` por múltiples tenants, agregar variantes con `tenantId` como prefijo para mantener selectividad.

## Validación en staging (runbook)

1. Deploy de índices en staging:

```bash
firebase deploy --only firestore:indexes --project <staging_project>
```

2. Confirmar estado:

```bash
firebase firestore:indexes --project <staging_project>
```

3. Verificar consultas críticas desde BO y funciones:
   - Abrir BO staging y validar carga/paginación de mantenimiento.
   - Ejecutar funciones que usen `orders`/`payments` por `collectionGroup`.

4. Medir latencia p50/p95 (antes y después):
   - Firestore Usage + Cloud Monitoring (`Document Read Count`, `Latency`).
   - Trazas de Cloud Functions para endpoints sensibles.

---

## 3) Vistas/materializaciones para dashboards intensivos

### Estrategia recomendada

Para dashboards de alto costo de lectura:

- Materializar documentos diarios/mensuales por tenant (`dashboard_daily/{tenantId_yyyy_mm_dd}` y/o `dashboard_monthly/{tenantId_yyyy_mm}`).
- Generar por scheduler (cada 5-15 min para near-real-time o diario para finanzas).
- Evitar calcular KPIs con scans ad-hoc sobre datos transaccionales.

### Shape mínimo sugerido

```json
{
  "tenantId": "tenant_abc",
  "periodKey": "2026-02-25",
  "orders": { "count": 120, "gross": 95000 },
  "maintenance": { "open": 18, "blockers": 3, "overdue": 5 },
  "updatedAt": "Timestamp",
  "sourceWindow": { "from": "Timestamp", "to": "Timestamp" }
}
```

### Controles operativos

- Idempotencia por `periodKey`.
- Watermark de última corrida para no recalcular histórico completo.
- Alertas si una materialización no se actualiza dentro del SLA.

---

## 4) Limitar scans globales y paginar siempre

### Aplicado en BO web

- Carga inicial limitada a `25` tareas.
- Paginación con cursor (`startAfter`) en botón "Cargar más tareas".
- Se evita la carga de todo el historial en cada snapshot inicial.

### Política transversal

- Toda query de listados debe tener:
  1. `orderBy` explícito,
  2. `limit` explícito,
  3. cursor para página siguiente (`startAfter` / `startAt`).
- Evitar `collectionGroup` sin filtro altamente selectivo.
- Cualquier proceso batch debe segmentar por ventana temporal y/o tenant.

---

## 5) Medir costo por consulta y latencia antes/después

## Métricas obligatorias

- **Costo**: lecturas/escrituras por feature y costo mensual estimado.
- **Latencia**: p50, p95, p99 por query crítica.
- **Volumen**: docs leídos por request y tamaño de payload.

## Instrumentación mínima

- Dashboard de Cloud Monitoring por endpoint/función.
- Logs estructurados con:
  - `query_name`,
  - `tenant_id`,
  - `result_count`,
  - `duration_ms`,
  - `source` (`bo_web`, `mobile`, `function`).

## Criterios de aceptación

- Reducción de lecturas en BO para mantenimiento > 60% en tenants con histórico grande.
- No degradar p95 de UX por encima de 400 ms en acciones de listado.
- Sin errores de índice faltante en staging ni producción.
