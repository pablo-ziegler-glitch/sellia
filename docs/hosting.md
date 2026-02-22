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


### Configuración segura por pipeline (recomendado)

`public/config.js` soporta inyección en runtime vía `window.__STORE_RUNTIME_CONFIG__` para evitar editar secretos manualmente.
Podés generar un bloque previo al deploy con variables del entorno (`apiKey`, `appId`, canales de contacto, etc.) y mantener en repo solo defaults no sensibles.

Ejemplo de inyección antes de cargar `config.js`:

```html
<script>
  window.__STORE_RUNTIME_CONFIG__ = {
    publicStoreUrl: "https://valkirja.com.ar/product.html",
    tenantId: "valkirja",
    firebase: {
      apiKey: "${FIREBASE_API_KEY}",
      authDomain: "sellia1993.firebaseapp.com",
      projectId: "sellia1993",
      storageBucket: "sellia1993.firebasestorage.app",
      messagingSenderId: "${FIREBASE_MESSAGING_SENDER_ID}",
      appId: "${FIREBASE_APP_ID}"
    },
    contact: {
      whatsapp: "${PUBLIC_WHATSAPP_URL}",
      instagram: "${PUBLIC_INSTAGRAM_URL}",
      maps: "${PUBLIC_MAPS_URL}"
    }
  };
</script>
```


### Dominio configurable por tenant (producción)

La web pública resuelve `publicStoreUrl` por tenant usando este orden:

1. `window.__STORE_RUNTIME_CONFIG__` (si el pipeline lo inyecta).
2. Query param `tenantId` o `TIENDA/tienda` en la URL.
3. Firestore público `public_tenant_directory/{tenantId}` leyendo `publicStoreUrl` o `publicDomain`.
4. Fallback seguro por tenant (para `valkirja`: `https://valkirja.com.ar/product.html`).

Para mantener consistencia operativa, la app Android guarda dominio/URL pública en:

- `tenants/{tenantId}/config/public_store`
- `tenant_directory/{tenantId}` (interno / staff)
- `public_tenant_directory/{tenantId}` (mínimo para discovery web)

Campos persistidos:

- `publicStoreUrl`
- `publicDomain`
- `updatedAt`

Esto evita hardcodeos por marca, reduce errores manuales y permite escalar múltiples tiendas con dominio propio sin redeploy del frontend.

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
