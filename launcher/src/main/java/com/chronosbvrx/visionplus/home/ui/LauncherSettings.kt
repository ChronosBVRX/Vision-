@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.chronosbvrx.visionplus.home.ui

import androidx.activity.compose.BackHandler
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
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import com.chronosbvrx.visionplus.home.data.TvInputSource
import com.chronosbvrx.visionplus.home.data.TvSourceKind
import com.chronosbvrx.visionplus.home.data.tvInputSubtitle

/** Las secciones de los ajustes del launcher. */
enum class LauncherSettingsSection(val label: String, val detail: String) {
    STARTUP("Inicio", "Inicio del sistema y personalización"),
    UPDATES("Actualizaciones", "Launcher y Vision+"),
    ABOUT("Acerca de", "Versión y autoría"),
}

/**
 * Las acciones del panel. Van agrupadas para que la pantalla no dependa de una firma de
 * veinte parámetros y para que el cableado se lea de un vistazo.
 */
class LauncherSettingsActions(
    val onDismiss: () -> Unit,
    val onSection: (LauncherSettingsSection) -> Unit = {},
    val onOpenPersistent: () -> Unit = {},
    val onPersonalize: () -> Unit = {},
    val onRequestHome: () -> Unit = {},
    val onHomeSettings: () -> Unit = {},
    val onUpdateHome: () -> Unit = {},
    val onCheckHome: () -> Unit = {},
    val onUpdateVision: () -> Unit = {},
    val onCheckVision: () -> Unit = {},
)

/**
 * Los ajustes PROPIOS del launcher, separados de «Personalizar Inicio»: aquí vive lo que hace
 * que se sienta un launcher de televisión y no una vitrina — cómo abre, las entradas de video
 * del equipo, las páginas del sistema, las actualizaciones y la autoría.
 */
@Composable
fun LauncherSettingsDialog(
    section: LauncherSettingsSection,
    profileName: String,
    homeVersion: String,
    visionInstalled: Boolean,
    visionVersion: String,
    latestVisionVersion: String,
    homeDefault: Boolean,
    currentHome: String?,
    persistentActive: Boolean,
    updateState: HomeUpdate.State,
    visionState: HomeUpdate.State,
    visionChecking: Boolean,
    actions: LauncherSettingsActions,
) {
    Dialog(
        onDismissRequest = actions.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = actions.onDismiss)
        val focus = rememberDialogFocus()
        Box(Modifier.fillMaxSize().background(HomeColors.Deep).dialogRootFocus(focus).dismissOnBack(actions.onDismiss)) {
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
                                homeDefault = homeDefault,
                                currentHome = currentHome,
                                persistentActive = persistentActive,
                                actions = actions,
                            )

                            LauncherSettingsSection.UPDATES -> UpdatesSection(
                                homeVersion = homeVersion,
                                visionInstalled = visionInstalled,
                                visionVersion = visionVersion,
                                latestVisionVersion = latestVisionVersion,
                                updateState = updateState,
                                visionState = visionState,
                                visionChecking = visionChecking,
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
    homeDefault: Boolean,
    currentHome: String?,
    persistentActive: Boolean,
    actions: LauncherSettingsActions,
) {
    SettingsList {
        item { SettingsHeader("Inicio", "El launcher abre como tú lo dejes, no como el fabricante quiera.") }
        item {
            SettingsValueRow(
                title = "Modo persistente",
                detail = if (persistentActive) {
                    "Activo · si el sistema intenta abrir su Inicio, Vision+ Home vuelve al frente"
                } else {
                    "Actívalo en Accesibilidad · evita que Google TV vuelva a su Inicio"
                },
                value = if (persistentActive) "Activo" else "Activar",
                icon = Icons.Rounded.Accessibility,
                onClick = actions.onOpenPersistent,
            )
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
private fun UpdatesSection(
    homeVersion: String,
    visionInstalled: Boolean,
    visionVersion: String,
    latestVisionVersion: String,
    updateState: HomeUpdate.State,
    visionState: HomeUpdate.State,
    visionChecking: Boolean,
    actions: LauncherSettingsActions,
) {
    val launcherPending = HomeUpdate.launcherPending()
    val launcherLatest = HomeUpdate.launcherLatest
    val launcherChecking = HomeUpdate.launcherChecking
    val visionOlder = visionVersion.isNotBlank() && latestVisionVersion.isNotBlank() &&
        HomeUpdate.isOlder(visionVersion, latestVisionVersion)

    SettingsList {
        item {
            SettingsHeader(
                "Actualizaciones",
                "La comprobación automática no siempre es instantánea: aquí puedes buscar AHORA la última versión de Vision+ Home y de Vision+.",
            )
        }

        item {
            SettingsCard(
                title = "Vision+ Home · v$homeVersion",
                body = when {
                    launcherChecking -> "Buscando la última versión publicada en el release…"
                    updateState is HomeUpdate.State.Checking -> "Preparando la actualización…"
                    updateState is HomeUpdate.State.Downloading -> "Descargando… ${updateState.percent} %"
                    updateState is HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
                    updateState is HomeUpdate.State.Failed -> "No se pudo: ${updateState.message}"
                    launcherPending && launcherLatest != null ->
                        "Hay una versión más nueva: v${launcherLatest.versionName}. Se instala encima sin perder tu personalización."
                    launcherLatest != null && HomeUpdate.isOlder(homeVersion, launcherLatest.versionName) ->
                        "v$homeVersion · última publicada v${launcherLatest.versionName}"
                    launcherLatest != null ->
                        "v$homeVersion · última publicada v${launcherLatest.versionName} · estás al día"
                    else -> "Aún no se ha consultado el release; pulsa «Buscar actualización»."
                },
            ) {
                if (launcherPending && launcherLatest != null) {
                    HomeButton(
                        label = "Actualizar a v${launcherLatest.versionName}",
                        onClick = actions.onUpdateHome,
                        icon = Icons.Rounded.Download,
                        primary = true,
                    )
                }
                HomeButton(
                    label = if (launcherChecking) "Buscando…" else "Buscar actualización",
                    onClick = if (launcherChecking) ({}) else actions.onCheckHome,
                    icon = Icons.Rounded.Refresh,
                    primary = !launcherPending,
                )
            }
        }

        item {
            SettingsCard(
                title = if (visionInstalled) "Vision+ · v${visionVersion.ifBlank { "?" }}" else "Vision+ no instalada",
                body = when {
                    visionChecking -> "Buscando la última versión de Vision+ en el release…"
                    visionState is HomeUpdate.State.Checking -> "Preparando la instalación de Vision+…"
                    visionState is HomeUpdate.State.Downloading -> "Descargando Vision+… ${visionState.percent} %"
                    visionState is HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
                    visionState is HomeUpdate.State.Failed -> "No se pudo: ${visionState.message}"
                    !visionInstalled -> "El launcher puede descargarla e instalarla desde el release oficial."
                    visionVersion.isBlank() -> "Está instalada, pero no responde al Inicio."
                    visionOlder -> "Hay una versión más nueva publicada: v$latestVisionVersion (tienes v$visionVersion)."
                    latestVisionVersion.isBlank() -> "Aún no se ha consultado el release; pulsa «Buscar actualización»."
                    else -> "v$visionVersion · última publicada v$latestVisionVersion · estás al día"
                },
            ) {
                if (!visionInstalled || visionOlder) {
                    HomeButton(
                        label = if (!visionInstalled) "Instalar Vision+" else "Actualizar Vision+",
                        onClick = actions.onUpdateVision,
                        icon = Icons.Rounded.SettingsRemote,
                        primary = true,
                    )
                }
                HomeButton(
                    label = if (visionChecking) "Buscando…" else "Buscar actualización",
                    onClick = if (visionChecking) ({}) else actions.onCheckVision,
                    icon = Icons.Rounded.Refresh,
                    primary = visionInstalled && !visionOlder,
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
