package com.chronosbvrx.visionplus.data.adb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El «Inicio en la TV» desde el teléfono: qué contesta la tele, a quién se le puede retirar el
 * Inicio, la lista de VARIAS teles con su restauración y el cableado del flujo.
 */
class TvHomeAdbTest {

    @Test
    fun understandsWhoOpensTheHomeWhenTheSystemAnswersInListForm() {
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
        // Android 14 en Google TV a veces solo deja esta línea (vista en el Chromecast real).
        val android14 = "ResumedActivity: ActivityRecord{b4706d6 u0 com.google.android.tungsten.setupwraith/.RecoveryActivity t15843}"
        assertEquals("com.google.android.tungsten.setupwraith", TvHomeRules.parseResumedPackage(android14))
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

    // --- Varias teles ---

    @Test
    fun keepsSeveralTvsAndUpdatesOnlyTheRightOne() {
        val sala = TvRecord(id = "a", label = "Sala", host = "192.168.1.10", paired = true)
        val cuarto = TvRecord(id = "b", label = "Cuarto", host = "192.168.1.11", paired = true)
        val list = TvAdbStore.upsert(TvAdbStore.upsert(emptyList(), sala), cuarto)
        assertEquals(listOf("b", "a"), list.map { it.id })

        val applied = TvAdbStore.upsert(
            list,
            sala.copy(applied = true, disabled = listOf("com.google.android.apps.tv.launcherx")),
        )
        assertEquals(2, applied.size)
        assertTrue(applied.first { it.id == "a" }.applied)
        assertEquals(listOf("com.google.android.apps.tv.launcherx"), applied.first { it.id == "a" }.disabled)
        assertFalse("la otra tele no se toca", applied.first { it.id == "b" }.applied)
        assertTrue(applied.first { it.id == "b" }.disabled.isEmpty())
    }

    @Test
    fun theListSurvivesARestartAndIgnoresGarbage() {
        val tvs = listOf(
            TvRecord(
                id = "9a053d28c255422c",
                label = "BeyondTV",
                host = "192.168.1.109",
                paired = true,
                applied = true,
                originalHome = "com.tcl.keycustomfunctionservice/.FallbackHome",
                disabled = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith"),
                updatedAt = 1234L,
            ),
        )
        assertEquals(tvs, TvAdbStore.parse(TvAdbStore.serialize(tvs)))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse(null))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse("no es json"))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse("""[{"label":"sin id"}]"""))
        assertEquals(2, TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().disabled.size)
    }

    @Test
    fun forgettingATvLeavesTheOthersAlone() {
        val list = listOf(
            TvRecord(id = "a", label = "Sala", host = "1"),
            TvRecord(id = "b", label = "Cuarto", host = "2"),
        )
        assertEquals(listOf("b"), TvAdbStore.remove(list, "a").map { it.id })
        assertEquals(list.size, TvAdbStore.remove(list, "no-existe").size)
    }

    @Test
    fun theTvIdentityPrefersAndroidIdAndNeverTakesJunk() {
        assertEquals("abc123", TvAdbStore.identityFrom("abc123", "serial"))
        assertEquals("serial", TvAdbStore.identityFrom(null, "serial"))
        assertEquals("serial", TvAdbStore.identityFrom("null", "serial"))
        assertEquals("", TvAdbStore.identityFrom("null", "unknown"))
        assertEquals("", TvAdbStore.identityFrom("   ", ""))
        // Dos teles con el mismo modelo siguen siendo distintas por su Android ID.
        assertFalse(
            TvAdbStore.identityFrom("id-sala", null) == TvAdbStore.identityFrom("id-cuarto", null),
        )
    }

    @Test
    fun theFlowPairsOncePerTvAndAlwaysKeepsARestorePathForEach() {
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no empareja con el código", setup.contains("TvAdb.pair("))
        assertTrue("no fija Vision+ Home como Inicio", setup.contains("pm set-home-activity"))
        assertTrue("no retira el Inicio que lo tapa", setup.contains("pm disable-user --user 0"))
        assertTrue("no hay vuelta atrás", setup.contains("pm enable "))
        assertTrue("no guarda cada tele por separado", setup.contains("TvAdbStore.upsert"))
        assertTrue("no sabe olvidar una tele", setup.contains("fun forget(context: Context, tvId: String)"))
        assertTrue("no restaura una tele concreta", setup.contains("suspend fun restore(context: Context, tvId: String)"))
        assertTrue("no identifica la tele antes de tocarla", setup.contains("connectTo(context"))
        assertTrue("no verifica quién abre la tele de verdad", setup.contains("probeEffectiveHome"))
    }

    @Test
    fun pairingHasARealTimeoutAndTheDialogCanAlwaysBeClosed() {
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no corre el emparejamiento en su propio hilo", setup.contains("Executors.newSingleThreadExecutor"))
        assertTrue("no abandona un emparejamiento colgado", setup.contains("future.get(timeoutMs"))
        assertTrue("no intenta conectar aunque la tele no confirme", setup.contains("pruebo a conectar igual"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("el diálogo no se puede cancelar", ui.contains("\"Cancelar\""))
        assertTrue("no cancela el trabajo al cerrar", ui.contains("job?.cancel()"))
    }

    @Test
    fun theShellToleratesLibadbsClosedStreamInsteadOfLosingTheOutput() {
        val adb = source("data/adb/TvAdb.kt")
        assertTrue("no tolera el cierre de LibADB", adb.contains("fun isClosedMessage("))
        assertTrue("no lee por bloques hasta el final", adb.contains("val read = input.read(buffer)"))
        assertTrue("no ignora solo el cierre", adb.contains("if (!isClosedMessage(t)) throw t"))
    }

    @Test
    fun onlyThePhoneOffersItAndTheListCanBeManaged() {
        val screen = source("ui/phone/SettingsScreen.kt")
        assertTrue("Ajustes del teléfono no muestra la sección", screen.contains("TvHomeSetupSection()"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("falta el código de emparejamiento", ui.contains("Código de 6 dígitos"))
        assertTrue("falta la acción principal", ui.contains("Emparejar y aplicar"))
        assertTrue("no se pueden agregar más teles", ui.contains("Agregar otra tele"))
        assertTrue("falta restaurar una tele", ui.contains("\"Restaurar\""))
        assertTrue("falta olvidar una tele", ui.contains("\"Olvidar\""))
        assertTrue("no avisa al olvidar con cambios pendientes", ui.contains("Restaurar y olvidar"))
        val tvSettings = File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        if (tvSettings.exists()) {
            assertFalse("la tele no debe ofrecer el flujo ADB", tvSettings.readText().contains("TvHomeSetupSection"))
        }
    }

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }
}
