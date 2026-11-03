package com.chronosbvrx.visionplus.ui.sports

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsRugby
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.ui.graphics.vector.ImageVector

/** Icono alusivo al deporte de la tarjeta (fútbol, básquet, motor…). */
internal fun geeSportIcon(category: String): ImageVector = when (category.trim().lowercase()) {
    "football" -> Icons.Default.SportsSoccer
    "basketball" -> Icons.Default.SportsBasketball
    "american football" -> Icons.Default.SportsFootball
    "baseball" -> Icons.Default.SportsBaseball
    "hockey" -> Icons.Default.SportsHockey
    "motorsport", "motorsports" -> Icons.Default.SportsMotorsports
    "tennis" -> Icons.Default.SportsTennis
    "fighting", "pelea", "peleas", "mma", "mixed martial arts" -> Icons.Default.SportsMma
    "boxing", "boxeo" -> Icons.Default.SportsMma
    "golf" -> Icons.Default.SportsGolf
    "volleyball" -> Icons.Default.SportsVolleyball
    "handball" -> Icons.Default.SportsHandball
    "rugby" -> Icons.Default.SportsRugby
    "cricket" -> Icons.Default.SportsCricket
    else -> Icons.Default.Sports
}
