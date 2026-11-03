package com.chronosbvrx.visionplus

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los juegos nuevos no deben volver a la narradora vieja en lobby, conteo ni podio. */
class FiestaDialogueWiringTest {
    private fun source(path: String) = File(path).readText()

    @Test
    fun lobbyCountdownAndPodiumUseTheFiestaCast() {
        val tv = source("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("FiestaHostAvatar"))
        assertTrue(tv.contains("FiestaHostBubble"))
        assertTrue(tv.contains("FiestaVoice.play(context, createdGame, \"lobby\")"))
        assertTrue(tv.contains("FiestaVoice.play(context, snapshot.game, \"start\")"))
        assertTrue(tv.contains("FiestaVoice.play(podiumContext, snapshot.game, \"podium\")"))

        val phone = source("src/main/java/com/chronosbvrx/visionplus/ui/games/GameRoomScreen.kt")
        assertTrue(phone.contains("FiestaHostBubble"))
        assertTrue(phone.contains("if (snap.isFiesta)"))
        assertTrue(phone.contains("if (snap.isFiesta) return@LaunchedEffect"))

        val voice = source("src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaVoice.kt")
        assertTrue(voice.contains("fiesta_"))
        assertTrue(voice.contains("getIdentifier"))
    }

    @Test
    fun theServerKeepsTextAndVoiceOnTheSameTake() {
        val server = source("../server/api/fiesta.py")
        assertTrue(server.contains("variant = rng.randrange(len(lines))"))
        assertTrue(server.contains("\"audio\": f\"fiesta_{self.game}_{moment}_{variant}\""))
        assertTrue(server.contains("\"lobby\": \"lobby\""))
        assertTrue(server.contains("\"countdown\": \"start\""))
    }
}
