# Política de PII y controles de privacidad (users, customers, logs, exports)

## 1) Clasificación de PII por dominio

### `users`
- **Directo**: `email`, `phone`.
- **Cuasi-identificador**: `displayName`.
- **Sensible**: `documentNumber`.

### `customers`
- **Directo**: `fullName`, `email`, `phoneE164`.
- **Sensible**: `documentNumber`.

### `logs`
- **Operacional**: `actorUid` (necesario para trazabilidad).
- **Sensible**: `requesterIp`.
- **Directo**: `payerEmail`.

### `exports`
- **Operacional**: `storagePath`.
- **Sensible**: `signedUrl`.

---

## 2) Reglas de visualización por rol

La matriz fuente vive en `functions/src/security/piiPolicy.ts` y define, por campo:
- `full`: valor completo.
- `partial`: valor parcialmente visible (ej.: `ab***yz`).
- `masked`: valor oculto (`***MASKED***`).

Roles contemplados: `owner`, `admin`, `manager`, `cashier`, `support`, `auditor`.

Reglas clave:
- `owner/admin` pueden ver parcialmente algunos identificadores directos para operación.
- `auditor` prioriza `partial`/`masked` para minimización de dato.
- `support` no obtiene exposición completa de PII sensible por defecto.

---

## 3) Masking aplicado en backend/logs/BO

### Logs y auditoría
- Se implementó `sanitizePiiForLog` en Cloud Functions para normalizar y enmascarar campos sensibles.
- Se evitan logs de PII en claro para:
  - `actorUid` en eventos de acceso a métricas.
  - `requesterIp`/`actorUid` en `tenants/{tenantId}/audit_logs`.
  - `payerEmail` en errores de webhook de Mercado Pago.

### Respuestas BO
- Para endpoints de backoffice, la guía es no devolver PII sensible en claro salvo necesidad operacional explícita.
- Cuando un valor debe aparecer, priorizar `partial`; si no es imprescindible, `masked`.

---

## 4) Retención y purge automático

Retención declarada en `PII_RETENTION_RULES`:
- `users`: 365 días.
- `customers`: 365 días.
- `logs`: 180 días.
- `exports`: 30 días.

### Estrategia técnica recomendada
1. **Firestore TTL** para colecciones de logs/eventos con campo `expiresAt`.
2. **Job programado diario** (Cloud Scheduler + Function) para purgar:
   - logs sin TTL histórico,
   - artefactos de export/backups expirados.
3. **Storage lifecycle rules** para borrar objetos de export/backup vencidos.
4. **Soft-delete + hard-delete diferido** en datos de cliente, si hay obligación legal de ventana de recuperación.

---

## 5) Controles para auditoría interna/regulatoria

1. **Matriz de clasificación vigente y versionada** (`piiPolicy.ts`).
2. **Evidencia de minimización en logs** (no PII sensible en claro).
3. **Retención por tipo de dato** formalizada y revisada trimestralmente.
4. **Trazabilidad de accesos** en `audit_logs` con identificadores parciales.
5. **Segregación de funciones por rol** (owner/admin/manager/support/auditor).
6. **Proceso de revocación** de permisos y rotación de secretos operativos.
7. **Pruebas periódicas**:
   - test unitario de masking,
   - test de regresión de logs para evitar exposición accidental.

---

## 6) Checklist operativo de cumplimiento

- [ ] Ningún log productivo imprime email/teléfono/documento/IP completos.
- [ ] Nuevos campos en `users/customers` se clasifican antes de deploy.
- [ ] Toda nueva exportación define TTL y mecanismo de purge.
- [ ] Cambios de permisos/roles quedan auditados.
- [ ] Existe runbook de incidente de exposición de datos.
