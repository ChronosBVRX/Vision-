package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Canal en negro al abrir TV en vivo desde Vision+ Home (y desde la propia app) hasta cambiar
 * de canal. El motor crea su vista de render (SurfaceView / TextureView) DENTRO del `play`
 * (`ed.e.A()` → `ad.c.a()`) y solo la añade al contenedor si `ed.e` ya está anclada a la
 * ventana (`ad.c.c(...)` se fija en `onAttachedToWindow`). Con el motor precalentado, el
 * montaje corre dentro de `onCreate` —antes del attach—: el render nunca llegaba al contenedor
 * y se oía el canal con el video negro. El primer cambio de canal sí pintaba porque el motor
 * volvía a crear la vista ya anclada.
 *
 * Arreglo: el primer play espera al attach y, por si acaso, se re-emite UNA vez si el
 * contenedor sigue vacío (el mismo play del zap, sin cambiar de canal).
 */
class LiveRenderAttachTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theFirstPlayWaitsForTheViewToBeAttached() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue(
            "el primer play debe esperar al attach",
            player.contains("val startWhenReady: () -> Unit = { startPlaybackWhenAttached(v, startPlayback) }"),
        )
        assertTrue(player.contains("if (introView == null) startWhenReady() else pendingPlay = startWhenReady"))
        assertFalse(
            "el play suelto en onCreate vuelve a adelantarse al attach",
            player.contains("if (introView == null) startPlayback() else pendingPlay = startPlayback"),
        )
        val helper = player.substringAfter("private fun startPlaybackWhenAttached(")
            .substringBefore("private fun ensureRenderViewAttached(")
        assertTrue("usa el estado real de la vista", helper.contains("isAttachedToWindow"))
        assertTrue("espera el attach", helper.contains("addOnAttachStateChangeListener"))
        assertTrue("un frame después del attach", helper.contains("view.post(action)"))
        assertTrue("no arranca una actividad que se está cerrando", helper.contains("isFinishing || isDestroyed"))
    }

    @Test
    fun aPlayThatLandedBeforeTheAttachIsReissuedOnce() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("el play programa la comprobación del render", player.contains("ensureRenderViewAttached(it)"))
        val helper = player.substringAfter("private fun ensureRenderViewAttached(")
            .substringBefore("private val RENDER_CHECK_MS")
        assertTrue("re-emite el mismo play", helper.contains("X4KTitan.play(v,"))
        assertTrue("solo una vez por reproducción", helper.contains("renderReattach"))
        assertTrue("se anota en diagnóstico", helper.contains("PLAYER_RENDER_REATTACH"))
        assertTrue("la decisión es la función pura", helper.contains("shouldReattachRender("))
        assertTrue(
            "la bandera se limpia en cada reproducción",
            player.substringAfter("private fun resetForNewPlay()").contains("renderReattach = false"),
        )
    }

    @Test
    fun theDecisionOnlyFiresWhenTheRenderIsReallyMissing() {
        // Activo, anclado, contenedor sin la vista de render y sin haber reintentado.
        assertTrue(shouldReattachRender(true, attached = true, renderChildren = 0, alreadyRetried = false))
        // Nada que hacer cuando no hay reproducción viva o la vista aún no está en la ventana.
        assertFalse(shouldReattachRender(isPlayerActive = false, attached = true, renderChildren = 0, alreadyRetried = false))
        assertFalse(shouldReattachRender(isPlayerActive = true, attached = false, renderChildren = 0, alreadyRetried = false))
        // Un contenedor con su vista de render ya está sano (el caso normal y el del zap).
        assertFalse(shouldReattachRender(isPlayerActive = true, attached = true, renderChildren = 1, alreadyRetried = false))
        // Jamás se repite: como mucho un re-play por reproducción.
        assertFalse(shouldReattachRender(isPlayerActive = true, attached = true, renderChildren = 0, alreadyRetried = true))
    }
}
