// Updated Kotlin and plugin versions
plugins {
    kotlin("jvm") version "2.1.10"
    id("some.plugin.id") version "some.plugin.version"
}

// other configurations
kotlin {
    jvmToolchain { 
        languageVersion.set("2.1.10")
    }
}