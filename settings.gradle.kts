pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.google.com")
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://repo1.maven.org/maven2")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") // <- necesario para tehras/charts

    }
}

rootProject.name = "selliaApp"
include(":app")
 
