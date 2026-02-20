# Setup mínimo de Mercado Pago (Cloud Functions)

Este proyecto consume credenciales de Mercado Pago desde **variables de entorno** y **Firebase Functions params** (compatible con Firebase CLI actual, sin `functions.config()`).

## ✅ Secretos requeridos
- `MP_ACCESS_TOKEN`: Access token privado de Mercado Pago.
- `MP_WEBHOOK_SECRET`: Secret para validar firmas de webhooks.

> ℹ️ **Sobre keys públicas:** para este flujo actual (Checkout Pro vía `createPaymentPreference` en Cloud Functions) **no** hace falta `PUBLIC_KEY` en la app Android. La `PUBLIC_KEY` solo es necesaria si integrás SDK cliente de Mercado Pago (CardForm/Bricks) directamente en frontend.

## Opción recomendada: Firebase Functions params (sin APIs deprecadas)
Definí params en `.env.<projectId>` dentro de la carpeta `functions/` (por ejemplo `.env.sellia1993`):

```bash
MP_ACCESS_TOKEN=TU_ACCESS_TOKEN_PRIVADO
MP_WEBHOOK_SECRET=TU_WEBHOOK_SECRET
```

También podés definir params en el deploy interactivo (`firebase deploy --only functions`) cuando CLI lo solicite.

## Opción alternativa: variables de entorno en CI/CD
Si tu pipeline inyecta env vars al runtime, seteá:

```bash
MP_ACCESS_TOKEN=TU_ACCESS_TOKEN_PRIVADO
MP_WEBHOOK_SECRET=TU_WEBHOOK_SECRET
```

> ✅ **Nota:** Las Functions priorizan `process.env` y luego leen params (`defineString`).

## Checklist de conexión completa (fin a fin)
1. **Secrets cargados en Functions** (`MP_ACCESS_TOKEN` + `MP_WEBHOOK_SECRET`).
2. **Webhook URL configurada en Mercado Pago Developers** apuntando a la función desplegada:
   - Formato esperado: `https://us-central1-<tu-proyecto>.cloudfunctions.net/mpWebhook`
   - Evento recomendado: `payment`
3. **Mismo entorno en todo el flujo**:
   - Sandbox/Test: token test + webhook test.
   - Producción: token prod + webhook prod.
4. **Verificación operativa**:
   - `createPaymentPreference` devuelve `init_point`.
   - Mercado Pago envía callback a `mpWebhook`.
   - Se persiste estado en Firestore (`tenants/{tenantId}/payments/{paymentId}`).

## Validación rápida
Si al crear una preferencia recibís errores de configuración, revisá:
- Que ambos secretos estén definidos.
- Que el access token sea el **privado** (no el público).
- Que el webhook secret corresponda al entorno correcto (test vs prod).

## Comandos útiles de diagnóstico
```bash
# Deploy de Functions (CLI actual)
firebase deploy --only functions

# Ver logs del webhook
firebase functions:log --only mpWebhook
```

## Go-live verificado

Usar esta lista como **criterio de salida a producción** para minimizar fallas operativas y cruces de credenciales entre ambientes.

1. **Confirmar URL final de `mpWebhook` por entorno/proyecto**
   - Verificar que la URL publicada coincida con el proyecto Firebase correcto.
   - Formato por proyecto: `https://us-central1-<projectId>.cloudfunctions.net/mpWebhook`.
   - Registrar explícitamente la URL en la documentación operativa de cada entorno.

2. **Registrar webhook en Mercado Pago Developers con evento `payment`**
   - Configurar el endpoint exacto del paso anterior.
   - Seleccionar evento `payment` para asegurar actualización de estado de cobros.
   - Confirmar que el panel de Mercado Pago muestre webhook activo y sin errores de validación.

3. **Ejecutar pago de prueba y validar logs en `mpWebhook`**
   - Generar preferencia con `createPaymentPreference` en el mismo entorno objetivo.
   - Completar un pago de prueba con usuario/tarjeta de test (o flujo real en prod controlada).
   - Validar recepción del callback en logs:

   ```bash
   firebase functions:log --only mpWebhook
   ```

4. **Validar escritura del documento de pago en Firestore**
   - Confirmar creación/actualización del documento esperado en:
     - `tenants/{tenantId}/payments/{paymentId}`
   - Verificar campos mínimos de control operativo: `status`, `statusDetail`, `amount`, `updatedAt`.
   - Corroborar consistencia entre el estado informado por Mercado Pago y el estado persistido.

5. **Definir y aplicar criterio de rollback**
   - Si no se observa callback en `mpWebhook` dentro de una ventana de tiempo definida (recomendado: **10-15 minutos**), ejecutar rollback controlado.
   - Rollback mínimo sugerido:
     - Deshabilitar temporalmente la promoción de tráfico al entorno afectado.
     - Restaurar variables del último deploy estable.
     - Revertir configuración de webhook al endpoint estable anterior.
   - Registrar incidente con timestamp, payment IDs afectados y acciones tomadas.

6. **Tabla de variables por entorno (test/prod) para evitar cruces de credenciales**

| Entorno | Firebase Project ID | `MP_ACCESS_TOKEN` | `MP_WEBHOOK_SECRET` | URL `mpWebhook` |
|---|---|---|---|---|
| Test | `<project-id-test>` | `TEST-...` | `test_webhook_secret_...` | `https://us-central1-<project-id-test>.cloudfunctions.net/mpWebhook` |
| Producción | `<project-id-prod>` | `APP_USR-...` | `prod_webhook_secret_...` | `https://us-central1-<project-id-prod>.cloudfunctions.net/mpWebhook` |

> ✅ Recomendación operativa: mantener esta tabla versionada junto al runbook de despliegue y actualizarla en cada cambio de credenciales.
