pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Emparejamiento ADB por Wi-Fi desde el móvil (LibADB, de App Manager).
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\.MuntashirAkon.*")
            }
        }
    }
}

rootProject.name = "Vision+"
include(":app")
// El launcher de Smart TV vive en su propio APK (decisión 2026-10-23): si falla, Vision+
// sigue intacto. Lo único que comparten es el contrato del feed, sin UI ni motor.
include(":home-contract")
include(":launcher")
