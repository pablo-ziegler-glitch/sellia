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

Editá `public/config.js` y reemplazá los valores de `firebaseConfig`.

También podés completar `storeContact` para mostrar datos de la tienda en la ficha pública.

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

Si vas a exponer productos desde Firestore, asegurate de permitir lecturas públicas solo de los campos necesarios.
Recomendación: crear reglas que permitan leer documentos de `products` y nada más.
