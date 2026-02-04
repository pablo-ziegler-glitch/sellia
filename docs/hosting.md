# Hosting público con Firebase

Esta guía explica cómo publicar el sitio web público para que el QR abra una ficha de producto sin instalar la app.

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

En la app, configurá la **URL pública** en la pantalla de configuración de marketing.
Esa URL se usará como base para los QRs, agregando el parámetro `q=`.

Ejemplo:

```
https://tu-proyecto.web.app/product.html?q=PRODUCT-123
```

## 6) Reglas de Firestore

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
