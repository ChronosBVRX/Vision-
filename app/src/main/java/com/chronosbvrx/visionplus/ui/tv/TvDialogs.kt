package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.ui.comingSoon
import com.chronosbvrx.visionplus.ui.plugins.PluginUpdatesCopy
import com.chronosbvrx.visionplus.ui.plugins.versionLine
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

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

/** A TV dialog: a dark rounded panel with a title, [content] below it; Back closes it ([onDismiss]). */
@Composable
internal fun TvDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Mientras el diálogo exista, el rescate de foco de la pantalla de atrás no debe robarle las teclas.
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 420.dp, max = 640.dp)
                .background(VisionSurface, RoundedCornerShape(16.dp))
                .padding(28.dp)
                .modalFocusTrap(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            content()
        }
    }
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
    TvDialog(PluginUpdatesCopy.TITLE, onDismiss) {
        Text(
            "${PluginUpdatesCopy.CURRENT}: v${AppUpdate.currentVersion(context)}",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )
        if (release == null || !pending) {
            Text(PluginUpdatesCopy.UP_TO_DATE, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvCompactAction(label = PluginUpdatesCopy.CLOSE, modifier = Modifier.dialogFocus(focus), onClick = onDismiss)
            }
        } else {
            Text(PluginUpdatesCopy.NEW_VERSION, style = MaterialTheme.typography.titleSmall, color = VisionBlue)
            Text(versionLine(AppUpdate.currentVersion(context), release.version), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (!release.notes.isNullOrBlank()) {
                Text(
                    release.notes.trim().take(480),
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
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
                TvCompactAction(label = PluginUpdatesCopy.CLOSE, onClick = onDismiss)
            }
        }
    }
}

