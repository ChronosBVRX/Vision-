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
    fun manifestAlsoSealsTheContentWithItsSha256() {
        // El tamaño solo no basta: una copia corrompida que mide lo mismo pasaba el sello y la
        // tele se quedaba sin reproducir para siempre (ZTE B866V2, 2.5.129/130).
        val sha = "a".repeat(64)
        val text = engineManifest(366, mapOf("sdk-0.dex" to 40L), mapOf("sdk-0.dex" to sha))
        val seal = parseEngineSeal(text)
        assertEquals(366, seal.version)
        assertEquals(40L, seal.sizes["sdk-0.dex"])
        assertEquals(sha, seal.hashes["sdk-0.dex"])
    }

    @Test
    fun aSealWithoutHashesStillReadsSizes() {
        val seal = parseEngineSeal("vc=12\nsdk-0.dex=40\n")
        assertEquals(12, seal.version)
        assertEquals(mapOf("sdk-0.dex" to 40L), seal.sizes)
        assertTrue(seal.hashes.isEmpty())
    }

    @Test
    fun manifestIsReadableAfterARealRoundTripAndIgnoresGarbage() {
        val (version, sizes) = parseEngineManifest("basura\nvc=12\n\nsdk-2.dex=7\nno-es-numero=x\n")
        assertEquals(12, version)
        assertEquals(mapOf("sdk-2.dex" to 7L), sizes)
        val empty = parseEngineManifest("")
        assertEquals(0, empty.first)
        assertTrue(empty.second.isEmpty())
        // Una huella que no mide 64 caracteres es basura: se ignora (y fuerza re-extraer).
        assertTrue(parseEngineSeal("sha:sdk-0.dex=corto").hashes.isEmpty())
    }

    @Test
    fun theSha256IsTheCanonicalOne() {
        // Vector conocido: sha256("abc").
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Of("abc".toByteArray()),
        )
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

    @Test
    fun aSameSizeCopyWithAnotherDigestIsCopiedAgain() {
        // El caso de la tele de campo: el archivo mide lo que el sello dice, pero su contenido
        // ya no es el del APK (bytes cambiados en el sitio). Antes pasaba como bueno.
        val expected = "a".repeat(64)
        val actual = "b".repeat(64)
        assertTrue(needs(true, 10L, 358, 358, mapOf("a" to 10L), expected, actual))
        assertTrue(corrupt(true, 10L, 358, 358, mapOf("a" to 10L), "a", expected, actual))
        // Con el mismo contenido no se copia 17 MB en cada arranque.
        assertFalse(needs(true, 10L, 358, 358, mapOf("a" to 10L), expected, expected))
        assertFalse(corrupt(true, 10L, 358, 358, mapOf("a" to 10L), "a", expected, expected))
        // Sello viejo (sin huella): manda el tamaño, como antes.
        assertFalse(needs(true, 10L, 358, 358, mapOf("a" to 10L), null, expected))
    }

    @Test
    fun theEngineIsExtractedInAGenerationDirectory() {
        // Una RUTA nueva de DEX (x4k-r1) hace que ART no reutilice el vdex/oat que ya tenía:
        // era lo que mantenía la tele rechazando yc.h/yc.e aunque el DEX fuera idéntico.
        assertEquals("x4k-r0", engineDirName(0))
        assertEquals("x4k-r7", engineDirName(7))
        assertEquals("x4k-r20", engineDirName(99))
        assertEquals(0, engineRevisionFrom(""))
        assertEquals(0, engineRevisionFrom("basura"))
        assertEquals(3, engineRevisionFrom(" 3 "))
        assertEquals(20, engineRevisionFrom("9999"))
    }

    private fun needs(
        exists: Boolean,
        length: Long,
        seal: Int,
        app: Int,
        sizes: Map<String, Long>,
        expectedSha: String? = null,
        actualSha: String? = null,
    ) = engineFileNeedsExtraction("a", exists, length, seal, app, sizes, expectedSha, actualSha)

    private fun corrupt(
        exists: Boolean,
        length: Long,
        seal: Int,
        app: Int,
        sizes: Map<String, Long>,
        name: String = "a",
        expectedSha: String? = null,
        actualSha: String? = null,
    ) = engineFileWasCorrupt(exists, length, seal, app, sizes, name, expectedSha, actualSha)

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

    @Test
    fun theRepairStartsANewGenerationAndCleansTheArtifactsOfArt() {
        // La tele de campo (ZTE B866V2, Android 12, 2.5.130) reparaba el motor y volvía a
        // fallar: re-extraía en la MISMA ruta, así que ART reutilizaba su vdex/oat dañado y
        // seguía rechazando yc.h/yc.e. La reparación debe estrenar generación y limpiar caché.
        val text = File("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KTitan.kt").readText()
        assertTrue("la extracción va en generación", text.contains("engineDirName(revision)"))
        assertTrue("el sello lleva sha256", text.contains("ENGINE_REVISION_FILE"))
        assertTrue("se limpian los artefactos de ART", text.contains("app.codeCacheDir?.listFiles()"))
        assertTrue("se borran todas las generaciones", text.contains("dir.name.startsWith(\"x4k-r\")"))
        // El archivo `x4k-rev` empieza igual: si se borrara, la generación nunca avanzaría.
        assertTrue("solo se borran directorios", text.contains("dir.isDirectory"))
        assertTrue("se distingue copia dañada de bytes idénticos", text.contains("ENGINE_DEX_MISMATCH"))
        assertTrue("y se reporta el rechazo de ART", text.contains("ENGINE_VERIFY_FAIL"))
        // Las clases que tumbaron la reproducción se verifican ANTES de arrancar el motor.
        assertTrue("se sondea yc.e al cargar", text.contains("probeEngineClass(cl, \"yc.e\""))
        assertTrue("la vista ed.e es obligatoria", text.contains("probeEngineClass(cl, \"ed.e\", required = true)"))
        // Última palanca: los mismos DEX en memoria (sin archivo, sin vdex).
        assertTrue("carga en memoria de rescate", text.contains("InMemoryDexClassLoader"))
        assertTrue("se reporta si el rescate funcionó", text.contains("ENGINE_MEMORY_OK"))
        // El .part es único por proceso: dos no pueden entrelazar la misma copia.
        assertTrue("part por proceso", text.contains("Process.myPid()"))
    }
}
