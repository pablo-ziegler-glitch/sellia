# Gobernanza de configuración (Functions + Android + Web)

## Objetivo

Definir un **contrato único** para parámetros críticos de seguridad/operación y forzar consistencia entre:

- Firebase Functions (fuente canónica de enforcement).
- Android (`BuildConfig` / runtime app config).
- Web (`window.STORE_CONFIG` / runtime config).

## Parámetros críticos (contrato)

> Fuente machine-readable: `docs/config-params-contract.json`.

| Parámetro | Origen | Default | Entorno | Impacto de seguridad |
|---|---|---:|---|---|
| `MP_ACCESS_TOKEN` | Functions params/env | _sin default_ | staging/prod | Alto (secreto API, riesgo de fraude/exfiltración) |
| `MP_WEBHOOK_SECRET` | Functions params/env | _sin default_ | staging/prod | Alto (validación de autenticidad webhook) |
| `MP_WEBHOOK_SECRET_REFS` | Functions params/env | `""` | staging/prod | Alto (rotación/gestión de secretos) |
| `MP_WEBHOOK_SIGNATURE_WINDOW_MS` | Functions params/env | `300000` | staging/prod | Medio (protección anti replay) |
| `MP_WEBHOOK_REPLAY_TTL_MS` | Functions params/env | `86400000` | staging/prod | Medio (deduplicación de eventos) |
| `MP_WEBHOOK_IP_ALLOWLIST` | Functions params/env | `""` | staging/prod | Medio (control de origen de red) |
| `APP_CHECK_ENFORCEMENT_MODE` | Functions params/env | `monitor` | dev/staging/prod | Alto (control de abuso de cliente) |
| `ADMIN_RATE_LIMIT_PER_MINUTE` | Functions params/env | `20` | staging/prod | Medio (frena brute force y abuso operativo) |
| `MP_RECONCILIATION_PENDING_MINUTES` | Functions params/env | `15` | staging/prod | Bajo (operación/reconciliación) |
| `MP_RECONCILIATION_BATCH_SIZE` | Functions params/env | `100` | staging/prod | Bajo (capacidad/costo de reconciliación) |
| `MP_AGED_PENDING_ALERT_MINUTES` | Functions params/env | `120` | staging/prod | Bajo (detección operativa temprana) |
| `BILLING_SOURCE` | Functions params/env | `monitoring` | staging/prod | Bajo (visibilidad de costos) |
| `BILLING_PROJECT_ID` | Functions params/env | _sin default_ | staging/prod | Medio (aislamiento de datos de costos) |
| `BILLING_BIGQUERY_PROJECT` | Functions params/env | _sin default_ | staging/prod | Bajo |
| `BILLING_BIGQUERY_DATASET` | Functions params/env | _sin default_ | staging/prod | Bajo |
| `BILLING_BIGQUERY_TABLE` | Functions params/env | _sin default_ | staging/prod | Bajo |

## Implementación técnica

### 1) Functions: módulo dedicado de configuración

Toda lectura de params/env debe pasar por:

- `functions/src/config/params.ts` (definición de params).
- `functions/src/config/getters.ts` (getters tipados con validación, defaults y límites).

Regla: **no leer `process.env.*` ni `defineString(...).value()` ad hoc fuera de este módulo**.

### 2) Android/Web: mapeo explícito contra contrato

- Android: `app/config/runtime-config-map.json`.
- Web: `public/config/runtime-config-map.json`.

Cada plataforma declara por parámetro:

- equivalente local (`equivalent`) cuando aplica, o
- `notApplicable: true` + razón cuando es backend-only.

### 3) Check automatizable en CI

Script: `scripts/check-config-consistency.js`.

Valida que:

1. Todos los parámetros del contrato existan en los mapas Android y Web.
2. Cada entrada tenga `equivalent` o `notApplicable`.
3. No haya parámetros extra fuera del contrato.

Ejecución local:

```bash
node scripts/check-config-consistency.js
```

## Reglas operativas y de seguridad

- Secretos (`MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`) nunca se exponen en clientes.
- Todo cambio de defaults/rangos en getters requiere actualizar:
  1) contrato JSON,
  2) esta documentación,
  3) mapeos Android/Web.
- CI bloquea drift de contrato para evitar divergencias silenciosas entre plataformas.
