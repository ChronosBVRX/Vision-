package com.chronosbvrx.visionplus

import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.showsFloatingClock
import com.chronosbvrx.visionplus.ui.tv.tvTopBarVisible
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guardianes del cableado de Vision+ Play: pestaña, rutas, deep link, assets y aislamiento. */
class GameWiringTest {

    private fun source(path: String): String =
        File("src/main/java/com/chronosbvrx/visionplus/$path").readText()

    @Test
    fun theGamesTabSitsAfterSportsAndBeforeTheLibrary() {
        val phone = source("ui/PhoneApp.kt")
        val sports = phone.indexOf("Tab(Route.Sports")
        val games = phone.indexOf("Tab(Route.Games")
        val library = phone.indexOf("Tab(Route.Library")
        assertTrue("falta la pestaña Juegos", games > 0)
        assertTrue(sports in 0 until games)
        assertTrue(games < library)
        assertTrue(phone.contains("Tab(Route.Games, \"Juegos\""))
    }

    @Test
    fun routesExistForTheHomeAndTheRoom() {
        val nav = source("ui/Navigation.kt")
        assertTrue(nav.contains("data object Games : Route"))
        assertTrue(nav.contains("data class GameRoom(val code: String) : Route"))
    }

    @Test
    fun theManifestAcceptsTheJoinDeepLink() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:host=\"play\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/join\""))
        assertTrue(manifest.contains("android:scheme=\"visionplus\""))
    }

    @Test
    fun theDeepLinkEntersTheRoomOnlyOnPhones() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("GameDeepLink.joinCode(uri)"))
        assertTrue(main.contains("Route.GameRoom(it)"))
        assertTrue(main.contains("if (isTv) return null"))
    }

    @Test
    fun theKillSwitchDefaultsToOn() {
        val home = source("ui/games/GamesHomeScreen.kt")
        assertTrue(home.contains("RemoteConfig.flag(context, \"games_enabled\", true)"))
        assertTrue(home.contains("Los juegos no están disponibles por ahora"))
    }

    @Test
    fun theGamesModuleDoesNotTouchTheEngineOrTheCatalog() {
        val forbidden = listOf(
            "data.xuper", "data.offline", "XuperCatalog", "X4KPlayerAty",
            ":x4k", "Popularity", "/v1/index", "RemoteIndexSource.films",
        )
        val roots = listOf("data/games", "ui/games")
        val files = roots.flatMap { root ->
            File("src/main/java/com/chronosbvrx/visionplus/$root").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        }
        assertTrue("no se encontraron fuentes de juegos", files.size >= 8)
        files.forEach { file ->
            val text = file.readText()
            forbidden.forEach { token ->
                assertFalse("${file.name} toca $token", text.contains(token))
            }
        }
    }

    @Test
    fun theClientUsesTheApiAndGameTokens() {
        val client = source("data/games/GameClient.kt")
        assertTrue(client.contains("\"X-Api-Key\""))
        assertTrue(client.contains("\"X-Game-Player\""))
        assertTrue(client.contains("\"X-Game-Host\""))
        assertTrue(client.contains("GameCodes.normalize"))
        assertFalse(client.contains("X-Api-Key\", \"dev\""))
    }

    @Test
    fun theTvHasItsOwnGamesDisplay() {
        val tv = source("ui/TvApp.kt")
        assertTrue(tv.contains("TvRailSpec(\"Juegos\""))
        // Vision+ Play está deshabilitado: la pestaña muestra «Próximamente», no el tablero.
        assertTrue(tv.contains("Route.Games, is Route.GameRoom -> GamesComingSoonScreen"))
        assertFalse(tv.contains("Route.Games -> TvGamesScreen"))
        assertTrue(source("ui/games/GamesComingSoon.kt").contains("Próximamente"))
        val screen = source("ui/games/TvGamesScreen.kt")
        assertTrue(screen.contains("mode = \"display\""))
        assertTrue(screen.contains("GameClient.state(code, since, \"\", \"display\", hostTokenForDisplay)"))
        assertTrue(screen.contains("syncCode = "))
        assertTrue(screen.contains(".ensureCode(context)"))
        assertFalse("la tele no inicia: el host es el primer teléfono", screen.contains("GameClient.start("))
        assertTrue(screen.contains("El primero que entre será el anfitrión"))
        assertTrue(screen.contains("GameQr"))
    }

    @Test
    fun thePhoneScansTheQrAndTheManifestAllowsTheCamera() {
        val home = source("ui/games/GamesHomeScreen.kt")
        assertTrue(home.contains("ScanContract"))
        assertTrue(home.contains("Escanear QR de la tele"))
        assertTrue(home.contains("GameDeepLink.codeFromScan"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.CAMERA"))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("zxing-android-embedded"))
    }

    @Test
    fun theQrUsesTheJoinDeepLink() {
        val qr = source("ui/games/GameQr.kt")
        assertTrue(qr.contains("QRCodeWriter"))
        assertTrue(qr.contains("visionplus://play/join?code="))
        assertTrue(qr.contains("ErrorCorrectionLevel.H"))
    }

    @Test
    fun theNarratorAssetsAreBundled() {
        val drawable = File("src/main/res/drawable-nodpi")
        (0..3).forEach { index ->
            val frame = File(drawable, "narrator_idle_$index.png")
            assertTrue(frame.isFile)
            assertTrue(frame.length() > 10_000)
        }
        assertTrue(File(drawable, "narrator_avatar.png").isFile)
        assertTrue(File(drawable, "narrator_static.png").isFile)
        listOf("welcome", "thinking", "happy", "oops", "celebrate").forEach { mood ->
            assertTrue("falta narrator_$mood.png", File(drawable, "narrator_$mood.png").isFile)
        }
        listOf(
            "trivia", "impostor", "dibujo", "subasta", "fibbage",
            "ultima_palabra", "vox_populi", "fuera_de_epoca", "texto_roto",
            "franela_fest", "el_tasador",
        ).forEach { game ->
            assertTrue("falta game_${game}_art.webp", File(drawable, "game_${game}_art.webp").isFile)
        }
        listOf(
            "game_tut_trivia", "game_tut_impostor", "game_tut_drawing",
            "game_tut_auction", "game_tut_fibbage",
        ).forEach { asset ->
            assertTrue("falta $asset.webp", File(drawable, "$asset.webp").isFile)
        }
        listOf(
            "game_auc_card_points", "game_auc_card_coins", "game_auc_card_steal",
            "game_auc_card_mystery", "game_auc_card_special",
            "game_imp_role_crew", "game_imp_role_spy",
            "game_medal_liar", "game_medal_detective", "game_medal_shark",
            "game_medal_chameleon", "game_medal_clueless",
            "game_bg_tv_noir_red", "game_bg_tv_noir_gold", "game_bg_tv_noir_cyan",
        ).forEach { asset ->
            assertTrue("falta $asset.webp", File(drawable, "$asset.webp").isFile)
        }
        val audio = listOf(
            "welcome", "create", "join", "start", "correct",
            "wrong", "time", "reveal", "podium", "solo",
            "selector", "charada_turn", "charada_correct", "charada_pass",
            "charada_reveal", "wager_start", "wager_clue", "wager_reveal",
            "wager_win", "wager_lose",
        )
        audio.forEach { name ->
            (0 until 10).forEach { n ->
                val file = File("src/main/res/raw/narrator_${name}_$n.m4a")
                assertTrue("falta narrator_${name}_$n.m4a", file.isFile)
                assertTrue(file.length() > 5_000)
            }
        }
        val partyAudio = listOf(
            "impostor_secret", "impostor_vote", "impostor_caught", "draw_start",
            "draw_vote", "auction_start", "auction_reveal", "fibbage_write", "fibbage_vote",
            "impostor_questions", "impostor_escaped", "party_timeout", "auction_tie",
            "lie_sweep", "nobody_guessed", "final_round_double",
        )
        partyAudio.forEach { name ->
            (0 until 3).forEach { n ->
                val file = File("src/main/res/raw/narrator_${name}_$n.m4a")
                assertTrue("falta narrator_${name}_$n.m4a", file.isFile)
                assertTrue(file.length() > 5_000)
            }
        }
        listOf(
            "tick", "vote", "secret", "reveal", "hammer", "whoosh",
            "buzzer", "glass", "heartbeat", "safe", "fanfare",
        ).forEach { name ->
            val file = File("src/main/res/raw/game_sfx_$name.wav")
            assertTrue("falta game_sfx_$name.wav", file.isFile)
            assertTrue(file.length() > 5_000)
        }
        val ambient = File("src/main/res/raw/game_ambient.mp3")
        assertTrue("falta game_ambient.mp3", ambient.isFile)
        assertTrue(ambient.length() > 100_000)
    }

    @Test
    fun theV2NarratorVoicesAreBundled() {
        val ids = listOf(
            "tutorial_trivia_v2", "tutorial_wager_v2", "tutorial_charada_v2",
            "tutorial_impostor_v2", "tutorial_drawing_v2", "tutorial_auction_v2",
            "tutorial_fibbage_v2", "trivia_attack_window", "trivia_punishment_wires",
            "wager_side_bet_alert", "charada_director_pick", "charada_buzz_pause",
            "charada_saboteur_win", "impostor_coartada_start", "drawing_ink_limit",
            "drawing_flashlight_round", "auction_cursed_lot", "auction_ghost_alarm",
            "auction_shield_reflect", "fibbage_poison_hit",
        )
        ids.forEach { id ->
            (0 until 3).forEach { n ->
                val file = File("src/main/res/raw/narrator_${id}_$n.m4a")
                assertTrue("falta narrator_${id}_$n.m4a", file.isFile)
                assertTrue(file.length() > 5_000)
            }
        }
    }

    @Test
    fun theAmbientMusicLivesOnTheTvUnderTheVoice() {
        val audio = source("data/games/NarratorAudio.kt")
        assertTrue(audio.contains("object GameAmbient"))
        assertTrue(audio.contains("isLooping = true"))
        assertTrue(audio.contains("GameAmbient.duck()"))
        assertTrue(audio.contains("GameAmbient.restore()"))
        val tv = source("ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("GameAmbient.start(context)"))
        assertTrue(tv.contains("GameAmbient.stop()"))
        val phone = source("ui/games/GamesHomeScreen.kt")
        assertFalse(phone.contains("GameAmbient"))
    }

    @Test
    fun theTvCreatesTheRoomAndThePhonesDriveThePace() {
        val home = source("ui/games/GamesHomeScreen.kt")
        assertTrue(home.contains("ScanContract"))
        // Iniciar sala es exclusivo de la tele; el teléfono solo se une.
        assertFalse(home.contains("GameClient.create("))
        assertTrue(home.contains("La sala se crea desde la tele"))
        val tv = source("ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("mode = \"display\""))
        assertTrue(tv.contains("hostName = \"Pantalla\""))
        val room = source("ui/games/GameRoomScreen.kt")
        assertTrue(room.contains("GameClient.state(normalized"))
        assertTrue(room.contains("GameClient.answer("))
        assertTrue(room.contains("GameClient.ready("))
        assertTrue(room.contains("GameClient.next("))
        assertTrue(room.contains("snap.allReady"))
    }

    @Test
    fun theNarratorSoundsOnTheTvNotOnPhones() {
        val audio = source("data/games/NarratorAudio.kt")
        assertTrue(audio.contains("DeviceKind.isTelevision(context)"))
        val view = source("ui/games/GameNarratorView.kt")
        assertTrue(view.contains("fun rememberNarratorVariant"))
        assertTrue(view.contains("fun playNarratorCue"))
        assertTrue(view.contains("fun rememberNarratorText"))
        val tv = source("ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("rememberNarratorVariant"))
        assertTrue(tv.contains("playNarratorCue"))
        assertTrue(tv.contains("rememberNarratorText"))
        assertTrue(tv.contains("NarratorEvent.Selector"))
        val phone = source("ui/games/GamesHomeScreen.kt")
        assertFalse(phone.contains("NarratorAudio.play"))
        assertFalse(phone.contains("rememberNarratorVariant"))
        assertTrue(phone.contains("suenan en la tele, no en los teléfonos"))
        val room = source("ui/games/GameRoomScreen.kt")
        assertTrue(room.contains("rememberNarratorText"))
        assertTrue(room.contains("playNarratorCue"))
    }

    @Test
    fun voiceAndBubbleShareOneVariantPerMoment() {
        val narrator = source("data/games/GameNarrator.kt")
        assertTrue(narrator.contains("fun cue("))
        assertTrue(narrator.contains("fun pickVariant("))
        assertTrue(narrator.contains("fun variantId("))
        assertTrue(narrator.contains("fun variantsFor("))
        // Sin llamadas directas a audios únicos: todo pasa por variantes.
        assertFalse(narrator.contains("fun audioResFor("))
        assertFalse(narrator.contains("fun lineFor("))
        val tv = source("ui/games/TvGamesScreen.kt")
        assertFalse(tv.contains("audioResFor"))
        val room = source("ui/games/GameRoomScreen.kt")
        assertFalse(room.contains("audioResFor"))
    }

    @Test
    fun theGameModeIsImmersiveAndThePhoneOnlyJoins() {
        assertFalse(tvTopBarVisible(Route.Games))
        assertFalse(showsFloatingClock(isTv = true, route = Route.Games))
        assertFalse(showsFloatingClock(isTv = true, route = Route.GameRoom("ABC234")))
        val tvBar = source("ui/tv/TvTopBar.kt")
        assertFalse(tvBar.contains("route == Route.Games ||"))
        val phone = source("ui/PhoneApp.kt")
        assertTrue(phone.contains("route == Route.Games || route is Route.GameRoom"))
        assertTrue(phone.contains("Route.Games, is Route.GameRoom -> GamesComingSoonScreen"))
        val home = source("ui/games/GamesHomeScreen.kt")
        assertTrue(home.contains("La sala se crea desde la tele"))
        assertTrue(home.contains("GameClient.join("))
        assertTrue(home.contains("GameFullscreenEffect()"))
        val room = source("ui/games/GameRoomScreen.kt")
        assertTrue(room.contains("GameFullscreenEffect()"))
        assertTrue(room.contains("CharadaContent("))
        assertTrue(room.contains("WagerContent("))
        val tv = source("ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("TvGameSelector("))
        assertTrue(tv.contains("TvTriviaPackPicker("))
        assertTrue(tv.contains("TvCharada("))
        assertTrue(tv.contains("TvWager("))
    }
}
