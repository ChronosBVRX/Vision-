package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * «Ver más» de una sección por género: las dos pantallas piden páginas reales al
 * servidor, respetan el techo del equipo y ofrecen el botón al final de la hilera
 * y de la cuadrícula. Los totales de la sección ya no se pierden (CatalogRow.total).
 */
class SectionPagingWiringTest {

    @Test
    fun theServerPageIsParsedWithTotalAndGenres() {
        val source = source("data/sources/RemoteIndexSource.kt")
        assertTrue("página de categoría", source.contains("suspend fun categoryPage("))
        assertTrue("endpoint paginado", source.contains("/v1/category/"))
        assertTrue("parseo puro", source.contains("internal fun parseCategoryPage("))
    }

    @Test
    fun theMobileSectionOffersSeeMoreAndLoadsPages() {
        val screen = source("ui/phone/SectionScreen.kt")
        assertTrue("tarjeta al final de la hilera", screen.contains("SeeMoreCard("))
        assertTrue("botón de la cuadrícula", screen.contains("Text(\"Ver más\")"))
        assertTrue("pide la siguiente página", screen.contains("RemoteIndexSource.categoryPage("))
        assertTrue("respeta el techo", screen.contains("SectionPaging.canLoadMore("))
        assertTrue("lote por equipo", screen.contains("SectionPaging.batch(lowEnd)"))
        assertTrue("muestra el avance", screen.contains("\"Mostrando \${displayedFilms.size} de "))
    }

    @Test
    fun theTvSectionOffersSeeMoreAndLoadsPages() {
        val screen = source("ui/tv/TvCategoryScreen.kt")
        assertTrue("tarjeta al final de la hilera", screen.contains("TvSeeMoreCard("))
        assertTrue("botón de la cuadrícula", screen.contains("TvCompactAction("))
        assertTrue("etiqueta del botón", screen.contains("label = \"Ver más\""))
        assertTrue("pide la siguiente página", screen.contains("RemoteIndexSource.categoryPage("))
        assertTrue("respeta el techo", screen.contains("SectionPaging.canLoadMore("))
        assertTrue("lote por equipo", screen.contains("SectionPaging.batch(lowEnd)"))
        assertTrue("muestra el avance", screen.contains("\"Mostrando \${displayedFilms.size} de "))
    }

    @Test
    fun theRowCarriesTheRealTotal() {
        val catalog = source("data/Catalog.kt")
        assertTrue("CatalogRow.total", catalog.contains("val total: Int = 0"))
        val index = source("data/sources/RemoteIndexSource.kt")
        assertTrue("lee el total del servidor", index.contains("total = category.optInt(\"total\""))
        assertTrue("lo guarda en caché", index.contains(".put(\"total\", row.total)"))
    }

    @Test
    fun theDeviceCapIsTheSameInBothScreens() {
        val mobile = source("ui/phone/SectionScreen.kt")
        val tv = source("ui/tv/TvCategoryScreen.kt")
        for (screen in listOf(mobile, tv)) {
            assertTrue("límite por equipo", screen.contains("SectionPaging.cap(lowEnd)"))
            assertTrue("detecta equipo modesto", screen.contains("DevicePerformance.isLowEndDevice(context)"))
        }
    }

    private fun source(rel: String): String {
        val path = "src/main/java/com/chronosbvrx/visionplus/$rel"
        val found = listOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
