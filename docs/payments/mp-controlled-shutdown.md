# Protocolo de apagado controlado de Mercado Pago (incidente)

## Objetivo
Reducir impacto operativo y financiero ante degradación de Mercado Pago, habilitando fallback transaccional sin detener ventas.

## Flags operativos

1. **Global**: `payments.mp.enabled`
   - Ubicación: `config/runtime_flags`
   - Semántica: kill switch para toda la plataforma.

2. **Por tenant**: `tenant.payments.mp.enabled`
   - Ubicación: `tenants/{tenantId}/config/runtime_flags`
   - Semántica: apaga MP solo para un tenant.

Estado efectivo: `global && tenant`.

## Runbook (paso a paso)

1. **Detectar incidente**
   - Señales: tasa de error > 5%, timeout sostenido, errores 5xx de PSP, aumento de abandono checkout.

2. **Contención rápida**
   - En Backoffice (`Cloud Services`) aplicar toggle:
     - scope `tenant` para impacto acotado;
     - scope `global` solo si el incidente es regional/plataforma.
   - Motivo obligatorio (ticket/incidente) para auditoría.

3. **Fallback automático en POS**
   - Si MP está apagado/degradado, la función `createPaymentPreference` responde `failed-precondition` con `fallbackPaymentMethod=TRANSFERENCIA`.
   - Android aplica fallback y cambia método de cobro a transferencia para permitir continuidad operativa.

4. **Comunicación interna**
   - Notificar operaciones/comercial:
     - tenants impactados,
     - ventana estimada,
     - método temporal habilitado.

5. **Recuperación**
   - Validar salud (error budget estable + pruebas end-to-end).
   - Rehabilitar toggles en orden: tenant(s) piloto → resto → global.

6. **Postmortem**
   - Revisar auditoría en `tenants/{tenantId}/audit_logs` (eventos `toggle_mercadopago`).
   - Registrar timeline, causa raíz, MTTD/MTTR y acciones preventivas.

## Controles de seguridad

- Solo `super admin` puede cambiar flag global.
- `owner/admin` del tenant (o super admin) pueden cambiar flag tenant.
- Cada cambio exige `reason` y genera auditoría con actor, IP, user-agent y timestamp.
- El fallback evita hard-stop de ingresos durante incidentes PSP.
