// Root project build file.
buildscript {
    repositories {
        google()
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.google.com")
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://repo1.maven.org/maven2")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}
