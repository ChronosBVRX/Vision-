package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import android.os.Build
import android.view.Window
import android.view.WindowManager
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.data.config.MigrationNotice
import com.chronosbvrx.visionplus.ui.comingSoon
import com.chronosbvrx.visionplus.ui.plugins.PluginUpdatesCopy
import com.chronosbvrx.visionplus.ui.plugins.versionLine
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where a TV dialog puts the D-pad focus when it opens. The dialog's window takes a few frames to be
 * focused, so the request repeats until the target really holds focus (as [rememberLandingFocus]).
 */
class DialogFocus internal constructor() {
    val requester = FocusRequester()
    internal var focused = false
}

@Composable
fun rememberDialogFocus(): DialogFocus {
    val focus = remember { DialogFocus() }
    LaunchedEffect(focus) {
        repeat(60) {
            if (focus.focused) return@LaunchedEffect
            runCatching { focus.requester.requestFocus() }
            delay(50)
        }
    }
    return focus
}

/** Marks the element a TV dialog opens on. */
fun Modifier.dialogFocus(focus: DialogFocus): Modifier =
    focusRequester(focus.requester).onFocusChanged { focus.focused = it.isFocused }

/**
 * A TV dialog. The default skin is a dark rounded panel with a title and [content] below it.
 * [glass] is the premium profile skin: a full-window scrim (75 % black, with a light blur on
 * Android 12+) and a content slot that draws its own frosted card; nothing is dimmed twice.
 * Back closes it ([onDismiss]).
 */
@Composable
internal fun TvDialog(
    title: String,
    onDismiss: () -> Unit,
    glass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Mientras el diálogo exista, el rescate de foco de la pantalla de atrás no debe robarle las teclas.
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = if (glass) {
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        } else {
            DialogProperties()
        },
    ) {
        BackHandler(onBack = onDismiss)
        if (glass) {
            GlassDialogWindow()
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            val entrance by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(300),
                label = "glass-dialog-in",
            )
            val rise = (1f - entrance) * 26f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .graphicsLayer {
                            alpha = entrance
                            translationY = rise * density
                        }
                        .modalFocusTrap()
                        .dismissOnBack(onDismiss),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }
        } else {
            Column(
                Modifier
                    .widthIn(min = 420.dp, max = 640.dp)
                    .background(VisionSurface, RoundedCornerShape(16.dp))
                    .padding(28.dp)
                    .modalFocusTrap()
                    .dismissOnBack(onDismiss),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                content()
            }
        }
    }
}

/**
 * Ajusta la ventana del diálogo de cristal: el velo lo dibuja Compose (75 % negro), así que se
 * apaga el dim del sistema; en Android 12+ se pide además el desenfoque de la app de atrás. En
 * televisores que no lo soportan, el velo solo sigue viéndose elegante.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassDialogWindow() {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = view.dialogWindowOrNull() ?: return@LaunchedEffect
        runCatching {
            val attrs = window.attributes
            attrs.dimAmount = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !DevicePerformance.isLowEndDevice(view.context)
            ) {
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                attrs.blurBehindRadius = (18f * view.resources.displayMetrics.density).toInt()
            }
            window.attributes = attrs
        }
    }
}

private fun android.view.View.dialogWindowOrNull(): Window? {
    var parent: android.view.ViewParent? = this.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

/**
 * The TV's bell sheet: the version this build carries and, when the repository is ahead, the new
 * release with its notes and "Descargar"; focus starts on the closing action.
 */
@Composable
internal fun TvAppUpdatesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = rememberDialogFocus()
    val updater = com.chronosbvrx.visionplus.ui.settings.rememberInAppUpdate()
    val release = AppUpdate.latest
    val pending = AppUpdate.pending(context)
    // Forzada: sin botón Cerrar. El diálogo solo se va instalando la actualización.
    val forced = AppUpdate.forced(context)
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    val migrationHeadline = if (forced) MigrationNotice.headline(context, now) else null
    val migrationMessage = MigrationNotice.message(context)
    TvDialog(
        if (forced) PluginUpdatesCopy.MIGRATION_TITLE else PluginUpdatesCopy.TITLE,
        onDismiss = { if (!forced) onDismiss() },
    ) {
        Text(
            "${PluginUpdatesCopy.CURRENT}: v${AppUpdate.currentVersion(context)}",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )
        Text(
            PluginUpdatesCopy.BY,
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary.copy(alpha = 0.8f),
        )
        if (forced && migrationHeadline != null) {
            Text(
                migrationHeadline,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFFFB74D),
            )
            Text(
                migrationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        } else if (forced) {
            Text(PluginUpdatesCopy.FORCED, style = MaterialTheme.typography.titleSmall, color = Color(0xFFF5C518))
        }
        if (release == null || !pending) {
            Text(
                if (forced) PluginUpdatesCopy.MIGRATION_OFFLINE else PluginUpdatesCopy.UP_TO_DATE,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            if (forced) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TvCompactAction(
                        label = PluginUpdatesCopy.MIGRATION_RETRY,
                        modifier = Modifier.dialogFocus(focus),
                        onClick = { scope.launch { AppUpdate.check(context, force = true) } },
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TvCompactAction(label = PluginUpdatesCopy.CLOSE, modifier = Modifier.dialogFocus(focus), onClick = onDismiss)
                }
            }
        } else {
            Text(PluginUpdatesCopy.NEW_VERSION, style = MaterialTheme.typography.titleSmall, color = VisionBlue)
            Text(versionLine(AppUpdate.currentVersion(context), release.version), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (!release.notes.isNullOrBlank()) {
                // La lista de novedades nunca debe empujar los botones fuera de la tele.
                // Se recorta a seis líneas y el resto vive en el release de GitHub.
                Text(
                    release.notes.trim().take(360),
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                )
            }
            if (updater.running) {
                Text(
                    if (updater.progress <= 100) "${PluginUpdatesCopy.DOWNLOADING}… ${updater.progress} %"
                    else PluginUpdatesCopy.INSTALLING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionBlue,
                )
            }
            updater.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF6B6B))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TvCompactAction(
                    label = PluginUpdatesCopy.DOWNLOAD,
                    modifier = Modifier.dialogFocus(focus),
                    onClick = { updater.start(release) },
                )
                if (!forced) {
                    TvCompactAction(label = PluginUpdatesCopy.CLOSE, onClick = onDismiss)
                }
            }
        }
    }
}

