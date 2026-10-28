package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoría del mando en la tele: cada pantalla aterriza en algo, los paneles no se quedan sin
 * foco cuando cambia su contenido y los filtros conservan el foco donde el usuario lo puso.
 */
class TvDpadAuditTest {

    private fun tv(name: String): String = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/$name")

    @Test
    fun everyTvScreenLandsTheRemoteOnSomething() {
        val screens = listOf(
            "TvHomeScreen.kt",
            "TvCategoryScreen.kt",
            "TvCategoriesScreen.kt",
            "TvLiveScreen.kt",
            "TvSearchScreen.kt",
            "TvSportsScreen.kt",
            "TvLibraryScreen.kt",
            "TvSettingsScreen.kt",
            "TvDownloadsScreen.kt",
        )
        screens.forEach { name ->
            assertTrue("$name tiene destino de aterrizaje", tv(name).contains("rememberLandingFocus("))
        }
    }

    @Test
    fun pickingAnotherGenreDoesNotStealTheFocusFromTheChip() {
        val category = tv("TvCategoryScreen.kt")
        // Regresión: el efecto estaba atado al género; cada selección devolvía el foco al
        // botón «Volver a Todo». Ahora solo se re-aterriza al CAMBIAR DE MODO.
        assertTrue("el aterrizaje depende del modo", category.contains("LaunchedEffect(gridMode)"))
        assertFalse("ya no se re-aterriza en cada género", category.contains("LaunchedEffect(activeCategory)"))
    }

    @Test
    fun closingTheLiveSearchRestoresTheFocus() {
        val live = tv("TvLiveScreen.kt")
        assertTrue("al cerrar el buscador vuelve el foco", live.contains("LaunchedEffect(searchOpen)"))
        assertTrue(
            "los canales navegan como grupo",
            live.contains("LazyColumn(Modifier.weight(1f).fillMaxHeight().focusGroup()"),
        )
        assertTrue(
            "los géneros navegan como grupo",
            live.contains("LazyColumn(Modifier.width(SIDE_COLUMN).fillMaxHeight().focusGroup()"),
        )
    }

    @Test
    fun theKidsPinDialogCanBeTypedWithTheRemote() {
        val kids = source("src/main/java/com/chronosbvrx/visionplus/ui/kids/KidsUi.kt")
        assertTrue("abre con foco verificado", kids.contains("rememberDialogFocus()"))
        assertTrue("el 1 es el destino", kids.contains("Modifier.dialogFocus(focus)"))
        assertTrue("dos filas de cinco (el 0 ya no se sale del panel)", kids.contains("digits.chunked(5)"))
        assertFalse("ya no hay una sola fila de diez", kids.contains("listOf(\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\", \"9\", \"0\").forEach"))
    }

    @Test
    fun theHomeKeepsTheNextRowReachableWithTheRemote() {
        val home = tv("TvHomeScreen.kt")
        // El LazyColumn solo compone lo visible: al enfocar una tarjeta se deja asomar un pico
        // de la fila siguiente (y de la anterior) para que Abajo/Arriba siempre tengan destino.
        assertTrue("deja asomar la siguiente fila", home.contains("revealNextRowPeek(slotIndex)"))
        assertTrue("el pico se calcula con el layout real", home.contains("visibleItemsInfo.firstOrNull { it.index == rowIndex }"))
        assertTrue("también deja asomar la fila anterior", home.contains("firstVisible > 0"))
        assertFalse(
            "un focusGroup en la fila bloqueaba la flecha Arriba",
            home.contains("modifier = Modifier.focusGroup().focusRestorer()"),
        )
        assertFalse("y la barra no vuelve a agrupar las pestañas así", tv("TvTopBar.kt").contains("focusGroup().focusRestorer()"))
    }

    @Test
    fun navigationChangesRetryTheRescue() {
        val app = source("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        assertTrue("el rescate escucha también los cambios de ruta", app.contains("LaunchedEffect(nav.stack.size, route)"))
    }

    private fun source(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
