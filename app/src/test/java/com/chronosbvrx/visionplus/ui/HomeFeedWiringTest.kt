package com.chronosbvrx.visionplus.ui

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cableado entre los DOS APK. Si alguien cambia un host, borra el provider o se olvida
 * del refresco, el launcher se queda sin Inicio y este test lo caza.
 */
class HomeFeedWiringTest {

    @Test
    fun theAppAnswersTheLinksTheLauncherFires() {
        val main = require("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("atiende el host `open`", main.contains("\"open\".equals(uri.host"))
        assertTrue("atiende el host `title`", main.contains("\"title\".equals(uri.host"))
        assertTrue("cablea el buscador con la consulta dictada", main.contains("SearchSeed.seed("))
        assertTrue("resuelve la ficha del título", main.contains("private fun titleRouteFor"))
        assertTrue("trae la sección desde el launcher", main.contains("\"section\" -> uri.pathSegments"))
        assertTrue("abre Mi lista / historial", main.contains("\"library\" -> Route.Library"))
    }

    @Test
    fun theFeedIsKeptWarmWithoutBlockingTheApp() {
        val main = require("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("refresco al volver a la app", main.contains("HomeFeedStore.refreshAsync(this)"))
        assertTrue(
            "refresco al terminar de cargar el catálogo",
            main.contains("HomeFeedStore.refreshAsync(this@MainActivity)"),
        )
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("el refresco vive en segundo plano", store.contains("Dispatchers.IO"))
        assertTrue("el provider sirve primero el disco", store.contains("if (target.exists() && age in 0..STALE_MS)"))
        assertTrue("nunca se queda sin respuesta", store.contains("HomeFeed(meta = meta(app))"))
    }

    @Test
    fun settingsCanInstallAndChooseTheLauncher() {
        val settings = require("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        assertTrue("ofrece instalar Vision+ Home", settings.contains("LauncherInstall.installLatest(context)"))
        assertTrue("abre el selector de Inicio del sistema", settings.contains("LauncherInstall.openHomeSettings(context)"))
        assertTrue("explica el modo", settings.contains("Poner Vision+ Home como Inicio"))
        val install = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/LauncherInstall.kt")
        assertTrue("descarga el asset del contrato", install.contains("HomeContract.LAUNCHER_ASSET"))
        assertTrue("abre el instalador del sistema", install.contains("application/vnd.android.package-archive"))
    }

    @Test
    fun theManifestDeclaresEveryHostTheLauncherFires() {
        val manifest = require("src/main/AndroidManifest.xml")
        for (host in listOf("watch", "live", "title", "open")) {
            assertTrue("falta el host $host en el filtro VIEW", manifest.contains("android:host=\"$host\""))
        }
    }

    @Test
    fun thePermissionIsSignatureAndTheProviderIsReadOnly() {
        val manifest = require("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:protectionLevel=\"signature\""))
        assertTrue(manifest.contains("android:readPermission=\"com.chronosbvrx.visionplus.permission.HOME_FEED\""))
        assertTrue(manifest.contains("android:name=\".data.homefeed.HomeFeedProvider\""))
        val provider = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedProvider.kt")
        assertTrue("no se puede escribir", provider.contains("UnsupportedOperationException"))
    }

    @Test
    fun bothApksShareTheContractModule() {
        val settings = File("settings.gradle.kts").takeIf { it.exists() } ?: File("../settings.gradle.kts")
        assertTrue("falta el contrato", settings.readText().contains("include(\":home-contract\")"))
        assertTrue("falta el launcher", settings.readText().contains("include(\":launcher\")"))
        val build = File("app/build.gradle.kts").takeIf { it.exists() } ?: File("../app/build.gradle.kts")
        assertTrue(build.readText().contains("project(\":home-contract\")"))
        val launcherBuild = File("launcher/build.gradle.kts")
            .takeIf { it.exists() } ?: File("../launcher/build.gradle.kts")
        assertTrue(launcherBuild.readText().contains("project(\":home-contract\")"))
    }

    @Test
    fun theLauncherNeverGrowsItsOwnStreamingEngine() {
        val launcherBuild = File("launcher/build.gradle.kts")
            .takeIf { it.exists() } ?: File("../launcher/build.gradle.kts")
        val text = launcherBuild.readText()
        assertTrue("no debe empaquetar media3", !text.contains("media3"))
        assertTrue("no debe empaquetar okhttp", !text.contains("okhttp"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
