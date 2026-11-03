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
        assertTrue("el botón de Inicio del sistema baja al contenido", chrome.contains("downTarget = downTarget"))
        assertTrue("buscar baja al contenido", chrome.contains("ChromeIcon(Icons.Rounded.Search, \"Buscar\", onSearch, downTarget)"))
        assertTrue("la red es solo un indicador, no una opción del TV", chrome.contains("NetworkChip(network = network, compact = compact)"))
        assertFalse("fuera el acceso a Wi-Fi del televisor", chrome.contains("onWifi"))
        assertTrue("centro de control baja al contenido", chrome.contains("onCommandCenter,") && chrome.contains("downTarget,"))
        assertTrue("ajustes de Vision+ bajan al contenido", chrome.contains("Icons.Rounded.Settings, \"Ajustes de Vision+\", onVisionSettings, downTarget"))
        assertTrue("el sistema implementa el atajo", chrome.contains("Modifier.softFocusDown(downTarget)"))
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

    @Test
    fun backGoesToTheSystemBarBeforeLeavingTheSection() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        // Misma dinámica que Vision+: el primer Atrás sube el foco a la barra de sistema; el
        // segundo, dentro de la ventana, vuelve a Inicio. Con el mando ya en la barra, Atrás
        // actúa de una vez.
        assertTrue("guardián del Atrás en dos pasos", screen.contains("LauncherBackToTopGuard()"))
        assertTrue("la barra sabe si tiene el foco", screen.contains("topBarFocused = it.hasFocus"))
        assertTrue("el primer Atrás enfoca el Inicio del sistema", screen.contains("requestFocusSafely(tabLanding)"))
        assertTrue("solo si el foco no está ya en la barra", screen.contains("!topBarFocused && backToTop.press(now)"))
        assertTrue("si no aterriza, el Atrás no se come", screen.contains("if (requestFocusSafely(tabLanding)) return@BackHandler"))
        assertFalse("la barra ya no tiene pestañas desplazables", screen.contains("topTabsState"))

        val chrome = ui("Chrome.kt")
        assertTrue("el foco inicial cae en el botón de Inicio", chrome.contains("activeTabRequester"))

        val guard = ui("BackToTop.kt")
        assertTrue("ventana de 2.5 s", guard.contains("LAUNCHER_BACK_TO_TOP_MS = 2_500L"))
        assertTrue("política pura", guard.contains("class LauncherBackToTopGuard"))
        assertTrue("se desarma al cambiar de pantalla", guard.contains("fun disarm()"))
    }

    @Test
    fun theLongPressKeepsTheMenuOpenWhileTheKeyIsHeld() {
        val components = ui("Components.kt")
        // Regresión real: el candado vencía a los 2.5 s y, si la persona seguía apretando OK
        // mientras leía el menú, al soltar se activaba la primera acción («Abrir» en una app).
        assertTrue("el candado ya no vence por tiempo", !components.contains("pendingAt = System.currentTimeMillis()"))
        assertTrue("come el KeyUp aunque tarde", components.contains("fun consumeRelease(): Boolean"))
        assertTrue("un OK nuevo lo desarma", components.contains("fun noteKeyDown(repeatCount: Int)"))
        assertTrue(
            "solo cuando el mando estuvo callado (los mandos que repiten con repeatCount=0 no lo desarman)",
            components.contains("now - lastOkAt >= quietMs"),
        )
        assertTrue("los diálogos consumen el KeyUp del OK largo", ui("DialogBack.kt").contains("fun Modifier.consumeLongPressRelease()"))
        assertTrue("el menú de la tarjeta lo usa", ui("Personalize.kt").contains(".consumeLongPressRelease()"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
