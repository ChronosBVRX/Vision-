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
    fun trimQueueKeepsTheNewestReports() {
        val dir = temp.newFolder("queue")
        (1..10).forEach { File(dir, "r-%02d".format(it)).writeText("x") }
        Diagnostics.trimQueue(dir, max = 4)
        val left = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("r-07", "r-08", "r-09", "r-10"), left)
    }
}
