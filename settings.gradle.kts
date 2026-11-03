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
        // Kadb publica su SPAKE2 de emparejamiento por Wi-Fi solo en JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.Flyfish233")
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
