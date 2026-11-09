package com.chronosbvrx.visionplus.ui.games

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.chronosbvrx.visionplus.R
import kotlin.random.Random

private val PARTY_WELCOME_TRACKS = intArrayOf(
    R.raw.party_welcome_0,
    R.raw.party_welcome_1,
    R.raw.party_welcome_2,
    R.raw.party_welcome_3,
    R.raw.party_welcome_4,
    R.raw.party_welcome_5,
    R.raw.party_welcome_6,
    R.raw.party_welcome_7,
    R.raw.party_welcome_8,
    R.raw.party_welcome_9,
)

/**
 * Bienvenida hablada de la selección de juegos.
 *
 * Se elige una toma distinta a la última que escuchó el equipo, para que la
 * entrada a Juegos no se sienta repetida. Son las tomas F1 de la selección y
 * viven en el APK para que la transición no dependa de la red; se liberan en
 * cuanto la pantalla sale de composición.
 */
@Composable
fun PartyWelcomeNarrator() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("vision_party_audio", Context.MODE_PRIVATE)
        val last = prefs.getInt("welcome_track", -1)
        val candidates = PARTY_WELCOME_TRACKS.indices
            .filter { it != last }
            .ifEmpty { PARTY_WELCOME_TRACKS.indices.toList() }
        val index = candidates[Random.nextInt(candidates.size)]
        prefs.edit().putInt("welcome_track", index).apply()

        val player = playPartyAudio(context, PARTY_WELCOME_TRACKS[index])
        onDispose { stopPartyAudio(player) }
    }
}
