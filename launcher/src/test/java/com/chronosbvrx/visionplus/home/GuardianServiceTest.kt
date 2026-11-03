package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El modo persistente solo debe actuar cuando la ventana al frente es el Inicio REAL del
 * sistema y ese Inicio no es Vision+ Home. Si se equivoca, cerraría apps o entraría en un
 * bucle; estos guardianes fijan esa frontera y el cableado del servicio de accesibilidad.
 */
class GuardianServiceTest {

    @Test
    fun onlyRescuesWhenTheForegroundIsTheForeignSystemHome() {
        val own = "com.chronosbvrx.visionplus.home"
        assertTrue(
            "Google TV abrió su launcher",
            GuardianAccessibilityService.shouldRescue(
                foregroundPackage = "com.google.android.apps.tv.launcherx",
                homePackage = "com.google.android.apps.tv.launcherx",
                ownPackage = own,
            ),
        )
        assertTrue(
            "el asistente de fábrica tomó el Inicio",
            GuardianAccessibilityService.shouldRescue(
                foregroundPackage = "com.google.android.tungsten.setupwraith",
                homePackage = "com.google.android.tungsten.setupwraith",
                ownPackage = own,
            ),
        )
        assertFalse(
            "una app cualquiera no es el Inicio",
            GuardianAccessibilityService.shouldRescue(
                foregroundPackage = "com.google.android.youtube.tv",
                homePackage = "com.google.android.apps.tv.launcherx",
                ownPackage = own,
            ),
        )
        assertFalse(
            "Vision+ Home ya está al frente",
            GuardianAccessibilityService.shouldRescue(
                foregroundPackage = own,
                homePackage = own,
                ownPackage = own,
            ),
        )
        assertFalse(
            "sin datos no se actúa",
            GuardianAccessibilityService.shouldRescue(null, null, own),
        )
    }

    @Test
    fun theGuardianIsARealAccessibilityServiceWithASettingsPath() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertTrue("servicio declarado", manifest.contains(".GuardianAccessibilityService"))
        assertTrue("permiso de accesibilidad", manifest.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertTrue("configuración del servicio", manifest.contains("@xml/vision_home_accessibility"))

        val xml = read("src/main/res/xml/vision_home_accessibility.xml")
        assertTrue("solo cambios de ventana", xml.contains("typeWindowStateChanged"))
        assertTrue("no lee la pantalla", xml.contains("canRetrieveWindowContent=\"false\""))

        val service = read("src/main/java/com/chronosbvrx/visionplus/home/GuardianAccessibilityService.kt")
        assertTrue("abre la pantalla de Accesibilidad", service.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"))
        assertTrue("comprueba el Inicio real", service.contains("resolveActivity"))
        assertTrue("cooldown contra bucles", service.contains("PULL_COOLDOWN_MS"))

        val settings = read("src/main/java/com/chronosbvrx/visionplus/home/ui/LauncherSettings.kt")
        assertTrue("opción visible en Ajustes", settings.contains("Modo persistente"))
        val overlays = read("src/main/java/com/chronosbvrx/visionplus/home/ui/Overlays.kt")
        assertTrue("se ofrece al elegir Inicio", overlays.contains("fun PersistentModeDialog("))
    }

    private fun read(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        requireNotNull(found) { "fuente no encontrada: $rel" }
        return found.readText()
    }
}
