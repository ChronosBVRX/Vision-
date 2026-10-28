package com.chronosbvrx.visionplus.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * El vocabulario de iconos de Vision+ — la fuente única de la casa (Material Symbols Rounded).
 *
 * Reglas del lote UI Premium:
 *  - Cero emojis en textos de interfaz; un icono de este mapa los sustituye.
 *  - Mismo icono = mismo significado en móvil, TV y el reproductor.
 *  - Los tintes viven en `theme/Theme.kt` (VisionBlueBright activo, VisionTextSecondary en
 *    reposo, VisionRatingGold para calificación, VisionSuccess para visto/descargado).
 *  - El reproductor nativo (proceso :x4k, Views) usa los gemelos vectoriales
 *    `res/drawable/ic_vp_*.xml` con la misma forma.
 */
object VisionIcons {
    /** Filas del Inicio. */
    val Novelties = Icons.Rounded.AutoAwesome
    val WeeklyTrending = Icons.Rounded.TrendingUp
    val ContinueWatching = Icons.Rounded.PlayCircleOutline
    val Collections = Icons.Rounded.Collections

    /** Tipos del catálogo (coherentes con el riel y las selecciones). */
    val Movies = Icons.Rounded.Movie
    val Series = Icons.Rounded.Theaters
    val Anime = Icons.Rounded.Animation
    val Kids = Icons.Rounded.ChildCare

    /** Acciones de ficha. */
    val Rating = Icons.Rounded.Star
    val Favorite = Icons.Rounded.Star
    val FavoriteBorder = Icons.Rounded.StarBorder
    val WatchLater = Icons.Rounded.BookmarkAdd
    val WatchLaterOn = Icons.Rounded.Bookmark
    val Follow = Icons.Rounded.Notifications
    val Following = Icons.Rounded.NotificationsActive
    val Download = Icons.Rounded.Download
    val Downloaded = Icons.Rounded.DownloadDone
    val Usb = Icons.Rounded.Usb

    /** Estados y navegación. */
    val Play = Icons.Rounded.PlayArrow
    val Check = Icons.Rounded.Check
    val CheckCircle = Icons.Rounded.CheckCircle
    val Unwatched = Icons.Rounded.RadioButtonUnchecked
    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val SeeAll = Icons.Rounded.ChevronRight
    val ChevronLeft = Icons.Rounded.ChevronLeft
    val ExpandMore = Icons.Rounded.ExpandMore
    val Close = Icons.Rounded.Close
    val Replay = Icons.Rounded.Replay

    /** Búsqueda y mundo. */
    val Search = Icons.Rounded.Search
    val SearchOff = Icons.Rounded.SearchOff
    val Globe = Icons.Rounded.Public
}

/** El icono del encabezado de una fila del Inicio según su id (null = sin icono). */
fun homeRowIcon(rowId: String): ImageVector? = when (rowId) {
    "home-estrenos" -> VisionIcons.Novelties
    "home-trending" -> VisionIcons.WeeklyTrending
    "home-because" -> VisionIcons.ContinueWatching
    "home-follow" -> VisionIcons.Following
    "home-kind-movie" -> VisionIcons.Movies
    "home-kind-series" -> VisionIcons.Series
    "home-kind-anime" -> VisionIcons.Anime
    "home-kind-kids" -> VisionIcons.Kids
    else -> if (rowId.startsWith("tema-")) VisionIcons.Collections else null
}
