# Setup mínimo de Mercado Pago (Cloud Functions)

Este proyecto consume credenciales de Mercado Pago desde **Firebase Functions config** o **variables de entorno** en runtime. Para un entorno nuevo, definí ambos secretos con uno de los métodos siguientes.

## ✅ Secretos requeridos
- `MP_ACCESS_TOKEN`: Access token privado de Mercado Pago.
- `MP_WEBHOOK_SECRET`: Secret para validar firmas de webhooks.

## Opción A: Firebase Functions config (recomendado en Firebase CLI)
1. Definir secretos:
   ```bash
   firebase functions:config:set \
     mercadopago.access_token="TU_ACCESS_TOKEN" \
     mercadopago.webhook_secret="TU_WEBHOOK_SECRET"
   ```
2. Verificar configuración (opcional):
   ```bash
   firebase functions:config:get
   ```
3. Deploy:
   ```bash
   firebase deploy --only functions
   ```

## Opción B: Variables de entorno seguras en runtime
Si el entorno de deploy permite variables de entorno, seteá:

```bash
MP_ACCESS_TOKEN=TU_ACCESS_TOKEN
MP_WEBHOOK_SECRET=TU_WEBHOOK_SECRET
```

> ✅ **Nota:** Las Functions priorizan `process.env` sobre `functions.config()` para permitir inyección segura en CI/CD.

## Validación rápida
Si al crear una preferencia recibís errores de configuración, revisá:
- Que ambos secretos estén definidos.
- Que el access token sea el **privado** (no el público).
- Que el webhook secret corresponda al entorno correcto (test vs prod).
