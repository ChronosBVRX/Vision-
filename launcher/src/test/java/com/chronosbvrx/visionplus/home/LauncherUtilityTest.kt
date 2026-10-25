package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un launcher útil no se queda en la vitrina: el OK largo del mando abre opciones reales,
 * las apps se pueden desinstalar/inspeccionar/cerrar, la lista se refresca sola y el
 * buscador también encuentra lo instalado.
 */
class LauncherUtilityTest {

    @Test
    fun theLongPressWorksWithTheRemote() {
        val components = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Components.kt")
        assertTrue("escucha la tecla antes que el click", components.contains("onPreviewKeyEvent"))
        assertTrue("OK del mando", components.contains("KEYCODE_DPAD_CENTER"))
        assertTrue("botón Menú/Info del mando", components.contains("KEYCODE_MENU"))
        assertTrue("temporizador de OK largo", components.contains("LONG_PRESS_MS"))
        assertTrue("candado del KeyUp", components.contains("TvPressGuard"))
        assertTrue("la repetición del mando también cuenta", components.contains("repeatCount"))

        val dialog = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Personalize.kt")
        assertTrue("el diálogo se come el KeyUp del OK largo", dialog.contains("TvPressGuard.takeKeyUp()"))
    }

    @Test
    fun appsCanBeUninstalledAndInspectedFromTheLauncher() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("acción Desinstalar", screen.contains("Desinstalar"))
        assertTrue("acción Información de la app", screen.contains("Información de la app"))
        assertTrue("acción Cerrar app", screen.contains("Cerrar app"))
        assertTrue("abre el desinstalador", screen.contains("appsRepo.requestUninstall"))
        assertTrue("abre la ficha del sistema", screen.contains("appsRepo.openAppInfo"))
        assertTrue("detiene la app", screen.contains("appsRepo.stopApp"))

        val repo = require("src/main/java/com/chronosbvrx/visionplus/home/data/AppsRepository.kt")
        assertTrue("desinstalador de Android", repo.contains("Intent.ACTION_DELETE"))
        assertTrue("ficha de la app", repo.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue("cierre real", repo.contains("killBackgroundProcesses"))
        assertTrue("regla de apps de fábrica", repo.contains("FLAG_UPDATED_SYSTEM_APP"))
        // FLAG_INSTALLED no viene en varias versiones y dejaba sin Desinstalar a las apps
        // instaladas por el usuario: la regla no puede depender de él.
        assertTrue("no depende de FLAG_INSTALLED", !repo.contains("ApplicationInfo.FLAG_INSTALLED"))

        val manifest = require("src/main/AndroidManifest.xml")
        assertTrue("permiso para cerrar apps", manifest.contains("KILL_BACKGROUND_PROCESSES"))
        assertTrue("permiso para desinstalar", manifest.contains("REQUEST_DELETE_PACKAGES"))
    }

    @Test
    fun theAppListRefreshesWhenSomethingIsInstalledOrRemoved() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("Intent.ACTION_PACKAGE_ADDED"))
        assertTrue(screen.contains("Intent.ACTION_PACKAGE_REMOVED"))
        assertTrue(screen.contains("appsRepo.load(force = true)"))
        assertTrue(screen.contains("registerReceiver"))
        assertTrue(screen.contains("unregisterReceiver"))
    }

    @Test
    fun theSearchAlsoFindsInstalledApps() {
        val search = require("src/main/java/com/chronosbvrx/visionplus/home/ui/SearchSurface.kt")
        assertTrue("filtra apps instaladas", search.contains("filterApps(apps, query)"))
        assertTrue("las pinta como atajos", search.contains("AppHitChip("))
        assertTrue("las abre", search.contains("onApp(app)"))

        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("el buscador recibe la lista", screen.contains("apps = apps,"))
        assertTrue("el launcher abre la app", screen.contains("onApp = { app ->"))
    }

    @Test
    fun theAppsTabOffersTheSystemPages() {
        val surfaces = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        assertTrue(surfaces.contains("Pantalla"))
        assertTrue(surfaces.contains("Sonido"))
        assertTrue(surfaces.contains("Almacenamiento"))
        assertTrue(surfaces.contains("Fecha y hora"))
        assertTrue(surfaces.contains("Acerca de"))
        assertTrue(surfaces.contains("Apps del sistema"))

        val repo = require("src/main/java/com/chronosbvrx/visionplus/home/data/AppsRepository.kt")
        assertTrue(repo.contains("ACTION_DISPLAY_SETTINGS"))
        assertTrue(repo.contains("ACTION_SOUND_SETTINGS"))
        assertTrue(repo.contains("ACTION_INTERNAL_STORAGE_SETTINGS"))
        assertTrue(repo.contains("ACTION_DEVICE_INFO_SETTINGS"))
        assertTrue(repo.contains("ACTION_DATE_SETTINGS"))
        assertTrue(repo.contains("ACTION_APPLICATION_SETTINGS"))
    }

    @Test
    fun theManagerAlsoLetsYouUninstall() {
        val personalize = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Personalize.kt")
        assertTrue("el gestor recibe la acción", personalize.contains("onUninstall"))
        assertTrue("papelera en la fila", personalize.contains("DeleteOutline"))
        assertTrue("solo si Android lo permite", personalize.contains("app.canUninstall"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
