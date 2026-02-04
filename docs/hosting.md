# Hosting público con Firebase

Esta guía explica cómo publicar el sitio web público que acompaña al negocio y cómo dejar la configuración lista
para producción.

## 1) Configurar Firebase CLI

```bash
npm install -g firebase-tools
firebase login
```

## 2) Inicializar Hosting

Desde la raíz del repo:

```bash
firebase init hosting
```

Elegí el proyecto correcto y deja `public` como directorio público.

## 3) Completar la configuración web

Editá `public/config.js` y completá:

- `firebase`: credenciales de Firebase Web (apiKey, projectId, etc.).
- `publicStoreUrl`: URL base donde vive `product.html`.
- `refreshIntervalMs`: frecuencia de refresco de precios en la ficha pública.
- `contact`: links de contacto (WhatsApp, Instagram, Maps).
- `tenantId`: el ID del tenant que usa la app Android (para resolver precios y stock).

## 4) Publicar

```bash
firebase deploy --only hosting
```

## 5) Generar QRs con URL pública

Usá la URL pública del hosting como destino del QR:

```
https://tu-proyecto.web.app/
```

Si necesitás atribución de campañas, agregá parámetros UTM a esa URL, por ejemplo:

```
https://tu-proyecto.web.app/?utm_source=qr&utm_medium=offline&utm_campaign=local
```

## 6) Reglas de Firestore (opcional)

La web pública lee desde `tenants/{tenantId}/public_products`, una colección cacheada y sanitizada.
La app Android sincroniza precios a `tenants/{tenantId}/products`, y una Cloud Function replica los campos públicos.

Para habilitar el refresco periódico, configurá el documento:

```
tenants/{tenantId}/config/public_store
{
  publicEnabled: true,
  syncIntervalMinutes: 15
}
```

Asegurate de permitir lecturas públicas únicamente de `public_products` (no de `products`).
