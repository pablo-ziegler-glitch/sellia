pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*")

            }
        }
        mavenCentral()
        // Gradle plugin artifacts publicados en el Plugin Portal.
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Requerido por la dependencia com.github.tehras:charts.
        maven(url = "https://jitpack.io")

    }
}

rootProject.name = "selliaApp"
include(":app")
