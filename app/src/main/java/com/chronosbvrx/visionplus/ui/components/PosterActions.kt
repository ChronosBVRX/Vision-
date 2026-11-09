@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.chronosbvrx.visionplus.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.isEpisodeEntry
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.nextEpisodeEntry
import com.chronosbvrx.visionplus.data.resumeSeriesFilm
import com.chronosbvrx.visionplus.ui.tv.FocusRescue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuánto hay que mantener OK para que salga el menú contextual de una tarjeta. */
internal const val POSTER_LONG_PRESS_MS = 550L

/**
 * El mismo candado que usa Vision+ Home para el OK largo, aplicado al menú de pósters.
 *
 * El KeyUp de la pulsación que abrió el menú NO debe activar su primera acción, aunque la
 * persona siga apretando OK mientras lee las opciones. El candado no vence por tiempo: come
 * todos los KeyUp hasta que llega un OK nuevo y deliberado (KeyDown con `repeatCount == 0`
 * después de haber visto el KeyUp de la pulsación que abrió el menú). Las repeticiones del
 * mando (`repeatCount > 0`) nunca lo desarman.
 */
internal class PressReleaseGuard {
    private var armed = false

    fun expectRelease() {
        armed = true
    }

    fun consumeRelease(): Boolean = armed

    /**
     * Un KeyDown con `repeatCount == 0` es una pulsación NUEVA: para que el sistema entregue
     * otro "down", la anterior ya se soltó (aunque su KeyUp se haya perdido al abrirse la
     * ventana del diálogo). Las repeticiones del mando llegan con `repeatCount > 0` y no
     * desarman el candado.
     */
    fun noteKeyDown(repeatCount: Int) {
        if (armed && repeatCount == 0) armed = false
    }
}

internal object TvPressGuard {
    private val guard = PressReleaseGuard()

    fun expectKeyUp() = guard.expectRelease()

    fun takeKeyUp(): Boolean = guard.consumeRelease()

    fun noteKeyDown(repeatCount: Int) = guard.noteKeyDown(repeatCount)
}

internal fun isOkKeyCode(nativeKeyCode: Int): Boolean =
    nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * OK corto = click; OK largo = menú contextual. Con mando, el temporizador no depende de que
 * el equipo mande repeticiones (algunos no lo hacen): a los [POSTER_LONG_PRESS_MS] sale el menú
 * aunque la tecla no repita. La pulsación se consume por completo para que el `clickable` de
 * la tarjeta no dispare además el click al soltar.
 */
@Composable
fun Modifier.posterLongPressKeys(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier {
    if (onLongClick == null) return this
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent.keyCode
            val isOk = isOkKeyCode(native)
            val isMenu = native == android.view.KeyEvent.KEYCODE_MENU ||
                native == android.view.KeyEvent.KEYCODE_INFO
            if (isMenu && event.type == KeyEventType.KeyDown) {
                longPressJob?.cancel()
                longPressJob = null
                longPressFired = false
                // La tecla que se suelta es Menú, no OK: no se arma el candado.
                onLongClick.invoke()
                return@onPreviewKeyEvent true
            }
            if (!isOk) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) {
                TvPressGuard.noteKeyDown(event.nativeKeyEvent.repeatCount)
            }
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        longPressFired = false
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            delay(POSTER_LONG_PRESS_MS)
                            if (focused) {
                                longPressFired = true
                                TvPressGuard.expectKeyUp()
                                onLongClick.invoke()
                            }
                        }
                    } else if (!longPressFired) {
                        // La repetición sostenida ya es un OK largo.
                        longPressJob?.cancel()
                        longPressJob = null
                        longPressFired = true
                        TvPressGuard.expectKeyUp()
                        onLongClick.invoke()
                    }
                    true
                }

                KeyEventType.KeyUp -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (!longPressFired) onClick()
                    longPressFired = false
                    true
                }

                else -> false
            }
        }
}

/**
 * Estado del menú contextual de pósters: una sola instancia en la raíz de la app para que
 * cualquier tarjeta (Inicio, secciones, Mi lista, buscador…) pueda abrirlo con OK largo.
 */
@Stable
class PosterActionsController {
    var film by mutableStateOf<Film?>(null)
        private set

    /** Cuando la tarjeta viene de «Seguir viendo»/Historial, además del póster se ofrecen
     *  las acciones de estado (reanudar, desde el inicio, visto/no visto, quitar). */
    var resumeCard by mutableStateOf<ResumeCard?>(null)
        private set

    fun open(film: Film) {
        this.film = film
        this.resumeCard = null
    }

    fun open(card: ResumeCard) {
        this.film = card.film ?: resumeSeriesFilm(card.entry)
        this.resumeCard = card
    }

    fun dismiss() {
        film = null
        resumeCard = null
    }
}

val LocalPosterActions = staticCompositionLocalOf<PosterActionsController?> { null }

/**
 * Atajo para las tarjetas: devuelve la acción de OK largo de ESE título (o `null` si la app
 * no montó el menú). Se lee en composición; la lambda solo abre el menú.
 */
@Composable
fun rememberPosterLongClick(film: Film?): (() -> Unit)? {
    val controller = LocalPosterActions.current ?: return null
    if (film == null) return null
    return remember(controller, film.id) { { controller.open(film) } }
}

/**
 * OK largo/pulsación larga sobre una tarjeta de «Seguir viendo» o Historial: abre el menú de
 * estado de esa reproducción (no el menú genérico de favoritos). Si la app no montó el
 * controlador (pruebas aisladas), cae al menú de póster.
 */
@Composable
fun rememberPlaybackLongClick(card: ResumeCard?): (() -> Unit)? {
    val controller = LocalPosterActions.current ?: return null
    if (card == null) return null
    return remember(controller, card.entry.key, card.entry.updatedAt) {
        { controller.open(card) }
    }
}

/**
 * El menú contextual de una tarjeta: ficha, favoritos y «Ver más tarde», sin salir de donde
 * está mirando. Se abre con OK largo (mando) o manteniendo pulsada la portada (dedo).
 */
@Composable
fun PosterActionsDialog(
    film: Film?,
    card: ResumeCard? = null,
    onDismiss: () -> Unit,
    onOpenFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit = {},
    onChanged: () -> Unit = {},
) {
    val target = film ?: return
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var focusAttached by remember { mutableStateOf(false) }
    var tick by remember(target.id) { mutableIntStateOf(0) }
    val favorite = remember(tick, target.id) { UserLibrary.isFavorite(context, target.id) }
    val later = remember(tick, target.id) { UserLibrary.isWatchLater(context, target.id) }
    val entry = card?.entry
    val completed = card?.completed == true
    val nextEntry = if (card != null && completed && card.hasNext) nextEpisodeEntry(entry!!) else null

    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    // La ventana del diálogo tarda unos cuadros en estar enfocada: se reintenta hasta que el
    // destino lo tiene de verdad.
    LaunchedEffect(target.id) {
        repeat(40) {
            if (focusAttached) return@LaunchedEffect
            runCatching { focus.requestFocus() }
            delay(50)
        }
    }

    fun toggleFavorite() {
        UserLibrary.toggleFavorite(context, target)
        val added = UserLibrary.isFavorite(context, target.id)
        tick++
        com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context)
        onChanged()
        android.widget.Toast.makeText(
            context,
            if (added) "«${target.title}» en favoritos" else "«${target.title}» fuera de favoritos",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    fun toggleLater() {
        UserLibrary.toggleWatchLater(context, target)
        val added = UserLibrary.isWatchLater(context, target.id)
        tick++
        com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context)
        onChanged()
        android.widget.Toast.makeText(
            context,
            if (added) "«${target.title}» en Ver más tarde" else "«${target.title}» fuera de Ver más tarde",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    fun resume(cardToPlay: ResumeCard) {
        onResume(cardToPlay)
        onDismiss()
    }

    fun markWatched(watched: Boolean) {
        val current = card ?: return
        val item = current.entry
        if (isEpisodeEntry(item) && item.seriesId.isNotBlank()) {
            UserLibrary.markEpisodeWatched(context, item.seriesId, item.episodeIndex + 1, watched)
        }
        PlaybackHistory.markCompleted(context, item.key, watched)
        com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context, force = true)
        onChanged()
        android.widget.Toast.makeText(
            context,
            if (watched) "«${current.film?.title ?: item.title}» marcado como visto"
            else "«${current.film?.title ?: item.title}» marcado como no visto",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        onDismiss()
    }

    fun removeFromContinue() {
        val current = card ?: return
        PlaybackHistory.delete(context, current.entry.key)
        com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context, force = true)
        onChanged()
        android.widget.Toast.makeText(context, "Quitado de Seguir viendo", android.widget.Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f))
                .dismissOnBack(onDismiss)
                .onPreviewKeyEvent { event ->
                    val isOk = isOkKeyCode(event.nativeKeyEvent.keyCode)
                    if (isOk && event.type == KeyEventType.KeyDown) {
                        TvPressGuard.noteKeyDown(event.nativeKeyEvent.repeatCount)
                    }
                    // El KeyUp que abrió el menú (o sus duplicados) no activa nada.
                    event.type == KeyEventType.KeyUp && isOk && TvPressGuard.takeKeyUp()
                },
            contentAlignment = Alignment.Center,
        ) {
            val panelHeight = maxHeight * 0.9f
            // En la tele cabe de sobra; en un teléfono de 360 dp el panel se adapta en vez de
            // salirse de la pantalla.
            val panelWidth = if (maxWidth < 440.dp) maxWidth * 0.92f else 440.dp
            Column(
                Modifier
                    .width(panelWidth)
                    .heightIn(max = panelHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VisionSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    target.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    target.metaLine().ifBlank { "Vision+" },
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                if (card != null) {
                    // Acciones de estado: la fila «Seguir viendo» y el Historial dejan de ser
                    // de solo lectura. La primera acción siempre retoma lo que estabas viendo.
                    if (nextEntry != null) {
                        PosterActionRow(
                            icon = Icons.Rounded.Check,
                            title = "Siguiente capítulo",
                            detail = "T${nextEntry.seasonIndex + 1} · C${nextEntry.episodeIndex + 1}",
                            modifier = Modifier
                                .focusRequester(focus)
                                .onFocusChanged { focusAttached = it.isFocused },
                            onClick = { resume(card.copy(entry = nextEntry)) },
                        )
                    } else if (completed && card.hasNext) {
                        PosterActionRow(
                            icon = Icons.Rounded.Check,
                            title = "Siguiente capítulo",
                            detail = "Abre la temporada nueva en su primer capítulo",
                            modifier = Modifier
                                .focusRequester(focus)
                                .onFocusChanged { focusAttached = it.isFocused },
                            onClick = {
                                onDismiss()
                                onOpenFilm(target)
                            },
                        )
                    } else if (completed) {
                        PosterActionRow(
                            icon = Icons.Rounded.Check,
                            title = "Volver a ver",
                            detail = "Desde el inicio",
                            modifier = Modifier
                                .focusRequester(focus)
                                .onFocusChanged { focusAttached = it.isFocused },
                            onClick = { resume(card.copy(entry = entry!!.copy(positionMs = 0L, completed = false))) },
                        )
                    } else {
                        PosterActionRow(
                            icon = Icons.Rounded.Check,
                            title = "Reanudar",
                            detail = "Continúa en ${formatResume(entry!!.positionMs)}",
                            modifier = Modifier
                                .focusRequester(focus)
                                .onFocusChanged { focusAttached = it.isFocused },
                            onClick = { resume(card) },
                        )
                    }
                    PosterActionRow(
                        icon = Icons.Rounded.Check,
                        title = "Desde el inicio",
                        detail = "Empieza este título desde 00:00",
                        onClick = {
                            resume(card.copy(entry = entry!!.copy(positionMs = 0L, completed = false)))
                        },
                    )
                    PosterActionRow(
                        icon = Icons.Rounded.Check,
                        title = if (completed) "Marcar como no visto" else "Marcar como visto",
                        detail = if (completed) "Lo retira del historial" else "Lo quita de Seguir viendo",
                        accent = completed,
                        onClick = { markWatched(!completed) },
                    )
                    PosterActionRow(
                        icon = Icons.Rounded.Close,
                        title = "Quitar de Seguir viendo",
                        detail = "No borra la ficha ni tus favoritos",
                        onClick = { removeFromContinue() },
                    )
                }
                PosterActionRow(
                    icon = Icons.Rounded.Info,
                    title = "Ver ficha",
                    detail = "Sinopsis, reparto y temporadas",
                    modifier = if (card == null) Modifier.focusRequester(focus).onFocusChanged { focusAttached = it.isFocused } else Modifier,
                    onClick = { onOpenFilm(target) },
                )
                PosterActionRow(
                    icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    title = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                    detail = "Aparece en Mi lista · Favoritos",
                    accent = favorite,
                    onClick = { toggleFavorite() },
                )
                PosterActionRow(
                    icon = if (later) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    title = if (later) "Quitar de Ver más tarde" else "Añadir a Ver más tarde",
                    detail = "Guárdalo para verlo después",
                    accent = later,
                    onClick = { toggleLater() },
                )
                PosterActionRow(
                    icon = Icons.Rounded.Close,
                    title = "Cerrar",
                    detail = "",
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PosterActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) VisionSurfaceHigh else Color.White.copy(alpha = 0.04f))
            .then(if (focused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            // El observador va ANTES del clickable: así ve el nodo enfocable que crea.
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) Color(0xFFF5C518) else VisionBlueBright,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
        }
        if (accent) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(18.dp))
        }
    }
}
