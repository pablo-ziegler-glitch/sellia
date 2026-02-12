# Producción: QR público con URL SEO + Web/App

Esta guía deja una estrategia lista para producción: un solo QR público que funciona en Android/iOS/web, con URL SEO amigable y deploy en Firebase Hosting con dominio propio.

## 1) Arquitectura recomendada (simple, escalable y SEO)

Usá **un único enlace canónico** para el QR:

- `https://www.tudominio.com/product/<product-slug>-<productId>?store=<storeId>`

Ejemplo:

- `https://www.tudominio.com/product/chaleco-negro-premium-CHALECO-NEGRO-001?store=valkirja`

Flujo de apertura:
1. El usuario escanea el QR.
2. Si tiene la app instalada y App Links/Universal Links están configurados, abre la app en el detalle correcto.
3. Si no tiene la app, abre la web pública en el mismo detalle.

> Decisión SEO: `/product/` comunica mejor intención semántica que `/p/`, mejora legibilidad para usuario y facilita mantenimiento analítico por categoría.

## 2) Estructura de URL recomendada

Para cada producto/publicación generá:

- Path: `/product/<slug>-<productId>`
- Query params opcionales:
  - `store`: identifica negocio/sucursal
  - `utm_source`, `utm_medium`, `utm_campaign`: medición

### Regla técnica para robustez

- El **slug** se usa para SEO/UX.
- El **ID** al final es la clave de negocio para lookup confiable.
- Si cambia el nombre del producto, podés regenerar slug sin romper búsqueda por ID.

## 3) Configuración de Firebase Hosting (rutas limpias)

Para que `/product/...` funcione en SPA o landing híbrida, agregá rewrite a `index.html`.

```json
{
  "hosting": {
    "public": "public",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
    "headers": [
      {
        "source": "/index.html",
        "headers": [{ "key": "Cache-Control", "value": "no-cache" }]
      },
      {
        "source": "**/*.@(js|css|png|jpg|jpeg|webp|svg|woff2)",
        "headers": [{ "key": "Cache-Control", "value": "public,max-age=31536000,immutable" }]
      }
    ],
    "rewrites": [{ "source": "**", "destination": "/index.html" }]
  }
}
```

> Mejora costo/performance: cache largo en assets estáticos reduce latencia y egresos. `no-cache` solo para `index.html` evita servir HTML viejo tras deploy.

## 4) Configurar dominio personalizado en Firebase Hosting

Supongamos que compraste `tudominio.com`.

### 4.1 Agregar dominio en Firebase

1. Entrá a **Firebase Console → Hosting → Add custom domain**.
2. Cargá `www.tudominio.com` (recomendado como canónico público).
3. Opcional: también agregá `tudominio.com` para redirección al `www`.

### 4.2 Configurar DNS en tu proveedor de dominio

Firebase te va a dar registros exactos. Configuralos tal cual:

- Para `www`: normalmente un `CNAME` hacia `ghs.googlehosted.com`.
- Para raíz (`@`): Firebase puede pedir `A`/`AAAA` específicos.
- Para verificación de dominio: un `TXT` temporal o definitivo según caso.

### 4.3 Verificación y certificado SSL

- Firebase valida DNS y emite SSL automáticamente.
- Esperá propagación DNS (habitual: minutos, puede tardar hasta 24h).
- Confirmá que `https://www.tudominio.com` carga sin warnings.

### 4.4 Política de canonical y redirección

Dejá **un solo dominio canónico** para SEO (ej. `www.tudominio.com`) y redirigí el resto.

## 5) Android App Links con dominio final

Configurar App Links con tu dominio real (no `web.app`).

### 5.1 Manifest (intent-filter)

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />

    <data
        android:scheme="https"
        android:host="www.tudominio.com"
        android:pathPrefix="/product/" />
</intent-filter>
```

### 5.2 Router en app (MVVM)

- Parsear `productId` y `store` desde `Intent.data`.
- Resolver en `ViewModel` con repositorio.
- Navegar a `ProductDetailScreen(productId)`.
- No hacer lecturas directas de Firebase en Composable.

## 6) Web pública: resolver detalle por URL

En `public/main.js`:

- leer `window.location.pathname`
- si empieza por `/product/`, extraer último segmento como `<slug>-<productId>`
- parsear `productId` desde el sufijo final
- cargar ese producto y abrir su detalle
- fallback: mostrar landing/catálogo si no existe

Con esto el mismo QR siempre tiene fallback web y URL amigable para compartir.

## 7) Publicación a producción

### 7.1 Pre-requisitos

```bash
npm i -g firebase-tools
firebase login
firebase use --add
```

Elegí tu proyecto de producción (ej. `sellia-prod`) y alias `prod`.

### 7.2 Deploy recomendado (hosting)

```bash
firebase use prod
firebase deploy --only hosting
```

### 7.3 Deploy completo (si hay cambios backend)

```bash
firebase deploy --project sellia-prod
```

## 8) Checklist de producción (obligatorio)

1. Dominio propio activo (`www.tudominio.com`) y SSL válido.
2. App Links/Universal Links verificados con dominio final.
3. URL canónica definida y redirecciones coherentes (`non-www` → `www` o viceversa).
4. Firestore Rules revisadas para lectura pública mínima.
5. Storage Rules cerradas por defecto.
6. UTM en QR para medición de conversiones offline.
7. Plan de rollback: hash de release + versión app.

## 9) Validación post deploy

1. Abrir URL QR en incógnito (sin app): debe abrir web pública.
2. Abrir URL QR con app instalada: debe abrir detalle in-app.
3. Probar URL con slug incorrecto pero ID correcto: debe resolver por ID.
4. Probar producto inexistente: fallback amigable (404 funcional o catálogo).
5. Medir LCP (<2.5s ideal en red móvil).

## 10) Estrategia de costos Firebase

- Evitá listeners en tiempo real en la landing pública.
- Preferí JSON estático versionado si el catálogo cambia poco.
- Migrá a Firestore público solo para casos que necesiten stock/precio en vivo.
- Indexá consultas reales; evitá lecturas sin filtros.

---

## Comandos operativos (copy/paste)

```bash
# Seleccionar proyecto
firebase use prod

# Deploy hosting
firebase deploy --only hosting

# Validar canales
firebase hosting:channel:list

# Preview QA
firebase hosting:channel:deploy qa-qr
```

Con esta estrategia tu QR queda estable en el tiempo, SEO-friendly y desacoplado del canal (app/web), evitando reimpresiones y reduciendo costos operativos.
