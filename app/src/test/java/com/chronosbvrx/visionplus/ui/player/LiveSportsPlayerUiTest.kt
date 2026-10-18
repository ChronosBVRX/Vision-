package com.chronosbvrx.visionplus.ui.player

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
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
    fun theLivePlayerShowsTheCableBannerAndTheLabelledPills() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        // El aviso de cable, con las mismas palabras que la TV en vivo.
        assertTrue("el aviso de cable existe", player.contains("LiveCableBanner("))
        assertTrue("la píldora EN VIVO", player.contains("\"EN VIVO\""))
        assertTrue("la línea AHORA", player.contains("\"AHORA:\""))
        assertTrue("la línea del marcador", player.contains("\"MARCADOR:\""))
        assertTrue("dónde se transmite", player.contains("\"SE TRANSMITE EN:\""))
        // Las píldoras, con las etiquetas de la TV en vivo.
        assertTrue("píldoras etiquetadas", player.contains("PlayerPill("))
        assertTrue("Canales", player.contains("label = \"Canales\""))
        assertTrue("Información", player.contains("label = \"Información\""))
        assertTrue("Audio y subtítulos", player.contains("label = \"Audio y subtítulos\""))
        assertTrue("Pausa", player.contains("if (playing) \"Pausa\" else \"Reproducir\""))
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
    fun theSportsDialogHandsTheMatchContextAndTheBackstopToThePlayer() {
        val mobile = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/GeeSportsTabs.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvGeeSportsTabs.kt")
        for ((name, text) in listOf("móvil" to mobile, "TV" to tv)) {
            assertTrue("$name: arma el contexto", text.contains("eventInfo: GeeZapSignals.EventInfo? = null"))
            assertTrue("$name: lo entrega al reproductor", text.contains("GeeZapSignals.arm(picked, zapOrder, eventInfo)"))
            assertTrue("$name: respaldo automático al tocar una caída", text.contains("GeeBackstop.forGuide(backstops, guideName)"))
            assertTrue("$name: lo dice en la fila", text.contains("respaldo automático"))
            // La lista enseña SOLO lo de GeeSports: ningún respaldo pintado.
            assertFalse("$name: sin sección de respaldos", text.contains("Respaldo en tu lista"))
            assertFalse("$name: sin filas de respaldo", text.contains("Backstop.Backstop,") || text.contains("back.channel.name"))
        }
        val screenMobile = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/sports/SportsScreen.kt")
        val screenTv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSportsScreen.kt")
        for ((name, text) in listOf("móvil" to screenMobile, "TV" to screenTv)) {
            assertTrue("$name: el respaldo viaja al reproductor", text.contains("fallback = pickingBackstops.firstOrNull()?.channel"))
        }
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
