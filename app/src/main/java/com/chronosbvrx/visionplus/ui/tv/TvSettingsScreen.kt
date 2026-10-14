package com.chronosbvrx.visionplus.ui.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.EffectsMode
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import com.chronosbvrx.visionplus.ui.settings.rememberUpdateCheck
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Pestañas de Ajustes en Smart TV. */
enum class TvSettingsTab(val label: String) {
    PLAYBACK("Reproducción"),
    SYSTEM("Sistema y Rendimiento"),
}

/** "Ajustes" en Smart TV con navegación D-pad. */
@Composable
fun TvSettingsScreen(initialTab: TvSettingsTab = TvSettingsTab.PLAYBACK) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val landing = rememberLandingFocus()

    Column(Modifier.fillMaxSize().background(VisionBlack).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.width(20.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(TvSettingsTab.entries.toList()) { t ->
                    TvTab(
                        label = t.label,
                        selected = t == tab,
                        onClick = { tab = t },
                        modifier = if (t == tab) Modifier.landingFocus(landing) else Modifier,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                TvSettingsTab.PLAYBACK -> TvPlaybackTab()
                TvSettingsTab.SYSTEM -> TvSystemTab()
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
}

/** Una fila cuyo valor se cicla con izquierda/derecha o presionando Enter / OK. */
@Composable
private fun TvStepOption(label: String, onStep: (delta: Int) -> Unit) {
    TvActionOption(
        "$label   ◀ ▶",
        modifier = Modifier.onPreviewKeyEvent { e ->
            val delta = when (e.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> +1
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown) onStep(delta)
            true
        },
    ) { onStep(+1) }
}

@Composable
private fun ColumnScope.TvPlaybackTab() {
    val context = LocalContext.current
    val s = VisionSettings

    TvSectionTitle("Video y Calidad")
    TvStepOption("Calidad preferida: ${s.defaultQuality}") { delta ->
        val list = s.qualityOptions
        val i = (list.indexOf(s.defaultQuality) + delta + list.size) % list.size
        s.setDefaultQuality(context, list[i])
    }
    Note("Resolución inicial para series y películas con múltiples calidades.")

    val aspectLabel = if (s.defaultAspect == 0) "Ajustar (16:9)" else "Rellenar pantalla (Zoom)"
    TvActionOption("Ajuste de pantalla: $aspectLabel") {
        s.setDefaultAspect(context, if (s.defaultAspect == 0) 1 else 0)
    }
    Note("Define cómo se escala el reproductor en pantalla por defecto.")

    TvSectionTitle("Series y Continuidad")
    TvActionOption("Siguiente episodio automático: ${if (s.autoPlayNext) "Sí" else "No"}") {
        s.setAutoPlayNext(context, !s.autoPlayNext)
    }
    Note("Al terminar un capítulo, arranca de inmediato con el siguiente.")

    TvActionOption("Reanudar automáticamente: ${if (s.autoResume) "Sí" else "No"}") {
        s.setAutoResume(context, !s.autoResume)
    }
    Note("Continúa directo desde el minuto guardado sin desplegar el diálogo de confirmación.")
}

@Composable
private fun ColumnScope.TvSystemTab() {
    val context = LocalContext.current
    val s = VisionSettings
    val update = rememberUpdateCheck()
    if (update.available != null) {
        TvAppUpdatesDialog(onDismiss = { update.available = null })
    }
    var showNotices by rememberSaveable { mutableStateOf(false) }
    if (showNotices) TvOssNoticesDialog(onDismiss = { showNotices = false })

    TvSectionTitle("Rendimiento y Pantalla")
    val mode = s.effectsMode
    val modeLabel = when (mode) {
        EffectsMode.AUTO -> {
            val detected = if (DevicePerformance.isLowEndDevice(context)) "Ligero" else "Completo"
            "Rendimiento de interfaz: Automático ($detected)"
        }
        EffectsMode.LIGHT -> "Rendimiento de interfaz: Modo ligero (TV lenta)"
        EffectsMode.FULL -> "Rendimiento de interfaz: Máxima calidad (efectos)"
    }
    TvActionOption(modeLabel) { s.cycleEffectsMode(context) }
    Note(mode.note)

    TvStepOption("Buffer de canales en vivo: ${s.liveBuffer}") { delta ->
        val list = s.liveBufferOptions
        val i = (list.indexOf(s.liveBuffer) + delta + list.size) % list.size
        s.setLiveBuffer(context, list[i])
    }
    Note("Margen de precarga antes de reproducir la transmisión en vivo.")

    TvSectionTitle("Almacenamiento")
    TvActionOption("Limpiar caché (${s.formatBytes(s.cacheSizeBytes)})") {
        if (s.clearCache(context)) {
            Toast.makeText(context, "Caché liberada correctamente", Toast.LENGTH_SHORT).show()
        }
    }
    Note("Libera memoria interna eliminando pósteres e índices temporales.")

    TvActionOption("Vaciar historial de «Seguir viendo»") {
        s.clearPlaybackHistory(context)
        Toast.makeText(context, "Historial de reproducción vaciado", Toast.LENGTH_SHORT).show()
    }

    TvSectionTitle("Diagnóstico y Red")
    Note("Conexión: ${s.getNetworkInfo(context)}")
    Note("Servidor de streaming: En línea (XTV Masnew)")
    TvActionOption("Enviar diagnósticos: ${if (s.diagnosticsEnabled) "Sí" else "No"}") {
        s.setDiagnosticsEnabled(context, !s.diagnosticsEnabled)
    }
    Note("Crashes y errores técnicos se envían al servidor para corregirlos (sin datos personales).")
    TvActionOption("Enviar diagnóstico ahora") {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.feedback("Diagnóstico manual desde Ajustes (TV)")
        Toast.makeText(context, "Diagnóstico enviado", Toast.LENGTH_SHORT).show()
    }

    TvSectionTitle("Actualizaciones y Sistema")
    Note("Versión instalada: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
    TvActionOption(if (update.checking) "Buscando…" else "Buscar actualizaciones") { update.run() }
    TvActionOption("Licencias de software libre") { showNotices = true }
}
