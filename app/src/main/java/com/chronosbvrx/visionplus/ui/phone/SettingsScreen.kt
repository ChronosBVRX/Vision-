package com.chronosbvrx.visionplus.ui.phone

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.EffectsMode
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.ui.components.VisionChip
import com.chronosbvrx.visionplus.ui.plugins.AppUpdatesDialog
import com.chronosbvrx.visionplus.ui.settings.OssNoticesDialog
import com.chronosbvrx.visionplus.ui.settings.SETTINGS_MARGIN
import com.chronosbvrx.visionplus.ui.settings.SettingsBlock
import com.chronosbvrx.visionplus.ui.settings.SettingsDestructiveRow
import com.chronosbvrx.visionplus.ui.settings.SettingsNavRow
import com.chronosbvrx.visionplus.ui.settings.SettingsRow
import com.chronosbvrx.visionplus.ui.settings.SettingsSection
import com.chronosbvrx.visionplus.ui.settings.SettingsSwitchRow
import com.chronosbvrx.visionplus.ui.settings.SettingsValueRow
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import com.chronosbvrx.visionplus.ui.settings.rememberUpdateCheck
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) {
    PLAYBACK("Reproducción"),
    SYSTEM("Sistema y Rendimiento"),
}

/**
 * Pantalla de Ajustes en teléfono y tablet.
 * Organizada en pestañas y tarjetas modulares con opciones reales y persistidas.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.PLAYBACK) }
    val scroll = rememberSaveable(tab, saver = ScrollState.Saver) { ScrollState(0) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = SETTINGS_MARGIN, vertical = 16.dp),
            )
            val tabsScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabsScroll)
                        .padding(horizontal = SETTINGS_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsTab.entries.forEach { t ->
                        VisionChip(t.label, t == tab) { tab = t }
                    }
                }
                TabRowEdges(tabsScroll, Modifier.matchParentSize())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = SETTINGS_MARGIN),
            ) {
                when (tab) {
                    SettingsTab.PLAYBACK -> PlaybackTab()
                    SettingsTab.SYSTEM -> SystemTab()
                }
                Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 32.dp))
            }
        }
    }
}

@Composable
private fun TabRowEdges(scroll: ScrollState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        if (scroll.canScrollBackward) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(EDGE_FADE)
                    .background(Brush.horizontalGradient(listOf(background, Color.Transparent))),
            )
        }
        if (scroll.canScrollForward) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(EDGE_FADE)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, background))),
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(background)
                        .clickable(onClickLabel = "Ver más pestañas") {
                            scope.launch { scroll.animateScrollBy(scroll.viewportSize * 0.7f) }
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Más pestañas")
                }
            }
        }
    }
}

private val EDGE_FADE = 32.dp

@Composable
private fun PlaybackTab() {
    val context = LocalContext.current
    val s = VisionSettings

    SettingsSection("Video y Calidad") {
        SettingsBlock {
            Text("Calidad de video preferida", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Resolución con la que arrancará la reproducción si el contenido ofrece múltiples calidades.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.qualityOptions.forEach { q ->
                    VisionChip(q, selected = s.defaultQuality == q) {
                        s.setDefaultQuality(context, q)
                    }
                }
            }
        }
        SettingsBlock {
            Text("Ajuste de pantalla (Aspect Ratio)", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Define cómo se escala el reproductor por defecto en tu pantalla.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.aspectOptions.forEachIndexed { index, label ->
                    VisionChip(label, selected = s.defaultAspect == index) {
                        s.setDefaultAspect(context, index)
                    }
                }
            }
        }
    }

    SettingsSection("Series y Continuidad") {
        SettingsBlock {
            Text("Omitir intro y créditos", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Con los tiempos de la comunidad, el botón aparece durante la entrada de series, novelas y anime; al llegar a los créditos, la tarjeta de siguiente capítulo sale antes del final.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.skipIntroModeOptions.forEach { mode ->
                    VisionChip(s.skipIntroModeLabel(mode), selected = s.skipIntroMode == mode) {
                        s.setSkipIntroMode(context, mode)
                    }
                }
            }
        }
        SettingsSwitchRow(
            title = "Reproducir siguiente episodio automáticamente",
            checked = s.autoPlayNext,
            onCheckedChange = { s.setAutoPlayNext(context, it) },
            supporting = "Al llegar a los créditos (o al terminar el capítulo), arranca de inmediato con el siguiente.",
        )
        SettingsSwitchRow(
            title = "Reanudar automáticamente",
            checked = s.autoResume,
            onCheckedChange = { s.setAutoResume(context, it) },
            supporting = "Continúa directo desde el minuto guardado sin preguntar «¿Reanudar o desde el inicio?».",
        )
    }
}

@Composable
private fun SystemTab() {
    val context = LocalContext.current
    val s = VisionSettings
    val update = rememberUpdateCheck()
    if (update.available != null) {
        AppUpdatesDialog(release = update.available!!, onDismiss = { update.available = null })
    }
    var showNotices by rememberSaveable { mutableStateOf(false) }
    if (showNotices) OssNoticesDialog(onDismiss = { showNotices = false })
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var showSlotDialog by rememberSaveable { mutableStateOf(false) }
    var slotTick by remember { mutableStateOf(0) }
    val slotStatus = remember(slotTick) {
        com.chronosbvrx.visionplus.data.gate.SlotClient.statusText(context)
    }
    if (showSlotDialog) {
        com.chronosbvrx.visionplus.ui.settings.SlotCodeDialog(
            onDismiss = { showSlotDialog = false },
            onRedeemed = {
                showSlotDialog = false
                slotTick++
                Toast.makeText(context, "Equipo con identidad propia", Toast.LENGTH_SHORT).show()
            },
        )
    }

    SettingsSection("Rendimiento y Pantalla") {
        SettingsBlock {
            val modeLabel = when (s.effectsMode) {
                EffectsMode.AUTO -> "Automático (Detecta dispositivo)"
                EffectsMode.LIGHT -> "Modo ligero (Sin animaciones pesadas)"
                EffectsMode.FULL -> "Máxima calidad (Efectos completos)"
            }
            Text("Rendimiento de interfaz", style = MaterialTheme.typography.bodyLarge)
            Text(
                s.effectsMode.note,
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            OutlinedButton(onClick = { s.cycleEffectsMode(context) }) {
                Text(modeLabel)
            }
        }
        SettingsBlock {
            Text("Buffer de canales en vivo", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Margen de precarga antes de reproducir la transmisión.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.liveBufferOptions.forEach { b ->
                    VisionChip(b, selected = s.liveBuffer == b) {
                        s.setLiveBuffer(context, b)
                    }
                }
            }
        }
    }

    SettingsSection("Almacenamiento y Datos") {
        SettingsRow(
            title = "Caché de imágenes y catálogo",
            supporting = "${s.formatBytes(s.cacheSizeBytes)} en almacenamiento local",
            onClick = {
                if (s.clearCache(context)) {
                    Toast.makeText(context, "Caché liberada correctamente", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Text("Limpiar", color = VisionBlue, style = MaterialTheme.typography.labelLarge)
        }
        SettingsDestructiveRow(
            title = "Vaciar historial de reproducción",
            supporting = "Elimina todos los títulos de la fila «Seguir viendo»",
            onClick = {
                s.clearPlaybackHistory(context)
                Toast.makeText(context, "Historial de reproducción vaciado", Toast.LENGTH_SHORT).show()
            },
        )
    }

    SettingsSection("Diagnóstico de Red") {
        SettingsValueRow(
            title = "Conexión a internet",
            value = s.getNetworkInfo(context),
            supporting = "Tipo de interfaz de red activa en el dispositivo",
        )
        SettingsValueRow(
            title = "Servidor de streaming",
            value = "En línea (XTV)",
            supporting = "Catálogo multimedia y canales en directo",
        )
    }

    SettingsSection("Diagnóstico y Soporte") {
        SettingsSwitchRow(
            title = "Enviar diagnósticos",
            checked = s.diagnosticsEnabled,
            onCheckedChange = { s.setDiagnosticsEnabled(context, it) },
            supporting = "Crashes y errores técnicos se envían al servidor para corregirlos. Sin datos personales ni historial.",
        )
        SettingsRow(
            title = "Reportar un problema",
            supporting = "Cuéntanos qué pasó; se adjunta el estado técnico (sin datos personales)",
            onClick = { showReportDialog = true },
        )
        SettingsRow(
            title = "Identidad del equipo",
            supporting = "Sesión del portal: $slotStatus",
            onClick = { showSlotDialog = true },
        )
    }

    if (showReportDialog) {
        DiagnosticsReportDialog(
            onDismiss = { showReportDialog = false },
            onSend = { text ->
                com.chronosbvrx.visionplus.data.diag.Diagnostics.feedback(
                    text.ifBlank { "Diagnóstico manual desde Ajustes" },
                )
                showReportDialog = false
                Toast.makeText(context, "Diagnóstico enviado", Toast.LENGTH_SHORT).show()
            },
        )
    }

    SettingsSection("Actualizaciones y Sistema") {
        SettingsRow(
            title = if (update.checking) "Buscando…" else "Buscar actualizaciones",
            supporting = "Versión instalada: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            enabled = !update.checking,
            onClick = { update.run() },
        )
        SettingsNavRow(
            title = "Licencias de software libre",
            supporting = "Bibliotecas de terceros de código abierto que usa Vision+",
            onClick = { showNotices = true },
        )
    }
}

/** Reporte manual con texto libre: se adjunta al diagnóstico técnico (sin datos personales). */
@Composable
private fun DiagnosticsReportDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar un problema") },
        text = {
            Column {
                Text(
                    "Describe qué pasó (opcional). Se adjuntan versión, dispositivo y estado técnico; nunca datos personales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 3,
                    placeholder = { Text("p. ej. el video se cortó al minuto 5") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(text.trim()) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
