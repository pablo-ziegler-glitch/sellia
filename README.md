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

## 🌐 Firebase Hosting
Para publicar el catálogo estático en Firebase Hosting:
1. Inicializar Firebase (si aún no está configurado en tu equipo):
   ```bash
   firebase init
   ```
   Elegí **Hosting**, vinculá el proyecto y confirmá que el directorio público es `public`.
2. Desplegar:
   ```bash
   firebase deploy
   ```

Guía recomendada para producción con QR público (web + app):
- `docs/produccion-qr-web-app.md`

## 🖼️ Reemplazo de assets
- Para reemplazar imágenes, agregalas dentro de `public/assets/` en formato `.webp`.
- Actualizá las rutas en `public/index.html` o `public/data/products.json` según corresponda.

## 🧪 Testing
Ejecutar los tests del módulo app:
```bash
./gradlew test
```

## 📤 Exportación CSV (productos, clientes, ventas y gastos)
Desde la pantalla **Cargas masivas** podés generar archivos CSV con los datos actuales:
1. Abrí **Configuración → Cargas masivas**.
2. Elegí **Exportar** en la tarjeta de **Productos**, **Clientes**, **Ventas** o **Gastos**.
3. El archivo se guarda en **Descargas** y se abre el panel para compartirlo.

Los CSV exportados respetan los encabezados de las plantillas actuales para facilitar reimportaciones o análisis externos.

### Exportación total e importación total
También podés generar un CSV único con todas las entidades y reimportarlo:
1. En **Cargas masivas**, usá **Exportar** en la tarjeta **Exportación total**.
2. Para restaurar, usá **Importar** en esa misma tarjeta y seleccioná el CSV total.

La importación total agrega registros de forma segura (no elimina datos existentes) y procesa productos, clientes, ventas y gastos.

## 🚀 Build de release
Generar un APK de release (requiere configuración de signing):
```bash
./gradlew assembleRelease
```

## 🤝 Contribuciones
Las contribuciones son bienvenidas. Crear una rama, aplicar cambios y abrir un PR con una descripción clara del impacto.
