# Cloud Functions - Métricas de consumo

Este módulo incluye un scheduler que recopila métricas de uso desde **Cloud Monitoring** o desde **BigQuery Billing Export** y las persiste en Firestore con normalización mensual.

## Función programada

- **Nombre:** `collectUsageMetrics`
- **Frecuencia:** cada 24h (UTC)
- **Destino:** `usageMetricsMonthly/{YYYY-MM}` en Firestore
- **Formato (resumen):**

```json
{
  "monthKey": "2024-05",
  "source": "monitoring",
  "period": { "start": "...", "end": "..." },
  "services": {
    "firestore": {
      "metrics": [
        {
          "metricType": "firestore.googleapis.com/document/read_count",
          "description": "Firestore document reads",
          "value": 123,
          "unit": "count"
        }
      ],
      "totalsByUnit": {
        "count": 123
      }
    }
  },
  "createdAt": "...",
  "updatedAt": "..."
}
```

> **Nota**: Las métricas se agregan para el mes en curso (desde el 1° día UTC hasta ahora). Esto deja los datos listos para reportes mensuales y control de consumo.

---

## Configuración requerida

### 1) Habilitar APIs

```bash
# Monitoring
gcloud services enable monitoring.googleapis.com

# BigQuery (si usás export de billing)
gcloud services enable bigquery.googleapis.com
```

### 2) Permisos del Service Account de Cloud Functions

Asignar roles al **service account** que ejecuta la función (por defecto, `PROJECT_ID@appspot.gserviceaccount.com`):

- **Monitoring (lectura de métricas)**
  - `roles/monitoring.viewer`
- **BigQuery (si usás Billing Export)**
  - `roles/bigquery.dataViewer`
  - `roles/bigquery.jobUser`
- **Firestore (escritura)**
  - `roles/datastore.user`

### 3) Configuración por variables (Functions Config)

#### Opción A: Cloud Monitoring (default)

```bash
firebase functions:config:set billing.source="monitoring" billing.project_id="TU_PROJECT_ID"
```

#### Opción B: BigQuery Billing Export

```bash
firebase functions:config:set \
  billing.source="bigquery" \
  billing.project_id="TU_PROJECT_ID" \
  billing.bigquery_project_id="BQ_PROJECT_ID" \
  billing.bigquery_dataset="DATASET" \
  billing.bigquery_table="gcp_billing_export_v1"
```

> **Tip:** Para Billing Export, el dataset y table se crean al habilitar el export en Cloud Billing.

### 4) Export de Billing a BigQuery (si aplica)

1. En Cloud Console, ir a **Billing > Export**.
2. Crear un export a BigQuery (Daily cost export).
3. Usar el dataset/table creados en la config.

---

## Ajuste de métricas y mapeo de servicios

La función trae métricas preconfiguradas en `functions/src/index.ts` y las mapea a servicios usados por la app:

- **Firestore**
- **Auth**
- **Storage**
- **Functions**
- **Hosting**

Si tu proyecto usa otras métricas o querés precisión fina (p. ej. Storage bytes por bucket), ajustá la lista `USAGE_METRICS` y/o el `SERVICE_ALIAS_MAP`.

---

## Consideraciones de costos y performance

- **Evitar consultas excesivas**: el scheduler corre una vez al día y suma métricas del mes en curso.
- **BigQuery**: la consulta usa filtros por fecha para minimizar bytes procesados.
- **Firestore**: escribe un único documento mensual (bajo costo).

---

## Deploy

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

### ¿Qué sigue después de `npm --prefix functions run build`?


> ⚠️ **Runtime requerido (deploy):** Cloud Functions con Node.js 18 fue decommissioned.
> Este proyecto debe desplegarse con **Node.js 20** (definido en `functions/package.json`).

Si el build terminó sin errores (como en tu captura), ya tenés el código TypeScript compilado y listo para subir.

1. Verificá que estés logueado en Firebase y en el proyecto correcto:

   ```bash
   firebase login
   firebase use <tu_project_id>
   ```

2. Confirmá que tu Firebase CLI esté actualizado (recomendado):

   ```bash
   firebase --version
   npm i -g firebase-tools@latest
   ```

3. Desplegá únicamente las Cloud Functions:

   ```bash
   firebase deploy --only functions
   ```

4. Probá que respondan:
   - HTTP Functions: abrí la URL que te devuelve el deploy.
   - Scheduler/cron (`collectUsageMetrics`): podés ejecutarla manualmente desde Firebase Console para validar logs y escrituras.

### ¿Para qué eran las `functions` en este proyecto?

Este módulo se usa para mover lógica sensible del cliente al backend de Firebase, principalmente:

- ejecutar tareas programadas (como `collectUsageMetrics`),
- centralizar integraciones externas (por ejemplo Mercado Pago/webhooks),
- proteger secretos y validaciones del lado servidor,
- reducir riesgo de manipulación desde la app/web cliente.

En resumen: **la app Android/web consume datos; `functions` hace la lógica de backend segura y automatizada**.
