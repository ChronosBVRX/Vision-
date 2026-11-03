package com.chronosbvrx.visionplus.home.ui

import androidx.annotation.DrawableRes
import com.chronosbvrx.visionplus.home.R
import com.chronosbvrx.visionplus.home.data.HeroSlide
import java.util.Locale

/**
 * Arte de referencia para un deporte cuando la agenda no trae una foto propia. Los clubes
 * siguen mostrando sus escudos reales; esta imagen solo llena el fondo para que un MotoGP,
 * una pelea o un partido sin portada no se queden en un degradado vacío.
 *
 * Son fotos de Unsplash empaquetadas en el APK: cargan al instante y funcionan sin red.
 */
@DrawableRes
internal fun sportBackdropForText(raw: String): Int {
    val text = raw.lowercase(Locale.ROOT)
    return when {
        text.hasAny(
            "moto", "motor", "motogp", "f1", "formula", "formula 1", "nascar", "indy",
            "rally", "wrc", "superbike", "velocidad",
        ) -> R.drawable.sport_motor

        text.hasAny("americano", "nfl", "super bowl", "superbowl", "football") -> R.drawable.sport_american_football

        text.hasAny(
            "futbol", "fútbol", "soccer", "liga mx", "liga", "champions", "premier",
            "laliga", "la liga", "serie a", "bundesliga", "libertadores", "sudamericana",
            "mls", "copa", "mundial", "eurocopa",
        ) -> R.drawable.sport_soccer

        text.hasAny("basket", "básquet", "basquet", "nba", "euroleague", "acb") ->
            R.drawable.sport_basketball

        text.hasAny("tenis", "tennis", "atp", "wta", "wimbledon", "roland garros") ->
            R.drawable.sport_tennis

        text.hasAny("beisbol", "béisbol", "baseball", "mlb", "serie mundial") ->
            R.drawable.sport_baseball

        text.hasAny("box", "boxeo", "pelea", "mma", "ufc", "combate") -> R.drawable.sport_boxing

        text.hasAny("voleibol", "volley", "voley", "volei") -> R.drawable.sport_volleyball

        text.hasAny("golf", "pga", "european tour") -> R.drawable.sport_golf

        text.hasAny("hockey", "nhl", "patinaje") -> R.drawable.sport_hockey

        else -> R.drawable.sport_default
    }
}

internal fun sportBackdropFor(slide: HeroSlide): Int =
    sportBackdropForText(
        listOf(slide.title, slide.league, slide.subtitle, slide.eyebrow)
            .filter { it.isNotBlank() }
            .joinToString(" "),
    )

private fun String.hasAny(vararg needles: String): Boolean = needles.any { it in this }
