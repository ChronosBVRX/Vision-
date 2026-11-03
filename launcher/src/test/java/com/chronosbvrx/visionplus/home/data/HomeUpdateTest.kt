package com.chronosbvrx.visionplus.home.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTA interno del launcher: se lee la versión REAL del `VisionPlusHome.apk` del release y
 * SOLO se ofrece instalar si es más nueva que la que está corriendo. Nunca un downgrade.
 */
class HomeUpdateTest {

    @Test
    fun aNewerLauncherIsOfferedAndAnEqualOrOlderIsNot() {
        assertTrue("1.3.5 (28) → 1.3.6 (29)", HomeUpdate.launcherNeedsUpdate(29, "1.3.6", 28, "1.3.5"))
        assertFalse("la misma versión no se ofrece", HomeUpdate.launcherNeedsUpdate(28, "1.3.5", 28, "1.3.5"))
        assertFalse("una versión vieja jamás degrada", HomeUpdate.launcherNeedsUpdate(27, "1.3.4", 28, "1.3.5"))
    }

    @Test
    fun withoutVersionCodeTheNameDecides() {
        assertTrue(HomeUpdate.launcherNeedsUpdate(0, "1.3.6", 0, "1.3.5"))
        assertFalse(HomeUpdate.launcherNeedsUpdate(0, "1.3.5", 0, "1.3.5"))
        assertFalse(HomeUpdate.launcherNeedsUpdate(0, "1.3.4", 0, "1.3.5"))
    }

    @Test
    fun theInstallChecksTheReleaseBeforeDownloadingAnything() {
        val update = source("data/HomeUpdate.kt")
        assertTrue("lee la versión del APK publicado", update.contains("getPackageArchiveInfo"))
        assertTrue("comprueba el release al instalar", update.contains("checkLauncher(app, force = true)"))
        assertTrue("se niega si no es más nueva", update.contains("if (!launcherNeedsUpdate("))
        assertTrue("estado honesto de «ya estás al día»", update.contains("State.UpToDate"))
        assertTrue("no se ofrece una versión igual o menor", update.contains("latestCode > currentCode"))
    }

    @Test
    fun theLauncherOffersTheUpdateOnlyWhenThereIsANewerOne() {
        val screen = source("LauncherScreen.kt")
        assertTrue("comprueba al arrancar", screen.contains("HomeUpdate.checkLauncher(context)"))
        assertTrue("solo con versión más nueva", screen.contains("HomeUpdate.launcherPending()"))
        assertTrue("aviso propio del launcher", screen.contains("LauncherUpdateBanner("))
        assertTrue("se puede posponer sin perder el aviso para la próxima versión", screen.contains("prefs.dismissedUpdate"))

        val surfaces = source("ui/Surfaces.kt")
        assertTrue("el aviso existe", surfaces.contains("fun LauncherUpdateBanner("))
        assertTrue("dice que solo se instala si es más nueva", surfaces.contains("solo se instala si es más nueva"))

        val settings = source("ui/LauncherSettings.kt")
        assertTrue("Ajustes muestra la versión publicada", settings.contains("launcherLatest"))
        assertTrue("y no permite bajar si ya está al día", settings.contains("if (launcherPending) actions.onUpdateHome() else actions.onCheckHome()"))
        assertTrue("no enseña una «última publicada» más vieja que la instalada", settings.contains("HomeUpdate.isOlder(homeVersion"))
        assertTrue("la sección se comprueba al abrirla", screen.contains("settingsSection == LauncherSettingsSection.UPDATES"))
        assertTrue("con una comprobación forzada", screen.contains("HomeUpdate.checkLauncher(context, force = true)"))
    }

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/home/$rel"),
            File("launcher/src/main/java/com/chronosbvrx/visionplus/home/$rel"),
            File("../launcher/src/main/java/com/chronosbvrx/visionplus/home/$rel"),
        ).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
