package com.chronosbvrx.visionplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Velocidad percibida al dar Play (reporte del usuario: «en el móvil es instantáneo y en la TV
 * tarda mucho»). En una caja de TV cada llamada al portal cuesta 0.2–0.7 s y el proceso `:x4k`
 * arranca de cero cuando el sistema lo mató, así que:
 *
 *  - el proceso del reproductor ya NO inicializa lo que solo usa la interfaz (avisos, modo
 *    niños, config remota con su hilo de red);
 *  - la sesión/SLB y la ficha del título se piden EN PARALELO, no en serie;
 *  - el hash de 17 MB del motor ya no se lee en cada arranque (solo tras un fallo);
 *  - y se mide el tramo que se siente (toque → primer cuadro) para no volver a adivinar.
 */
class PlaybackSpeedWiringTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    private fun between(text: String, from: String, to: String): String {
        val start = text.indexOf(from)
        assertTrue("no está «$from»", start >= 0)
        val end = text.indexOf(to, start + from.length).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    @Test
    fun thePlayerProcessOnlyInitialisesWhatTheEngineNeeds() {
        val app = source("VisionApp.kt")
        val before = app.substringBefore("if (mainProcess)")
        // Avisos, modo niños y config remota son de la interfaz: fuera del arranque del motor.
        assertFalse("avisos en :x4k", before.contains("ReminderKit.ensureChannel"))
        assertFalse("modo niños en :x4k", before.contains("KidsMode.init"))
        assertFalse("config remota (red) en :x4k", before.contains("RemoteConfig.init"))
        val after = app.substringAfter("if (mainProcess)")
        assertTrue("avisos en el proceso principal", after.contains("ReminderKit.ensureChannel"))
        assertTrue("modo niños en el proceso principal", after.contains("KidsMode.init"))
        assertTrue("config remota en el proceso principal", after.contains("RemoteConfig.init"))
        // Lo que el motor SÍ necesita sigue en ambos procesos.
        assertTrue("registro offline en ambos", app.contains("OfflineRegistry.init(this)"))
        assertTrue("identidad del portal en ambos", app.contains("X4KApi.initSlots(this)"))
        assertTrue("diagnóstico en ambos", app.contains("Diagnostics.init(this)"))
    }

    @Test
    fun theHandshakeAsksSessionAndTitleCardAtTheSameTime() {
        val text = source("data/xuper/X4KApi.kt")
        val prepare = between(text, "suspend fun prepareVod(", "suspend fun playVod(")
        // Antes iban en serie: cada llamada pagaba los 0.2–0.7 s de la caja.
        assertTrue("falta el bloque paralelo", prepare.contains("coroutineScope {"))
        assertTrue("falta el async del SLB", prepare.contains("ensureEngineData"))
        assertTrue("el SLB debe ir en async", prepare.contains("val slb = async"))
        assertTrue("la ficha debe ir en async", prepare.contains("val meta = if (fallbackDurationMs <= 0L) async"))
        // Y el desglose para saber qué fase se come el tiempo.
        assertTrue("sin telemetría de la preparación", prepare.contains("PLAYBACK_PREP"))
        assertTrue(prepare.contains("slbMs="))
        assertTrue(prepare.contains("licenseMs="))
    }

    @Test
    fun seriesDoNotInheritTheSeasonDuration() {
        // Un capítulo de 42 min mostraba 1 h 40 (la duración de la temporada). La ficha
        // de la serie/temporada ya no se usa como duración del capítulo.
        val api = source("data/xuper/X4KApi.kt")
        val prepare = between(api, "suspend fun prepareVod(", "suspend fun playVod(")
        assertTrue(
            "en series la duración del portal no se usa",
            prepare.contains("var durMs = if (sid.isBlank()) extractDurationMs(item) else 0L"),
        )
        assertFalse("no se hereda la ficha de la serie", prepare.contains("itemInfo(context, sid)"))
        val mobile = source("ui/title/TitleScreen.kt")
        assertTrue("la ficha móvil deja la serie en 0", mobile.contains("if (seriesLike) itemDurationMs = 0L"))
        assertTrue("la ficha móvil no pinta duración de serie", mobile.contains("duration.takeIf { !seriesLike && it.isNotBlank() }"))
        val tv = source("ui/tv/TvTitleScreen.kt")
        assertTrue("la ficha TV deja la serie en 0", tv.contains("if (seriesLike) itemDurationMs = 0L"))
        assertTrue("la ficha TV no pinta duración de serie", tv.contains("durationLabel = if (seriesLike) \"\" else duration"))
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("el reproductor no toma la duración del historial en series", player.contains("!seriesContext"))
    }

    @Test
    fun theLicenseGetsASlowNetworkRetry() {        val text = File("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt").readText()
        val prepare = text.substringAfter("suspend fun prepareVod(").substringBefore("suspend fun playVod(")
        // La TCL (2.5.138) fallaba con «sin-medios» y el portal a 47 s: el tope de 25 s cortaba
        // la licencia. Ahora hay un último intento con más paciencia y el motivo en telemetría.
        assertTrue("sin reintento con timeout largo", prepare.contains("connectMs = 25_000, readMs = 60_000"))
        assertTrue("sin telemetría del motivo", prepare.contains("PLAYBACK_LICENSE_FAIL"))
        assertTrue("el reintento no reutiliza el cuerpo", prepare.contains("suspend fun license("))
        val post = text.substringAfter("private fun post(").substringBefore("private fun snTokenCall")
        assertTrue("post no acepta timeouts", post.contains("connectMs: Int = 15_000"))
        assertTrue(post.contains("readMs: Int = 25_000"))
    }

    @Test
    fun theVodPortalMigratedToMagisWithoutLosingTheCatalog() {
        val api = source("data/xuper/X4KApi.kt")
        // Los hosts/llave históricos de XTV contestan portal200001 desde 2026-11-12.
        assertTrue("host principal de Magis", api.contains("rhokqi.ryh1gifvn.com"))
        assertTrue("host de respaldo de Magis", api.contains("zgmnxa.ryh1gifvn.com"))
        assertTrue("llave vigente", api.contains("37a759c3-fc2b-413d-b424-0bb0e1505a5e"))
        assertTrue("appId de Magis", api.contains("com.msandroid.mobile"))
        assertTrue("versión vigente", api.contains("APK_VER = \"60507\""))
        assertTrue("el catálogo sigue en masnew", api.contains("PORTAL = \"masnew\""))
        // La licencia libre solo la firma el tenant masmobile y solo por v9.
        assertTrue("tenant de reproducción", api.contains("PORTAL_PLAY = \"masmobile\""))
        assertTrue("play v9", api.contains("/api/portalCore/v9/startPlayVOD"))
        assertTrue("el cuerpo usa el tenant de play", api.contains("put(\"portalCode\", PORTAL_PLAY)"))
        assertFalse("no debe quedar el play v10, ya no entrega medios", api.contains("/api/portalCore/v10/startPlayVOD"))
    }

    @Test
    fun theSessionKeepsTheUserIdThatSignedIn() {
        val api = source("data/xuper/X4KApi.kt")
        // v8/active puede devolver un userId distinto al del slot; mezclarlo con el token
        // hace que el portal conteste «请求参数异常» y el play se quede sin licencia.
        assertTrue("guarda el userId de la sesión", api.contains("K_USER_ID"))
        assertTrue("storeSession recibe userId", api.contains("userId: String = \"\""))
        assertTrue("active persiste su userId", api.contains("storeSession(context, sn, userToken, userId)"))
        assertTrue("userIdOf prioriza la sesión", api.contains("sessionUserId?.takeIf { it.isNotBlank() }"))
    }

    @Test
    fun theEngineIdentifiesAsTheNewPortal() {
        val titan = source("data/xuper/X4KTitan.kt")
        assertTrue("llave del motor", titan.contains("37a759c3-fc2b-413d-b424-0bb0e1505a5e"))
        assertTrue("appId del motor", titan.contains("APP_ID = \"com.msandroid.mobile\""))
        assertTrue("versión del motor", titan.contains("APP_VER = \"60507\""))
    }

    @Test
    fun oldEngineEntriesAreInvalidatedOnThePortalMigration() {
        val api = source("data/xuper/X4KApi.kt")
        assertTrue("marca de generación", api.contains("K_ENGINE_GEN"))
        assertTrue("generación del portal", api.contains("ENGINE_GENERATION = 2"))
        assertTrue("no se reutilizan entries viejas", api.contains("generationOk = p.getInt(K_ENGINE_GEN, 0) == ENGINE_GENERATION"))
    }

    @Test
    fun theEngineNoLongerHashesItsDexOnEveryPlayerStart() {
        val titan = source("data/xuper/X4KTitan.kt")
        // La marca se enciende con un fallo (o una reparación) y se apaga al verificar bien.
        assertTrue("falta la marca de verificación", titan.contains("ENGINE_VERIFY_FLAG"))
        assertTrue("no se consulta antes de hashear", titan.contains("val verifyContent = File(app.filesDir, ENGINE_VERIFY_FLAG).exists()"))
        assertTrue(
            "el hash debe depender de la marca",
            titan.contains("if (verifyContent && file.exists() && expectedSha != null)"),
        )
        // El sha se calcula copiando: sellar no cuesta una lectura extra de 17 MB.
        assertTrue("la copia no devuelve el sha", titan.contains("private fun copyAssetAtomically(app: Context, asset: String, target: File): String?"))
        assertTrue("no se hashea durante la copia", titan.contains("digest.update(buffer, 0, read)"))
        assertTrue("no se apaga la marca al cargar bien", titan.contains("ENGINE_VERIFY_FLAG).delete()"))
    }

    @Test
    fun thePlayerReportsHowLongTheFirstFrameTook() {
        val player = source("ui/player/X4KPlayerAty.kt")
        val firstFrame = between(player, "private fun onFirstFrame()", "private fun ")
        assertTrue("sin telemetría del primer cuadro", firstFrame.contains("PLAYER_START"))
        assertTrue("sin el tiempo hasta el primer cuadro", firstFrame.contains("firstFrameMs="))
        assertTrue("sin el tiempo del motor", firstFrame.contains("X4KTitan.lastBootMs"))
        // El motor expone cuánto tardó en arrancar (0 = ya estaba caliente y se reutilizó).
        val titan = source("data/xuper/X4KTitan.kt")
        assertTrue("el motor no reporta su arranque", titan.contains("var lastBootMs: Long = 0L"))
        assertTrue(titan.contains("lastBootMs = android.os.SystemClock.uptimeMillis() - started"))
    }

    @Test
    fun thePlayerRetriesFastWhenNothingArrivedAtAll() {
        val player = source("ui/player/X4KPlayerAty.kt")
        // TCL: primer intento 26.9 s con pos=-1 dur=-1 playing=false (el motor no recibió nada)
        // y el reintento pintó en 3 s. Sin datos no se espera el tope largo.
        assertTrue("falta la detección de «sin datos»", player.contains("val engineHasNoData = !liveMode && !engineSignal"))
        assertTrue(
            "no se reintenta rápido",
            player.contains("engineHasNoData && softRestarts == 0 -> 10_000L"),
        )
        assertTrue("sin tope corto en reintentos", player.contains("engineHasNoData -> 15_000L"))
        // Con datos sí se respeta la ventana larga (buffering legítimo).
        assertTrue(player.contains("softRestarts == 0 -> 25_000L"))
    }
}
