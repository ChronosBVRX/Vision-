package com.chronosbvrx.visionplus.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.ResumeInfo
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.xuper.MagisClient
import com.chronosbvrx.visionplus.data.xuper.MagisTitan
import com.chronosbvrx.visionplus.data.xuper.TitanSdk
import com.chronosbvrx.visionplus.ui.EpisodePlay
import com.chronosbvrx.visionplus.ui.brand.VisionWordmark
import com.chronosbvrx.visionplus.ui.formatDuration
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.tv.landingFocus
import com.chronosbvrx.visionplus.ui.tv.modalFocusTrap
import com.chronosbvrx.visionplus.ui.tv.rememberLandingFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far the arrows and the ±10 buttons jump. */
private const val SEEK_STEP_MS = 10_000L

/** "Saltar intro": shown between these positions, on films long enough to have an opening; jumps this far. */
private const val SKIP_FROM_MS = 3_000L
private const val SKIP_UNTIL_MS = 120_000L
private const val SKIP_MIN_DURATION_MS = 10 * 60_000L
private const val SKIP_INTRO_MS = 85_000L

/** The Vision+ pre-roll: "Omitir" appears after this long. */
private const val BRAND_SKIP_DELAY_MS = 2_000L

/** The TV's overscan safe zone around the controls. */
private val SAFE_H = 44.dp
private val SAFE_V = 44.dp

/** The pre-roll plays once per app run, before the first film. */
internal object IntroSession {
    var played = false
}

/** The player for a catalog film: media3 ExoPlayer under Vision's controls, with the pre-roll first. */
@Composable
fun PlayerScreen(film: Film, isTv: Boolean, onBack: () -> Unit, episode: EpisodePlay? = null, resumeMs: Long = 0) {
    // Al abrir una película alimenta las recomendaciones del inicio.
    val libraryContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(film.id) { com.chronosbvrx.visionplus.data.UserLibrary.recordPlay(libraryContext, film) }
    // A portal film (`magisvod:<contentId>`): en Vision+, el catálogo masnew reproduce a través
    // del motor nativo XTV (X4K Titan en proceso :x4k). Normalizamos directamente a `xtv:<contentId>`.
    if (film.videoUrl.startsWith("magisvod:")) {
        val cid = film.videoUrl.removePrefix("magisvod:")
        PlayerScreen(film.copy(id = "xtv:$cid", videoUrl = "xtv:$cid"), isTv, onBack, episode, resumeMs)
        return
    }
    // A downloaded vault film (`vault:<id>`): reproduce inmediatamente desde el búnker cifrado
    if (film.videoUrl.startsWith("vault:")) {
        PlayerCore(
            title = film.title,
            subtitle = film.metaLine(),
            url = film.videoUrl,
            isLive = false,
            isTv = isTv,
            onBack = onBack,
            showIntro = false,
            resumeInfo = ResumeInfo(film.videoUrl, film.title, film.posterUrl, film.videoUrl),
            resumeMs = resumeMs,
            declaredDurationMs = if (episode != null && episode.durationMs > 0) episode.durationMs else if (film.durationMin > 0) film.durationMin * 60_000L else 0L,
        )
        return
    }
    // An XTV film (`xtv:<contentId>`) or live channel (`xtvlive:<code>`): the portal mints the
    // play codes here and the X4K engine (its own process) does the streaming. The handoff
    // screen holds the room until the player takes over.
    if (film.videoUrl.startsWith("xtv:") || film.videoUrl.startsWith("xtvlive:")) {
        // El motor nativo solo puede con una cosa a la vez y la descarga lo ocupa entero:
        // con una descarga activa se ofrece pausarla antes de abrir (nunca se cruzan).
        EngineDownloadGate(isTv = isTv, onCancel = onBack) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val live = film.videoUrl.startsWith("xtvlive:")
            val code = film.videoUrl.substringAfter(':')
            // El capítulo llega con su contexto: el handoff ("Abriendo…") sale al instante y el
            // portal resuelve detrás, en vez del click muerto de lanzar el play en segundo plano.
            val ep = episode
            val fallbackDuration = if (ep != null && ep.durationMs > 0) ep.durationMs else if (film.durationMin > 0) film.durationMin * 60_000L else 0L
            XtvHandoff(
                title = film.title + (ep?.titleSuffix.orEmpty()),
                prepare = {
                    if (live) com.chronosbvrx.visionplus.data.xuper.X4KApi.playLive(context, code, film.title)
                    else if (ep != null) com.chronosbvrx.visionplus.data.xuper.X4KApi.playVod(
                        context, ep.code, film.title + ep.titleSuffix, seriesId = ep.seriesId,
                        episodeIds = ep.episodeIds, episodeNames = ep.episodeNames, episodeIndex = ep.episodeIndex,
                        seasonIds = ep.seasonIds, seasonNames = ep.seasonNames, seasonIndex = ep.seasonIndex,
                        resumeMs = ep.resumeMs, posterUrl = film.posterUrl,
                        fallbackDurationMs = fallbackDuration,
                    )
                    else com.chronosbvrx.visionplus.data.xuper.X4KApi.playVod(
                        context, code, film.title, resumeMs = resumeMs, posterUrl = film.posterUrl,
                        fallbackDurationMs = fallbackDuration,
                    )
                },
                onDone = onBack,
            )
        }
        return
    }
    // The url of a film may be a ref (`ia:identifier|title`): resolve it into a real url once,
    // on the player's terms, before the pre-roll or the film start loading anything.
    var streamUrl by remember { mutableStateOf(film.videoUrl) }
    LaunchedEffect(film.id) {
        if (streamUrl.contains('|')) {
            streamUrl = com.chronosbvrx.visionplus.data.sources.PlaybackResolve.urlFor(streamUrl)
        }
    }
    if (streamUrl.contains('|')) {
        // Still resolving (one network roundtrip): keep the screen black, no player built yet.
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
        )
        return
    }
    PlayerCore(
        title = film.title,
        subtitle = film.metaLine(),
        url = streamUrl,
        isLive = false,
        isTv = isTv,
        onBack = onBack,
        showIntro = true,
        resumeInfo = ResumeInfo(film.videoUrl, film.title, film.posterUrl, streamUrl),
        resumeMs = resumeMs,
        declaredDurationMs = if (episode != null && episode.durationMs > 0) episode.durationMs else if (film.durationMin > 0) film.durationMin * 60_000L else 0L,
    )
}

/** The player for a live channel: no seek bar, no pre-roll, headers from the playlist when it gave any. */
@Composable
fun LivePlayerScreen(
    channel: LiveChannel,
    isTv: Boolean,
    channelCodes: List<String> = emptyList(),
    groupTitle: String? = null,
    onBack: () -> Unit,
) {
    // An XTV live channel (`xtvlive:<code>`): the portal mints its licence here and the X4K
    // engine (its own process) takes over with the full zapper. The handoff screen holds the
    // room until the player takes over, so the menu never flashes back in between.
    if (channel.url.startsWith("xtvlive:")) {
        // Mismo motor que las descargas: con una XTV activa se ofrece pausarla antes de abrir.
        EngineDownloadGate(isTv = isTv, onCancel = onBack) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val code = channel.url.substringAfter(':')
            XtvHandoff(
                title = channel.name,
                prepare = {
                    com.chronosbvrx.visionplus.data.xuper.X4KApi.playLive(
                        context,
                        code,
                        channel.name,
                        channelCodes = channelCodes,
                        groupTitle = groupTitle,
                    )
                },
                onDone = onBack,
            )
        }
        return
    }
    // A portal channel (`magis:<code>|…`) plays through the provider's own engine, inside the app.
    if (channel.url.startsWith("magis:")) {
        TitanLivePlayer(channel, onBack)
        return
    }
    // A channel's url may be a source ref (`xuperch:<code>|…`): its source mints the
    // per-channel session before the player opens anything.
    val isSourceRef = channel.url.contains('|') &&
        !channel.url.startsWith("http://", ignoreCase = true) &&
        !channel.url.startsWith("https://", ignoreCase = true)
    var streamUrl by remember(channel.url) { mutableStateOf(channel.url) }
    LaunchedEffect(channel.url) {
        if (isSourceRef) {
            streamUrl = com.chronosbvrx.visionplus.data.sources.PlaybackResolve.urlFor(streamUrl)
        }
    }
    if (isSourceRef && streamUrl.contains('|')) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
        )
        return
    }
    val parsedInline = remember(streamUrl) {
        if (streamUrl.contains('|')) {
            com.chronosbvrx.visionplus.data.sports.GeeSportsFallback.parseStreamUrl(streamUrl)
        } else null
    }
    PlayerCore(
        title = channel.name,
        subtitle = channel.group,
        url = parsedInline?.url ?: streamUrl,
        isLive = true,
        isTv = isTv,
        onBack = onBack,
        showIntro = false,
        userAgent = channel.userAgent ?: parsedInline?.userAgent,
        referrer = channel.referrer ?: parsedInline?.referrer,
        origin = channel.origin ?: parsedInline?.origin,
        drmKey = channel.drmKey,
        drmType = channel.drmType,
    )
}

/**
 * A portal channel: the provider's own Titan engine renders it, inside Vision+. The engine boots
 * with the carried session (device activation), the channel carries its CDN addresses and the
 * engine resolves, signs and plays like the provider's app does.
 */
@Composable
private fun TitanLivePlayer(channel: LiveChannel, onBack: () -> Unit) {
    val context = LocalContext.current
    val code = channel.url.removePrefix("magis:").substringBefore('|')
    var titanView by remember { mutableStateOf<android.view.View?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(code) {
        val sessionOk = withContext(Dispatchers.IO) { MagisTitan.ensureSession(context) }
        if (!sessionOk) {
            error = "No se pudo preparar el reproductor."
            return@LaunchedEffect
        }
        val view = withContext(Dispatchers.Main) { TitanSdk.newVideoView(context) }
        if (view == null) {
            error = "No se pudo crear la pantalla del reproductor."
            return@LaunchedEffect
        }
        titanView = view
        delay(300)
        val medias = withContext(Dispatchers.IO) {
            runCatching { MagisClient(context).mediaFor(code) }.getOrDefault(emptyList())
        }
        if (medias.isEmpty()) {
            error = "Este canal no trae direcciones de reproducción."
            return@LaunchedEffect
        }
        val played = withContext(Dispatchers.Main) { TitanSdk.playLive(view, code, channel.name, medias) }
        if (!played) error = "No se pudo iniciar la reproducción."
    }

    val controls = rememberControlsState()
    AutoHideEffect(controls, playing = true)
    val surface = rememberLandingFocus()
    var swallowWakeupKey by remember { mutableStateOf<Key?>(null) }
    var lastWakeupAtMs by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (error != null) {
            onBack()
            return@BackHandler
        }
        controls.handleBack(onExit = onBack)
    }

    // The player follows the phone's orientation; the app's setting returns on leaving.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(Unit) {
        onDispose { titanView?.let { TitanSdk.stop(it) } }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .landingFocus(surface)
            .onPreviewKeyEvent { e ->
                if (swallowWakeupKey != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        swallowWakeupKey = null
                        return@onPreviewKeyEvent true
                    }
                    if (e.key == swallowWakeupKey) return@onPreviewKeyEvent true
                }
                if (e.type == KeyEventType.KeyDown) {
                    swallowWakeupKey = null
                    val action = resolvePlayerKeyDownAction(
                        keyCode = e.key.nativeKeyCode,
                        isLive = true,
                        isControlsVisible = controls.visible,
                        hasControlFocus = controls.visible,
                        isErrorShown = error != null,
                    )
                    if (action == PlayerKeyDownAction.WAKE_CONTROLS_ONLY) {
                        swallowWakeupKey = e.key
                        lastWakeupAtMs = android.os.SystemClock.uptimeMillis()
                        controls.bump()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { controls.toggle() } },
        contentAlignment = Alignment.Center,
    ) {
        titanView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { view },
            )
        }

        AnimatedVisibility(
            visible = controls.visible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (android.os.SystemClock.uptimeMillis() - lastWakeupAtMs >= 220L && swallowWakeupKey == null) {
                            onBack()
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        channel.group,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error!!, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                ) { Text("Volver") }
            }
        }
    }
}

/**
 * A portal film/series: the provider's own engine renders it, inside Vision+. Same boot as the
 * live path; the addresses come from startPlayVOD for the content id.
 */
@Composable
fun TitanVodPlayerScreen(film: Film, onBack: () -> Unit) {
    val context = LocalContext.current
    val contentId = film.videoUrl.removePrefix("magisvod:")
    var titanView by remember { mutableStateOf<android.view.View?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contentId) {
        val sessionOk = withContext(Dispatchers.IO) { MagisTitan.ensureSession(context) }
        if (!sessionOk) {
            error = "No se pudo preparar el reproductor."
            return@LaunchedEffect
        }
        val view = withContext(Dispatchers.Main) { TitanSdk.newVideoView(context) }
        if (view == null) {
            error = "No se pudo crear la pantalla del reproductor."
            return@LaunchedEffect
        }
        titanView = view
        delay(300)
        val medias = withContext(Dispatchers.IO) {
            runCatching { MagisClient(context).startPlayVodRecovering(contentId) }.getOrDefault(emptyList())
        }
        if (medias.isEmpty()) {
            // Respaldo XTV: toca el mismo motor que una descarga activa. Sin coordinar,
            // el play se buggea; mejor un aviso honesto que un fallo raro.
            val engineBusy = withContext(Dispatchers.IO) {
                runCatching { OfflineDownloadManager.activeEngineDownload(context) }.getOrNull()
            }
            if (engineBusy != null) {
                error = "Hay una descarga en curso: pausa la descarga en «Descargas» para ver este título en línea."
                return@LaunchedEffect
            }
            val playedXtv = withContext(Dispatchers.IO) {
                com.chronosbvrx.visionplus.data.xuper.X4KApi.playVod(context, contentId, film.title)
            }
            if (!playedXtv) {
                error = "Este título no está disponible en este momento."
            } else {
                onBack()
            }
            return@LaunchedEffect
        }
        val played = withContext(Dispatchers.Main) { TitanSdk.playVod(view, contentId, film.title, medias) }
        if (!played) error = "No se pudo iniciar la reproducción."
    }

    val controls = rememberControlsState()
    AutoHideEffect(controls, playing = true)
    val surface = rememberLandingFocus()
    var swallowWakeupKey by remember { mutableStateOf<Key?>(null) }
    var lastWakeupAtMs by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (error != null) {
            onBack()
            return@BackHandler
        }
        controls.handleBack(onExit = onBack)
    }

    // The player follows the phone's orientation; the app's setting returns on leaving.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(Unit) {
        onDispose { titanView?.let { TitanSdk.stop(it) } }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .landingFocus(surface)
            .onPreviewKeyEvent { e ->
                if (swallowWakeupKey != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        swallowWakeupKey = null
                        return@onPreviewKeyEvent true
                    }
                    if (e.key == swallowWakeupKey) return@onPreviewKeyEvent true
                }
                if (e.type == KeyEventType.KeyDown) {
                    swallowWakeupKey = null
                    val action = resolvePlayerKeyDownAction(
                        keyCode = e.key.nativeKeyCode,
                        isLive = false,
                        isControlsVisible = controls.visible,
                        hasControlFocus = controls.visible,
                        isErrorShown = error != null,
                    )
                    if (action == PlayerKeyDownAction.WAKE_CONTROLS_ONLY) {
                        swallowWakeupKey = e.key
                        lastWakeupAtMs = android.os.SystemClock.uptimeMillis()
                        controls.bump()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { controls.toggle() } },
        contentAlignment = Alignment.Center,
    ) {
        titanView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { view },
            )
        }

        AnimatedVisibility(
            visible = controls.visible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (android.os.SystemClock.uptimeMillis() - lastWakeupAtMs >= 220L && swallowWakeupKey == null) {
                            onBack()
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        film.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        film.metaLine(),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error!!, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                ) { Text("Volver") }
            }
        }
    }
}

/**
 * The player: a plain media3 ExoPlayer under Vision's controls overlay (title, progress bar with the
 * buffer, rewind / play-pause / forward, and on the phone the rotate button). For a film it opens
 * with the Vision+ pre-roll; for a live channel it shows "EN VIVO" instead of the progress bar.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerCore(
    title: String,
    subtitle: String,
    url: String,
    isLive: Boolean,
    isTv: Boolean,
    onBack: () -> Unit,
    showIntro: Boolean,
    userAgent: String? = null,
    referrer: String? = null,
    origin: String? = null,
    drmKey: String? = null,
    drmType: String? = null,
    resumeInfo: com.chronosbvrx.visionplus.data.ResumeInfo? = null,
    resumeMs: Long = 0,
    declaredDurationMs: Long = 0L,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val startWithIntro = showIntro && !IntroSession.played
    var introPlaying by remember { mutableStateOf(startWithIntro) }
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: VISION_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val headers = buildMap {
            referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            origin?.takeIf { it.isNotBlank() }?.let { put("Origin", it) }
        }
        if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers)
        val baseDataSourceFactory = if (url.startsWith("vault:")) {
            com.chronosbvrx.visionplus.data.offline.EncryptedVaultDataSource.Factory(context)
        } else {
            DefaultDataSource.Factory(context, http)
        }
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(baseDataSourceFactory, extractorsFactory)
        if (!drmKey.isNullOrBlank()) {
            val clearKeyJson = com.chronosbvrx.visionplus.data.sports.GeeSportsFallback.buildClearKeyJson(drmKey)
            if (clearKeyJson != null) {
                val drmCallback = androidx.media3.exoplayer.drm.LocalMediaDrmCallback(clearKeyJson.toByteArray(Charsets.UTF_8))
                val drmManager = androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        androidx.media3.common.C.CLEARKEY_UUID,
                        androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER,
                    )
                    .setMultiSession(false)
                    .build(drmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(if (startWithIntro) introMediaItem() else mediaItemFor(url, isLive, drmType))
                prepare()
                playWhenReady = true
            }
    }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(declaredDurationMs) }
    var bufferedFraction by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var barFocused by remember { mutableStateOf(false) }
    val controls = rememberControlsState()
    AutoHideEffect(controls, playing)
    val menu = rememberPlayerMenuState()
    var showTracks by remember { mutableStateOf(false) }
    var showMarkers by remember { mutableStateOf(false) }
    var skipUsed by remember { mutableStateOf(false) }
    // Historial («Seguir viendo»): minuto guardado y diálogo de continuar.
    var resumeConsumed by remember { mutableStateOf(false) }
    var resumeEntry by remember {
        mutableStateOf(
            if (!isLive && resumeMs <= 0) {
                resumeInfo?.let { com.chronosbvrx.visionplus.data.PlaybackHistory.get(context, it.key) }
                    ?.takeIf { com.chronosbvrx.visionplus.data.shouldKeep(it.positionMs, it.durationMs) }
            } else {
                null
            },
        )
    }

    fun saveHistory() {
        val info = resumeInfo ?: return
        if (isLive) return
        val pos = positionMs
        val dur = durationMs
        if (com.chronosbvrx.visionplus.data.isFinished(pos, dur)) {
            com.chronosbvrx.visionplus.data.PlaybackHistory.delete(context, info.key)
            return
        }
        if (!com.chronosbvrx.visionplus.data.shouldKeep(pos, dur)) return
        com.chronosbvrx.visionplus.data.PlaybackHistory.save(
            context,
            com.chronosbvrx.visionplus.data.ResumeEntry(
                key = info.key, title = info.title, posterUrl = info.posterUrl,
                videoUrl = info.videoUrl, positionMs = pos, durationMs = dur,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun acceptResume(atMs: Long) {
        resumeConsumed = true
        resumeEntry = null
        if (atMs > 0) player.seekTo(atMs)
        player.play()
        playing = true
        controls.bump()
    }

    fun startMainItem() {
        introPlaying = false
        IntroSession.played = true
        buffering = true
        player.setMediaItem(mediaItemFor(url, isLive, drmType))
        player.prepare()
        player.playWhenReady = true
        playing = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = player.playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE && error == null
                if (state == Player.STATE_READY && !introPlaying) {
                    val d = player.duration.coerceAtLeast(0L)
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    if (!isBufferWindowDuration(d, pos, declaredDurationMs, isLive)) {
                        if (d > 0L) durationMs = d
                    } else if (durationMs <= 0L && declaredDurationMs > 0L) {
                        durationMs = declaredDurationMs
                    }
                }
                if (state == Player.STATE_ENDED && introPlaying) startMainItem() else if (state == Player.STATE_ENDED) controls.bump()
            }

            override fun onPlayerError(e: PlaybackException) {
                error = if (isLive) "No se pudo reproducir este canal." else "No se pudo reproducir este video."
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Dynamic video quality constraint on ExoPlayer
    LaunchedEffect(menu.quality, player) {
        val builder = player.trackSelectionParameters.buildUpon()
        when (menu.quality) {
            "1080p" -> builder.setMaxVideoSize(1920, 1080).setMinVideoSize(1280, 720)
            "720p" -> builder.setMaxVideoSize(1280, 720).setMinVideoSize(854, 480)
            "480p" -> builder.setMaxVideoSize(854, 480).setMinVideoSize(0, 0)
            else -> builder.clearVideoSizeConstraints()
        }
        player.trackSelectionParameters = builder.build()
    }

    // Progress is polled: the position moves continuously and has no listener of its own.
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (!introPlaying && !isBufferWindowDuration(d, positionMs, declaredDurationMs, isLive)) {
                if (d > 0L) durationMs = d
            } else if (durationMs <= 0L && declaredDurationMs > 0L) {
                durationMs = declaredDurationMs
            }
            bufferedFraction = if (!introPlaying && durationMs > 0) player.bufferedPosition.toFloat() / durationMs else 0f
            playing = player.playWhenReady
            if (!isLive && !introPlaying && !resumeConsumed && resumeMs > 0 &&
                player.playbackState == Player.STATE_READY && durationMs > 0
            ) {
                resumeConsumed = true
                resumeEntry = null
                player.seekTo(resumeMs)
                Toast.makeText(
                    context,
                    "Continuando en " + com.chronosbvrx.visionplus.data.formatResume(resumeMs),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            delay(500)
        }
    }

    // Leaving the app pauses; coming back leaves it paused with the controls up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveHistory()
                player.pause()
                controls.bump()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            saveHistory()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Full screen while the player is open: system bars hidden, back to normal on leaving.
    DisposableEffect(activity, url) {
        val window = activity?.window
        if (url.startsWith("vault:")) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (url.startsWith("vault:")) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var swallowWakeupKey by remember { mutableStateOf<Key?>(null) }
    var lastWakeupAtMs by remember { mutableLongStateOf(0L) }

    fun canExecuteControlAction(): Boolean {
        if (!controls.visible || swallowWakeupKey != null) return false
        return canExecuteControlAction(android.os.SystemClock.uptimeMillis(), lastWakeupAtMs)
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = !player.playWhenReady
        playing = player.playWhenReady
        controls.bump()
    }

    fun seekBy(deltaMs: Long) {
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
        positionMs = player.currentPosition
        controls.bump()
    }

    // The video surface takes the D-pad while the controls are hidden (and is the screen's landing).
    val surface = rememberLandingFocus()
    var rewindFocused by remember { mutableStateOf(false) }
    var playPauseFocused by remember { mutableStateOf(false) }
    var forwardFocused by remember { mutableStateOf(false) }
    var tracksFocused by remember { mutableStateOf(false) }
    var markersFocused by remember { mutableStateOf(false) }
    val barFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    val rewindFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val tracksFocus = remember { FocusRequester() }
    val markersFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val brandSkipFocus = remember { FocusRequester() }
    var skipFocused by remember { mutableStateOf(false) }
    var brandSkipFocused by remember { mutableStateOf(false) }
    val hasControlFocus = barFocused || rewindFocused || playPauseFocused || forwardFocused || tracksFocused || markersFocused || skipFocused

    // "Saltar intro" during the opening minutes; only interactive when navigation menu is visible.
    val showSkip = !isLive && !introPlaying && !skipUsed && error == null &&
        durationMs > SKIP_MIN_DURATION_MS && positionMs in SKIP_FROM_MS..SKIP_UNTIL_MS
    fun skipIntro() {
        if (!canExecuteControlAction()) return
        skipUsed = true
        seekBy(SKIP_INTRO_MS)
        if (isTv) controls.hide()
    }

    // The pre-roll's "Omitir", after a couple of seconds.
    val brandSkipVisible = introPlaying && error == null && positionMs >= BRAND_SKIP_DELAY_MS

    // TV: the overlay's play/pause takes focus as it opens; the video takes it back as it closes.
    // Repeated until it holds, since a request made while the overlay is still appearing can be dropped.
    // With the controls hidden, the surface ALWAYS holds focus so the first D-pad click only wakes the menu.
    LaunchedEffect(controls.visible, isTv, brandSkipVisible, introPlaying) {
        if (!isTv) return@LaunchedEffect
        repeat(40) {
            val held = when {
                brandSkipVisible && brandSkipFocused -> true
                controls.visible && !introPlaying && hasControlFocus -> true
                !controls.visible && surface.focused -> true
                else -> false
            }
            if (held) return@LaunchedEffect
            runCatching {
                when {
                    brandSkipVisible -> brandSkipFocus.requestFocus()
                    controls.visible && !introPlaying -> playPauseFocus.requestFocus()
                    else -> surface.request()
                }
            }
            delay(50)
        }
    }

    BackHandler {
        // Con el diálogo de continuar, atrás continúa donde quedó (no destructivo).
        val pending = if (!resumeConsumed) resumeEntry else null
        if (pending != null) {
            acceptResume(pending.positionMs)
            return@BackHandler
        }
        if (error != null) {
            onBack()
            return@BackHandler
        }
        if (controls.handleBack(onExit = onBack)) {
            return@BackHandler
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .landingFocus(surface)
            .onPreviewKeyEvent { e ->
                if (swallowWakeupKey != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        swallowWakeupKey = null
                        return@onPreviewKeyEvent true
                    }
                    if (e.key == swallowWakeupKey) {
                        return@onPreviewKeyEvent true
                    }
                }
                if (e.type == KeyEventType.KeyDown) {
                    swallowWakeupKey = null
                    val modalOpen = showTracks || showMarkers || (!resumeConsumed && resumeEntry != null)
                    val action = resolvePlayerKeyDownAction(
                        keyCode = e.key.nativeKeyCode,
                        isLive = isLive,
                        isControlsVisible = controls.visible,
                        hasControlFocus = if (isTv) hasControlFocus else controls.visible,
                        isModalActive = modalOpen,
                        isIntroActive = introPlaying,
                        isErrorShown = error != null,
                    )
                    if (action == PlayerKeyDownAction.WAKE_CONTROLS_ONLY) {
                        swallowWakeupKey = e.key
                        lastWakeupAtMs = android.os.SystemClock.uptimeMillis()
                        controls.bump()
                        if (isTv && !introPlaying) {
                            runCatching { playPauseFocus.requestFocus() }
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || controls.visible) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.DirectionLeft,
                    Key.DirectionRight,
                    Key.DirectionUp,
                    Key.DirectionDown,
                    Key.Menu -> {
                        // Regla inmutable: con el menú oculto, el primer clic SOLO hace visible el menú.
                        swallowWakeupKey = e.key
                        lastWakeupAtMs = android.os.SystemClock.uptimeMillis()
                        controls.bump()
                        true
                    }
                    Key.MediaPlayPause, Key.Spacebar -> {
                        if (introPlaying && brandSkipVisible) startMainItem() else togglePlayPause()
                        true
                    }
                    Key.MediaRewind -> {
                        if (isLive) false else { seekBy(-SEEK_STEP_MS); true }
                    }
                    Key.MediaFastForward -> {
                        if (isLive) false else { seekBy(SEEK_STEP_MS); true }
                    }
                    else -> false
                }
            }
            .focusable()
            // A tap toggles the controls; no `clickable`, which would add a second focus target here.
            .pointerInput(Unit) { detectTapGestures { controls.toggle() } },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            // El pre-roll llena la pantalla (con recorte prudente); la película va encuadrada.
            update = { view ->
                view.resizeMode = if (introPlaying) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else if (com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultAspect == 1) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
        )

        if (buffering && error == null) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }

        error?.let { message ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        error = null
                        buffering = true
                        player.prepare()
                        player.play()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                ) { Text("Reintentar") }
            }
        }

        // "¿Continuar viendo?" al reabrir a medias (atrás también continúa: no destructivo).
        val pendingResume = if (!resumeConsumed) resumeEntry else null
        LaunchedEffect(pendingResume) {
            if (pendingResume != null) {
                player.pause()
                if (isTv) {
                    delay(300)
                    runCatching { resumeFocus.requestFocus() }
                }
            }
        }
        pendingResume?.let { entry ->
            AlertDialog(
                onDismissRequest = { acceptResume(entry.positionMs) },
                modifier = Modifier.modalFocusTrap(),
                title = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Text(
                        "Continuar en " + formatResume(entry.positionMs) +
                            (if (entry.durationMs > 0) " de " + formatResume(entry.durationMs) else ""),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { acceptResume(entry.positionMs) },
                        modifier = Modifier.focusRequester(resumeFocus),
                    ) { Text("Continuar") }
                },
                dismissButton = {
                    TextButton(onClick = { acceptResume(0) }) { Text("Desde el inicio") }
                },
            )
        }

        AnimatedVisibility(
            visible = controls.visible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Scrim that makes text and controls readable over the video; on TV the top holds longer
            // so the big title stays readable.
            val scrim = if (isTv) {
                arrayOf(
                    0.0f to Color(0xB3000000),
                    0.22f to Color(0x8C000000),
                    0.42f to Color(0x14000000),
                    0.70f to Color(0x14000000),
                    1.0f to Color(0xD9000000),
                )
            } else {
                arrayOf(
                    0.0f to Color(0xB3000000),
                    0.30f to Color(0x14000000),
                    0.70f to Color(0x14000000),
                    1.0f to Color(0xD9000000),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(*scrim))
                    // Any key while the overlay is open resets the auto-hide timer.
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key != Key.Back) controls.keepAlive()
                        false
                    }
                    .then(if (isTv) Modifier.padding(horizontal = SAFE_H, vertical = SAFE_V) else Modifier),
            ) {
                if (isTv) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .systemBarsPadding()
                            .padding(start = 16.dp, end = 16.dp)
                            .fillMaxWidth(0.6f),
                    ) {
                        Text(
                            title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 42.sp, lineHeight = 54.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .systemBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { if (canExecuteControlAction()) onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                subtitle,
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (!introPlaying && !isLive) {
                        val totalDur = if (durationMs > 0) durationMs else declaredDurationMs
                        val shownPosition = dragValue?.toLong() ?: positionMs
                        val seekStepMs = when {
                            totalDur > 90 * 60_000L -> 30_000L
                            totalDur > 30 * 60_000L -> 20_000L
                            else -> 10_000L
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(shownPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            SeekBar(
                                positionMs = shownPosition,
                                durationMs = totalDur,
                                bufferedFraction = bufferedFraction,
                                focused = barFocused,
                                onDrag = { dragValue = it; controls.bump() },
                                onDragEnd = {
                                    dragValue?.let { player.seekTo(it.toLong()) }
                                    dragValue = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                                    .then(
                                        if (!isTv) Modifier else Modifier
                                            .focusRequester(barFocus)
                                            .focusProperties {
                                                down = playPauseFocus
                                                left = barFocus
                                                right = barFocus
                                                up = if (showSkip) skipFocus else barFocus
                                            }
                                            // The bar's D-pad is handled here, before the Slider's own key handler:
                                            // left/right jump smoothly with dynamic step, down goes to the buttons, OK toggles play/pause.
                                            .onPreviewKeyEvent { e ->
                                                if (!canExecuteControlAction()) return@onPreviewKeyEvent true
                                                val arrow = e.key == Key.DirectionUp || e.key == Key.DirectionDown ||
                                                    e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent arrow
                                                when (e.key) {
                                                    Key.DirectionRight -> { seekBy(seekStepMs); true }
                                                    Key.DirectionLeft -> { seekBy(-seekStepMs); true }
                                                    Key.DirectionDown -> { runCatching { playPauseFocus.requestFocus() }; true }
                                                    Key.DirectionUp -> {
                                                        if (showSkip) runCatching { skipFocus.requestFocus() }
                                                        true
                                                    }
                                                    Key.DirectionCenter, Key.Enter -> { togglePlayPause(); true }
                                                    else -> false
                                                }
                                            }
                                            .onFocusChanged { barFocused = it.isFocused }
                                            .focusable(),
                                    ),
                            )
                            Text(formatDuration(totalDur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (isLive && !introPlaying) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(VisionLiveRed, CircleShape))
                            Text("EN VIVO", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(title, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = if (isTv) Modifier else Modifier.fillMaxWidth(),
                        horizontalArrangement = if (!isTv && !isLandscape) Arrangement.SpaceEvenly else Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isLive && !introPlaying) {
                            TransportButton(
                                icon = Icons.Default.Replay10,
                                contentDescription = "Atrasar 10s",
                                onClick = { if (canExecuteControlAction()) seekBy(-SEEK_STEP_MS) },
                                modifier = if (!isTv) Modifier else Modifier
                                    .focusRequester(rewindFocus)
                                    .onFocusChanged { rewindFocused = it.isFocused }
                                    .focusProperties { left = rewindFocus; right = playPauseFocus; up = barFocus; down = rewindFocus },
                            )
                        }
                        if (!introPlaying) {
                            TransportButton(
                                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pausar" else "Reproducir",
                                onClick = { if (canExecuteControlAction()) togglePlayPause() },
                                iconSize = 34.dp,
                                modifier = if (!isTv) Modifier else Modifier
                                    .focusRequester(playPauseFocus)
                                    .onFocusChanged { playPauseFocused = it.isFocused }
                                    .focusProperties {
                                        left = if (isLive) playPauseFocus else rewindFocus
                                        right = if (isLive) tracksFocus else forwardFocus
                                        up = if (isLive) playPauseFocus else barFocus
                                        down = playPauseFocus
                                    },
                            )
                        }
                        if (!isLive && !introPlaying) {
                            TransportButton(
                                icon = Icons.Default.Forward10,
                                contentDescription = "Adelantar 10s",
                                onClick = { if (canExecuteControlAction()) seekBy(SEEK_STEP_MS) },
                                modifier = if (!isTv) Modifier else Modifier
                                    .focusRequester(forwardFocus)
                                    .onFocusChanged { forwardFocused = it.isFocused }
                                    .focusProperties { left = playPauseFocus; right = tracksFocus; up = barFocus; down = forwardFocus },
                            )
                        }
                        if (!introPlaying) {
                            TransportButton(
                                icon = Icons.Default.Subtitles,
                                contentDescription = "Audio, subtítulos y calidad",
                                onClick = { if (canExecuteControlAction()) { showTracks = true; controls.bump() } },
                                iconSize = 26.dp,
                                modifier = if (!isTv) Modifier else Modifier
                                    .focusRequester(tracksFocus)
                                    .onFocusChanged { tracksFocused = it.isFocused }
                                    .focusProperties {
                                        left = if (isLive) playPauseFocus else forwardFocus
                                        right = if (isLive) tracksFocus else markersFocus
                                        up = if (isLive) playPauseFocus else barFocus
                                        down = tracksFocus
                                    },
                            )
                        }
                        if (!isLive && !introPlaying) {
                            TransportButton(
                                icon = Icons.Default.Tune,
                                contentDescription = "Corregir intro y outro",
                                onClick = { if (canExecuteControlAction()) { showMarkers = true; controls.bump() } },
                                iconSize = 26.dp,
                                modifier = if (!isTv) Modifier else Modifier
                                    .focusRequester(markersFocus)
                                    .onFocusChanged { markersFocused = it.isFocused }
                                    .focusProperties { left = tracksFocus; right = markersFocus; up = barFocus; down = markersFocus },
                            )
                        }
                        if (!isTv && !introPlaying) {
                            if (isLandscape) Spacer(Modifier.weight(1f))
                            // Rotate: forces the orientation the system's auto-rotate would not give.
                            IconButton(onClick = {
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = if (isLandscape) "Cambiar a vertical" else "Cambiar a horizontal",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSkip && controls.visible) {
            SkipPill(
                label = "Saltar intro",
                onClick = { skipIntro() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(end = if (isTv) SAFE_H + 16.dp else 20.dp, bottom = 130.dp)
                    .then(
                        if (!isTv) Modifier else Modifier
                            .focusRequester(skipFocus)
                            .onFocusChanged { skipFocused = it.isFocused }
                            .focusProperties {
                                down = barFocus
                                up = skipFocus
                                left = skipFocus
                                right = skipFocus
                            },
                    ),
            )
        }

        // The pre-roll's skip: no overlay chrome, just the pill once it appears.
        if (brandSkipVisible) {
            SkipPill(
                label = "Omitir",
                onClick = { startMainItem() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(end = if (isTv) SAFE_H + 16.dp else 20.dp, bottom = if (isTv) SAFE_V else 32.dp)
                    .then(
                        if (!isTv) Modifier else Modifier
                            .focusRequester(brandSkipFocus)
                            .onFocusChanged { brandSkipFocused = it.isFocused },
                    ),
            )
        }
    }

    if (showTracks) AudioAndSubtitlesDialog(menu, onDismiss = { showTracks = false })
    if (showMarkers && !isLive) {
        SkipMarkersDialog(
            positionLabel = formatDuration(positionMs),
            onPick = { intro ->
                showMarkers = false
                val what = if (intro) "Fin de la intro" else "Inicio del outro"
                Toast.makeText(context, "$what marcado en ${formatDuration(positionMs)}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMarkers = false },
        )
    }

    LaunchedEffect(showTracks) {
        if (!showTracks && isTv && controls.visible) {
            delay(100)
            runCatching { tracksFocus.requestFocus() }
        }
    }
    LaunchedEffect(showMarkers) {
        if (!showMarkers && isTv && controls.visible) {
            delay(100)
            runCatching { markersFocus.requestFocus() }
        }
    }
}

/** The media item for a URL: live channels that don't look like a plain file are treated as HLS or DASH. */
private fun mediaItemFor(url: String, isLive: Boolean, drmType: String? = null): MediaItem {
    val builder = MediaItem.Builder().setUri(Uri.parse(url))
    val liveMime = if (isLive) liveMimeType(url, drmType) else null
    if (liveMime != null) {
        builder.setMimeType(liveMime)
        builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
    }
    return builder.build()
}

private fun liveMimeType(url: String, drmType: String? = null): String? {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    if (drmType == "dash" || path.endsWith(".mpd")) return MimeTypes.APPLICATION_MPD
    val plainFile = listOf(".mp4", ".mkv", ".webm", ".ts", ".mp3", ".aac", ".mov").any { path.endsWith(it) }
    return if (plainFile) null else MimeTypes.APPLICATION_M3U8
}

private fun introMediaItem(): MediaItem =
    MediaItem.fromUri(Uri.parse("rawresource:///${R.raw.brand_intro}"))

private const val VISION_USER_AGENT = "VisionPlus/2.0"

/** A white-bordered pill over the video; inverts while it holds the D-pad focus. */
@Composable
private fun SkipPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White else Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/**
 * The progress bar: a three-layer track (faint background, the downloaded buffer in light grey, the
 * played part in blue) that thickens while it holds the D-pad focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    focused: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = if (durationMs > 0) durationMs.toFloat() else 1f
    Slider(
        value = if (durationMs > 0) positionMs.toFloat().coerceIn(0f, max) else 0f,
        onValueChange = onDrag,
        onValueChangeFinished = onDragEnd,
        valueRange = 0f..max,
        colors = SliderDefaults.colors(
            thumbColor = if (focused) Color.White else VisionBlue,
            activeTrackColor = VisionBlue,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
        track = { _ ->
            val posFrac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val bufFrac = bufferedFraction.coerceIn(0f, 1f)
            val trackHeight by animateDpAsState(targetValue = if (focused) 8.dp else 4.dp, label = "progressBarThickness")
            Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                val y = size.height / 2f
                val sw = size.height
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), sw, StrokeCap.Round)
                if (bufFrac > 0f) drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width * bufFrac, y), sw, StrokeCap.Round)
                if (posFrac > 0f) drawLine(VisionBlue, Offset(0f, y), Offset(size.width * posFrac, y), sw, StrokeCap.Round)
            }
        },
        modifier = modifier,
    )
}

/**
 * A transport button. Focused it inverts (white circle, black icon), so from the couch it's obvious
 * which one is selected; on a touch screen nothing takes focus and it looks the same as ever.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White else Color.Transparent, CircleShape),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(iconSize))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Puerta de coordinación descarga ↔ reproducción en línea.
 *
 * El motor nativo del proveedor vive en un único proceso (`:x4k`) y solo mantiene un
 * programa: una descarga XTV activa lo ocupa durante todo el transfer y el reproductor
 * no puede abrir mientras tanto (al forzarlo, la película se buggea y la descarga muere).
 * Antes de lanzar el reproductor se detecta esa descarga, se pregunta con honestidad y,
 * si el usuario acepta, se pausa esperando el ack del servicio: al continuar, el motor
 * ya está libre. "Seguir descargando" cancela la reproducción.
 */
@Composable
private fun EngineDownloadGate(isTv: Boolean, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf<OfflineDownload?>(null) }
    var pausing by remember { mutableStateOf(false) }
    var proceed by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) {
            runCatching { OfflineDownloadManager.activeEngineDownload(context) }.getOrNull()
        }
        blocking = found
        checked = true
        if (found == null) proceed = true
    }

    if (proceed) {
        content()
        return
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!checked) CircularProgressIndicator(color = VisionBlue)
        blocking?.let { item ->
            AlertDialog(
                onDismissRequest = { if (!pausing) onCancel() },
                modifier = Modifier.modalFocusTrap(),
                title = { Text("Descarga en curso") },
                text = {
                    Text(
                        buildString {
                            append("«${item.title}» va al ${item.progressPercent}%. ")
                            append("Vision+ usa el mismo motor de video para descargar y para reproducir: ")
                            append("para ver en línea ahora hay que pausar la descarga. ")
                            append("Al reanudarla empezará desde el principio.")
                            if (pausing) append("\n\nPausando descarga…")
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { pausing = true },
                        enabled = !pausing,
                        modifier = Modifier.focusRequester(confirmFocus),
                    ) { Text(if (pausing) "Pausando…" else "Pausar y ver ahora") }
                },
                dismissButton = {
                    TextButton(onClick = onCancel, enabled = !pausing) { Text("Seguir descargando") }
                },
            )
            LaunchedEffect(item.id) {
                if (isTv) {
                    delay(300)
                    runCatching { confirmFocus.requestFocus() }
                }
            }
            if (pausing) {
                LaunchedEffect(item.id) {
                    withContext(Dispatchers.IO) {
                        runCatching { OfflineDownloadManager.pauseEngineDownloadsForPlayback(context) }
                    }
                    proceed = true
                }
            }
        }
    }
}

/**
 * The branded handoff to the X4K player: it stays on screen (wordmark, spinner and the title)
 * while the portal mints the play codes and the player's own process wakes up. It only leaves
 * when the app comes back to the foreground (the player closed) — the menu never flashes back
 * in between — and it never bounces back on a failure: it offers retry or return instead.
 */
@Composable
private fun XtvHandoff(
    title: String,
    prepare: suspend () -> Boolean,
    onDone: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    var attempt by remember { mutableStateOf(0) }
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Atrás vuelve a la ficha: sin esto el mando sale de la app (TvApp desactiva su
    // propio BackHandler en el reproductor y aquí no había ninguno).
    BackHandler { onDone() }
    // El error deja un destino de foco: sin foco inicial el D-pad se queda muerto.
    val errorLanding = rememberLandingFocus(failed)

    LaunchedEffect(attempt) {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.phase("reproductor")
        failed = false
        prepared = false
        var ok = false
        // Auto-reintento transparente de hasta 4 intentos con pausas escalonadas (400/900/1800 ms):
        // resuelve picos de red, handshakes fríos y, sobre todo, la sesión pateada por el
        // servidor (el mint forzado ya no queda bloqueado por el silencio de 30 s).
        val backoffs = longArrayOf(400L, 900L, 1800L)
        var lastError = ""
        for (retryIndex in 0..3) {
            val attempt = withContext(Dispatchers.IO) { runCatching { prepare() } }
            ok = attempt.getOrDefault(false)
            attempt.exceptionOrNull()?.let { lastError = "${it.javaClass.simpleName}: ${it.message}" }
            if (ok) break
            if (retryIndex < backoffs.size) {
                delay(backoffs[retryIndex])
            }
        }
        if (!ok) {
            // El detalle vacío no sirve en campo: sin excepción suele ser prepare=null.
            val detail = lastError.ifBlank { "sin-medios (prepare=null)" }
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly("PLAYBACK_HANDOFF_FAIL", detail)
            failed = true
        } else {
            // Cold-starting the player's own process can bounce a spurious resume on some ROMs
            // (HyperOS); the grace window keeps that from counting as "the person came back".
            delay(500)
            prepared = true
        }
    }
    DisposableEffect(lifecycle, prepared) {
        if (!prepared) {
            onDispose { }
        } else {
            val armedAt = System.currentTimeMillis()
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                // Only a resume well after the launch counts as the player having closed.
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                    System.currentTimeMillis() - armedAt > 900
                ) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        delay(600)
                        com.chronosbvrx.visionplus.data.xuper.X4KApi.prewarmEngine(appContext)
                    }
                    onDone()
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "No se pudo abrir",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { attempt += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text("Reintentar") }
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                        modifier = Modifier.landingFocus(errorLanding),
                    ) { Text("Volver") }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                VisionWordmark(
                    height = 36.dp,
                )
                CircularProgressIndicator(color = VisionBlue)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Abriendo…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
