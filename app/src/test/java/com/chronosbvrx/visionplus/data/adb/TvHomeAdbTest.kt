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
                cleaned = listOf("com.tcl.ttvs", "com.tcl.messagebox"),
                cleanupList = "tcl-g05-google-tv",
                backupFile = "9a053d28c255422c.json",
                cleanupAt = 777L,
                dnsMode = "opportunistic",
                dnsSpecifier = "",
                dnsRecorded = true,
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
        assertEquals(2, TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().cleaned.size)
        assertEquals("tcl-g05-google-tv", TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().cleanupList)
        assertEquals("9a053d28c255422c.json", TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().backupFile)
        assertEquals(777L, TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().cleanupAt)
        assertEquals("opportunistic", TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().dnsMode)
        assertTrue(TvAdbStore.parse(TvAdbStore.serialize(tvs)).first().dnsRecorded)
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
        assertTrue("el diálogo no se puede cancelar", ui.contains("onDismiss"))
        assertTrue("no cancela el trabajo al cerrar", ui.contains("TvHomeSetup.cancel()"))
    }

    @Test
    fun theShellUsesKadbAndKeepsItsStreamsAlive() {
        val adb = source("data/adb/TvAdb.kt")
        assertTrue("no usa Kadb, que es el cliente probado en la TCL", adb.contains("Kadb.create("))
        assertTrue("no reutiliza la llave ya autorizada", adb.contains("KadbCert.set("))
        assertTrue("no abre el shell clásico", adb.contains("\"shell:\$command\""))
        assertTrue("no lee la salida del stream", adb.contains("readUtf8()"))
        assertTrue("no cierra el stream", adb.contains("stream.close()"))
        assertTrue("no sube el APK por sync", adb.contains("connection.push(file, remote"))
        assertTrue("no instala con pm install", adb.contains("pm install -r \$remote"))
    }

    @Test
    fun onlyThePhoneOffersItAndTheListCanBeManaged() {
        val screen = source("ui/phone/SettingsScreen.kt")
        assertTrue("Ajustes del teléfono no muestra la sección", screen.contains("TvHomeSetupSection()"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("falta el código de emparejamiento", ui.contains("Escribe el código de la tele"))
        assertTrue("falta la acción principal", ui.contains("Mi tele está lista"))
        assertTrue("no se pueden agregar más teles", ui.contains("Buscar otra vez"))
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
        assertTrue("no apunta al canal nuevo", pkg.contains("Vision-Releases"))
        assertTrue("no pide el APK del launcher", pkg.contains("HomeContract.LAUNCHER_ASSET"))
        assertTrue("no pide el APK de la app", pkg.contains("HomeContract.APP_ASSET"))
        assertTrue("no lee el version.json del release", pkg.contains("version.json"))
        assertTrue("no conserva el respaldo dentro de la app", pkg.contains("vision_home.apk"))
        val adb = source("data/adb/TvAdb.kt")
        assertTrue("una tele con una versión vieja no se actualizaría", adb.contains("pm install -r \$remote"))
        assertTrue("un APK de 82 MB no puede cargarse en memoria", adb.contains("fun installApkFileOutput("))
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no instala Vision+ antes del Inicio", setup.contains("ensureVisionPlus(context, updateApp)"))
        assertTrue("no deja la tele sin app si no pudo instalar Vision+", setup.contains("No toco su Inicio"))
        val latestAt = setup.indexOf("TvAppPackage.latestVersion(context)")
        val localAt = setup.indexOf("TvAppPackage.local(context)")
        assertTrue("el release debe consultarse antes de elegir la copia local", latestAt in 0 until localAt)
    }

    @Test
    fun theReleaseWinsWhenItIsNewerThanThePhonesCopy() {
        assertTrue(TvAppPackage.shouldDownloadRelease("2.5.249", "2.5.247"))
        assertTrue(TvAppPackage.shouldDownloadRelease("2.6.0", "2.5.247"))
        assertTrue(TvAppPackage.shouldDownloadRelease("2.5.247", null))
        assertFalse("misma versión: no se bajan 95 MB", TvAppPackage.shouldDownloadRelease("2.5.247", "2.5.247"))
        assertFalse("sin manifiesto se usa la copia local", TvAppPackage.shouldDownloadRelease(null, "2.5.247"))
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
        assertTrue("falta la categoría Mi tele", screen.contains("MY_TV(\"Mi tele\")"))
        assertTrue(
            "falta la categoría Sistema",
            screen.contains("SYSTEM(\"Sistema y red\")"),
        )
        assertTrue("la categoría no pinta la sección", screen.contains("SettingsTab.MY_TV -> TvHomeSetupSection()"))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("no ofrece instalar", ui.contains("TvHomeSetup.install(context, request.id)"))
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
            setup.contains("Enciende «ADB por red» en la tele"),
        )
        assertTrue(
            "ya no se depende del control remoto de Vision+ para saber la dirección",
            !setup.contains("deja el control remoto vinculado"),
        )
        // La búsqueda automática (mDNS + barrido) no funcionaba en redes reales: se retiró.
        assertFalse("no debe quedar la búsqueda automática", setup.contains("searchLegacy"))
        assertFalse(
            "el barrido de la red no puede lanzarse solo",
            setup.substringAfter("suspend fun search(").substringBefore("suspend fun connectByAddress(")
                .contains("scanNetwork"),
        )
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("no hay campo de dirección", ui.contains("IP de la tele"))
        assertTrue("no explica la depuración", ui.contains("Depuración inalámbrica"))
        assertTrue("no ofrece conectar", ui.contains("TvHomeSetup.connectByAddress(context"))
        assertTrue("no ofrece reintentar", ui.contains("Reintentar"))
        assertTrue("el descubrimiento no vive en el diálogo", ui.contains("LaunchedEffect(request)"))
        assertFalse("no debe quedar el botón de búsqueda viejo", ui.contains("Buscar teles Android 8/9"))
        assertTrue(
            "la cancelación del diálogo no puede mostrarse como fallo de conexión",
            setup.contains("if (t is CancellationException) throw t"),
        )
        assertTrue("un trabajo cancelado no debe escribir estado", setup.contains("if (!isActive) return@withContext"))
        assertTrue("no recuerda la última dirección para reintentar", setup.contains("legacyHost = host"))
    }

    @Test
    fun legacyTvsCanShowTheDebuggingPromptAndTakeTheirTime() {
        // El cliente anterior abortaba antes de enviar la llave pública: Android 8/9 nunca
        // mostraba «¿Permitir depuración?». Kadb la envía y espera lo suficiente.
        val adb = source("data/adb/TvAdb.kt")
        assertTrue("debe reutilizar la llave que la tele ya autorizó", adb.contains("KadbCert.set("))
        assertTrue("falta el camino que espera la autorización", adb.contains("fun connectAllowingAuthorization("))
        assertTrue("la espera tiene que dar tiempo a aceptar el aviso", adb.contains("timeoutMs: Long = 180_000"))
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue(
            "la conexión por dirección debe permitir el aviso del sistema",
            setup.contains("TvAdb.connectAllowingAuthorization(host, 5555)"),
        )
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue(
            "la dirección no puede quedar bloqueada por la búsqueda con código",
            ui.contains("ManualIpField(") && ui.contains("state.phase == TvHomeSetup.Phase.READY"),
        )
        assertFalse(ui.contains("phase == TvHomeSetup.Phase.WORKING || phase == TvHomeSetup.Phase.SEARCHING"))
        assertTrue("no explica que la tele pedirá permiso", ui.contains("Permitir depuración"))
        assertTrue("no muestra el progreso de la descarga", ui.contains("LinearProgressIndicator"))
        assertTrue("no muestra el porcentaje de Vision+", ui.contains("Descargando \$label"))
        assertTrue(
            "debe usar el APK del teléfono antes de bajar 97 MB",
            setup.contains("TvAppPackage.local(context)"),
        )
        assertTrue("falta la instalación desde el teléfono", setup.contains("desde este teléfono"))
        val pkg = source("data/adb/TvLauncherPackage.kt")
        assertTrue("no expone el APK local del teléfono", pkg.contains("fun local(context: Context): Apk?"))
    }

    @Test
    fun aFallbackHomeIsWaitedForAndNeverDisabled() {
        listOf(
            "android",
            "com.android.tv.settings",
            "com.android.tv.fallbackhome",
            "com.android.settings",
            "com.android.provision",
            "com.google.android.setupwizard",
        ).forEach { assertTrue("$it es respaldo, no el Inicio del fabricante", TvHomeRules.isTransientHome(it)) }
        assertFalse(TvHomeRules.isTransientHome("com.google.android.apps.tv.launcherx"))
        assertFalse(TvHomeRules.isTransientHome(TvHomeRules.OUR_PKG))
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("no abre Vision+ Home cuando la tele se queda en el respaldo", setup.contains("launchOurHome()"))
        assertTrue("no usa am start para sacar a la tele del respaldo", setup.contains("am start --user 0"))
        assertTrue(
            "Android 8/9 puede seguir usando mFocusedActivity",
            TvHomeRules.parseResumedPackage(
                "mFocusedActivity: ActivityRecord{abc123 u0 com.tcl.tv/.MainActivity t42}",
            ) == "com.tcl.tv",
        )
    }

    @Test
    fun explainsAnInstallBlockedByTheVerifier() {
        assertTrue(TvHomeRules.looksLikeVerificationBlocked("Failure [INSTALL_FAILED_VERIFICATION_FAILURE]"))
        assertTrue(TvHomeRules.looksLikeVerificationBlocked("Performing Streamed Install\nverifier rejected"))
        assertFalse(TvHomeRules.looksLikeVerificationBlocked("Success"))
        assertEquals(
            "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]",
            TvHomeRules.installReason("Performing Streamed Install\nFailure [INSTALL_FAILED_INSUFFICIENT_STORAGE]\n"),
        )
        assertEquals("Success", TvHomeRules.installReason("Success"))
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue(
            "debe apagar la verificación de instalaciones por ADB antes de instalar",
            setup.contains("verifier_verify_adb_installs"),
        )
        assertTrue("falta el reintento si el verificador bloquea", setup.contains("installWithVerificationRetry("))
        assertTrue("falta el respaldo de set-home-activity", setup.contains("cmd package set-home-activity"))
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
    fun theAutomaticSearchIsGoneButTheNetworkScanIsExplicit() {
        assertTrue(
            "el barrido explícito de 5555 debe existir",
            File("src/main/java/com/chronosbvrx/visionplus/data/adb/TvLanScan.kt").isFile,
        )
        val adb = source("data/adb/TvAdb.kt")
        assertFalse("no debe quedar el descubrimiento Android TV por mDNS", adb.contains("findAndroidTvs"))
        val setup = source("data/adb/TvHomeSetup.kt")
        val searchBody = setup.substringAfter("suspend fun search(").substringBefore("suspend fun connectByAddress(")
        assertFalse("el barrido no puede lanzarse solo al abrir el diálogo", searchBody.contains("TvLanScan.scan("))
        assertTrue("falta el barrido pedido por la persona", setup.contains("TvLanScan.scan("))
        assertTrue("falta el descubrimiento de las teles", setup.contains("suspend fun discover("))
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("no hay botón para buscar teles", ui.contains("Buscar otra vez"))
        assertTrue("no se listan las teles encontradas", ui.contains("state.candidates"))
    }

    @Test
    fun theNetworkScanOnlyTouchesThePhonesOwnPrivateSubnet() {
        val hosts = TvLanScan.hostsOf("192.168.1.10")
        assertEquals(253, hosts.size)
        assertTrue("192.168.1.11" in hosts)
        assertFalse("no se prueba la propia IP", "192.168.1.10" in hosts)
        assertTrue(TvLanScan.hostsOf("10.0.0.5").isNotEmpty())
        assertTrue(TvLanScan.hostsOf("172.16.4.8").isNotEmpty())
        assertTrue("una IP pública no se barre", TvLanScan.hostsOf("8.8.8.8").isEmpty())
        assertTrue("una IP inválida no se barre", TvLanScan.hostsOf("192.168.1.999").isEmpty())
        assertTrue(TvLanScan.isValidHost("192.168.1.50"))
        assertFalse(TvLanScan.isValidHost("192.168.1.999"))
        assertFalse(TvLanScan.isValidHost("la tele"))
    }

    @Test
    fun theNetworkScanFindsAnOpenAdbPort() = kotlinx.coroutines.runBlocking {
        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        try {
            assertTrue(TvLanScan.isOpen("127.0.0.1", server.localPort, 700))
        } finally {
            runCatching { server.close() }
        }
        assertFalse(TvLanScan.isOpen("127.0.0.1", server.localPort, 300))
    }

    @Test
    fun cleanupIsReviewedAndBackedUpBeforeAnythingIsDisabled() {
        val setup = source("data/adb/TvHomeSetup.kt")
        assertTrue("falta la fase de revisión", setup.contains("Phase.REVIEW"))
        assertTrue("no calcula el plan con la lista del modelo", setup.contains("DebloatRules.plan("))
        assertTrue("no guarda la copia antes de limpiar", setup.contains("TvBackupStore.save(context"))
        assertTrue("no apaga con el motor verificado", setup.contains("debloater.run(selected)"))
        assertTrue("no registra lo apagado para restaurar", setup.contains("cleaned = (record.cleaned + cleaned)"))
        assertTrue("no lee la lista de deshacer del televisor", setup.contains("debloater.readMirror()"))
        assertTrue("no limpia la lista de deshacer al restaurar", setup.contains("debloater.clearMirror()"))
        assertTrue("Fire TV no puede quedarse sin su Inicio", setup.contains("Fire TV no permite cambiar el Inicio"))
        assertTrue("no se protege un launcher persistente", setup.contains("isPersistent(effective)"))
        assertTrue("no se protege la app de setup de Google TV", setup.contains("setupWraithSafe()"))
        assertTrue("falta el bloqueo de anuncios por Private DNS", setup.contains("private_dns_mode"))
        assertTrue("Restaurar no devuelve el DNS original", setup.contains("restoreDns(record)"))
        assertFalse("la limpieza no puede desinstalar", setup.contains("pm uninstall"))
        val commands = source("data/debloat/DebloatEngine.kt")
        assertFalse("la limpieza no debe desinstalar apps", commands.contains("pm uninstall"))
        assertTrue("falta el tope de espera de Android", commands.contains("SAVE_WAIT_MS"))
    }

    @Test
    fun theCleanupListsTravelInsideTheApp() {
        val dir = File("src/main/assets/debloat")
        assertTrue("faltan las listas de limpieza", dir.isDirectory)
        val files = dir.listFiles { file -> file.isFile && file.extension == "txt" && !file.name.startsWith("LICENSE") }
        assertTrue("deberían viajar las 335 listas", (files?.size ?: 0) >= 300)
        assertTrue("falta la licencia de TidyTV", File(dir, "LICENSE-TidyTV.txt").isFile)
        assertTrue("falta la licencia de CleanYourTV", File(dir, "LICENSE-CleanYourTV.txt").isFile)
    }

    @Test
    fun thePhoneShowsTheReviewBeforeTouchingTheTv() {
        val setup = source("data/adb/TvHomeSetup.kt")
        val ui = source("ui/settings/TvHomeSetupCard.kt")
        assertTrue("falta la revisión", ui.contains("Limpieza recomendada") || ui.contains("Modelo parecido"))
        assertTrue("no deja elegir grupos", ui.contains("CleanupGroup.entries"))
        assertTrue("no muestra el total exacto", ui.contains("apps seleccionadas") && ui.contains("selected.size"))
        assertTrue("no confirma antes de limpiar", ui.contains("TvHomeSetup.confirm(context)"))
        assertTrue("no permite cancelar sin tocar la tele", ui.contains("TvHomeSetup.cancel()"))
        assertTrue("no explica que se puede restaurar", ui.contains("Restaurar"))
        assertTrue("falta el interruptor global", ui.contains("Limpiar antes de instalar"))
        assertTrue("falta la entrada amigable", ui.contains("Mi tele está lista"))
        assertTrue("no deja marcar apps una por una", ui.contains("toggleApp"))
        assertTrue("no muestra apps de otras listas", ui.contains("Más apps"))
        assertTrue("faltan las herramientas de espacio", ui.contains("Espacio"))
        assertTrue("faltan las herramientas de revisión", ui.contains("Revisar"))
        assertTrue("faltan las herramientas de anuncios", ui.contains("Bloquear anuncios"))
        assertTrue("no deja elegir la versión familiar", ui.contains("Versión familiar"))
        assertTrue("falta el registro en vivo", ui.contains("Registro en vivo"))
        assertTrue("el registro no muestra el último paso", ui.contains("lastLogAt"))
        assertTrue("el registro no se puede copiar", ui.contains("Copiar"))
        assertTrue("el log no tiene hora", setup.contains("LOG_TIME"))
        assertTrue("el log se queda corto", setup.contains("takeLast(200)"))
    }

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }
}
