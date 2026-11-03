package com.chronosbvrx.visionplus.ui.games

import com.chronosbvrx.visionplus.data.games.GameCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guardianes de la temporada larga: giros, monedas, tienda, minijuego y plató. */
class FiestaDepthTest {

    private fun source(path: String): String =
        File("src/main/java/com/chronosbvrx/visionplus/$path").readText()

    @Test
    fun theLongGamesAreLongerAndHaveMoreRounds() {
        val longGames = listOf(
            GameCatalog.UltimaPalabra,
            GameCatalog.VoxPopuli,
            GameCatalog.FueraDeEpoca,
            GameCatalog.TextoRoto,
            GameCatalog.FranelaFest,
            GameCatalog.ElTasador,
        )
        longGames.forEach { game ->
            assertTrue("${game.id} debe durar al menos 20 min", game.approxMinutes >= 20)
            assertTrue("${game.id} debe tener al menos 6 rondas", game.rounds >= 6)
        }
    }

    @Test
    fun theServerShipsTwistsCoinsAndTheBonusRound() {
        val server = File("../server/api/fiesta.py").takeIf { it.exists() }
            ?: File("server/api/fiesta.py")
        val text = server.readText()
        assertTrue(text.contains("TWISTS"))
        assertTrue(text.contains("PERKS"))
        assertTrue(text.contains("FIESTA_START_COINS"))
        assertTrue(text.contains("La Ruleta de Fiesta"))
        assertTrue(text.contains("fiesta_bonus"))
        assertTrue(text.contains("fiesta_perk"))
        assertTrue(text.contains("_settle_perks"))
    }

    @Test
    fun theClientParsesTheNewDepthPayload() {
        val models = source("data/games/GameModels.kt")
        assertTrue(models.contains("data class FiestaTwist("))
        assertTrue(models.contains("data class FiestaPerk("))
        assertTrue(models.contains("data class FiestaBonus("))
        assertTrue(models.contains("val twist: FiestaTwist?"))
        assertTrue(models.contains("val bonus: FiestaBonus?"))
        assertTrue(models.contains("val coins: Int"))
    }

    @Test
    fun theTvAndPhoneShowTheNewLayer() {
        val tv = source("ui/games/FiestaTvScreen.kt")
        assertTrue(tv.contains("FiestaTvBonus("))
        assertTrue(tv.contains("FiestaWorldTv("))
        val worlds = source("ui/games/FiestaTvWorlds.kt")
        assertTrue(worlds.contains("info.banter"))
        assertTrue(worlds.contains("twist"))
        assertTrue(worlds.contains("FiestaTauntCloud"))
        val phone = source("ui/games/FiestaPhoneWorlds.kt")
        assertTrue(phone.contains("FiestaWorldPhone("))
        assertTrue(phone.contains("PhoneReveal("))
        assertTrue(phone.contains("fiesta_bonus") || phone.contains("fiesta_perk"))
        assertTrue(phone.contains("fiesta_bet"))
        assertTrue(phone.contains("fiesta_hint"))
        assertTrue(phone.contains("fiesta_taunt"))
    }

    @Test
    fun everyGameHasItsOwnStageArt() {
        val art = source("ui/games/FiestaStageArt.kt")
        val drawable = File("src/main/res/drawable-nodpi")
        GameCatalog.all.forEach { game ->
            assertTrue("falta el mapeo de ${game.id}", art.contains("\"${game.id}\""))
            val file = File(drawable, "fiesta_stage_${game.id}.webp")
            assertTrue("falta ${file.name}", file.isFile)
            assertTrue("${file.name} debe pesar más de 20 KB", file.length() > 20_000)
            val poster = File(drawable, "game_${game.id}_poster.webp")
            assertTrue("falta ${poster.name}", poster.isFile)
            assertTrue("${poster.name} debe pesar más de 40 KB", poster.length() > 40_000)
        }
    }

    @Test
    fun theNarratorNoLongerTouchesPlaybackParamsOnStart() {
        val audio = source("data/games/NarratorAudio.kt")
        assertFalse(audio.contains("setPlaybackParams("))
        assertTrue(audio.contains("No se toca `playbackParams`"))
    }

    @Test
    fun theGameRailKeepsEveryCardInsideItsLane() {
        val screen = source("ui/games/TvGamesScreen.kt")
        assertTrue(screen.contains("clipToBounds()"))
        assertTrue(screen.contains("contentPadding = PaddingValues(horizontal = 56.dp)"))
        assertFalse("el escalado de foco ya no desfasa las tarjetas", screen.contains("if (focused) 1.02f"))
    }

    @Test
    fun theLiveStatsPanelStaysCompactAndTheFullCardScrolls() {
        val player = source("ui/player/PlayerScreen.kt")
        assertTrue(player.contains("heightIn(max = panelMaxHeight)"))
        assertTrue(player.contains("keys.take(3)"))
        assertTrue(player.contains("GeeMatchInfoDialog("))
        assertTrue(player.contains("initialPage = liveInfoPage"))
        val dialog = source("ui/player/PlayerDialogs.kt")
        assertTrue(dialog.contains("StatsPage("))
        assertTrue(dialog.contains("LineupsPage("))
        assertTrue(dialog.contains("\"Alineaciones\""))
    }

    @Test
    fun everyGameHasFiveVoiceTakesPerMomentAndItsOwnMusic() {
        val moments = mapOf(
            "ultima_palabra" to listOf("lobby", "start", "write", "vote", "reveal", "podium"),
            "vox_populi" to listOf("lobby", "start", "guess", "reveal", "podium"),
            "fuera_de_epoca" to listOf("lobby", "start", "guess", "reveal", "podium"),
            "texto_roto" to listOf("lobby", "start", "write", "vote", "reveal", "podium"),
            "franela_fest" to listOf("lobby", "start", "create", "vote", "reveal", "podium"),
            "el_tasador" to listOf("lobby", "start", "pitch", "vote", "reveal", "podium"),
        )
        val raw = File("src/main/res/raw")
        moments.forEach { (game, labels) ->
            labels.forEach { moment ->
                for (variant in 0..4) {
                    val file = File(raw, "fiesta_${game}_${moment}_$variant.m4a")
                    assertTrue("falta la toma ${file.name}", file.isFile)
                    assertTrue("${file.name} está vacía", file.length() > 2_000)
                }
            }
            assertTrue("falta la música de $game", File(raw, "fiesta_music_$game.m4a").isFile)
            assertTrue("falta el sting de $game", File(raw, "fiesta_sting_$game.m4a").isFile)
        }
    }

    @Test
    fun theSixWorldsHaveTheirOwnShellAndArt() {
        val design = source("ui/games/FiestaDesign.kt")
        assertTrue(design.contains("enum class FiestaShell"))
        assertTrue(design.contains("FiestaShell.Stage"))
        assertTrue(design.contains("FiestaShell.Newsroom"))
        assertTrue(design.contains("FiestaShell.Timelab"))
        assertTrue(design.contains("FiestaShell.Chat"))
        assertTrue(design.contains("FiestaShell.Runway"))
        assertTrue(design.contains("FiestaShell.Auction"))
        assertTrue(design.contains("FiestaLobbyBackdrop"))
        val drawable = File("src/main/res/drawable-nodpi")
        assertTrue("falta el fondo IA del lobby", File(drawable, "fiesta_lobby_bg.webp").isFile)
        GameCatalog.all.forEach { game ->
            assertTrue("falta el teléfono de ${game.id}", File(drawable, "fiesta_phone_${game.id}.webp").isFile)
            assertTrue("falta el presentador de ${game.id}", File(drawable, "fiesta_host_${game.id}.webp").isFile)
            assertTrue("falta el póster de ${game.id}", File(drawable, "game_${game.id}_poster.webp").isFile)
        }
    }

    @Test
    fun theSelectorUsesAiKeyArtAndTheLobbyBackdrop() {
        val tv = source("ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("FiestaLobbyBackdrop"))
        assertTrue(tv.contains("fiestaSelectorArtRes("))
        val phone = source("ui/games/GamesHomeScreen.kt")
        assertTrue(phone.contains("FiestaLobbyBackdrop"))
        val art = source("ui/games/FiestaStageArt.kt")
        assertTrue(art.contains("fiestaSelectorArtRes"))
        assertTrue(art.contains("fiestaLobbyRes"))
        assertTrue(art.contains("R.drawable.fiesta_lobby_bg"))
    }

}
