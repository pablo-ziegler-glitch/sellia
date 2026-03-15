# SRE Playbook: SLI/SLO, Alertas, Runbooks y Mejora Continua

## 1) SLI definidos

Los SLI se centran en la salud de los flujos críticos del negocio (backoffice y autorización):

### SLI-1: Disponibilidad de Backoffice (BO)
- **Qué mide:** porcentaje de solicitudes HTTP exitosas (`2xx` y `3xx`) al backoffice web sobre el total de solicitudes válidas.
- **Fórmula:**
  - `availability_bo = successful_bo_requests / total_bo_requests * 100`
- **Exclusiones:**
  - Ventanas de mantenimiento aprobadas.
  - Requests de bots/noise detectados por WAF (si no forman parte de usuarios reales).
- **Fuente de datos sugerida:**
  - Uptime checks sintéticos + logs de CDN/Hosting + traces backend (Cloud Functions/API).

### SLI-2: Latencia p95 API
- **Qué mide:** percentil 95 de latencia end-to-end de APIs críticas (authz, catálogo admin, escritura de configuración, operaciones de permisos).
- **Fórmula:**
  - `latency_p95_api = p95(request_duration_ms)` por endpoint y agregado de endpoints críticos.
- **Segmentación mínima:**
  - Por endpoint crítico.
  - Por región.
  - Por tipo de cliente (`web-admin`, `android-app`, `internal-service`).

### SLI-3: Tasa de error de autorización (authz)
- **Qué mide:** ratio de respuestas de error por fallas inesperadas de autorización sobre el total de requests authz evaluadas.
- **Fórmula recomendada:**
  - `authz_error_rate = authz_5xx_or_policy_misconfig / total_authz_evaluations * 100`
- **Nota importante:**
  - Los `403` legítimos por falta de permisos de usuario **no** cuentan como error de plataforma.
  - Sí cuentan como error eventos de regresión por despliegue incorrecto de políticas/reglas.

---

## 2) SLO por etapas

Se define una estrategia de madurez incremental con tres etapas operativas.

| Etapa | Ventana de error budget | Disponibilidad BO (objetivo) | Latencia API p95 (objetivo) | Error authz (objetivo) |
|---|---:|---:|---:|---:|
| Actual | 24h | >= 99.0% diario | <= 900 ms diario | <= 1.00% diario |
| Intermedia | 2h | >= 99.5% por 2h | <= 650 ms por 2h | <= 0.50% por 2h |
| Objetivo | 30m | >= 99.9% por 30m | <= 400 ms por 30m | <= 0.10% por 30m |

### Criterio de burn-rate (recomendado)
- **Fast burn:** alerta crítica cuando se consume >10% del budget en 1 hora.
- **Slow burn:** alerta warning cuando la tendencia proyecta agotamiento en <24h.

### Política de operación del error budget
- Si se agota el budget de la etapa en curso:
  1. Se congelan cambios no críticos.
  2. Se priorizan fixes de confiabilidad.
  3. Se habilita revisión de arquitectura y rollout gates para cambios de permisos/authz.

---

## 3) Alertas con severidad y on-call

### Matriz de severidad

| Severidad | Disparador | Tiempo de respuesta | Escalamiento |
|---|---|---|---|
| Sev-1 (Crítico) | Caída total de BO, auth caída generalizada, error authz > 5% sostenido 10 min | <= 5 min | Pager on-call primario + secundario + incident channel |
| Sev-2 (Alto) | Degradación fuerte p95 (>2x objetivo) o authz entre 1%-5% por 15 min | <= 15 min | On-call primario + líder técnico |
| Sev-3 (Medio) | Tendencia de degradación sin impacto masivo aún | <= 1 h | Ticket priorizado + guardia de negocio |

### Reglas concretas de alerting
- **Disponibilidad BO:**
  - Sev-1: disponibilidad < 97% por 10 min.
  - Sev-2: disponibilidad < 99% por 30 min.
- **Latencia p95 API:**
  - Sev-1: p95 > 1.5s por 10 min en endpoints críticos.
  - Sev-2: p95 > 900ms por 20 min.
- **Error authz:**
  - Sev-1: error authz > 5% por 10 min.
  - Sev-2: error authz > 1% por 15 min.

### Operación on-call
- Rotación semanal primaria + secundaria.
- Hand-off formal de guardia con incidentes abiertos, mitigaciones temporales y riesgos.
- Política “ack en 5 minutos” para Sev-1.

---

## 4) Runbooks de incidentes

Cada runbook debe incluir: trigger, diagnóstico rápido, mitigación inmediata, rollback, validación y comunicación.

### Runbook A: Auth caída
- **Síntoma:** picos de `401/5xx`, login/token verify fallando globalmente.
- **Diagnóstico rápido:**
  1. Revisar status de proveedor de identidad/Auth.
  2. Verificar expiración/rotación de secretos, certificados y claves.
  3. Confirmar latencia/errores en endpoints de token/introspección.
- **Mitigación inmediata:**
  - Activar ruta de contingencia (graceful degradation para operaciones no sensibles).
  - Reducir carga con rate-limit defensivo.
- **Salida del incidente:**
  - Restaurar autenticación completa.
  - Ejecutar smoke tests de login + refresh + operación autorizada.

### Runbook B: Permisos mal desplegados
- **Síntoma:** aumento abrupto de `403` no esperados o acceso indebido detectado.
- **Diagnóstico rápido:**
  1. Comparar versión de reglas/policies desplegada vs aprobada.
  2. Verificar tenant/role mappings y defaults.
  3. Revisar cambios recientes en Firestore Rules / middleware authz.
- **Mitigación inmediata:**
  - Rollback inmediato a versión de política estable.
  - Bloqueo temporal de endpoints sensibles si hay riesgo de sobrepermisos.
- **Salida del incidente:**
  - Reejecutar suite de pruebas de autorización (happy + negative paths).

### Runbook C: Degradación de Functions
- **Síntoma:** aumento de latencia/errores en Cloud Functions o timeouts.
- **Diagnóstico rápido:**
  1. Analizar cold starts, concurrencia y saturación por región.
  2. Revisar dependencias externas (DB, APIs, colas).
  3. Detectar cambios recientes de runtime/config (memoria, timeout, minInstances).
- **Mitigación inmediata:**
  - Escalar recursos (min instances / memoria) según capacidad.
  - Aplicar circuit breaker/timeouts más agresivos a dependencias inestables.
  - Rollback de release de functions si coincide temporalmente.
- **Salida del incidente:**
  - Confirmar recuperación de p95 y tasa de error.

---

## 5) MTTR y postmortems obligatorios

### Medición de MTTR
- **Definición MTTR:** tiempo desde `incident_start` hasta `incident_resolved` validado.
- **Campos mínimos en cada incidente:**
  - `incident_id`
  - `severity`
  - `start_ts`
  - `detect_ts`
  - `mitigation_ts`
  - `resolved_ts`
  - `root_cause`
  - `customer_impact`
- **Métrica agregada semanal/mensual:**
  - MTTR por severidad.
  - P50/P90 de MTTR.

### Postmortem obligatorio (sin culpables)
- Requerido para todo Sev-1 y Sev-2.
- Deadline: borrador en 48h, versión final en 5 días hábiles.
- Estructura mínima:
  1. Timeline detallado.
  2. Causa raíz técnica y organizacional.
  3. Qué alertó tarde / qué detección faltó.
  4. Acciones correctivas (owner + fecha compromiso).
  5. Verificación de cierre.

### KPIs de mejora continua
- Reducción mensual de MTTR.
- Disminución de incidentes repetidos por misma causa.
- % de acciones postmortem cerradas en fecha.

---

## Implementación recomendada (30 días)

1. **Semana 1:** instrumentación de métricas y dashboards por SLI.
2. **Semana 2:** alertas con severidad + rotación on-call y simulacro.
3. **Semana 3:** runbooks operativos y pruebas de respuesta.
4. **Semana 4:** reporte MTTR + primera ronda formal de postmortems.

