package com.chronosbvrx.visionplus.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.Release
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Every text of the app's updates bell and sheet, phone and TV. */
internal object PluginUpdatesCopy {
    const val TITLE = "Actualizaciones de Vision+"
    const val CURRENT = "Tu versión"
    const val NEW_VERSION = "Nueva versión disponible"
    const val DOWNLOAD = "Actualizar ahora"
    const val UP_TO_DATE = "Tu app está al día."
    const val CLOSE = "Cerrar"
    const val DOWNLOAD_FAILED = "No se pudo abrir la descarga de la actualización"
    const val DOWNLOADING = "Descargando"
    const val INSTALLING = "Abriendo el instalador…"
    const val BY = "Creada por ChronosBVRX"
}

/** "v1.2.0 → v1.3.0". */
internal fun versionLine(from: String, to: String): String = "v$from → v$to"

/** What a screen reader says about [n] pending updates. */
internal fun pendingUpdatesLabel(n: Int): String =
    if (n == 1) "1 actualización disponible" else "$n actualizaciones disponibles"

/** What the TV rail says beside Plugins while [n] updates wait. */
internal fun railBadgeLabel(n: Int): String = if (n == 1) "1 por descargar" else "$n por descargar"

/** An icon with [count] in a red badge on its corner (none at 0). */
@Composable
fun CountBadgedIcon(icon: ImageVector, label: String, count: Int, tint: Color = Color.White, size: Dp = 24.dp) {
    BadgedBox(
        badge = { if (count > 0) Badge(containerColor = VisionBlue, contentColor = Color.White) { Text("$count") } },
        modifier = Modifier.semantics { contentDescription = if (count > 0) "$label, ${pendingUpdatesLabel(count)}" else label },
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/** The Home bell's glyph with its count. */
@Composable
fun PluginUpdatesBellIcon(count: Int) = CountBadgedIcon(Icons.Default.Notifications, PluginUpdatesCopy.TITLE, count)

/** How the badge counts: 1 while the one source's repository holds a newer release than this build. */
@Composable
fun appUpdateBadgeCount(): Int {
    val context = LocalContext.current
    return if (AppUpdate.pending(context)) 1 else 0
}

/**
 * The app's releases sheet: the version this build carries and, when the repository is ahead, the
 * new release with its notes. "Actualizar ahora" downloads it right here — with live progress —
 * and hands it to the system installer, so nobody leaves Vision+.
 */
@Composable
fun AppUpdatesDialog(release: Release?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updater = com.chronosbvrx.visionplus.ui.settings.rememberInAppUpdate()
    Dialog(onDismissRequest = { if (!updater.running) onDismiss() }) {
        Surface(shape = RoundedCornerShape(16.dp), color = VisionSurface, modifier = Modifier.widthIn(max = 560.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(PluginUpdatesCopy.TITLE, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    if (release == null || !AppUpdate.pending(context)) {
                        Text(PluginUpdatesCopy.UP_TO_DATE, style = MaterialTheme.typography.bodyLarge, color = Color.White)
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
                            if (updater.progress in 0..100) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { updater.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = VisionBlue,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                )
                            }
                        }
                        updater.error?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF6B6B))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (release != null && AppUpdate.pending(context) && !updater.running) {
                        TextButton(onClick = { updater.start(release) }) { Text(PluginUpdatesCopy.DOWNLOAD, color = VisionBlue) }
                    }
                    Button(
                        onClick = onDismiss,
                        enabled = !updater.running,
                        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                    ) {
                        Text(PluginUpdatesCopy.CLOSE)
                    }
                }
            }
        }
    }
}
