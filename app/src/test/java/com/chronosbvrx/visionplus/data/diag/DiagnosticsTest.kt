package com.chronosbvrx.visionplus.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Antirregresiones del diagnóstico de campo: huella estable y cola acotada. */
class DiagnosticsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun fingerprintIsStableAndIgnoresLineNumbers() {
        val a = Diagnostics.fingerprint("crash", "NPE", "com.a.B.c(B.kt:123)\ncom.a.D.e(D.kt:9)")
        val b = Diagnostics.fingerprint("crash", "NPE", "com.a.B.c(B.kt:999)\ncom.a.D.e(D.kt:1)")
        assertEquals(a, b)
    }

    @Test
    fun fingerprintChangesWithKindOrCode() {
        val base = Diagnostics.fingerprint("crash", "NPE", "com.a.B.c(B.kt:1)")
        assertNotEquals(base, Diagnostics.fingerprint("anr", "NPE", "com.a.B.c(B.kt:1)"))
        assertNotEquals(base, Diagnostics.fingerprint("crash", "OOM", "com.a.B.c(B.kt:1)"))
    }

    @Test
    fun fingerprintIsStableHex16() {
        val fp = Diagnostics.fingerprint("anomaly", "INDEX_FETCH_FAIL", "IOException: timeout")
        assertEquals(16, fp.length)
        assertTrue(fp.all { it in "0123456789abcdef" })
        assertEquals(fp, Diagnostics.fingerprint("anomaly", "INDEX_FETCH_FAIL", "IOException: timeout"))
    }

    @Test
    fun anomalyFingerprintIgnoresTheMeasurements() {
        // Un arranque de 3.0 s y otro de 0.9 s son el MISMO problema. Antes cada valor de
        // `totalMs`/`metaMs` creaba una huella (y una alerta de Telegram) distinta.
        val slow = Diagnostics.fingerprint(
            "anomaly", "PLAYBACK_PREP",
            "cached=false slbMs=3 metaMs=2684 licenseMs=358 totalMs=3049 healed=false",
        )
        val quick = Diagnostics.fingerprint(
            "anomaly", "PLAYBACK_PREP",
            "cached=false slbMs=9 metaMs=812 licenseMs=140 totalMs=961 healed=false",
        )
        assertEquals(slow, quick)
        // Las claves de texto siguen separando caminos: caché de la ficha, auto-reparado…
        assertNotEquals(
            slow,
            Diagnostics.fingerprint("anomaly", "PLAYBACK_PREP", "cached=true totalMs=0 desdeFicha=1200ms"),
        )
        assertNotEquals(
            slow,
            Diagnostics.fingerprint(
                "anomaly", "PLAYBACK_PREP",
                "cached=false slbMs=3 metaMs=2684 licenseMs=358 totalMs=3049 healed=true",
            ),
        )
        // El motivo de un fallo de licencia no es una medida: no se agrupa a ciegas.
        assertNotEquals(
            Diagnostics.fingerprint("anomaly", "PLAYBACK_LICENSE_FAIL", "cid=12 motivo=Timeout ms=3000"),
            Diagnostics.fingerprint("anomaly", "PLAYBACK_LICENSE_FAIL", "cid=12 motivo=sin medios ms=3000"),
        )
    }

    @Test
    fun playerStartGroupsAttemptsButKeepsLiveAndVodApart() {
        fun fp(attempts: Int, live: Boolean) = Diagnostics.fingerprint(
            "anomaly", "PLAYER_START",
            "firstFrameMs=${1_200 * attempts} bootMs=2100 intentos=$attempts live=$live",
        )
        assertEquals(fp(2, false), fp(3, false))
        assertNotEquals(fp(2, false), fp(2, true))
    }

    @Test
    fun numbersStillMatterOutsideAnomalies() {
        // El alcance es solo `anomaly`: un mensaje de usuario no se agrupa por parecerse.
        assertNotEquals(
            Diagnostics.fingerprint("feedback", "USER_FEEDBACK", "no abre el canal 1500"),
            Diagnostics.fingerprint("feedback", "USER_FEEDBACK", "no abre el canal 1501"),
        )
    }

    @Test
    fun trimQueueKeepsTheNewestReports() {
        val dir = temp.newFolder("queue")
        (1..10).forEach { File(dir, "r-%02d".format(it)).writeText("x") }
        Diagnostics.trimQueue(dir, max = 4)
        val left = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("r-07", "r-08", "r-09", "r-10"), left)
    }
}
