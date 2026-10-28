package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El camino del mando en Vision+ Home: cada diálogo abre con foco, el foco nunca se queda
 * huérfano cuando el contenido cambia debajo del cursor y la barra superior siempre tiene una
 * salida hacia el contenido.
 *
 * Son guardianes de cableado (como el resto de la suite del launcher): el módulo no arrastra
 * Robolectric y estas reglas se rompen justo en los sitios que se tocan a mano.
 */
class LauncherDpadTest {

    private fun ui(name: String): String = require("src/main/java/com/chronosbvrx/visionplus/home/ui/$name")

    @Test
    fun everyDialogOpensWithFocusAndNeverLosesIt() {
        val focus = ui("DialogFocus.kt")
        assertTrue("reintenta hasta que el destino tiene el foco", focus.contains("repeat(40)"))
        assertTrue("vigila que la ventana no se quede sin ningún control", focus.contains("rootHasFocus"))
        assertTrue("el vigilante solo actúa sin foco", focus.contains("!focus.rootHasFocus"))
        assertTrue("si el destino desapareció cae al control que siempre existe", focus.contains("requestFallback()"))

        val overlays = ui("Overlays.kt")
        assertTrue("centro de control con foco de diálogo", overlays.contains("rememberDialogFocus()"))
        assertTrue("raíz del centro de control", overlays.contains("dialogRootFocus(focus)"))
        assertTrue("la guía del Inicio también abre enfocada", overlays.contains("Modifier.dialogFocus(focus)"))
        assertTrue("y su Cerrar es el respaldo", overlays.contains("dialogFallbackFocus(focus)"))

        val personalize = ui("Personalize.kt")
        assertTrue("el panel de personalización raíz", personalize.contains("dialogRootFocus(focus)"))
        assertTrue("el hub abre en Canales", personalize.contains("Modifier.dialogFocus(focus)"))
        assertTrue("las acciones de la tarjeta abren en la primera", personalize.contains("Modifier.dialogFocus(focus) else Modifier"))
        assertTrue("su Cerrar es el respaldo", personalize.contains("dialogFallbackFocus(focus)"))
        assertFalse("ya no hay peticiones de una sola vez", personalize.contains("rememberInitialFocus"))

        val search = ui("SearchSurface.kt")
        assertTrue("el buscador también", search.contains("rememberDialogFocus()"))
        assertTrue("y su raíz", search.contains("dialogRootFocus(focus)"))
        assertTrue("el teclado del mando abre en la primera tecla", search.contains("Modifier.dialogFocus(focus)"))
        assertTrue("y su Cerrar es el respaldo", search.contains("dialogFallbackFocus(focus)"))
    }

    @Test
    fun aContentSwapKeepsTheRemoteAlive() {
        val personalize = ui("Personalize.kt")
        // «Restablecer todo» reemplaza la fila enfocada por los botones de confirmación: el
        // destino de esa zona se reutiliza (y el vigilante cubre cualquier otro cambio).
        assertTrue(
            "la confirmación conserva el foco en la zona",
            personalize.contains("\"Sí, restablecer todo\"") &&
                personalize.contains("val resetFocus = remember { FocusRequester() }") &&
                personalize.contains("LaunchedEffect(confirmReset)"),
        )
        // Los gestores filtran/ocultan filas: el mismo destino vuelve al primer control visible.
        assertTrue("canales", personalize.contains("dialogFocus = focus"))
        assertTrue("apps", personalize.contains("dialogFocus = focus"))
    }

    @Test
    fun theChromeAlwaysHasAWayDown() {
        val chrome = ui("Chrome.kt")
        assertTrue("las pestañas recuerdan su último foco", chrome.contains("focusGroup().focusRestorer()"))
        assertTrue("perfil baja al contenido", chrome.contains("ProfileChip(name = profile, compact = compact, downTarget = downTarget)"))
        assertTrue("buscar baja al contenido", chrome.contains("ChromeIcon(Icons.Rounded.Search, \"Buscar\", onSearch, downTarget)"))
        assertTrue("ajustes baja al contenido", chrome.contains("Icons.Rounded.Settings, \"Ajustes\", onCommandCenter, downTarget"))
        assertTrue("el chip implementa el atajo", chrome.contains("Modifier.softFocusDown(downTarget)"))
    }

    @Test
    fun theHomeNeverEatsAKeyWhenTheFocusDisappears() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("vigilante del foco del Inicio", screen.contains("if (!screenHasFocus) requestFocusSafely(target)"))
        assertTrue("solo mientras el launcher está al frente", screen.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        assertTrue("no pelea con un panel abierto", screen.contains("val panelOpen ="))
    }

    @Test
    fun theSettingsPanelTargetsTheFocusableNode() {
        val overlays = ui("Overlays.kt")
        // Regresión real: el modificador con el FocusRequester se aplicaba a una fila que no
        // era enfocable y la petición no enfocaba nada.
        assertTrue(
            "el destino va en el nodo enfocable",
            overlays.contains("modifier = Modifier.fillMaxWidth().then(modifier)"),
        )
    }

    @Test
    fun theInstallBannerIsReachableWithTheRemote() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        // Regresión: el aviso de instalar/actualizar Vision+ vive ENTRE la barra y el
        // contenido. Con «abajo» y «arriba» apuntando al héroe, sus botones quedaban
        // inalcanzables con el mando.
        assertTrue("el aviso tiene destino propio", screen.contains("val bannerVisible = !visionInstalled || appOutdated"))
        assertTrue("la barra baja al aviso", screen.contains("downTarget = if (bannerVisible) bannerFocus else contentLanding"))
        assertTrue(
            "el contenido sube al aviso",
            screen.contains("val contentUpTarget: FocusRequester? = if (bannerVisible) bannerFocus else tabLanding"),
        )
        assertTrue("el botón primario del aviso lo sostiene", screen.contains("primaryModifier = bannerPrimaryModifier"))
        assertTrue("y baja al contenido", screen.contains(".softFocusDown(contentLanding)"))

        val surfaces = ui("Surfaces.kt")
        assertTrue("los avisos aceptan el modificador del primario", surfaces.contains("primaryModifier: Modifier = Modifier"))
        assertTrue("se aplica al botón enfocable", surfaces.contains("modifier = primaryModifier"))
    }

    @Test
    fun theChannelAndAppManagersKeepTheirFiltersReachable() {
        val personalize = ui("Personalize.kt")
        // Los chips viven arriba a la izquierda y las acciones de cada fila arriba a la derecha:
        // sin enlaces, «arriba» caía en Cerrar y «abajo» no entraba a la lista.
        assertTrue("la primera fila sube a los filtros", personalize.contains("upTarget = filtersFocus"))
        assertTrue("los filtros bajan a la primera fila", personalize.contains("softFocusDown(focus.requester)"))
        assertTrue(
            "la fila usa el enlace de subida",
            personalize.contains("if (first && upTarget != null) Modifier.focusProperties { up = upTarget }"),
        )
    }

    @Test
    fun theHomeRowsKeepTheNeighbourRowReachable() {
        val surfaces = ui("Surfaces.kt")
        // Mismo caso que la tele: el LazyColumn solo compone lo visible. Al enfocar una fila
        // se deja asomar un pico de la vecina para que Abajo/Arriba siempre tengan destino.
        assertTrue("deja asomar la fila siguiente", surfaces.contains("revealRowPeek(\"row-"))
        assertTrue("se calcula con el layout real", surfaces.contains("visibleItemsInfo.firstOrNull { it.key == itemKey }"))
        assertTrue("también la anterior", surfaces.contains("firstVisible > 0"))
        assertFalse(
            "las filas no se encierran en un focusGroup que bloquea Arriba",
            surfaces.contains("modifier = Modifier.focusGroup().focusRestorer(),"),
        )
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
