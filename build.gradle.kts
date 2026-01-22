plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false

    // [REEMPLAZO] Kotlin alineado con stdlib 2.1.x (evita el error de metadata 2.1.0)
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.21" apply false

    // [NUEVO] Necesario para Compose con Kotlin 2.x (Compose compiler plugin)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false

    // Hilt (puede quedar 2.50, pero conviene subirlo para compatibilidad con toolchain moderno)
    id("com.google.dagger.hilt.android") version "2.55" apply false

    // google-services (esto da igual, pero tu versión "4.4.4" no es la típica; 4.4.2 es común)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
