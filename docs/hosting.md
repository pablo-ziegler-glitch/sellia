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
- `refreshIntervalMs`: intervalo opcional de refresco en segundo plano. El frontend valida rango seguro entre 15 y 30 minutos.
- `contact`: links de contacto (WhatsApp, Instagram, Maps).
- `tenantId`: el ID del tenant que usa la app Android (para resolver precios y stock).

## Estrategia de refresco y costo Firestore

La ficha pública de producto (`public/product.js`) usa una estrategia híbrida orientada a costos:

- Carga al entrar en la página.
- Revalida al volver al primer plano (`visibilitychange`, `focus`).
- Refresco periódico de respaldo cada `refreshIntervalMs` (con límites 15–30 min).
- Cache por SKU en `sessionStorage` para reutilizar el último payload durante la sesión y evitar requests repetidos inmediatos.

Impacto esperado:

- Menos lecturas Firestore por usuario inactivo o con pestaña en segundo plano.
- Mejor UX en retorno a la página, porque primero se muestra el dato cacheado y luego se revalida.
- Más control operativo del costo al limitar la frecuencia mínima de refresco.

Recomendaciones de límites para producción:

- Catálogo con cambios poco frecuentes: usar 20–30 min.
- Catálogo con cambios frecuentes: usar 15 min y habilitar actualización manual del usuario si es necesario.
- Evitar valores menores a 15 min salvo campañas puntuales, porque multiplican lecturas sin mejora proporcional de UX.

## Opcional para producción: endpoint backend cacheado

Para reducir aún más costos y exposición del API key web, se recomienda mover la lectura pública a un endpoint backend cacheado (Cloud Functions/Cloud Run + CDN/Hosting rewrite):

- El cliente consulta `GET /public-product/{sku}`.
- El backend valida tenant + SKU y responde desde cache (memoria/Redis/Firestore cache) con TTL corto.
- Solo en cache miss consulta Firestore y actualiza cache.

Esto evita que cada cliente pegue directo a Firestore REST y simplifica la evolución de reglas de seguridad para datos públicos.

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
