package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Anti-regresión del arranque del motor: la tele de campo (ZTE B866V2, v2.5.122) quedó con
 * un `sdk-N.dex` copiado a medias y el motor murió con
 * `VerifyError: Verifier rejected class yc.h … can't resolve java.lang.String`.
 *
 * La copia ahora es atómica (`.part` + rename) y queda sellada con versión y tamaños, así que
 * un archivo corto se detecta y se vuelve a copiar. Si aun así una clase no verifica, `boot`
 * borra el motor extraído y lo estrena una vez (sin que el usuario borre datos).
 */
class EngineExtractionTest {

    // ----- sello de la extracción -----

    @Test
    fun manifestKeepsVersionAndSizes() {
        val text = engineManifest(358, mapOf("sdk-1.dex" to 12_000L, "sdk-0.dex" to 40L))
        val (version, sizes) = parseEngineManifest(text)
        assertEquals(358, version)
        assertEquals(40L, sizes["sdk-0.dex"])
        assertEquals(12_000L, sizes["sdk-1.dex"])
    }

    @Test
    fun manifestIsReadableAfterARealRoundTripAndIgnoresGarbage() {
        val (version, sizes) = parseEngineManifest("basura\nvc=12\n\nsdk-2.dex=7\nno-es-numero=x\n")
        assertEquals(12, version)
        assertEquals(mapOf("sdk-2.dex" to 7L), sizes)
        val empty = parseEngineManifest("")
        assertEquals(0, empty.first)
        assertTrue(empty.second.isEmpty())
    }

    // ----- cuándo hay que volver a copiar -----

    @Test
    fun aMissingOrEmptyFileIsAlwaysCopied() {
        assertTrue(needs(false, 0L, 358, 358, mapOf("a" to 10L)))
        assertTrue(needs(true, 0L, 358, 358, mapOf("a" to 10L)))
    }

    @Test
    fun aSealedFileOfTheSameVersionAndSizeIsKept() {
        assertFalse(needs(true, 10L, 358, 358, mapOf("a" to 10L)))
    }

    @Test
    fun aTruncatedFileIsCopiedAgain() {
        // El caso de campo: el archivo existe, pero mide menos que lo que se copió.
        assertTrue(needs(true, 4L, 358, 358, mapOf("a" to 10L)))
    }

    @Test
    fun aSealFromAnotherAppVersionForcesAFreshCopy() {
        // El motor pudo cambiar en la actualización: los DEX viejos no se reutilizan.
        assertTrue(needs(true, 10L, 357, 358, mapOf("a" to 10L)))
        assertTrue(needs(true, 10L, 0, 358, mapOf("a" to 10L)))
    }

    @Test
    fun onlyASameVersionSizeMismatchCountsAsCorruption() {
        assertFalse(corrupt(true, 10L, 358, 358, mapOf("a" to 10L)))
        assertTrue(corrupt(true, 4L, 358, 358, mapOf("a" to 10L)))
        // Instalación nueva o actualización: no es una copia dañada, no debe llenar la telemetría.
        assertFalse(corrupt(false, 0L, 358, 358, mapOf("a" to 10L)))
        assertFalse(corrupt(true, 10L, 357, 358, mapOf("a" to 10L)))
        assertFalse(corrupt(true, 10L, 0, 358, mapOf("a" to 10L)))
    }

    private fun needs(exists: Boolean, length: Long, seal: Int, app: Int, sizes: Map<String, Long>) =
        engineFileNeedsExtraction("a", exists, length, seal, app, sizes)

    private fun corrupt(exists: Boolean, length: Long, seal: Int, app: Int, sizes: Map<String, Long>) =
        engineFileWasCorrupt(exists, length, seal, app, sizes, "a")

    // ----- cableado del arranque -----

    @Test
    fun theEngineIsCopiedAtomicallyAndHealsItself() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KTitan.kt")
        assertTrue("falta X4KTitan.kt", source.exists())
        val text = source.readText()
        // La copia vieja (`input.copyTo(output)`) dejaba archivos cortos si mataban la app.
        assertFalse("no debe quedar la copia directa a archivo", text.contains("input.copyTo(output)"))
        assertTrue("la copia debe ser .part + rename", text.contains("copyAssetAtomically"))
        assertTrue("se sella con versión y tamaño", text.contains("engineManifest("))
        // Auto-reparación: un LinkageError borra lo extraído y se reintenta una vez.
        assertTrue(text.contains("linkageFailure"))
        assertTrue(text.contains("healExtracted("))
        // La fachada se verifica al cargar (antes de arrancar), no a media reproducción.
        assertTrue(text.contains("cl.loadClass(\"yc.h\")"))
    }
}
