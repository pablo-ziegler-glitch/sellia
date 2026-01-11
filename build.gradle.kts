// Updated Kotlin and plugin versions
plugins {
    kotlin("jvm") version "2.1.10"
}

// other configurations
kotlin {
    jvmToolchain { 
        languageVersion.set("2.1.10")
    }
}
