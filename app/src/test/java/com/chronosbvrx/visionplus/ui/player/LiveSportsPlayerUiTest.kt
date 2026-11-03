package com.chronosbvrx.visionplus.ui.player

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.MatchStats
import com.chronosbvrx.visionplus.data.sports.TeamMatchStats
import com.chronosbvrx.visionplus.ui.sports.geeScoreLine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reproductor de los canales de GeeSports con la MISMA interfaz que la TV en vivo: el aviso de
 * cable (EN VIVO, liga, «AHORA», marcador) y las píldoras etiquetadas (Canales / Información y las
 * funciones propias del reproductor en línea en la misma piel), conservando lo suyo — reconexión,
 * failover, señales y zapping.
 *
 * La parte visual se inspecciona en la fuente (es una pantalla de Compose con un ExoPlayer vivo
 * detrás); lo puro se prueba aquí mismo.
 */
class LiveSportsPlayerUiTest {

    @Test
    fun theSignalTypeIsLabelledLikeThePickerDoes() {
        assertEquals("DASH · ClearKey", zapSignalType("https://cdn/x.mpd", "dash", "kid:key"))
        assertEquals("DASH", zapSignalType("https://cdn/x.mpd", "dash", ""))
        assertEquals("DASH", zapSignalType("https://cdn/x.mpd", null, null))
        assertEquals("HLS", zapSignalType("https://cdn/x.m3u8", "hls", null))
        // Un DASH sin tipo declarado pero con .mpd en la dirección también se rotula.
        assertEquals("DASH", zapSignalType("https://cdn/live/manifest.MPD?x=1", null, null))
    }

    @Test
    fun theScoreLineCarriesWhatTheAgendaKnows() {
        val withScore = event(scoreHome = 1, scoreAway = 0, detail = "37'")
        assertEquals("1 - 0 · 37'", geeScoreLine(withScore))
        assertEquals("", geeScoreLine(event()))
        assertEquals("2 - 2", geeScoreLine(event(scoreHome = 2, scoreAway = 2)))
    }

    @Test
    fun theLivePlayerUsesTheMatchCenterHudForSportsEvents() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        val hud = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/MatchCenterHud.kt")
        // La TV en vivo normal conserva el aviso de cable; el evento deportivo usa el HUD.
        assertTrue("el aviso de cable sigue para la TV", player.contains("LiveCableBanner("))
        assertTrue("el HUD se decide por el contexto del partido", player.contains("showMatchHud"))
        assertTrue("el HUD se pinta abajo", player.contains("MatchCenterHud("))
        assertTrue("el aviso se omite con HUD", player.contains("if (showMatchHud)"))
        // D-Pad: izquierda/derecha cambia de página; OK oculta; cada tecla reinicia el auto-hide.
        assertTrue("página anterior", player.contains("matchHudPageAfter(matchHudPage, -1)"))
        assertTrue("página siguiente", player.contains("matchHudPageAfter(matchHudPage, 1)"))
        assertTrue("OK oculta el menú", player.contains("Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> controls.hide()"))
        // El bloque único: liga + EN VIVO, pestañas, marcador, cuerpo fijo y pie.
        assertTrue("liga y EN VIVO", hud.contains("\"EN VIVO\""))
        assertTrue("pestaña Resumen", hud.contains("\"1. Resumen\""))
        assertTrue("pestaña Estadísticas", hud.contains("\"2. Estadísticas\""))
        assertTrue("pestaña Tarjetas y Goles", hud.contains("\"3. Tarjetas y Goles\""))
        assertTrue("pestaña Alineaciones", hud.contains("\"4. Alineaciones\""))
        assertTrue("cifras clave", hud.contains("HUD_SUMMARY_STATS"))
        assertTrue("disciplina y ataque", hud.contains("HUD_DISCIPLINE_STATS"))
        assertTrue("cronología con iconos", hud.contains("hudEventLine("))
        assertTrue("formación publicada", hud.contains("formation"))
        assertTrue("selector de señal", hud.contains("\"↑ ↓ SEÑAL"))
        assertTrue("guía de pestañas", hud.contains("Estadísticas y Alineaciones"))
        assertTrue("guía de señal", hud.contains("Cambiar señal del partido"))
        assertTrue("guía para ocultar", hud.contains("OK / Atrás"))
        assertTrue("carga las cifras reales", hud.contains("SportsStatsSource.load("))
        // La fila de 4 píldoras ya no se pinta en el evento deportivo (sí en TV en vivo).
        assertTrue("píldoras solo fuera del HUD", player.contains("if (!showMatchHud) {"))
        assertTrue("Canales sigue en la TV en vivo", player.contains("label = \"Canales\""))
        // La ficha de Información sigue disponible para el canal en vivo.
        assertTrue("información del canal", player.contains("GeeMatchInfoDialog("))
        // En vivo no hay pausa: el botón de pausa es solo del VOD.
        assertFalse("sin píldora de pausa en vivo", player.contains("if (playing) \"Pausa\" else \"Reproducir\""))
        // El contexto del partido viaja al reproductor y se lee antes que las señales.
        assertTrue("el modal arma el contexto", player.contains("takeInfoFor(channel)"))
        assertTrue("y el reproductor lo recibe", player.contains("eventInfo = eventInfo"))
        // Lo propio del reproductor en línea sigue ahí: reconexión, failover y señales.
        assertTrue("reconexión", player.contains("Reconectando… intento"))
        assertTrue("failover", player.contains("failoverAllowed("))
        assertTrue("el selector de señales", player.contains("ZapListDialog("))
        // Ya no hay píldora de zapping aparte: el aviso de cable es el que lo cuenta.
        assertFalse("sin píldora duplicada", player.contains("zapBannerName"))
    }

    @Test
    fun theSportsDialogHandsTheMatchContextAndOnlyGeeSources() {
        val mobile = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/GeeSportsTabs.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvGeeSportsTabs.kt")
        for ((name, text) in listOf("móvil" to mobile, "TV" to tv)) {
            assertTrue("$name: arma el contexto", text.contains("eventInfo: GeeZapSignals.EventInfo? = null"))
            assertTrue("$name: lo entrega al reproductor", text.contains("GeeZapSignals.arm(picked, zapOrder, eventInfo)"))
            assertTrue("$name: la lista no atribuye la transmisión", text.contains("Transmisiones disponibles:"))
            assertFalse("$name: sin mención de la fuente en la UI", text.contains("Transmisiones de GeeSports:"))
            // La lista la arma la pieza pura con LOS enlaces del evento (sin guía/catálogo).
            assertTrue("$name: usa solo los enlaces del evento", text.contains("geePickRows(title, links, checks, playingUrl)"))
            assertTrue("$name: zapping solo entre las disponibles del evento", text.contains("it.playable && it.alive != false && it.channel.url.isNotBlank()"))
            assertFalse("$name: sin la línea de la guía", text.contains("Se transmite en: "))
            // Sin la sección del catálogo ni respaldo automático.
            assertFalse("$name: sin Canales posibles", text.contains("Canales posibles"))
            assertFalse("$name: sin respaldo", text.contains("GeeBackstop"))
            assertFalse("$name: sin filas de respaldo", text.contains("respaldo automático"))
        }
        val picks = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/GeePicks.kt")
        assertTrue("las ranuras declaradas sin URL se conservan", picks.contains("if (raw.isBlank() && link.name.isBlank() && link.tokenApi.isBlank()) continue"))
        assertTrue("y se marcan como no sintonizables", picks.contains("val playable: Boolean"))
        assertFalse("sin canales de la guía", picks.contains("GeeGuide.channelsFor"))
        assertTrue("lo que responde va primero", picks.contains("aliveRank(it)"))
        val screenMobile = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/SportsScreen.kt")
        val screenTv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSportsScreen.kt")
        for ((name, text) in listOf("móvil" to screenMobile, "TV" to screenTv)) {
            assertFalse("$name: sin pickingBackstops", text.contains("pickingBackstops"))
            assertFalse("$name: sin fallback al catálogo", text.contains("fallback ="))
        }
    }

    @Test
    fun theSportsScreenRefreshesTheLinksWhileItIsOpen() {
        val mobile = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/SportsScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSportsScreen.kt")
        for ((name, text) in listOf("móvil" to mobile, "TV" to tv)) {
            assertTrue("$name: pide enlaces frescos", text.contains("gee.refreshLinks(force = true)"))
            assertTrue("$name: solo con la pantalla al frente", text.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        }
        val state = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/GeeSportsState.kt")
        assertTrue("pase periódico de 5 minutos", state.contains("GEE_LINKS_REFRESH_MS = 5L * 60 * 1000"))
        assertTrue("solo eventos, no grupos", state.contains("GeeSportsSource.refreshEvents(context, force)"))
    }

    @Test
    fun thePlayerFallsBackToTheHouseChannelByItselfWhenEverythingElseGivesUp() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        // El respaldo es del reproductor: al agotarse las señales lo pide, con telemetría.
        assertTrue("lo pide al agotarse", player.contains("PLAYER_FAILOVER_XTV"))
        assertTrue("una sola vez", player.contains("fellBackToXtv"))
        assertTrue("por el puente del proceso", player.contains("GeeZapSignals.requestFallback(back)"))
        // Y el reproductor en vivo lo atiende abriendo el canal de la casa con el motor nativo.
        assertTrue("atiende el pedido", player.contains("GeeZapSignals.consumeFallback()"))
        assertTrue("con el reproductor nativo", player.contains("X4KApi.playLive("))
        // La firma del reproductor NO crece: ART rechaza las composables con demasiados parámetros
        // (VerifyError visto en vivo al agregar dos más).
        assertFalse("sin parámetros nuevos", player.contains("xtvFallback: LiveChannel? = null"))
    }

    @Test
    fun theGeeSportsSourceAlwaysKeepsPriority() {
        val backstop = requireSource("src/main/java/com/chronosbvrx/visionplus/data/gee/GeeBackstop.kt")
        // La regla vive en el motor: solo se ofrece respaldo de una cadena que la fuente NO
        // pueda servir EN VIVO (si su opción está viva, no hay respaldo que valga).
        assertTrue("solo si la fuente no la sirve", backstop.contains("if (liveOptions.any { it.alive && covers(guideName, it.name) }) continue"))
    }

    @Test
    fun theInfoDialogShowsTheMatchAndItsSignals() {
        val dialogs = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerDialogs.kt")
        assertTrue("el modal existe", dialogs.contains("internal fun GeeMatchInfoDialog("))
        assertTrue("titulado Información", dialogs.contains("Text(\"Información\")"))
        assertTrue("liga", dialogs.contains("MenuRow(\"Liga\""))
        assertTrue("marcador", dialogs.contains("MenuRow(\"Marcador\""))
        assertTrue("señal en pantalla", dialogs.contains("MenuRow(\"Señal en pantalla\""))
        assertTrue("se transmite en", dialogs.contains("MenuRow(\"Se transmite en\""))
        assertTrue("las señales del evento", dialogs.contains("Señales del evento"))
    }


    @Test
    fun theStatLabelsKeepTheImportantNumbersFirstAndAddTheSourceExtras() {
        val stats = MatchStats(
            home = TeamMatchStats(
                name = "Local",
                values = mapOf(
                    "Posesión" to "55",
                    "Tiros" to "9",
                    "Tarjetas rojas" to "1",
                    "Precisión de pase" to "88%",
                ),
                lineup = emptyList(),
            ),
            away = TeamMatchStats(
                name = "Visita",
                values = mapOf(
                    "Posesión" to "45",
                    "Tiros" to "7",
                    "Córners" to "3",
                ),
                lineup = emptyList(),
            ),
            fetchedAt = 0L,
        )
        val labels = matchStatLabels(stats)
        assertEquals(listOf("Posesión", "Tiros", "Córners"), labels.take(3))
        assertTrue(labels.contains("Córners"))
        assertTrue(labels.contains("Tarjetas rojas"))
    }

    private fun event(scoreHome: Int? = null, scoreAway: Int? = null, detail: String = "") =
        GeeSportsSource.GeeEvent(
            id = "e",
            category = "Football",
            name = "Guadalajara vs Tigres UANL",
            logo = "",
            teamA = "",
            teamB = "",
            dateIso = "2026-10-17",
            dateText = "",
            time = "17:07:00",
            alive = 2,
            total = 2,
            links = emptyList(),
            scoreHome = scoreHome,
            scoreAway = scoreAway,
            detail = detail,
        )

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
