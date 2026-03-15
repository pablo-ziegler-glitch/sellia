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



## Alta de admin por email hasheado (claim + perfil + tenant)

Si un usuario no puede acceder al backoffice, este script deja todo consistente en una sola ejecución:

- setea custom claims (`admin=true`, `role=admin|owner`, opcional `superAdmin=true`),
- actualiza `users/{uid}` con `tenantId`, `role`, `status=active`, permisos administrativos,
- actualiza `tenant_users/{tenantId}_{uid}`,
- guarda huella del email en `admin_email_hashes/{sha256(email_normalizado)}` (sin almacenar email plano en ese registro).

### Uso

```bash
# 1) Simulación segura
npm run admin:grant:dry -- --email <EMAIL> --tenant <TENANT_ID> --role admin

# Alternativa explícita (recomendado en Windows/CI):
npm run admin:grant:dry -- --email <EMAIL> --tenant <TENANT_ID> --role admin --service-account <PATH_JSON>

# 2) Aplicar cambios
npm run admin:grant -- --email <EMAIL> --tenant <TENANT_ID> --role admin

# Opcional: elevar también super admin
npm run admin:grant -- --email <EMAIL> --tenant <TENANT_ID> --role admin --super-admin
```

> Después de aplicar claims, forzar refresh del token del usuario (`logout/login` o `getIdToken(true)`).


> Resolución de proyecto: el script toma `--project`, luego `GCLOUD_PROJECT`/`GOOGLE_CLOUD_PROJECT`, luego `FIREBASE_CONFIG.projectId` y por último `.firebaserc`.
>
> Si no hay ADC configuradas, el script ahora corta con error explícito (sin depender de `metadata.google.internal`): ejecutar `gcloud auth application-default login`, exportar `GOOGLE_APPLICATION_CREDENTIALS=/ruta/service-account.json`, o pasar `--service-account <PATH_JSON>`.

## Gestión de claim `superAdmin` (asignar/revocar)

Las reglas y funciones administrativas ya **no dependen de un email fijo**. El bypass global usa `request.auth.token.superAdmin == true`.

### Flujo operativo (CLI)

1. Ejecutar con una identidad con permisos para `firebaseauth.users.update` (por ejemplo, service account de administración o credenciales gcloud con rol adecuado).
2. Asignar claim:

```bash
npm run claims:super-admin:grant -- --uid <UID>
# o
npm run claims:super-admin:grant -- --email <EMAIL>
```

3. Revocar claim:

```bash
npm run claims:super-admin:revoke -- --uid <UID>
# o
npm run claims:super-admin:revoke -- --email <EMAIL>
```

4. Forzar refresh de token en cliente (logout/login o `getIdToken(true)`) para que el claim impacte de inmediato.

### Alcance técnico

- El script `functions/scripts/manage-super-admin-claim.js` preserva claims existentes y solo actualiza `superAdmin`.
- Se recomienda auditar cada cambio de claims en procesos internos (ticket/cambio) para trazabilidad.

## Configuración requerida

## Prerequisitos de Cloud Functions programadas

Estas validaciones aplican especialmente a las funciones con scheduler (`collectUsageMetrics`, `evaluateUsageAlerts`, `refreshPublicProducts`, `createDailyTenantBackups`) para evitar fallos silenciosos en producción.

### 1) APIs a habilitar (lista exacta)

```bash
gcloud services enable cloudscheduler.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable monitoring.googleapis.com
gcloud services enable bigquery.googleapis.com # solo cuando BILLING_SOURCE=bigquery
```

### 2) IAM mínimo por service account de ejecución

> Service account de ejecución por defecto: `PROJECT_ID@appspot.gserviceaccount.com`.
> Si usás un SA dedicado por función (recomendado en producción), asigná el mismo set mínimo por responsabilidad.

| Service account (ejecución) | Funciones programadas | Roles IAM mínimos |
| --- | --- | --- |
| `PROJECT_ID@appspot.gserviceaccount.com` (o SA dedicado de scheduler) | `collectUsageMetrics` (source=monitoring) | `roles/monitoring.viewer`, `roles/datastore.user` |
| `PROJECT_ID@appspot.gserviceaccount.com` (o SA dedicado de scheduler) | `collectUsageMetrics` (source=bigquery) | `roles/bigquery.dataViewer`, `roles/bigquery.jobUser`, `roles/datastore.user` |
| `PROJECT_ID@appspot.gserviceaccount.com` (o SA dedicado de scheduler) | `evaluateUsageAlerts`, `refreshPublicProducts`, `createDailyTenantBackups` | `roles/datastore.user` |

### 3) Facturación activa (requisito)

Cloud Scheduler requiere proyecto con **facturación activa** para crear/ejecutar jobs programados. Si billing está desactivado, los schedulers no van a disparar aunque el deploy termine correctamente.

### 4) Validación post-deploy

#### 4.1 Logs por función

```bash
firebase functions:log --only collectUsageMetrics
firebase functions:log --only evaluateUsageAlerts
firebase functions:log --only refreshPublicProducts
firebase functions:log --only createDailyTenantBackups
```

#### 4.2 Ejecución manual desde consola (cada scheduler)

1. Ir a **Google Cloud Console > Cloud Scheduler**.
2. Ubicar el job creado por cada función programada.
3. Ejecutar **Run now** en cada job.
4. Verificar en logs de Firebase/Cloud Logging que:
   - no existan errores de permisos (`PERMISSION_DENIED`),
   - no existan errores por API deshabilitada,
   - se complete escritura en Firestore según la función.

### 5) Matriz función -> dependencia externa/API

| Función programada | Trigger | Dependencia externa | API crítica |
| --- | --- | --- | --- |
| `collectUsageMetrics` | Scheduler cada 24h | Cloud Monitoring (default) o BigQuery Billing Export (opcional) | `cloudscheduler.googleapis.com`, `pubsub.googleapis.com`, `monitoring.googleapis.com`, `bigquery.googleapis.com` (si aplica) |
| `evaluateUsageAlerts` | Scheduler cada 1h | Firestore (lectura/escritura de usage y alerts) | `cloudscheduler.googleapis.com`, `pubsub.googleapis.com` |
| `refreshPublicProducts` | Scheduler cada 15 min | Firestore (sincronización `products` -> `public_products`) | `cloudscheduler.googleapis.com`, `pubsub.googleapis.com` |
| `createDailyTenantBackups` | Scheduler cada 24h | Firestore (backup recursivo + retención) | `cloudscheduler.googleapis.com`, `pubsub.googleapis.com` |

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

### 3) Configuración por variables (params + env vars, sin `functions.config()` deprecado)

Definí variables en `functions/.env.<projectId>` (ej. `functions/.env.sellia1993`) o inyectalas vía CI/CD.

#### Opción A: Cloud Monitoring (default)

```bash
BILLING_SOURCE=monitoring
BILLING_PROJECT_ID=TU_PROJECT_ID
```

#### Opción B: BigQuery Billing Export

```bash
BILLING_SOURCE=bigquery
BILLING_PROJECT_ID=TU_PROJECT_ID
BILLING_BIGQUERY_PROJECT=BQ_PROJECT_ID
BILLING_BIGQUERY_DATASET=DATASET
BILLING_BIGQUERY_TABLE=gcp_billing_export_v1
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

---

## Gestión de titularidad de tienda (admin)

Se incorporó la callable function:

- **Nombre:** `manageTenantOwnership`
- **Tipo:** `https.onCall`
- **Acciones soportadas:**
  - `ASSOCIATE_OWNER` (asociar co-dueño)
  - `TRANSFER_PRIMARY_OWNER` (cambiar dueño principal)
  - `DELEGATE_STORE` (delegar operación de tienda)

### Payload base

```json
{
  "tenantId": "tenant_123",
  "action": "TRANSFER_PRIMARY_OWNER",
  "targetEmail": "nuevo.dueno@negocio.com",
  "keepPreviousOwnerAccess": true
}
```

### Garantía de continuidad de datos

La función **solo modifica metadatos de titularidad/permisos** (`tenants`, `users`, `tenant_users`, `ownershipEvents`) y **no migra ni borra datos operativos** del tenant. Por diseño, inventario, ventas, caja, facturas e histórico quedan intactos.

---

## Backup total automático cada 24h (Storage + metadatos en Firestore)

Se incorporó la función programada:

- **Nombre:** `createDailyTenantBackups`
- **Frecuencia:** cada 24h (UTC)
- **Cobertura:** respaldo recursivo completo de cada `tenants/{tenantId}` con todas sus subcolecciones.
- **Payload completo:** se serializa a JSON, se comprime con `gzip` y se sube a Cloud Storage en `gs://<bucket>/tenant-backups/{tenantId}/{runId}.json.gz`.
- **Metadatos en Firestore:** `tenant_backups/{tenantId}/runs/{runId}` guarda estado, actor, métricas (`docCount`, bytes), checksum `sha256` y puntero al archivo (`storageBucket`, `storagePath`, `storageUri`).
- **Retención Firestore:** se conservan solo los **últimos 7** metadatos por tenant para la UI; al podar una corrida también se elimina su archivo en Storage.
- **Retención Storage (recomendada):** configurar lifecycle policy por bucket (por ejemplo 30/60/90 días) para borrar automáticamente objetos antiguos y controlar costo de almacenamiento.

- **Consistencia ante fallos:** si falla la escritura de metadatos después de subir el archivo, la función intenta rollback del objeto en Storage para evitar archivos huérfanos.
- **Trigger de purga optimizado:** `purgeDeletedProductFromBackups` solo dispara nuevo backup cuando `purgeBackup` cambia de `false -> true`, evitando corridas duplicadas por actualizaciones posteriores.
Esto reduce drásticamente costo de lecturas/escrituras en Firestore y permite backups más grandes sin fragmentar en chunks.

### Ejemplo de lifecycle policy (Storage)

```json
{
  "rule": [
    {
      "action": { "type": "Delete" },
      "condition": { "age": 60, "matchesPrefix": ["tenant-backups/"] }
    }
  ]
}
```

Aplicación:

```bash
gsutil lifecycle set lifecycle.json gs://<bucket>
```
