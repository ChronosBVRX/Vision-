package com.chronosbvrx.visionplus.data.adb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El «Inicio en la TV» desde el teléfono: qué contesta la tele, a quién se le puede retirar el
 * Inicio y que el cableado no se rompa (emparejar una vez, restaurar siempre).
 */
class TvHomeAdbTest {

    @Test
    fun understandsWhoOpensTheHomeWhenTheSystemAnswersInListForm() {
        // Salida real de `cmd package resolve-activity --brief` en un Chromecast.
        val output = """
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.apps.tv.launcherx/.home.HomeActivity
        """.trimIndent()
        assertEquals(
            "com.google.android.apps.tv.launcherx/.home.HomeActivity",
            TvHomeRules.parseResolveActivity(output),
        )
    }

    @Test
    fun ignoresTheLinesThatAreNotTheActivity() {
        assertNull(TvHomeRules.parseResolveActivity(""))
        assertNull(TvHomeRules.parseResolveActivity("priority=0 preferredOrder=0"))
        assertNull(TvHomeRules.parseResolveActivity("Starting: Intent { act=android.intent.action.MAIN }"))
    }

    @Test
    fun readsTheLauncherThatIsReallyOnScreen() {
        val modern =
            "topResumedActivity=ActivityRecord{bf3665c u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t15601}"
        assertEquals("com.google.android.apps.tv.launcherx", TvHomeRules.parseResumedPackage(modern))
        val classic = "mResumedActivity: ActivityRecord{f70442e u0 com.chronosbvrx.visionplus.home/.LauncherActivity t1300}"
        assertEquals("com.chronosbvrx.visionplus.home", TvHomeRules.parseResumedPackage(classic))
        assertNull(TvHomeRules.parseResumedPackage("dumpsys sin actividades"))
    }

    @Test
    fun neverTouchesTheAppsThatWouldLeaveTheTvUnusable() {
        listOf(
            "android",
            "com.android.systemui",
            "com.android.tv.settings",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        ).forEach { pkg ->
            assertFalse("no se puede desactivar $pkg", TvHomeRules.canDisable(pkg))
        }
        assertFalse(TvHomeRules.canDisable(TvHomeRules.OUR_PKG))
        assertFalse(TvHomeRules.canDisable(null))
        assertFalse(TvHomeRules.canDisable("   "))
    }

    @Test
    fun canRetireTheFactoryHomeThatStealsTheScreen() {
        assertTrue(TvHomeRules.canDisable("com.google.android.apps.tv.launcherx"))
        assertTrue(TvHomeRules.canDisable("com.google.android.tungsten.setupwraith"))
        assertTrue(TvHomeRules.canDisable("com.tcl.tv"))
    }

    @Test
    fun takesThePackageOutOfAComponent() {
        assertEquals("com.chronosbvrx.visionplus.home", TvHomeRules.packageOf("com.chronosbvrx.visionplus.home/.LauncherActivity"))
        assertNull(TvHomeRules.packageOf(null))
        assertNull(TvHomeRules.packageOf(""))
    }

    @Test
    fun theFlowPairsOnceAppliesAndAlwaysKeepsARestorePath() {
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no empareja con el código", setup.contains("TvAdb.pair("))
        assertTrue("no fija Vision+ Home como Inicio", setup.contains("pm set-home-activity"))
        assertTrue("no retira el Inicio que lo tapa", setup.contains("pm disable-user --user 0"))
        assertTrue("no hay vuelta atrás", setup.contains("pm enable "))
        assertTrue("no recuerda la llave para no pedir código siempre", setup.contains("K_PAIRED"))
        assertTrue("no cubre teles sin emparejamiento moderno", setup.contains("connectLegacyTcp"))
        assertTrue("no verifica quién abre la tele de verdad", setup.contains("probeEffectiveHome"))
    }

    @Test
    fun onlyThePhoneOffersIt() {
        val screen = source("ui/phone/SettingsScreen.kt")
        assertTrue("Ajustes del teléfono no muestra la sección", screen.contains("TvHomeSetupSection()"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("falta el código de emparejamiento", ui.contains("Código de 6 dígitos"))
        assertTrue("falta la acción principal", ui.contains("Emparejar y aplicar"))
        assertTrue("falta restaurar la tele", ui.contains("Restaurar la tele"))
        // La app de la tele no tiene por qué exponer este flujo.
        val tvSettings = File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        if (tvSettings.exists()) {
            assertFalse(
                "la tele no debe ofrecer el flujo ADB",
                tvSettings.readText().contains("TvHomeSetupSection"),
            )
        }
    }

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }
}
