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

Editá `public/config.js` y reemplazá los valores de `window.SELLIA_CONFIG`:

- `brand.name` y `brand.youtubeVideoId` para la identidad del sitio.
- `contact.whatsappUrl`, `contact.instagramUrl` y `contact.mapsUrl` para los enlaces de contacto.
- `firebase.config` **solo si vas a consumir Firebase desde el sitio web** (si no, puede quedar en `null`).

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

Si en el futuro exponés productos desde Firestore, asegurate de permitir lecturas públicas solo de los campos
necesarios. Recomendación: crear reglas que permitan leer documentos de `products` y nada más.
