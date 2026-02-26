# Criterio de compatibilidad Kotlin / Jetpack Compose BOM

- `composeBom` debe apuntar **solo** a versiones publicadas de `androidx.compose:compose-bom` (calendario `YYYY.MM.00/01`), verificadas en Google Maven.
- Antes de subir o cambiar versión, validar compatibilidad con el plugin Kotlin configurado en raíz (`org.jetbrains.kotlin.android`) y con `kotlinCompilerExtensionVersion` del módulo `app`.
- En este repo se fija `composeBom = 2024.12.01` porque es una versión existente y estable para el ecosistema Compose actual del proyecto, evitando valores inventados/no publicados como `2025.08.00`.
- Regla operativa: si falla resolución en CI o hay incompatibilidad del compilador Compose, priorizar una BOM publicada inmediatamente anterior compatible y documentar el ajuste en este archivo.
