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
