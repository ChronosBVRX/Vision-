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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.EffectsMode
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.follow.FollowStore
import com.chronosbvrx.visionplus.data.kids.KidsMode
import com.chronosbvrx.visionplus.data.remote.RemoteBus
import com.chronosbvrx.visionplus.data.remote.RemoteServer
import com.chronosbvrx.visionplus.data.stats.formatMinutes
import com.chronosbvrx.visionplus.data.stats.kindLabel
import com.chronosbvrx.visionplus.data.stats.summarize
import com.chronosbvrx.visionplus.ui.kids.TvKidsPinDialog
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import com.chronosbvrx.visionplus.ui.settings.rememberUpdateCheck
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        label,
        trailing = {
            Icon(VisionIcons.ChevronLeft, contentDescription = null, tint = VisionTextSecondary, modifier = Modifier.size(18.dp))
            Icon(VisionIcons.SeeAll, contentDescription = null, tint = VisionTextSecondary, modifier = Modifier.size(18.dp))
        },
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
    TvActionOption("Omitir intro: ${s.skipIntroModeLabel(s.skipIntroMode)}") {
        s.setSkipIntroMode(context, s.nextSkipIntroMode(s.skipIntroMode))
    }
    Note("Botón durante la entrada (como Netflix), automático sin botón, o desactivado. Los tiempos vienen de la comunidad; en los créditos, la tarjeta de siguiente capítulo aparece antes del final.")

    TvActionOption("Siguiente episodio automático: ${if (s.autoPlayNext) "Sí" else "No"}") {
        s.setAutoPlayNext(context, !s.autoPlayNext)
    }
    Note("Al terminar el capítulo, arranca solo con el siguiente. Si el modo rápido está activo, empieza al final de los créditos.")

    TvActionOption("Avanzar en créditos (rápido): ${if (s.nextOnCredits) "Sí" else "No"}") {
        s.setNextOnCredits(context, !s.nextOnCredits)
    }
    Note("Apagado (recomendado): espera al final para no cortar créditos ni escenas post-créditos. Encendido: empieza al terminar los créditos.")

    TvActionOption("Reanudar automáticamente: ${if (s.autoResume) "Sí" else "No"}") {
        s.setAutoResume(context, !s.autoResume)
    }
    Note("Continúa directo desde el minuto guardado sin desplegar el diálogo de confirmación.")

    TvSectionTitle("Subtítulos")
    TvActionOption("Tamaño: ${s.subSizeLabel(s.subSize)}") {
        s.setSubSize(context, s.nextSubSize(s.subSize))
    }
    Note("Ahora los pinta la app: el tamaño sí se aplica.")
    val subPosLabel = if (s.subPosition == VisionSettings.SUB_POS_TOP) "Arriba" else "Abajo"
    TvActionOption("Posición: $subPosLabel") {
        s.setSubPosition(
            context,
            if (s.subPosition == VisionSettings.SUB_POS_TOP) VisionSettings.SUB_POS_BOTTOM
            else VisionSettings.SUB_POS_TOP,
        )
    }
    TvActionOption("Bajar el siguiente capítulo con WiFi: ${if (s.autoDownloadNext) "Sí" else "No"}") {
        s.setAutoDownloadNext(context, !s.autoDownloadNext)
    }
    Note("Al salir del reproductor deja bajando solo el siguiente capítulo pendiente.")
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
    var showSync by rememberSaveable { mutableStateOf(false) }
    if (showSync) TvSyncDialog(onDismiss = { showSync = false })
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    if (showProfiles) TvProfilesDialog(onDismiss = { showProfiles = false })

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
    TvActionOption("Identidad del equipo: $slotStatus") { showSlotDialog = true }
    Note("Se asigna sola al arrancar; el código del operador es solo respaldo.")
    TvActionOption("Enviar diagnósticos: ${if (s.diagnosticsEnabled) "Sí" else "No"}") {
        s.setDiagnosticsEnabled(context, !s.diagnosticsEnabled)
    }
    Note("Crashes y errores técnicos se envían al servidor para corregirlos (sin datos personales).")
    TvActionOption("Enviar diagnóstico ahora") {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.feedback("Diagnóstico manual desde Ajustes (TV)")
        Toast.makeText(context, "Diagnóstico enviado", Toast.LENGTH_SHORT).show()
    }

    TvSectionTitle("Control remoto")
    val pairedDevices = RemoteBus.pairedDevices.value
    if (pairedDevices.isEmpty()) {
        Note("Ningún dispositivo vinculado todavía. Al emparejar con el PIN, cada teléfono queda recordado y la próxima vez entra solo.")
    } else {
        Note("Dispositivos vinculados: ${pairedDevices.size} · entran solos, sin PIN.")
        pairedDevices.forEach { device -> Note("· $device") }
        TvActionOption("Olvidar dispositivos vinculados") {
            val forgotten = RemoteServer.forgetPairings(context)
            Toast.makeText(context, "Vínculos olvidados: $forgotten", Toast.LENGTH_SHORT).show()
        }
        Note("Los teléfonos tendrán que emparejar de nuevo con el PIN que muestre la tele.")
    }

    TvSectionTitle("Perfiles")
    TvActionOption(
        "Cambiar de perfil (${com.chronosbvrx.visionplus.data.profile.ProfileStore.active(context).name})",
    ) { showProfiles = true }
    Note("Cada perfil tiene su «Seguir viendo», historial y Mi lista. Los ajustes y las descargas son del equipo.")

    TvSectionTitle("Continuidad entre equipos")
    TvActionOption("Sincronizar con mi teléfono") { showSync = true }
    Note("Comparte el minuto donde te quedaste y Mi lista. La tele muestra un código; se escribe una vez en el teléfono.")

    TvSectionTitle("Modo niños")
    TvKidsSettingsRows()
    TvSectionTitle("Mi actividad")
    TvActivityRows()

    TvSectionTitle("Actualizaciones y Sistema")
    Note("Versión instalada: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
    TvActionOption(if (update.checking) "Buscando…" else "Buscar actualizaciones") { update.run() }
    TvActionOption("Licencias de software libre") { showNotices = true }
}

/** Filas de Modo niños para Ajustes TV: crear PIN, bloquear y cambiar PIN. */
@Composable
private fun ColumnScope.TvKidsSettingsRows() {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(KidsMode.hasPin(context)) }
    var lockedTick by remember { mutableStateOf(0) }
    remember(lockedTick) { KidsMode.locked }
    var askSetup by rememberSaveable { mutableStateOf(false) }
    var askUnlock by rememberSaveable { mutableStateOf(false) }
    var askChange by rememberSaveable { mutableStateOf(false) }
    if (askSetup) {
        TvKidsPinDialog(setup = true, onDismiss = { askSetup = false }, onOk = { pin ->
            if (KidsMode.setPin(context, pin)) {
                hasPin = true
                askSetup = false
                Toast.makeText(context, "PIN creado", Toast.LENGTH_SHORT).show()
            }
        })
    }
    if (askUnlock) {
        TvKidsPinDialog(setup = false, onDismiss = { askUnlock = false }, onOk = { pin ->
            if (KidsMode.unlock(context, pin)) {
                askUnlock = false
                lockedTick++
            } else {
                Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            }
        })
    }
    if (askChange) {
        TvKidsPinDialog(setup = false, onDismiss = { askChange = false }, onOk = { pin ->
            if (KidsMode.verify(context, pin)) {
                askChange = false
                askSetup = true
            } else {
                Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            }
        })
    }
    if (!hasPin) {
        TvActionOption("Crear PIN de niños") { askSetup = true }
        Note("De 4 a 8 dígitos. Solo muestra contenido infantil al bloquear.")
    } else {
        TvActionOption("Bloqueo infantil: ${if (KidsMode.locked) "Activo" else "Apagado"}") {
            if (KidsMode.locked) askUnlock = true
            else {
                KidsMode.lock(context)
                lockedTick++
            }
        }
        Note("En vivo, deportes, lista y descargas piden el PIN.")
        TvActionOption("Cambiar PIN") { askChange = true }
    }
}

/** «Mi actividad» para Ajustes TV: resumen local, nada se sube. */
@Composable
private fun ColumnScope.TvActivityRows() {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            val entries = PlaybackHistory.list(context)
            val films = UserLibrary.played(context).associateBy { it.id }
            val stats = summarize(entries, films)
            val following = FollowStore.followed(context).size
            buildList {
                if (stats.isEmpty && following == 0) return@buildList
                if (stats.minutesWatched > 0) add("Tiempo visto" to formatMinutes(stats.minutesWatched))
                if (stats.episodesFinished > 0) add("Capítulos terminados" to "${stats.episodesFinished}")
                if (stats.filmsFinished > 0) add("Películas terminadas" to "${stats.filmsFinished}")
                if (stats.topKind.isNotBlank()) add("Lo que más ves" to kindLabel(stats.topKind))
                if (following > 0) add("Series que sigues" to "$following")
            }
        }
        lines = result
        loaded = true
    }
    if (!loaded) {
        Note("Calculando…")
    } else if (lines.isEmpty()) {
        Note("Aún no hay actividad: reproduce algo y aquí verás tu resumen.")
    } else {
        lines.forEach { (k, v) -> Note("$k: $v") }
    }
}
