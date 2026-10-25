package com.chronosbvrx.visionplus.home.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas puras del menú de apps: qué se puede desinstalar y cómo se buscan las apps
 * instaladas desde el buscador del launcher.
 */
class AppActionsTest {

    private val apps = listOf(
        AppEntry("com.netflix.ninja", "Netflix", canUninstall = true),
        AppEntry("com.google.android.youtube.tv", "YouTube", canUninstall = true),
        AppEntry("com.disney.disneyplus", "Disney+", canUninstall = true),
        AppEntry("com.example.mexico", "México TV", isSystem = true),
        AppEntry("com.example.otra", "Otra app", canUninstall = true),
        AppEntry("com.example.tercera", "Tercera", canUninstall = true),
        AppEntry("com.example.cuarta", "Cuarta", canUninstall = true),
        AppEntry("com.example.quinta", "Quinta", canUninstall = true),
    )

    @Test
    fun aPartialNameFindsTheApp() {
        assertEquals(listOf("Netflix"), filterApps(apps, "net").map { it.label })
    }

    @Test
    fun accentsAndCaseDoNotMatter() {
        assertEquals(listOf("México TV"), filterApps(apps, "MEXICO").map { it.label })
        assertEquals(listOf("México TV"), filterApps(apps, "méxico").map { it.label })
    }

    @Test
    fun spacedWordsFindCompactNames() {
        assertEquals(listOf("YouTube"), filterApps(apps, "you tube").map { it.label })
    }

    @Test
    fun thePackageNameAlsoWorks() {
        assertEquals(listOf("Disney+"), filterApps(apps, "disneyplus").map { it.label })
    }

    @Test
    fun aBlankQueryDoesNotReturnTheWholeDrawer() {
        assertTrue(filterApps(apps, "   ").isEmpty())
        assertTrue(filterApps(apps, "").isEmpty())
    }

    @Test
    fun theSearchRespectsItsLimit() {
        assertEquals(2, filterApps(apps, "a", limit = 2).size)
        assertEquals(3, filterApps(apps, "e", limit = 3).size)
    }

    @Test
    fun aUserAppCanBeUninstalled() {
        assertTrue(uninstallable(isSystem = false, updatedSystem = false))
    }

    @Test
    fun aFactoryAppWithoutUpdatesCannotBeRemoved() {
        assertFalse(uninstallable(isSystem = true, updatedSystem = false))
    }

    @Test
    fun anUpdatedFactoryAppCanDropItsUpdate() {
        assertTrue(uninstallable(isSystem = true, updatedSystem = true))
    }

    @Test
    fun aUserAppDoesNotNeedAnyExtraInstalledFlag() {
        // Regresión real: FLAG_INSTALLED no siempre viene y dejaba sin «Desinstalar» a las
        // apps instaladas por el usuario (Android 16).
        assertTrue(uninstallable(isSystem = false, updatedSystem = false))
    }
}
