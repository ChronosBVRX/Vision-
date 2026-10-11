package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.ui.plugins.AppUpdatesDialog
import com.chronosbvrx.visionplus.ui.components.VisionChip
import com.chronosbvrx.visionplus.ui.components.SettingLabel
import com.chronosbvrx.visionplus.ui.comingSoon
import com.chronosbvrx.visionplus.ui.settings.ConnectedGreen
import com.chronosbvrx.visionplus.ui.settings.VisionCompanion
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import com.chronosbvrx.visionplus.ui.settings.OssNoticesDialog
import com.chronosbvrx.visionplus.ui.settings.rememberUpdateCheck
import com.chronosbvrx.visionplus.ui.settings.SETTINGS_MARGIN
import com.chronosbvrx.visionplus.ui.settings.SettingsBlock
import com.chronosbvrx.visionplus.ui.settings.SettingsCard
import com.chronosbvrx.visionplus.ui.settings.SettingsSectionTitle
import com.chronosbvrx.visionplus.ui.settings.SettingsDestructiveRow
import com.chronosbvrx.visionplus.ui.settings.SettingsNavRow
import com.chronosbvrx.visionplus.ui.settings.SettingsNote
import com.chronosbvrx.visionplus.ui.settings.SettingsRow
import com.chronosbvrx.visionplus.ui.settings.SettingsSection
import com.chronosbvrx.visionplus.ui.settings.SettingsSwitchRow
import com.chronosbvrx.visionplus.ui.settings.SettingsValueRow
import com.chronosbvrx.visionplus.ui.settings.SubtitlePreview
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsTab(val label: String) {
    SUBTITLES("Subtítulos"),
    APP("App"),
}

/**
 * "Ajustes" on the phone, in tabs; each tab is a run of grey section cards (SettingsCards.kt). The controls
 * respond; their values live only while the app runs.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.SUBTITLES) }
    // One scroll per tab: entering "App" from the bottom of "Subtítulos" must start at the top.
    val scroll = rememberSaveable(tab, saver = ScrollState.Saver) { ScrollState(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = SETTINGS_MARGIN, vertical = 16.dp))
            val tabsScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(tabsScroll).padding(horizontal = SETTINGS_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsTab.entries.forEach { t -> VisionChip(t.label, t == tab) { tab = t } }
                }
                TabRowEdges(tabsScroll, Modifier.matchParentSize())
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = SETTINGS_MARGIN),
            ) {
                when (tab) {
                    SettingsTab.SUBTITLES -> SubtitlesTab()
                    SettingsTab.APP -> AppTab()
                }
                Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 32.dp))
            }
        }
    }
}

/** The tab row's edges while it can scroll: a fade on each side with more tabs, and a "›" on the right that scrolls on. */
@Composable
private fun TabRowEdges(scroll: ScrollState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        if (scroll.canScrollBackward) {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(EDGE_FADE)
                    .background(Brush.horizontalGradient(listOf(background, Color.Transparent))),
            )
        }
        if (scroll.canScrollForward) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().width(EDGE_FADE).background(Brush.horizontalGradient(listOf(Color.Transparent, background))))
                Box(
                    Modifier.fillMaxHeight().background(background)
                        .clickable(onClickLabel = "Ver más pestañas") { scope.launch { scroll.animateScrollBy(scroll.viewportSize * 0.7f) } }
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
private fun LanguageOrderEditor(title: String, options: List<String>, order: MutableList<String>) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Column(Modifier.padding(top = 4.dp)) {
        order.toList().forEachIndexed { i, lang ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = true, onCheckedChange = { VisionSettings.toggle(order, lang) }, colors = CheckboxDefaults.colors(checkedColor = VisionBlue))
                Text("${i + 1}. $lang", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { VisionSettings.moveUp(order, lang) }, enabled = i > 0) {
                    Text("▲", color = if (i > 0) Color.White else VisionTextSecondary)
                }
                TextButton(onClick = { VisionSettings.moveDown(order, lang) }, enabled = i < order.lastIndex) {
                    Text("▼", color = if (i < order.lastIndex) Color.White else VisionTextSecondary)
                }
            }
        }
        options.filterNot { it in order }.forEach { lang ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = { VisionSettings.toggle(order, lang) }, colors = CheckboxDefaults.colors(checkedColor = VisionBlue))
                Text(lang, color = VisionTextSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SubtitlesTab() {
    val s = VisionSettings
    SettingsSection("Idioma") {
        SettingsBlock { LanguageOrderEditor("Idioma del audio (en orden de preferencia)", s.audioLanguages, s.audioOrder) }
        SettingsBlock {
            Text("Idiomas que entiendo", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Los subtítulos se prenden solos únicamente cuando el audio queda en un idioma que no está en esta lista.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            s.audioLanguages.forEach { lang ->
                val checked = lang in s.understood
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { s.toggle(s.understood, lang) }, colors = CheckboxDefaults.colors(checkedColor = VisionBlue))
                    Text(lang, color = if (checked) Color.White else VisionTextSecondary)
                }
            }
        }
    }
    SettingsSection("Subtítulos") {
        SettingsBlock { LanguageOrderEditor("Idioma de los subtítulos (en orden de preferencia)", s.subtitleLanguages, s.subtitleOrder) }
        SettingsBlock {
            Text("Cuándo mostrarlos", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisionChip("Automático", s.subtitlesAuto) { s.subtitlesAuto = true }
                VisionChip("Desactivado", !s.subtitlesAuto) { s.subtitlesAuto = false }
            }
            Text(
                "Automático: se prenden solo si el audio quedó en un idioma que no marcaste como entendido.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    SettingsSection("Estilo de los subtítulos") {
        SettingsBlock {
            SubtitlePreview(isTv = false, modifier = Modifier.padding(bottom = 4.dp))
            SettingLabel("Tamaño: ${s.size} %")
            StepSlider(s.sizes, s.size) { s.size = it }
            SettingLabel("Color del texto")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.colors.forEach { (color, _) ->
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(Color(color))
                            .border(
                                if (color == s.textColor) 3.dp else 1.dp,
                                if (color == s.textColor) VisionBlue else Color.Gray,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { s.textColor = color },
                    )
                }
            }
            SettingLabel("Grosor del texto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisionChip("Normal", !s.bold) { s.bold = false }
                VisionChip("Negrita", s.bold) { s.bold = true }
            }
            SettingLabel("Opacidad del fondo: ${s.opacity} %")
            StepSlider(s.opacities, s.opacity) { s.opacity = it }
            SettingLabel("Borde del texto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.edges.forEach { e -> VisionChip(e, s.edge == e) { s.edge = e } }
            }
            SettingLabel("Altura sobre el borde inferior: ${s.positionLabel(s.position)}")
            StepSlider(s.positions, s.position) { s.position = it }
        }
    }
}

/** A slider that only lands on [values]. */
@Composable
private fun StepSlider(values: List<Int>, current: Int, onChange: (Int) -> Unit) {
    Slider(
        value = values.indexOf(current).coerceAtLeast(0).toFloat(),
        onValueChange = { index -> onChange(values[index.roundToInt().coerceIn(0, values.lastIndex)]) },
        valueRange = 0f..values.lastIndex.toFloat(),
        steps = values.size - 2,
        colors = SliderDefaults.colors(thumbColor = VisionBlue, activeTrackColor = VisionBlue),
    )
}

@Composable
private fun AppTab() {
    val context = LocalContext.current
    val s = VisionSettings
    val update = rememberUpdateCheck()
    update.available?.let { release ->
        AppUpdatesDialog(release = release, onDismiss = { update.available = null })
    }
    var showNotices by rememberSaveable { mutableStateOf(false) }
    if (showNotices) OssNoticesDialog(onDismiss = { showNotices = false })
    SettingsSection("Actualizaciones") {
        SettingsRow(
            if (update.checking) "Buscando…" else "Buscar actualizaciones",
            supporting = "Versión instalada: ${AppUpdate.currentVersion(context)}",
            enabled = !update.checking,
            onClick = { update.run() },
        )
        SettingsNavRow("Licencias de software libre", onClick = { showNotices = true }, supporting = "Las bibliotecas de terceros que usa Vision")
    }
    SettingsSection("Reproducción") {
        SettingsSwitchRow("Datos curiosos", s.funFacts, { s.funFacts = it }, "Muestra un dato curioso de la película o serie durante la reproducción.")
        SettingsSwitchRow(
            "Saltar intro en anime",
            s.animeSkip,
            { s.animeSkip = it },
            "Busca dónde empieza y termina el opening de cada capítulo de anime y te muestra «Saltar intro».",
        )
        SettingsSwitchRow(
            "Saltar automáticamente",
            s.autoSkip,
            { s.autoSkip = it },
            "Cuando un capítulo tiene la intro marcada, se la salta solo, sin que toques el botón.",
        )
    }
    SettingsSection("Red") {
        // The value goes under the text, not beside it: "Cloudflare (predeterminado)" would squeeze the explanation.
        SettingsBlock {
            Text("DNS seguro", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Si tu proveedor de internet bloquea canales o servicios, prueba con Google. «Ninguno» usa el DNS de tu dispositivo, sin el de la app. Se aplica de inmediato.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            OutlinedButton(
                onClick = { s.dns = s.dnsModes[(s.dnsModes.indexOf(s.dns) + 1) % s.dnsModes.size] },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(s.dns) }
        }
    }
}

@Composable
private fun ConnectTab() {
    val context = LocalContext.current
    var ipPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Text(
        "Vincula este celular con un VISION de tu TV en la misma red Wi-Fi para controlarlo.",
        style = MaterialTheme.typography.bodyMedium,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp),
    )
    val c = VisionCompanion
    SettingsSection("Estado") {
        val connected = c.connectedTvName()
        SettingsRow("Estado", supporting = connected?.let { "Controlando $it" }) {
            Text(
                if (connected != null) "Conectado" else "Inactivo",
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected != null) ConnectedGreen else VisionTextSecondary,
            )
        }
        if (connected != null) SettingsDestructiveRow("Desconectar", onClick = { c.connectedTvId = null })
    }
    SettingsSection("Dispositivos encontrados") {
        c.foundTvs.forEach { (id, info) ->
            val (name, address) = info
            val paired = c.pairedTvs.any { it.id == id }
            val isConnected = id == c.connectedTvId
            SettingsRow(
                name,
                supporting = address,
                onClick = if (isConnected) null else {
                    { if (paired) c.connectedTvId = id else comingSoon(context) }
                },
            ) {
                Text(
                    when {
                        isConnected -> "Conectado"
                        paired -> "Conectar"
                        else -> "Emparejar"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) ConnectedGreen else Color.Unspecified,
                )
            }
        }
    }
    SettingsSection("Conectar por IP") {
        SettingsBlock {
            Text(
                "Si no aparece en la lista, ingresa su dirección y el código que muestra en pantalla.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(value = ipPort, onValueChange = { ipPort = it }, singleLine = true, label = { Text("IP:puerto") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it },
                singleLine = true,
                label = { Text("Código (si es la primera vez)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = { comingSoon(context) }, modifier = Modifier.padding(top = 8.dp)) { Text("Conectar por IP") }
        }
    }
    PairedDevicesSync()
}

/**
 * "Dispositivos emparejados": each paired TV in its own card with its sync status, "Olvidar" and "Sincronizar con este
 * dispositivo" (this phone's choice only); then one "Sincronizar ahora" for every selected TV.
 */
@Composable
private fun PairedDevicesSync() {
    val c = VisionCompanion
    val scope = rememberCoroutineScope()
    if (c.pairedTvs.isEmpty()) {
        SettingsSection("Dispositivos emparejados") { SettingsNote("Todavía no hay dispositivos emparejados.") }
        return
    }
    SettingsSectionTitle("Dispositivos emparejados", Modifier.padding(top = 24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        c.pairedTvs.forEach { tv ->
            SettingsCard {
                SettingsRow(tv.name, supporting = tv.statusLine()) {
                    OutlinedButton(onClick = { c.forget(c.pairedTvs, tv) }) { Text("Olvidar") }
                }
                SettingsSwitchRow("Sincronizar con este dispositivo", tv.selected, { tv.selected = it })
            }
        }
        SettingsCard {
            SettingsRow(
                if (c.syncing) "Sincronizando…" else "Sincronizar ahora",
                supporting = "Con todos los dispositivos elegidos que estén en tu red",
                enabled = !c.syncing && c.pairedTvs.any { it.selected },
                onClick = {
                    c.syncing = true
                    scope.launch {
                        try {
                            delay(1_500)
                        } finally {
                            c.finishSync(c.pairedTvs)
                        }
                    }
                },
            )
        }
    }
}
