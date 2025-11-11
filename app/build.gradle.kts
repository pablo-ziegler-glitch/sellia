plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    // ⬇️ Aplica Hilt en el módulo app
    id("com.google.dagger.hilt.android")

}

android {
    namespace = "com.example.selliaapp"
    compileSdk = 35

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    defaultConfig {
        applicationId = "com.example.selliaapp"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // ⬇️ Kotlin DSL: usar "isReturnDefaultValues" (no "returnDefaultValues")
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Si usás Robolectric:
            // isIncludeAndroidResources = true
        }
    }

}
val cameraxVersion = "1.4.2" // estable al 16 Jul 2025

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
     implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.camera.core)
    implementation(libs.play.services.mlkit.barcode.scanning)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.benchmark.traceprocessor.android)
    implementation(libs.androidx.databinding.adapters)
    implementation(libs.engage.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.core)
    implementation(libs.androidx.paging.common.android)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.storage)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.junit.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.ui)
//    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    //implementation("androidx.compose.compiler:compiler:1.5.12")

// Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

// Firebase (opcional si vas a usar Firestore)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(platform(libs.firebase.bom))


    implementation(libs.material.icons.extended) // SIN versión
    implementation(platform(libs.androidx.compose.bom)) // o tu BOM

    // CameraX
    //implementation(platform("androidx.camera:camera-bom:1.3.4"))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ML Kit

    implementation(libs.zxing.android.embedded)
    implementation(libs.core)


    // Accompanist (para permisos)
    implementation(libs.accompanist.permissions)


    implementation(libs.androidx.concurrent.futures)
    implementation(libs.listenablefuture)
    implementation(libs.kotlinx.coroutines.guava)

    // ⬇️ Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


    // (Opcional) Hilt para WorkManager si vas a inyectar Workers
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.zxing.android.embedded) // o última estable

    implementation(libs.charts)

    // WorkManager (KTX)
    implementation(libs.androidx.work.runtime.ktx.v290)

    // Paging runtime (Room + Flow)
    implementation(libs.androidx.paging.runtime.ktx)

    // Paging Compose (para collectAsLazyPagingItems y items(lazyPagingItems))
    implementation(libs.androidx.paging.compose)
    implementation("androidx.room:room-paging:2.6.1")   // ⬅️ **AGREGAR ESTO**


    // --- Networking ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5")

    // --- Tests (unit) ---
    testImplementation("junit:junit:4.13.2")
    // Alineo coroutines a 1.9.0 (compat con Kotlin 2.1.x)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    // --- AndroidTest (instrumented) ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")        // mejora tiempos de build
        arg("room.expandProjection", "true")   // ayuda con queries complejas
    }
}
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.1.10")
        }
    }
}


