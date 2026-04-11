# 📱 Sellia (Android)

## ✨ Descripción del proyecto
Sellia es una aplicación Android pensada para simplificar el proceso de venta en distintos marketplaces. El enfoque está puesto en la usabilidad y la eficiencia para ayudar a las personas a publicar y gestionar ventas rápidamente desde el teléfono.

## 💻 Stack tecnológico
- **Lenguaje**: Kotlin
- **Plataforma**: Android
- **Build system**: Gradle (Android Gradle Plugin)
- **IDE recomendado**: Android Studio

## 📦 Instalación y ejecución
### Opción A: Android Studio
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/pablo-ziegler-glitch/sellia.git
   cd sellia
   ```
2. Abrir **Android Studio** y seleccionar **Open** sobre la carpeta del proyecto.
3. Esperar la sincronización de Gradle.
4. Seleccionar un dispositivo físico o emulador y presionar **Run** (▶).

### Opción B: Línea de comandos (Gradle Wrapper)
Compilar el APK de debug:
```bash
./gradlew assembleDebug
```
Instalar en un dispositivo conectado (opcional):
```bash
./gradlew installDebug
```

## 🗂️ Estructura del proyecto
```
 sellia/
 ├── app/                 # Módulo Android principal
 ├── public/              # Sitio estático para catálogo (Firebase Hosting)
│   ├── index.html       # Landing pública de Valkirja
│   ├── styles.css       # Estilos globales (mobile-first)
│   ├── main.js          # Lógica de interacción de la landing
│   ├── data/            # JSON de productos
│   ├── admin/           # Backoffice interno (auth + guards + hardening)
│   └── assets/          # Imágenes placeholder
 ├── gradle/              # Gradle wrapper
 ├── build.gradle.kts     # Configuración raíz de Gradle
 ├── settings.gradle.kts  # Definición de módulos
 ├── gradle.properties    # Propiedades de Gradle
 ├── gradlew              # Wrapper (Unix)
 ├── gradlew.bat          # Wrapper (Windows)
 ├── docs/                # Documentación adicional
 ├── firebase.json        # Configuración de Firebase Hosting
 └── README.md            # Overview del proyecto
```

## ⚙️ Variables configurables
Las configuraciones de la landing pública viven en `public/main.js` al inicio del archivo:
- `BRAND_NAME`
- `YOUTUBE_VIDEO_ID`
- `WHATSAPP_URL`
- `INSTAGRAM_URL`
- `MAPS_URL`

Reemplazá los valores `REEMPLAZAR` con los datos reales antes de desplegar o probar el sitio estático.

## 👀 Previsualización local del catálogo
El catálogo web es un sitio estático dentro de `public/`. Para previsualizarlo en local:
```bash
python3 -m http.server 8080 --directory public
```
Luego abrí `http://localhost:8080` en el navegador.

Backoffice separado (operación interna):
- `http://localhost:8080/admin/`
- Requiere Firebase Auth + perfil en `users/{uid}` (tenant/role/status).

Backoffice legacy (mantenimiento puntual / compatibilidad):
- `http://localhost:8080/backoffice.html`
- No reemplaza el flujo principal de `admin/`; mantener solo para transición controlada.

## 🚀 Despliegue a producción (secuencia única y obligatoria)
> Esta secuencia es **obligatoria** para evitar desalineación entre reglas/índices, funciones y hosting. No cambiar el orden.

### Precondiciones (una sola vez por equipo)
1. Tener Firebase CLI instalado y autenticado:
   ```bash
   firebase login
   ```
2. Validar que `firebase.json`, `firestore.rules`, `firestore.indexes.json` y `storage.rules` estén versionados en este repo.

### Orden de despliegue manual (producción)
1. Seleccionar proyecto:
   ```bash
   firebase use <projectId>
   ```
2. Desplegar reglas e índices de datos:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes,storage
   ```
3. Desplegar backend (Cloud Functions):
   ```bash
   firebase deploy --only functions
   ```
4. Desplegar frontend público (Hosting):
   ```bash
   firebase deploy --only hosting
   ```
5. Ejecutar verificación final (smoke tests):
   - **Callables**: confirmar que las funciones callable críticas responden `2xx` y sin errores de permisos para usuarios válidos.
   - **Webhook**: disparar evento de prueba del proveedor integrado y validar recepción + procesamiento exitoso en logs de Functions.
   - **Catálogo público**: abrir la URL de Hosting, navegar listado, validar carga de imágenes y consulta de productos sin errores en consola.

### Variante CI/CD (no interactiva, mismo orden fijo)
Usar siempre variables de entorno (`FIREBASE_TOKEN` y `FIREBASE_PROJECT_ID`) y comandos no interactivos:

```bash
firebase use "$FIREBASE_PROJECT_ID" --token "$FIREBASE_TOKEN" --non-interactive
firebase deploy --only firestore:rules,firestore:indexes,storage --project "$FIREBASE_PROJECT_ID" --token "$FIREBASE_TOKEN" --non-interactive
firebase deploy --only functions --project "$FIREBASE_PROJECT_ID" --token "$FIREBASE_TOKEN" --non-interactive
firebase deploy --only hosting --project "$FIREBASE_PROJECT_ID" --token "$FIREBASE_TOKEN" --non-interactive
```

> Recomendación de operación: bloquear merges a `main` si falla cualquier smoke test post-deploy para reducir incidentes en producción.

## 🧭 Índices de Firestore para catálogo público
La consulta `structuredQuery` de `public/catalog.js` usa `collectionGroup` sobre `public_products` y ordena por `tenantId` + `name` en orden ascendente.

Este índice compuesto quedó versionado en `firestore.indexes.json` y referenciado desde `firebase.json` para evitar errores de catálogo cuando escala el volumen de tenants/productos.

Deploy de índices:
```bash
firebase deploy --only firestore:indexes
```

## ☁️ Firebase App Hosting (opcional)
Si preferís desplegar la web con **Firebase App Hosting**, este repo incluye un servidor Node.js mínimo (`apphosting-server.js`) para evitar el error de detección de buildpacks (`No buildpack groups passed detection`).

Comandos:
```bash
npm install
npm run start
```

En App Hosting, configurá como raíz del servicio la carpeta del repositorio (`/workspace/sellia`) para que detecte `package.json` y use el script `start`.

Guía recomendada para producción con QR público (web + app):
- `docs/produccion-qr-web-app.md`
- `docs/sre-sli-slo-alerting-runbooks.md` (SLI/SLO, alertas, runbooks, MTTR y postmortems)

## 🖼️ Reemplazo de assets
- Para reemplazar imágenes, agregalas dentro de `public/assets/` en formato `.webp`.
- Actualizá las rutas en `public/index.html` o `public/data/products.json` según corresponda.

## 🖼️ URLs públicas de imágenes para importación masiva
Para que las imágenes funcionen en la carga masiva de **Productos**, las columnas `imageUrl` / `image_urls` deben apuntar a una URL pública.

Ruta pública recomendada en Firebase Storage (catálogo público):
- `tenants/{tenantId}/public_products/{productId}/images/{archivo_versionado}`

Flujo recomendado para cargas masivas y app Android:
1. Subí la imagen desde la app (gestión de producto) o desde backend/admin a la ruta pública anterior.
2. Conservá **naming versionado** para cache busting sin romper URLs existentes:
   - Formato sugerido: `{orden}_{slug}_v{hash|timestamp}.{ext}`
   - Ejemplo: `01_campera-negra_v1739899476.webp`
3. En sincronización de producto público, Cloud Functions normaliza `imageUrl` / `imageUrls` a URLs finales `alt=media` apuntando a `public_products`.
4. En CSV, pegá esas URLs en `imageUrl` (principal) o `image_urls` (múltiples separadas por `|`).

Notas de operación:
- `public/assets/` se usa para la web estática; el catálogo dinámico de productos usa Firebase Storage.
- No reutilices exactamente el mismo nombre de archivo al reemplazar imagen: creá nueva versión (`v...`) para invalidar caché de CDN/navegadores sin afectar clientes que ya consumen la URL anterior.

## 🧪 Testing
Ejecutar los tests del módulo app:
```bash
./gradlew test
```

## 🔐 Firebase App Check seguro (debug y release)
- **debug**: por defecto usa `DebugAppCheckProviderFactory.getInstance()` con `BuildConfig.APP_CHECK_DEBUG=true`.
  - Prueba puntual de token productivo en debug: `./gradlew assembleDebug -PappCheckDebug=false`.
- **release**: usa exclusivamente `PlayIntegrityAppCheckProviderFactory.getInstance()` con `BuildConfig.APP_CHECK_DEBUG=false`.
- **modo productivo total (recomendado para preproducción/UAT)**: forzá Play Integrity también en debug con `-PforceProductionAppCheck=true`.
  - Ejemplo: `./gradlew assembleDebug -PforceProductionAppCheck=true`.
  - Si querés que quede fijo para todo el equipo/CI, agregá `forceProductionAppCheck=true` en `gradle.properties` (o en `~/.gradle/gradle.properties` si querés solo local).
- No se embeben secrets de App Check en código fuente ni en logs de aplicación.

### Registro temporal de debug tokens (Firebase Console)
1. Ejecutá la app en build `debug`.
2. En Logcat, filtrá por `DebugAppCheckProvider` y copiá el token generado por el SDK (sin persistirlo en repositorio, docs internas ni gestores de tareas).
3. Abrí **Firebase Console → App Check → [tu app Android] → Manage debug tokens**.
4. Registrá el token con etiqueta de trazabilidad (ejemplo: `dev-<iniciales>-<fecha>`).
5. Volvé a ejecutar la app y validá que App Check responde OK.

### Rotación recomendada de debug tokens
- Crear tokens por dispositivo/persona (no compartidos).
- Definir vencimiento operativo corto (7-30 días) y rotación inmediata ante baja de equipo, pérdida de dispositivo o sospecha de exposición.
- Eliminar tokens no utilizados en cada cierre de sprint.
- Nunca publicar tokens en PRs, capturas, chats abiertos o documentación versionada.

### ¿Qué vas a poder hacer y qué no vas a poder hacer? (explicado simple)
**Vas a poder:**
- Probar la app en `debug` durante desarrollo aunque App Check esté activo, registrando un token temporal en Firebase Console.
- Mantener entornos de desarrollo y producción separados sin mezclar proveedores de App Check.
- Detectar rápido problemas de configuración de App Check sin exponer secretos en el código.

**No vas a poder:**
- Usar un secreto fijo en el código para destrabar cualquier dispositivo. Eso se eliminó por seguridad.
- Pasar por App Check en `release` con el provider de debug: en producción solo se admite Play Integrity.
- Reutilizar tokens de debug viejos indefinidamente: deben rotarse y eliminarse.

**¿Para qué sirve esto?**
- Evita que apps modificadas o no confiables consuman tus recursos Firebase (Firestore, Storage, Functions).
- Reduce riesgo operativo y de costos por abuso de backend.
- Te deja un flujo seguro para desarrollo y un flujo estricto para producción, que es lo correcto para escalar.

## 📤 Exportación CSV (productos, clientes, ventas y gastos)
Desde la pantalla **Cargas masivas** podés generar archivos CSV con los datos actuales:
1. Abrí **Configuración → Cargas masivas**.
2. Elegí **Exportar** en la tarjeta de **Productos**, **Clientes**, **Ventas** o **Gastos**.
3. El archivo se guarda en **Descargas** y se abre el panel para compartirlo.

Los CSV exportados respetan los encabezados de las plantillas actuales para facilitar reimportaciones o análisis externos.

### Regla de importación de stock para productos existentes
- La columna `actualizar_stock` controla si una fila que coincide con un producto existente puede modificar existencias.
- Valores válidos para actualizar stock: `1`, `true`, `si`, `sí`, `x`, `update`, `stock`.
- Si el producto ya existe y `actualizar_stock` no está marcado (o es inválido), la fila se rechaza y queda en el reporte de importación.
- Si el producto no existe, se crea normalmente aunque `actualizar_stock` esté vacío.

### Exportación total e importación total
También podés generar un CSV único con todas las entidades y reimportarlo:
1. En **Cargas masivas**, usá **Exportar** en la tarjeta **Exportación total**.
2. Para restaurar, usá **Importar** en esa misma tarjeta y seleccioná el CSV total.

La importación total agrega registros de forma segura (no elimina datos existentes) y procesa productos, clientes, ventas y gastos.
Si el backup viene en un formato anterior y faltan columnas nuevas (por ejemplo `actualizar_stock`), el importador mantiene retrocompatibilidad y genera automáticamente un archivo total **normalizado** en Descargas con las columnas agregadas.

## 🚀 Build de release
Generar un APK de release (requiere configuración de signing):
```bash
./gradlew assembleRelease
```

## 🤝 Contribuciones
Las contribuciones son bienvenidas. Crear una rama, aplicar cambios y abrir un PR con una descripción clara del impacto.
