plugins {
    kotlin("android") version "1.6.0"
    kotlin("kapt") version "1.6.0"
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.sellia"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(platform("com.example:catalog:1.0")) // using the version catalog
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.retrofit2:converter-gson")
    // other dependencies without duplicates
}