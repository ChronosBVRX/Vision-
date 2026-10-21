package com.chronosbvrx.visionplus.ui

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián del Modo Sin Conexión:
 *
 * al quedarse sin internet la app abre la interfaz «Vision+ Offline» (solo lo descargado)
 * y, en cuanto vuelve la red, regresa sola. Un reproductor ya abierto (película o canal en
 * vivo) NO se interrumpe por una caída momentánea: el reproductor maneja sus reintentos.
 */
class OfflineModeGateTest {

    private val film = Film(
        id = "xtv:1",
        title = "Prueba",
        year = 2026,
        durationMin = 90,
        synopsis = "",
        posterUrl = "",
        videoUrl = "xtv:1",
        sourceUrl = "xtv",
    )

    @Test
    fun withoutInternetTheMenuOpensTheOfflineScreen() {
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Home))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Categories))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Library))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Downloads))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Search))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Settings))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Sports))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Title(film)))
        assertTrue(shouldShowOfflineMode(isOnline = false, route = Route.Section("movies", "Películas")))
    }

    @Test
    fun withInternetTheAppShowsItsNormalScreens() {
        assertFalse(shouldShowOfflineMode(isOnline = true, route = Route.Home))
        assertFalse(shouldShowOfflineMode(isOnline = true, route = Route.Title(film)))
        assertFalse(shouldShowOfflineMode(isOnline = true, route = Route.Downloads))
    }

    @Test
    fun anOpenPlayerIsNotYankedAwayByAMomentaryDrop() {
        assertFalse(shouldShowOfflineMode(isOnline = false, route = Route.Player(film)))
        assertFalse(
            shouldShowOfflineMode(
                isOnline = false,
                route = Route.LivePlayer(LiveChannel(name = "Canal", url = "https://x/y.m3u8")),
            ),
        )
    }

    @Test
    fun bothShellsUseTheSameGate() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        assertTrue("el móvil decide con shouldShowOfflineMode", phone.contains("shouldShowOfflineMode(isOnline, route)"))
        assertTrue("la tele decide con shouldShowOfflineMode", tv.contains("shouldShowOfflineMode(isOnline, route)"))
        assertTrue("reintento honesto en móvil", phone.contains("Aún no hay conexión a internet"))
        assertTrue("reintento honesto en tele", tv.contains("Aún no hay conexión a internet"))
    }

    @Test
    fun aNetworkThatCannotBeValidatedIsProbedBeforeLoadingTheCatalog() {
        val main = requireSource("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("el arranque confirma la salida real", main.contains("NetworkMonitor.verify("))
        val monitor = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/NetworkMonitor.kt")
        assertTrue("el sistema valida la red", monitor.contains("NET_CAPABILITY_VALIDATED"))
        assertTrue("sonda de salida", monitor.contains("probeReachable("))
        assertTrue("la sonda no corre en el hilo principal", monitor.contains("Dispatchers.IO"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
