package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cargador aislado del motor no se puede ejercitar de verdad en la JVM (el DEX es Android y el
 * motor nativo no corre aquí), así que se blinda el cableado: que el arranque del SDK use
 * `EngineClassLoader`, que respete el interruptor remoto y que la autocomprobación se reporte.
 */
class EngineLoaderWiringTest {

    private val titan = requireSource("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KTitan.kt")

    @Test
    fun theEngineBootsWithTheChildFirstLoader() {
        assertTrue(
            "el arranque del SDK tiene que crear el cargador aislado",
            titan.contains("EngineClassLoader("),
        )
        assertTrue(
            "el plan B (InMemoryDexClassLoader) sigue existiendo si el DexClassLoader es rechazado",
            titan.contains("InMemoryDexClassLoader("),
        )
    }

    @Test
    fun theIsolationCanBeTurnedOffRemotelyWithoutAnOta() {
        assertTrue(
            "kill-switch remoto: engine_child_first devuelve el cargador parent-first",
            titan.contains("flag(app, \"engine_child_first\", true)"),
        )
        assertTrue("sin el flag también se usa el DexClassLoader de siempre", titan.contains("DexClassLoader("))
    }

    @Test
    fun theLeakIsReportedInsteadOfWaitingForTheCrash() {
        assertTrue(
            "tras cargar el motor se comprueba que sus clases las sirva él y no el APK",
            titan.contains("leakedEngineNames(cl)"),
        )
        assertTrue(
            "y se reporta con código propio para que el triage lo vea",
            titan.contains("ENGINE_NAMESPACE_LEAK"),
        )
    }

    private fun requireSource(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
