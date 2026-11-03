package com.chronosbvrx.visionplus.ui.games

import android.content.Context
import com.chronosbvrx.visionplus.data.games.NarratorAudio
import kotlinx.coroutines.flow.StateFlow

/**
 * Voz de Vision+ Fiesta. Cada juego tiene su presentador y cada momento tiene
 * cinco tomas generadas con Qwen3-TTS. El servidor manda el nombre exacto en
 * `hostCue.audio`; si no llega, se usa `fiesta_<juego>_<momento>_<variante>`.
 *
 * No hay constantes R.raw por clip: los nombres se resuelven contra la tabla de
 * recursos. Así se pueden regenerar las tomas sin tocar Kotlin.
 */
object FiestaVoice {
    /** La TV observa esto para animar al presentador solo cuando suena su voz. */
    val isHostSpeaking: StateFlow<Boolean> get() = NarratorAudio.isHostSpeaking

    fun play(context: Context, game: String, moment: String, variant: Int = 0, audio: String = "") {
        val candidates = buildList {
            if (audio.isNotBlank()) add(audio)
            add(resourceName(game, moment, variant))
            add("fiesta_${game}_${moment}_0")
            add("fiesta_${game}_${moment}")
        }.distinct()
        val res = candidates.firstNotNullOfOrNull { name ->
            runCatching {
                context.resources.getIdentifier(name, "raw", context.packageName)
            }.getOrDefault(0).takeIf { it != 0 }
        } ?: return
        NarratorAudio.play(context, res)
    }

    /** Nombre canónico de una toma. */
    fun resourceName(game: String, moment: String, variant: Int): String =
        "fiesta_${game}_${moment}_${variant.coerceAtLeast(0)}"
}
