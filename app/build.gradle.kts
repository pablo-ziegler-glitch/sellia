apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "org.jetbrains.kotlin.kapt")
apply(plugin = "com.google.dagger.hilt.android")

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.sellia"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform("com.example:catalog:1.0")) // using the version catalog
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.retrofit2:converter-gson")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    kapt("com.google.dagger:hilt-compiler:2.50")
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // other dependencies without duplicates
}
