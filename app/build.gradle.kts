plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")

    // [NUEVO] obligatorio con Kotlin 2.x + Compose
    id("org.jetbrains.kotlin.plugin.compose")
    // [NUEVO] Necesario si usás Firebase con google-services.json
    id("com.google.gms.google-services")

    id("com.google.dagger.hilt.android")
    // id("com.google.gms.google-services") // solo si tenés google-services.json
}

android {
    namespace = "com.example.selliaapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.selliaapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "boolean",
            "REQUIRE_CASH_SESSION_FOR_CASH_PAYMENTS",
            "true"
        )
        buildConfigField("String", "GLOBAL_PUBLIC_CUSTOMER_TENANT_ID", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }





    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "APP_CHECK_DEBUG", "true")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "APP_CHECK_DEBUG", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // [NUEVO] Kotlin toolchain a 21 (afecta Kotlin y KAPT stubs)
    kotlin {
        jvmToolchain(21)
    }

    // [NUEVO] Kotlin bytecode target 21 (tiene que matchear Javac)
    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module"
            )
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(libs.androidx.compose.runtime.saveable)
    val hiltVersion = "2.55"

    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-compiler:$hiltVersion")

    // Si usás Hilt + WorkManager:
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.7")

    // Hilt + Navigation Compose (hiltViewModel)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

 
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.junit.junit)
    implementation(libs.androidx.viewbinding)
    implementation(libs.androidx.viewbinding)
    implementation(libs.androidx.viewbinding)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.ui)

    implementation("com.google.android.gms:play-services-auth:21.2.0")

// Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler.vroomversion)

    // Firebase (opcional si vas a usar Firestore)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    // Firebase (sin KTX)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.play.services.auth)

    implementation(libs.androidx.browser)


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
    //implementation(libs.hilt.android)
    //kapt(libs.hilt.android.compiler)
    //implementation(libs.androidx.hilt.navigation.compose)


    // (Opcional) Hilt para WorkManager si vas a inyectar Workers
    //implementation(libs.androidx.hilt.work)
    //kapt(libs.androidx.hilt.compiler)

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
    // Necesario para soportar archivos .xlsx/.xlsm en runtime (WorkbookFactory + OOXML providers)
    implementation("org.apache.poi:poi-ooxml:5.2.5")

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

    // [NUEVO] Apache POI completo para tests JVM (XSSFWorkbook)
    testImplementation("org.apache.poi:poi:5.2.5")
    testImplementation("org.apache.poi:poi-ooxml:5.2.5")

    // (si ya lo tenés, no lo repitas)
    testImplementation("com.google.truth:truth:1.1.5")

}
