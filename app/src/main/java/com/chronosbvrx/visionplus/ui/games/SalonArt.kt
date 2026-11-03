package com.chronosbvrx.visionplus.ui.games

import androidx.annotation.DrawableRes
import com.chronosbvrx.visionplus.R

/** Assets del "salón" de Fase 4. Todos son WebP generados localmente. */
@DrawableRes
internal fun auctionCardArtRes(effect: String?, isMystery: Boolean): Int = when {
    isMystery -> R.drawable.game_auc_card_mystery
    effect == "points" -> R.drawable.game_auc_card_points
    effect == "coins" -> R.drawable.game_auc_card_coins
    effect == "steal" -> R.drawable.game_auc_card_steal
    effect == "double_next" || effect == "shield" -> R.drawable.game_auc_card_special
    else -> R.drawable.game_auc_card_mystery
}

@DrawableRes
internal fun medalIconRes(medalId: String): Int = when (medalId) {
    "liar" -> R.drawable.game_medal_liar
    "detective" -> R.drawable.game_medal_detective
    "shark" -> R.drawable.game_medal_shark
    "chameleon" -> R.drawable.game_medal_chameleon
    else -> R.drawable.game_medal_clueless
}

@DrawableRes
internal fun tvModeBackgroundRes(gameId: String): Int = when (gameId) {
    "impostor" -> R.drawable.game_bg_tv_noir_red
    "subasta" -> R.drawable.game_bg_tv_noir_gold
    else -> R.drawable.game_bg_tv_noir_cyan
}
