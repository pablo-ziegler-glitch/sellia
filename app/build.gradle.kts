plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")

    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")

    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.selliaapp"
    compileSdk = 35

    defaultConfig {
        val mergedPrs = (project.findProperty("mergedPrs") as String?)
            ?.trim()
            .orEmpty()
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
        buildConfigField("String", "MERGED_PRS", "\"${mergedPrs.replace("\"", "\\\"")}\"")
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

    val appCheckDebugOverride = (project.findProperty("appCheckDebug") as String?)
        ?.trim()
        ?.lowercase()
        ?.toBooleanStrictOrNull()
    val forceProductionAppCheck = (project.findProperty("forceProductionAppCheck") as String?)
        ?.trim()
        ?.lowercase()
        ?.toBooleanStrictOrNull()
        ?: false

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["allowBackup"] = "true"
            val useDebugAppCheck = if (forceProductionAppCheck) {
                false
            } else {
                appCheckDebugOverride ?: true
            }
            buildConfigField("boolean", "APP_CHECK_DEBUG", useDebugAppCheck.toString())
        }
        release {
            isMinifyEnabled = true
            manifestPlaceholders["allowBackup"] = "false"
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

    kotlin {
        jvmToolchain(21)
    }

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
    val hiltVersion = "2.55"

    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.ui)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx.v290)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.play.services.auth)
    implementation(libs.play.services.mlkit.barcode.scanning)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)

    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation(libs.zxing.android.embedded)
    implementation(libs.core)
    implementation(libs.accompanist.permissions)
    implementation(libs.charts)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.listenablefuture)
    implementation(libs.androidx.concurrent.futures)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.apache.poi:poi:5.2.5")
    testImplementation("org.apache.poi:poi-ooxml:5.2.5")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

