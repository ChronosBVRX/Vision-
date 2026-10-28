@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import com.chronosbvrx.visionplus.home.data.StartTab
import com.chronosbvrx.visionplus.home.data.TvInputSource
import com.chronosbvrx.visionplus.home.data.TvSourceKind
import com.chronosbvrx.visionplus.home.data.tvInputSubtitle
import com.chronosbvrx.visionplus.home.data.visibleInputs

/** Las secciones de los ajustes del launcher. */
enum class LauncherSettingsSection(val label: String, val detail: String) {
    STARTUP("Inicio", "Pestaña y fila de entradas"),
    INPUTS("Entradas", "TV abierta y HDMI"),
    SYSTEM("Sistema", "Red, pantalla y sonido"),
    UPDATES("Actualizaciones", "Launcher y Vision+"),
    ABOUT("Acerca de", "Versión y autoría"),
}

/** Las páginas del sistema que el launcher abre sin obligarte a buscar en Ajustes. */
enum class LauncherSystemPage(val label: String) {
    WIFI("Wi-Fi y red"),
    DISPLAY("Pantalla"),
    SOUND("Sonido"),
    STORAGE("Almacenamiento"),
    DATE("Fecha y hora"),
    LANGUAGE("Idioma y región"),
    ACCESSIBILITY("Accesibilidad"),
    SCREENSAVER("Protector de pantalla"),
    APP_LIST("Apps del sistema"),
    DEFAULT_APPS("Apps predeterminadas"),
    SETTINGS("Ajustes de la tele"),
}

/**
 * Las acciones del panel. Van agrupadas para que la pantalla no dependa de una firma de
 * veinte parámetros y para que el cableado se lea de un vistazo.
 */
class LauncherSettingsActions(
    val onDismiss: () -> Unit,
    val onSection: (LauncherSettingsSection) -> Unit = {},
    val onStartTab: (StartTab) -> Unit = {},
    val onToggleInputsRow: () -> Unit = {},
    val onToggleInput: (String) -> Unit = {},
    val onRefreshInputs: () -> Unit = {},
    val onOpenInput: (TvInputSource) -> Unit = {},
    val onOpenLive: () -> Unit = {},
    val onPersonalize: () -> Unit = {},
    val onSystemPage: (LauncherSystemPage) -> Unit = {},
    val onRequestHome: () -> Unit = {},
    val onHomeSettings: () -> Unit = {},
    val onUpdateHome: () -> Unit = {},
    val onUpdateVision: () -> Unit = {},
)

/** Lo que se ve del equipo en la sección «Sistema», ya resuelto fuera de la interfaz. */
data class LauncherSystemInfo(
    val device: String = "",
    val network: String = "",
    val storage: String = "",
)

/** La siguiente pestaña al pulsar OK sobre la preferencia (ciclo, como el resto del panel). */
internal fun nextStartTab(current: StartTab, inputsAvailable: Boolean = true): StartTab {
    // En una TV box no se ofrece arrancar en «Entradas»: no existen ahí.
    val all = StartTab.entries.filter { inputsAvailable || it != StartTab.INPUTS }
    if (all.isEmpty()) return StartTab.HOME
    val index = all.indexOf(current)
    return if (index < 0) all.first() else all[(index + 1) % all.size]
}

/**
 * Los ajustes PROPIOS del launcher, separados de «Personalizar Inicio»: aquí vive lo que hace
 * que se sienta un launcher de televisión y no una vitrina — cómo abre, las entradas de video
 * del equipo, las páginas del sistema, las actualizaciones y la autoría.
 */
@Composable
fun LauncherSettingsDialog(
    section: LauncherSettingsSection,
    profileName: String,
    config: HomeConfig,
    inputs: List<TvInputSource>,
    homeVersion: String,
    visionInstalled: Boolean,
    visionVersion: String,
    latestVisionVersion: String,
    homeDefault: Boolean,
    currentHome: String?,
    system: LauncherSystemInfo,
    updateState: HomeUpdate.State,
    visionState: HomeUpdate.State,
    actions: LauncherSettingsActions,
) {
    Dialog(
        onDismissRequest = actions.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val focus = rememberDialogFocus()
        Box(Modifier.fillMaxSize().background(HomeColors.Deep).dialogRootFocus(focus)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Ajustes de Vision+ Home",
                            color = HomeColors.Text,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${profileName.trim().ifBlank { "Principal" }} · se guarda en este equipo · OK cambia cada opción",
                            color = HomeColors.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HomeButton(
                        label = "Cerrar",
                        onClick = actions.onDismiss,
                        icon = Icons.Rounded.Close,
                        primary = false,
                        modifier = Modifier.dialogFallbackFocus(focus),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Riel de secciones: el mando se mueve en vertical y entra a la derecha.
                    Column(Modifier.width(238.dp).fillMaxHeight().focusGroup()) {
                        LauncherSettingsSection.entries.forEach { item ->
                            RailItem(
                                section = item,
                                selected = item == section,
                                onClick = { actions.onSection(item) },
                                modifier = if (item == section) Modifier.dialogFocus(focus) else Modifier,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                "v$homeVersion",
                                color = HomeColors.TextFaint,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "por ChronosBVRX",
                                color = HomeColors.TextFaint,
                                style = MaterialTheme.typography.bodySmall,
                                letterSpacing = 0.6.sp,
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (section) {
                            LauncherSettingsSection.STARTUP -> StartupSection(
                                config = config,
                                hasInputs = inputs.isNotEmpty(),
                                homeDefault = homeDefault,
                                currentHome = currentHome,
                                actions = actions,
                            )

                            LauncherSettingsSection.INPUTS -> InputsSection(
                                inputs = inputs,
                                config = config,
                                actions = actions,
                            )

                            LauncherSettingsSection.SYSTEM -> SystemSection(
                                system = system,
                                actions = actions,
                            )

                            LauncherSettingsSection.UPDATES -> UpdatesSection(
                                homeVersion = homeVersion,
                                visionInstalled = visionInstalled,
                                visionVersion = visionVersion,
                                latestVisionVersion = latestVisionVersion,
                                updateState = updateState,
                                visionState = visionState,
                                actions = actions,
                            )

                            LauncherSettingsSection.ABOUT -> AboutSection(homeVersion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    section: LauncherSettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.panel,
        modifier = modifier.fillMaxWidth(),
        focusedScale = 1.01f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        selected -> HomeColors.SurfaceHigh
                        focused -> HomeColors.Surface
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .background(if (selected) HomeColors.Blue else Color.Transparent),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    section.label,
                    color = if (selected || focused) HomeColors.Text else HomeColors.TextDim,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Text(
                    section.detail,
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StartupSection(
    config: HomeConfig,
    hasInputs: Boolean,
    homeDefault: Boolean,
    currentHome: String?,
    actions: LauncherSettingsActions,
) {
    SettingsList {
        item { SettingsHeader("Inicio", "El launcher abre como tú lo dejes, no como el fabricante quiera.") }
        item {
            SettingsValueRow(
                title = "Pestaña al abrir",
                detail = "Al encender la tele · el botón Home siempre vuelve a Inicio",
                value = config.startTab.label,
                onClick = { actions.onStartTab(nextStartTab(config.startTab, inputsAvailable = hasInputs)) },
            )
        }
        if (hasInputs) {
            item {
                val visible = "inputs" !in config.hiddenRows
                SettingsValueRow(
                    title = "Fila de entradas en el Inicio",
                    detail = "TV abierta y HDMI · solo las fuentes que reporta este equipo",
                    value = if (visible) "Sí" else "No",
                    onClick = actions.onToggleInputsRow,
                )
            }
        }
        item {
            SettingsValueRow(
                title = "Personalizar Inicio",
                detail = "Canales, apps, orden de las filas y apariencia",
                value = "Abrir",
                icon = Icons.Rounded.Tune,
                onClick = actions.onPersonalize,
            )
        }
        item {
            SettingsCard(
                title = if (homeDefault) "Vision+ Home es el Inicio de la tele" else "El Inicio de la tele es ${currentHome ?: "el de fábrica"}",
                body = if (homeDefault) {
                    "Cada vez que pulses Home vas a volver aquí. Puedes cambiarlo cuando quieras."
                } else {
                    "Puedes poner Vision+ Home como Inicio sin buscar en los ajustes: te llevo a la ventana exacta."
                },
            ) {
                HomeButton(
                    label = if (homeDefault) "Cambiar el Inicio" else "Poner como Inicio",
                    onClick = if (homeDefault) actions.onHomeSettings else actions.onRequestHome,
                    icon = Icons.Rounded.Home,
                    primary = !homeDefault,
                )
                if (!homeDefault) {
                    HomeButton(
                        label = "Cambiar el Inicio",
                        onClick = actions.onHomeSettings,
                        icon = Icons.Rounded.Settings,
                        primary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputsSection(
    inputs: List<TvInputSource>,
    config: HomeConfig,
    actions: LauncherSettingsActions,
) {
    val visible = visibleInputs(inputs, config.hiddenInputs)
    SettingsList {
        item {
            SettingsHeader(
                "Entradas de la tele",
                "El launcher muestra solo las fuentes que el equipo reporta: TV abierta, HDMI y las demás.",
            )
        }
        if (inputs.isEmpty()) {
            item {
                SettingsCard(
                    title = "Este equipo no tiene entradas de TV",
                    body = "En una tele verás TV abierta y sus HDMI. Una TV box o una PC no tienen sintonizador ni entradas de video, así que el launcher no muestra la sección en vez de inventarla.",
                ) {
                    HomeButton(
                        label = "Buscar de nuevo",
                        onClick = actions.onRefreshInputs,
                        icon = Icons.Rounded.Refresh,
                        primary = true,
                    )
                    HomeButton(
                        label = "Abrir TV en vivo",
                        onClick = actions.onOpenLive,
                        icon = Icons.Rounded.LiveTv,
                        primary = false,
                    )
                }
            }
        } else {
            item {
                Text(
                    "${visible.size} visibles de ${inputs.size} · OK abre la fuente, el ojo la oculta",
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(inputs, key = { it.id.ifBlank { it.label } }) { item ->
                InputSettingsRow(
                    item = item,
                    hidden = item.id in config.hiddenInputs,
                    onOpen = { actions.onOpenInput(item) },
                    onToggle = { actions.onToggleInput(item.id) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeButton(
                        label = "Buscar de nuevo",
                        onClick = actions.onRefreshInputs,
                        icon = Icons.Rounded.Refresh,
                        primary = false,
                    )
                    HomeButton(
                        label = "Ajustes de la tele",
                        onClick = { actions.onSystemPage(LauncherSystemPage.SETTINGS) },
                        icon = Icons.Rounded.Settings,
                        primary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputSettingsRow(
    item: TvInputSource,
    hidden: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .focusGroup()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FocusableSurface(
            onClick = onOpen,
            shape = HomeShapes.panel,
            modifier = Modifier.weight(1f),
            focusedScale = 1.01f,
        ) { focused ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InputIcon(item, size = 34)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        color = if (hidden) HomeColors.TextFaint else HomeColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tvInputSubtitle(item),
                        color = HomeColors.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Text(
                    if (hidden) "Oculta" else "Ver",
                    color = if (hidden) HomeColors.TextFaint else HomeColors.BlueBright,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        MiniAction(
            icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            label = if (hidden) "Mostrar ${item.label}" else "Ocultar ${item.label}",
            onClick = onToggle,
        )
    }
}

@Composable
private fun SystemSection(system: LauncherSystemInfo, actions: LauncherSettingsActions) {
    SettingsList {
        item {
            SettingsHeader(
                "Sistema",
                listOf(system.device, system.network, system.storage)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
        val pages = listOf(
            LauncherSystemPage.WIFI to Icons.Rounded.Wifi,
            LauncherSystemPage.DISPLAY to Icons.Rounded.Tv,
            LauncherSystemPage.SOUND to Icons.AutoMirrored.Rounded.VolumeUp,
            LauncherSystemPage.STORAGE to Icons.Rounded.Storage,
            LauncherSystemPage.DATE to Icons.Rounded.Schedule,
            LauncherSystemPage.LANGUAGE to Icons.Rounded.Language,
            LauncherSystemPage.ACCESSIBILITY to Icons.Rounded.Accessibility,
            LauncherSystemPage.SCREENSAVER to Icons.Rounded.Image,
            LauncherSystemPage.APP_LIST to Icons.Rounded.Apps,
            LauncherSystemPage.DEFAULT_APPS to Icons.Rounded.SettingsRemote,
        )
        items(pages, key = { it.first.name }) { page ->
            SettingsValueRow(
                title = page.first.label,
                detail = "Se abre la pantalla del sistema",
                value = "",
                icon = page.second,
                onClick = { actions.onSystemPage(page.first) },
            )
        }
        item {
            SettingsValueRow(
                title = "Ajustes de la tele",
                detail = "Todas las opciones del equipo",
                value = "",
                icon = Icons.Rounded.Settings,
                onClick = { actions.onSystemPage(LauncherSystemPage.SETTINGS) },
            )
        }
    }
}

@Composable
private fun UpdatesSection(
    homeVersion: String,
    visionInstalled: Boolean,
    visionVersion: String,
    latestVisionVersion: String,
    updateState: HomeUpdate.State,
    visionState: HomeUpdate.State,
    actions: LauncherSettingsActions,
) {
    SettingsList {
        item { SettingsHeader("Actualizaciones", "Todo se descarga del release oficial de Vision+, sin tienda.") }
        item {
            SettingsValueRow(
                title = "Vision+ Home",
                detail = when (updateState) {
                    HomeUpdate.State.Idle -> "Launcher de la tele · v$homeVersion"
                    HomeUpdate.State.Checking -> "Buscando la última versión…"
                    is HomeUpdate.State.Downloading -> "Descargando… ${updateState.percent} %"
                    HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
                    is HomeUpdate.State.Failed -> "No se pudo: ${updateState.message}"
                },
                value = "v$homeVersion",
                icon = Icons.Rounded.Download,
                onClick = actions.onUpdateHome,
            )
        }
        item {
            SettingsValueRow(
                title = if (visionInstalled) "Vision+" else "Instalar Vision+",
                detail = when {
                    !visionInstalled -> "El launcher se baja la app del release oficial"
                    visionVersion.isBlank() -> "Instalada, pero no responde al Inicio"
                    latestVisionVersion.isNotBlank() && visionVersion != latestVisionVersion ->
                        "v$visionVersion → v$latestVisionVersion"
                    else -> "v$visionVersion · es la que abre el contenido"
                },
                value = if (visionInstalled) "Actualizar" else "Instalar",
                icon = Icons.Rounded.SettingsRemote,
                onClick = actions.onUpdateVision,
            )
        }
        item {
            val visionStateText = when (visionState) {
                HomeUpdate.State.Checking -> "Buscando Vision+ en el release…"
                is HomeUpdate.State.Downloading -> "Descargando Vision+… ${visionState.percent} %"
                HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
                is HomeUpdate.State.Failed -> "Vision+: ${visionState.message}"
                HomeUpdate.State.Idle -> ""
            }
            if (visionStateText.isNotBlank()) {
                Text(
                    visionStateText,
                    color = HomeColors.BlueBright,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AboutSection(homeVersion: String) {
    SettingsList {
        item { SettingsHeader("Acerca de", "Vision+ Home, el launcher de tu televisión.") }
        item {
            SettingsCard(
                title = "Vision+ Home",
                body = "Versión $homeVersion. Hecho para vivir en la tele: todo se maneja con el mando, sin cursores ni teclados.",
            ) {}
        }
        item {
            SettingsCard(
                title = "Autoría",
                body = "ChronosBVRX. Un estudio de una sola persona. Las mejores historias no se compran, se revelan.",
            ) {}
        }
        item {
            SettingsCard(
                title = "Código abierto",
                body = "Interfaz con Jetpack Compose, imágenes con Coil e iconografía de Material Icons (Apache 2.0).",
            ) {}
        }
        item {
            Text(
                "Vision+ Home no reproduce video: abre el contenido en Vision+ y las entradas del televisor en su propia app.",
                color = HomeColors.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        content = content,
    )
}

@Composable
private fun SettingsHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, color = HomeColors.Text, style = MaterialTheme.typography.headlineSmall)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = HomeColors.TextFaint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    detail: String,
    value: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    FocusableSurface(onClick = onClick, shape = HomeShapes.panel, modifier = Modifier.fillMaxWidth()) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = if (focused) Color.White else HomeColors.BlueBright,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = HomeColors.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = HomeColors.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value.isNotBlank()) {
                Text(
                    value,
                    color = HomeColors.BlueBright,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            } else if (icon != null) {
                Icon(Icons.Rounded.ChevronRight, null, tint = HomeColors.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HomeColors.Surface, HomeShapes.panel)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(title, color = HomeColors.Text, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(body, color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/** El icono de la fuente: el del sistema si lo trae, si no el de su tipo. */
@Composable
internal fun InputIcon(item: TvInputSource, size: Int = 30) {
    val icon = item.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = item.label,
            modifier = Modifier.size(size.dp),
        )
        return
    }
    Icon(
        when (item.kind) {
            TvSourceKind.TUNER -> Icons.Rounded.Tv
            TvSourceKind.HDMI -> Icons.Rounded.SettingsInputHdmi
            TvSourceKind.OTHER -> Icons.Rounded.SettingsInputComponent
        },
        contentDescription = item.label,
        tint = HomeColors.BlueBright,
        modifier = Modifier.size(size.dp),
    )
}
