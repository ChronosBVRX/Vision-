package com.chronosbvrx.visionplus.ui.games

import androidx.annotation.DrawableRes
import com.chronosbvrx.visionplus.R

/** Arte de las tarjetas del selector (generado con ComfyUI local, 16:9). */
@DrawableRes
internal fun gameArtRes(id: String): Int = when (id) {
    "impostor" -> R.drawable.game_impostor_art
    "dibujo" -> R.drawable.game_dibujo_art
    "subasta" -> R.drawable.game_subasta_art
    "fibbage" -> R.drawable.game_fibbage_art
    "ultima_palabra" -> R.drawable.game_ultima_palabra_art
    "vox_populi" -> R.drawable.game_vox_populi_art
    "fuera_de_epoca" -> R.drawable.game_fuera_de_epoca_art
    "texto_roto" -> R.drawable.game_texto_roto_art
    "franela_fest" -> R.drawable.game_franela_fest_art
    "el_tasador" -> R.drawable.game_el_tasador_art
    else -> R.drawable.game_trivia_art
}

/** Tablero de 3 pasos del tutorial v2 (1280x720). */
@DrawableRes
internal fun tutorialBoardRes(id: String): Int = when (id) {
    "impostor" -> R.drawable.game_tut_impostor
    "dibujo" -> R.drawable.game_tut_drawing
    "subasta" -> R.drawable.game_tut_auction
    "fibbage" -> R.drawable.game_tut_fibbage
    else -> R.drawable.game_tut_trivia
}
