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
                appVersion = "2.5.171",
                launcherVersion = "1.3.1",
                updatedAt = 1234L,
            ),
        )
        assertEquals(tvs, TvAdbStore.parse(TvAdbStore.serialize(tvs)))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse(null))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse("no es json"))
        assertEquals(emptyList<TvRecord>(), TvAdbStore.parse("""[{"label":"sin id"}]"""))
        assertEquals(2, TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().disabled.size)
        assertEquals("1.3.1", TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().launcherVersion)
        assertEquals("2.5.171", TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().appVersion)
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
        assertTrue(
            "no filtra el dumpsys en la propia tele (por ADB tarda decenas de segundos)",
            setup.contains("grep -m1 -E"),
        )
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

    // --- Instalar Vision+ y su Inicio desde el release ---

    @Test
    fun installsTheLatestVisionPlusAndLauncherFromTheOfficialRelease() {
        val pkg = source("data/adb/TvLauncherPackage.kt")
        assertTrue("no baja del release oficial", pkg.contains("releases/latest/download"))
        assertTrue("no pide el APK del launcher", pkg.contains("HomeContract.LAUNCHER_ASSET"))
        assertTrue("no pide el APK de la app", pkg.contains("HomeContract.APP_ASSET"))
        assertTrue("no lee el version.json del release", pkg.contains("version.json"))
        assertTrue("no conserva el respaldo dentro de la app", pkg.contains("vision_home.apk"))
        val adb = source("data/adb/TvAdb.kt")
        assertTrue("una tele con una versión vieja no se actualizaría", adb.contains("pm install -r -S"))
        assertTrue("un APK de 82 MB no puede cargarse en memoria", adb.contains("fun installApkFileOutput("))
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no instala Vision+ antes del Inicio", setup.contains("ensureVisionPlus(context, updateApp)"))
        assertTrue("no deja la tele sin app si no pudo instalar Vision+", setup.contains("No toco su Inicio"))
    }

    @Test
    fun knowsWhenTheTvsVersionsAreOld() {
        assertTrue(TvLauncherPackage.isNewer("1.3.1", "1.0.14"))
        assertTrue(TvLauncherPackage.isNewer("2.5.171", "2.5.9"))
        assertFalse(TvLauncherPackage.isNewer("1.0.14", "1.3.1"))
        assertFalse(TvLauncherPackage.isNewer("1.3.1", "1.3.1"))
        assertFalse(TvLauncherPackage.isNewer("2.0", "2.0.0"))
        assertTrue(TvLauncherPackage.isNewer("1.3.1", ""))
        assertFalse(TvLauncherPackage.isNewer("", "1.3.1"))
        assertEquals("1.3.1 (vc24)", TvLauncherPackage.versionLine("1.3.1", 24))
        assertEquals("1.3.1", TvLauncherPackage.versionLine(" 1.3.1 ", 0))
    }

    @Test
    fun aCachedApkOnlyCountsWhileItIsFresh() {
        val now = 10_000_000L
        assertTrue(TvLauncherPackage.isFresh(now - 1_000, now))
        assertFalse(TvLauncherPackage.isFresh(now - TvLauncherPackage.CACHE_TTL_MS - 1, now))
        assertFalse("un reloj que se fue para atrás no vale", TvLauncherPackage.isFresh(now + 60_000, now))
        assertFalse(TvLauncherPackage.isFresh(0, now))
    }

    @Test
    fun readsTheInstalledVersionTheTvReports() {
        val dumpsys = """
            Packages:
              Package [com.chronosbvrx.visionplus.home] (8f2a):
                versionName=1.3.1
                versionCode=24
        """.trimIndent()
        assertEquals("1.3.1", TvHomeRules.parseVersionName(dumpsys))
        assertNull(TvHomeRules.parseVersionName("Unable to find package: com.chronosbvrx.visionplus.home"))
        assertNull(TvHomeRules.parseVersionName("versionName=null"))
    }

    @Test
    fun theSettingsHaveADedicatedInstallTvTab() {
        val screen = source("ui/phone/SettingsScreen.kt")
        assertTrue("falta la pestaña", screen.contains("INSTALL_TV(\"Instalar en TV\")"))
        assertTrue(
            "la pestaña de sistema debe ser corta para que las tres quepan sin desplazar",
            screen.contains("SYSTEM(\"Sistema\")"),
        )
        assertTrue("la pestaña no pinta la sección", screen.contains("TvHomeSetupSection()"))
        assertTrue("la pestaña no tiene cuerpo", screen.contains("private fun TvInstallTab()"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("no ofrece instalar", ui.contains("TvHomeSetup.install(context, tv.id)"))
        assertTrue("no dice que se descarga del release", ui.contains("descarga del release"))
        assertTrue("no muestra la versión de la tele", ui.contains("tv.launcherVersion"))
    }

    // --- Teles Android 8/9: sin código de emparejamiento, se conectan por dirección ---

    @Test
    fun eightOrNineTvsConnectByAddressBecauseTheyHaveNoPairingCode() {
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no conecta por dirección", setup.contains("suspend fun connectByAddress("))
        assertTrue("no acepta la dirección escrita", setup.contains("fun normalizeHost("))
        assertTrue("no comprueba el puerto antes de esperar", setup.contains("fun isPortOpen("))
        assertTrue(
            "no explica que el puerto 5555 está cerrado",
            setup.contains("El puerto 5555 de \$host está cerrado"),
        )
        assertTrue(
            "ya no se depende del control remoto de Vision+ para saber la dirección",
            !setup.contains("deja el control remoto vinculado"),
        )
        // La búsqueda automática (mDNS + barrido) no funcionaba en redes reales: se retiró.
        assertFalse("no debe quedar la búsqueda automática", setup.contains("searchLegacy"))
        assertFalse("no debe quedar el barrido de la red", setup.contains("LanScan"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("no hay campo de dirección", ui.contains("Dirección (192.168.1.x)"))
        assertTrue("no explica ADB por red", ui.contains("ADB por red"))
        assertTrue("no ofrece conectar", ui.contains("TvHomeSetup.connectByAddress(context"))
        assertTrue("no ofrece reintentar la conexión", ui.contains("Reintentar conexión"))
        assertTrue("la búsqueda con código vive fuera del diálogo", ui.contains("LaunchedEffect(Unit)"))
        assertTrue("no cancela la acción anterior", ui.contains("fun run(block: suspend () -> Unit)"))
        assertFalse("no debe quedar el botón de búsqueda", ui.contains("Buscar teles Android 8/9"))
        assertTrue(
            "la cancelación del diálogo no puede mostrarse como fallo de conexión",
            setup.contains("if (t is CancellationException) throw t"),
        )
        assertTrue("un trabajo cancelado no debe escribir estado", setup.contains("if (!isActive) return@withContext"))
        assertTrue("no recuerda la última dirección para reintentar", setup.contains("legacyHost = host"))
    }

    @Test
    fun checksTheAdbPortBeforeWaitingAndUnderstandsTheTypedAddress() {
        assertEquals("192.168.1.50", TvHomeSetup.normalizeHost("192.168.1.50"))
        assertEquals("192.168.1.50", TvHomeSetup.normalizeHost("192.168.1.50:5555"))
        assertEquals("192.168.1.50", TvHomeSetup.normalizeHost("http://192.168.1.50/"))
        assertNull(TvHomeSetup.normalizeHost("la tele"))
        assertNull(TvHomeSetup.normalizeHost("192.168.1.999"))

        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        try {
            assertTrue(
                "no detecta el puerto abierto",
                TvHomeSetup.isPortOpen("127.0.0.1", server.localPort, 700),
            )
        } finally {
            runCatching { server.close() }
        }
        assertFalse(
            "un puerto cerrado no puede dar por buena la conexión",
            TvHomeSetup.isPortOpen("127.0.0.1", server.localPort, 300),
        )
    }

    @Test
    fun theAutomaticSearchIsGone() {
        assertFalse(
            "LanScan.kt se retiró",
            File("src/main/java/com/chronosbvrx/visionplus/data/adb/LanScan.kt").exists(),
        )
        val adb = source("data/adb/TvAdb.kt")
        assertFalse("no debe quedar el descubrimiento Android TV por mDNS", adb.contains("findAndroidTvs"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertFalse("no debe quedar la lista de teles encontradas", ui.contains("candidates"))
    }

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }
}
