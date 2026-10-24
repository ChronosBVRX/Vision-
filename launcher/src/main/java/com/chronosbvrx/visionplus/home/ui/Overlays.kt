package com.chronosbvrx.visionplus.home.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import kotlinx.coroutines.delay

/**
 * El «centro de control» del sistema: perfil, ajustes, actualización y la puerta al Inicio
 * del sistema. Es un diálogo de ventana propia: el foco no se escapa al fondo.
 */
@Composable
fun CommandCenterDialog(
    profileName: String,
    profiles: Int,
    onDismiss: () -> Unit,
    onOpenVisionSettings: () -> Unit,
    onSystemSettings: () -> Unit,
    onHomeSettings: () -> Unit,
    onUpdate: () -> Unit,
    visionInstalled: Boolean = true,
    visionInstallState: HomeUpdate.State = HomeUpdate.State.Idle,
    onInstallVision: () -> Unit = {},
    appOutdated: Boolean = false,
    appVersion: String = "",
    latestAppVersion: String = "",
    onUpdateVision: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { first.requestFocus() }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(410.dp)
                    .background(HomeColors.Deep)
                    .padding(horizontal = 26.dp, vertical = 26.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Centro de control",
                        color = HomeColors.Text,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    HomeButton(label = "Cerrar", onClick = onDismiss, icon = Icons.Rounded.Close, primary = false)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(HomeShapes.panel)
                        .background(HomeColors.Surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(HomeColors.Blue, Color(0xFF6E4BF6)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profileName.trim().ifBlank { "Principal" }.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            profileName.trim().ifBlank { "Principal" },
                            color = HomeColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (profiles > 1) "$profiles perfiles en este equipo" else "Perfil principal",
                            color = HomeColors.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HomeButton(label = "Cambiar", onClick = onOpenVisionSettings, icon = null)
                }
                Spacer(Modifier.height(18.dp))
                if (!visionInstalled) {
                    PanelAction(
                        icon = Icons.Rounded.Download,
                        title = "Instalar Vision+",
                        detail = when (visionInstallState) {
                            is HomeUpdate.State.Checking -> "Buscando la última versión…"
                            is HomeUpdate.State.Downloading -> "Descargando… ${visionInstallState.percent} %"
                            is HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
                            is HomeUpdate.State.Failed -> "No se pudo: ${visionInstallState.message}"
                            HomeUpdate.State.Idle -> "El launcher se baja la app del release oficial"
                        },
                        onClick = onInstallVision,
                    )
                } else if (appOutdated) {
                    PanelAction(
                        icon = Icons.Rounded.Download,
                        title = "Actualizar Vision+",
                        detail = if (appVersion.isBlank()) {
                            "Vision+ no responde al Inicio; actualízalo"
                        } else {
                            "v$appVersion → v$latestAppVersion"
                        },
                        onClick = onUpdateVision,
                    )
                }
                PanelAction(
                    icon = Icons.Rounded.SettingsRemote,
                    title = "Ajustes de Vision+",
                    detail = "Perfiles, continuidad, calidad y descargas",
                    onClick = onOpenVisionSettings,
                    modifier = Modifier.focusRequester(first),
                )
                PanelAction(
                    icon = Icons.Rounded.Settings,
                    title = "Ajustes de la tele",
                    detail = "Wi-Fi, pantalla, apps y sistema",
                    onClick = onSystemSettings,
                )
                PanelAction(
                    icon = Icons.Rounded.Home,
                    title = "Cambiar el Inicio",
                    detail = "Elegir Vision+ Home o el Inicio del sistema",
                    onClick = onHomeSettings,
                )
                UpdateAction(onUpdate = onUpdate)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Vision+ Home ${HomeUpdate.versionName}",
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "por ChronosBVRX · EST. 2026",
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

@Composable
private fun UpdateAction(onUpdate: () -> Unit) {
    val state = HomeUpdate.state
    val detail = when (state) {
        HomeUpdate.State.Checking -> "Buscando la última versión…"
        is HomeUpdate.State.Downloading -> "Descargando… ${state.percent} %"
        HomeUpdate.State.Ready -> "Descarga lista: confirma en la pantalla del sistema"
        is HomeUpdate.State.Failed -> "No se pudo: ${state.message}"
        HomeUpdate.State.Idle -> "Descarga el APK del mismo release"
    }
    PanelAction(
        icon = Icons.Rounded.Download,
        title = "Actualizar Vision+ Home",
        detail = detail,
        onClick = onUpdate,
    )
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HomeShapes.panel)
            .background(HomeColors.Surface),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .padding(2.dp),
        ) {
            FocusableSurface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = HomeShapes.panel,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, null, tint = HomeColors.BlueBright, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = HomeColors.Text, style = MaterialTheme.typography.titleMedium)
                        Text(
                            detail,
                            color = HomeColors.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Buscar en Vision+ (texto o voz): el launcher no duplica el catálogo, lo delega. */
@Composable
fun SearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { first.requestFocus() }
        }
        val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onSearch(text) else onDismiss()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(560.dp)
                    .clip(HomeShapes.panel)
                    .background(HomeColors.Deep)
                    .padding(30.dp),
            ) {
                Text("Buscar", color = HomeColors.Text, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "El catálogo completo vive en Vision+: aquí solo eliges cómo buscarlo.",
                    color = HomeColors.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeButton(
                        label = "Buscar por voz",
                        onClick = {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Di el título de la película o serie")
                            val launched = runCatching { voice.launch(intent) }.isSuccess
                            if (!launched) onOpenSearch()
                        },
                        icon = Icons.Rounded.Mic,
                        primary = true,
                        modifier = Modifier.focusRequester(first),
                    )
                    HomeButton(
                        label = "Abrir el buscador",
                        onClick = onOpenSearch,
                        icon = Icons.Rounded.Search,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeButton(label = "Cerrar", onClick = onDismiss, icon = Icons.Rounded.Close, primary = false)
                }
            }
        }
    }
}
