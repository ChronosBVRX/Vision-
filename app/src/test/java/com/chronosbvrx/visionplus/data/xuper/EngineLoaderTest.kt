package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * El aislamiento del espacio de nombres del motor X4K (apagón de la 2.5.117).
 *
 * El motor se carga parent-first: si nuestro APK define una clase con el nombre de una suya
 * (`k4.x` y compañía), la del motor queda tapada y `onPrepared` muere con `NoSuchMethodError`.
 * `isEngineNamespace` decide qué nombres se preguntan primero al DEX del motor, y
 * `leakedEngineNames` es la autocomprobación que lo reporta en vez de esperar al crash.
 */
class EngineLoaderTest {

    @Test
    fun engineNamespaceCoversTheObfuscatedRoots() {
        assertTrue(isEngineNamespace("k4.x"))
        assertTrue(isEngineNamespace("a1.a"))
        assertTrue(isEngineNamespace("ed.e"))
        assertTrue(isEngineNamespace("zf.q"))
        assertTrue(isEngineNamespace("a.b"))
        assertTrue(isEngineNamespace("z9.a"))
        // `tv.*` es el propio IjkMediaPlayer del motor, así que también se pregunta a su DEX.
        assertTrue(isEngineNamespace("tv.danmaku.ijk.media.player.IjkMediaPlayer"))
    }

    @Test
    fun realNamesStayWithTheApp() {
        assertFalse(isEngineNamespace("androidx.core.app.CoreComponentFactory"))
        assertFalse(isEngineNamespace("okhttp3.Address"))
        assertFalse(isEngineNamespace("kotlin.collections.IndexedValue"))
        assertFalse(isEngineNamespace("com.titan.ranger.bean.Env"))
        assertFalse("sin paquete no hay espacio del motor", isEngineNamespace("NoPackageClass"))
        assertFalse("una raíz en mayúsculas es un nombre real", isEngineNamespace("K4.X"))
        assertFalse(isEngineNamespace(""))
    }

    @Test
    fun aHealthyEngineReportsNoLeaks() {
        val engine = requireNotNull(javaClass.classLoader)
        // Las clases del bootstrap no tienen cargador y las inexistentes no resuelven: cero fugas.
        assertEquals(
            emptyList<String>(),
            leakedEngineNames(engine, listOf("java.lang.String", "no.existe.EstaClase")),
        )
    }

    @Test
    fun aClassServedByTheAppIsReportedAsToolkitLeak() {
        val app = requireNotNull(javaClass.classLoader)
        // Un cargador "motor" que delega al padre reproduce el ensombrecimiento: la clase existe,
        // pero la sirve el APK (owner != engine).
        val engine = URLClassLoader(arrayOf(File(".").toURI().toURL()), app)
        assertEquals(
            listOf("com.chronosbvrx.visionplus.data.xuper.EngineLoaderTest"),
            leakedEngineNames(engine, listOf("com.chronosbvrx.visionplus.data.xuper.EngineLoaderTest")),
        )
    }
}
