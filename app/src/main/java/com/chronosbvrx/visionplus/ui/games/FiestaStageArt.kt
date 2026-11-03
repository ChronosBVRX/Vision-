package com.chronosbvrx.visionplus.ui.games

import androidx.annotation.DrawableRes
import com.chronosbvrx.visionplus.R

/**
 * Arte de tarjeta del selector de Fiesta (IA, 3:4). Es distinto del plató:
 * muestra al presentador y la utilería del juego en primer plano.
 */
@DrawableRes
internal fun fiestaSelectorArtRes(game: String): Int = when (game) {
    "ultima_palabra" -> R.drawable.game_ultima_palabra_poster
    "vox_populi" -> R.drawable.game_vox_populi_poster
    "fuera_de_epoca" -> R.drawable.game_fuera_de_epoca_poster
    "texto_roto" -> R.drawable.game_texto_roto_poster
    "franela_fest" -> R.drawable.game_franela_fest_poster
    "el_tasador" -> R.drawable.game_el_tasador_poster
    else -> R.drawable.fiesta_lobby_bg
}

/** Fondo único del lobby de selección (IA, 16:9). */
@DrawableRes
internal fun fiestaLobbyRes(): Int = R.drawable.fiesta_lobby_bg

/**
 * Fondos de plató de Vision+ Fiesta. Son ilustraciones originales, oscuras y con
 * el acento de cada juego para que la tele se sienta como un set distinto en cada
 * partida sin competir con el texto ni con el arte principal.
 */
@DrawableRes
internal fun fiestaStageRes(game: String): Int = when (game) {
    "ultima_palabra" -> R.drawable.fiesta_stage_ultima_palabra
    "vox_populi" -> R.drawable.fiesta_stage_vox_populi
    "fuera_de_epoca" -> R.drawable.fiesta_stage_fuera_de_epoca
    "texto_roto" -> R.drawable.fiesta_stage_texto_roto
    "franela_fest" -> R.drawable.fiesta_stage_franela_fest
    "el_tasador" -> R.drawable.fiesta_stage_el_tasador
    else -> R.drawable.fiesta_stage_ultima_palabra
}
