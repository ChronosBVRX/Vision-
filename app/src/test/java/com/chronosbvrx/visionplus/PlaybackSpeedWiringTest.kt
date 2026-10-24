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
}
