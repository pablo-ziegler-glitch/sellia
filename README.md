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
 ├── gradle/              # Gradle wrapper
 ├── build.gradle.kts     # Configuración raíz de Gradle
 ├── settings.gradle.kts  # Definición de módulos
 ├── gradle.properties    # Propiedades de Gradle
 ├── gradlew              # Wrapper (Unix)
 ├── gradlew.bat          # Wrapper (Windows)
 ├── docs/                # Documentación adicional
 └── README.md            # Overview del proyecto
```

## 🧪 Testing
Ejecutar los tests del módulo app:
```bash
./gradlew test
```

## 🚀 Build de release
Generar un APK de release (requiere configuración de signing):
```bash
./gradlew assembleRelease
```

## 🤝 Contribuciones
Las contribuciones son bienvenidas. Crear una rama, aplicar cambios y abrir un PR con una descripción clara del impacto.
