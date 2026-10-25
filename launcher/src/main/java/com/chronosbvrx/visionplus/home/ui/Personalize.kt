package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.ChannelSort
import com.chronosbvrx.visionplus.home.data.DisplayConfig
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.LauncherAnimations
import com.chronosbvrx.visionplus.home.data.LauncherDensity
import com.chronosbvrx.visionplus.home.data.RowEntry
import com.chronosbvrx.visionplus.home.data.favoriteChannels
import com.chronosbvrx.visionplus.home.data.visibleChannels
import kotlinx.coroutines.delay

/** Una acción del menú contextual de una tarjeta. */
internal data class MenuAction(
    val title: String,
    val detail: String = "",
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        requestFocusSafely(first)
    }
    return first
}

@Composable
private fun PersonalizePanel(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    first: FocusRequester,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(HomeColors.Deep)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 26.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = HomeColors.Text, style = MaterialTheme.typography.headlineMedium)
                        Text(subtitle, color = HomeColors.TextFaint, style = MaterialTheme.typography.bodySmall)
                    }
                    HomeButton(
                        label = "Cerrar",
                        onClick = onDismiss,
                        icon = Icons.Rounded.Close,
                        primary = false,
                    )
                }
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}

@Composable
fun PersonalizeHubDialog(
    profileName: String,
    config: HomeConfig,
    channelCount: Int,
    appCount: Int,
    onChannels: () -> Unit,
    onApps: () -> Unit,
    onRows: () -> Unit,
    onAppearance: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberInitialFocus()
    var confirmReset by remember { mutableStateOf(false) }
    PersonalizePanel(
        title = "Personalizar Inicio",
        subtitle = "${profileName.ifBlank { "Principal" }} · se guarda por perfil",
        onDismiss = onDismiss,
        first = first,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PersonalizeRow(
                    icon = Icons.Rounded.LiveTv,
                    title = "Canales",
                    detail = "${config.channelFavorites.size} favoritos · ${config.hiddenChannels.size} ocultos · $channelCount en la lista",
                    onClick = onChannels,
                    modifier = Modifier.focusRequester(first),
                )
            }
            item {
                PersonalizeRow(
                    icon = Icons.Rounded.Apps,
                    title = "Apps",
                    detail = "${config.appFavorites.size} favoritas · ${config.hiddenApps.size} ocultas · $appCount instaladas",
                    onClick = onApps,
                )
            }
            item {
                PersonalizeRow(
                    icon = Icons.Rounded.ViewStream,
                    title = "Filas del Inicio",
                    detail = "${config.rowOrder.size - config.hiddenRows.size} visibles · orden y tarjetas",
                    onClick = onRows,
                )
            }
            item {
                PersonalizeRow(
                    icon = Icons.Rounded.Palette,
                    title = "Apariencia",
                    detail = "Densidad, animaciones, reloj y guía AHORA",
                    onClick = onAppearance,
                )
            }
            item {
                if (confirmReset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeButton(
                            label = "Sí, restablecer todo",
                            onClick = {
                                confirmReset = false
                                onReset()
                            },
                            primary = true,
                            modifier = Modifier.focusRequester(first),
                        )
                        HomeButton(label = "Cancelar", onClick = { confirmReset = false }, primary = false)
                    }
                } else {
                    PersonalizeRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = "Restablecer personalización",
                        detail = "Vuelve a los canales, apps, filas y apariencia originales",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(onClick = onClick, shape = HomeShapes.panel, modifier = modifier.fillMaxWidth()) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = if (focused) Color.White else HomeColors.BlueBright, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = HomeColors.Text, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = HomeColors.TextFaint, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = HomeColors.TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ItemActionsDialog(
    title: String,
    subtitle: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        requestFocusSafely(first)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f))
                // Si el menú salió con OK largo, el KeyUp que se está soltando no debe
                // activar la primera acción. Lo consume esta ventana.
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyUp && TvPressGuard.takeKeyUp()
                },
            contentAlignment = Alignment.Center,
        ) {
            val maxPanelHeight = maxHeight * 0.92f
            Column(
                Modifier
                    .width(470.dp)
                    .heightIn(max = maxPanelHeight)
                    .clip(HomeShapes.panel)
                    .background(HomeColors.Deep)
                    // Nunca se esconde una acción: si no cabe, el mando la desplaza.
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(title, color = HomeColors.Text, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = HomeColors.TextFaint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                actions.forEachIndexed { index, action ->
                    FocusableSurface(
                        onClick = action.onClick,
                        shape = HomeShapes.panel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    action.title,
                                    color = if (action.danger) HomeColors.Live else HomeColors.Text,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (action.detail.isNotBlank()) {
                                    Text(action.detail, color = HomeColors.TextFaint, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                HomeButton(label = "Cerrar", onClick = onDismiss, icon = Icons.Rounded.Close, primary = false)
            }
        }
    }
}

@Composable
fun ManageChannelsDialog(
    channels: List<LiveItem>,
    config: HomeConfig,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onSort: (ChannelSort) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberInitialFocus()
    var filter by remember { mutableIntStateOf(0) }
    val favorites = config.channelFavorites.toSet()
    val visible = remember(channels, config, filter) {
        when (filter) {
            1 -> favoriteChannels(channels, config)
            2 -> channels.filter { it.code in config.hiddenChannels }
            else -> visibleChannels(channels, config)
        }
    }
    PersonalizePanel(
        title = "Canales",
        subtitle = "OK en estrella = favorito · OK en ojo = oculto · flechas = orden",
        onDismiss = onDismiss,
        first = first,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
            PersonalizeChip("Todos", filter == 0) { filter = 0 }
            PersonalizeChip("Favoritos", filter == 1) { filter = 1 }
            PersonalizeChip("Ocultos", filter == 2) { filter = 2 }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
            PersonalizeChip("Auto", config.channelSort == ChannelSort.AUTO) { onSort(ChannelSort.AUTO) }
            PersonalizeChip("Favoritos primero", config.channelSort == ChannelSort.FAVORITES) {
                onSort(ChannelSort.FAVORITES)
            }
            PersonalizeChip("A-Z", config.channelSort == ChannelSort.ALPHA) { onSort(ChannelSort.ALPHA) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(visible, key = { it.code }) { item ->
                ChannelManageRow(
                    item = item,
                    favorite = item.code in favorites,
                    hidden = item.code in config.hiddenChannels,
                    favoriteIndex = config.channelFavorites.indexOf(item.code),
                    favoriteCount = config.channelFavorites.size,
                    first = item.code == visible.firstOrNull()?.code,
                    firstRequester = first,
                    onToggleFavorite = { onToggleFavorite(item.code) },
                    onToggleHidden = { onToggleHidden(item.code) },
                    onMoveFavorite = { delta -> onMoveFavorite(item.code, delta) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HomeButton(label = "Restablecer canales", onClick = onReset, icon = Icons.Rounded.RestartAlt, primary = false)
    }
}

@Composable
private fun ChannelManageRow(
    item: LiveItem,
    favorite: Boolean,
    hidden: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    first: Boolean,
    firstRequester: FocusRequester?,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveFavorite: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .focusGroup()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (item.logo.isNotBlank()) {
            AsyncImage(
                model = item.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                color = if (hidden) HomeColors.TextFaint else HomeColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                currentProgram(item, System.currentTimeMillis()).ifBlank { "Guía no disponible" },
                color = HomeColors.TextFaint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            MiniAction(
                icon = Icons.Rounded.ArrowUpward,
                label = "Subir",
                enabled = favoriteIndex > 0,
                onClick = { onMoveFavorite(-1) },
                modifier = if (first && favoriteIndex == 0) Modifier.focusRequester(firstRequester!!) else Modifier,
            )
            MiniAction(
                icon = Icons.Rounded.ArrowDownward,
                label = "Bajar",
                enabled = favoriteIndex in 0 until favoriteCount - 1,
                onClick = { onMoveFavorite(1) },
            )
        }
        MiniAction(
            icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            label = if (favorite) "Quitar de favoritos" else "Fijar en Mis canales",
            onClick = onToggleFavorite,
            modifier = if (first && !(favorite && favoriteIndex == 0)) Modifier.focusRequester(firstRequester!!) else Modifier,
        )
        MiniAction(
            icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            label = if (hidden) "Mostrar" else "Ocultar",
            onClick = onToggleHidden,
        )
    }
}

@Composable
fun ManageAppsDialog(
    apps: List<AppEntry>,
    config: HomeConfig,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberInitialFocus()
    var filter by remember { mutableIntStateOf(0) }
    val favorites = config.appFavorites.toSet()
    val visible = remember(apps, config, filter) {
        when (filter) {
            1 -> apps.filter { it.packageName in favorites }
            2 -> apps.filter { it.packageName in config.hiddenApps }
            else -> apps
        }
    }
    PersonalizePanel(
        title = "Apps",
        subtitle = "OK en estrella = favorita · ojo = oculta · papelera = desinstalar",
        onDismiss = onDismiss,
        first = first,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
            PersonalizeChip("Todas", filter == 0) { filter = 0 }
            PersonalizeChip("Favoritas", filter == 1) { filter = 1 }
            PersonalizeChip("Ocultas", filter == 2) { filter = 2 }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(visible, key = { it.packageName }) { app ->
                AppManageRow(
                    app = app,
                    favorite = app.packageName in favorites,
                    hidden = app.packageName in config.hiddenApps,
                    favoriteIndex = config.appFavorites.indexOf(app.packageName),
                    favoriteCount = config.appFavorites.size,
                    first = app.packageName == visible.firstOrNull()?.packageName,
                    firstRequester = first,
                    onToggleFavorite = { onToggleFavorite(app.packageName) },
                    onToggleHidden = { onToggleHidden(app.packageName) },
                    onMoveFavorite = { delta -> onMoveFavorite(app.packageName, delta) },
                    onUninstall = { onUninstall(app) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HomeButton(label = "Restablecer apps", onClick = onReset, icon = Icons.Rounded.RestartAlt, primary = false)
    }
}

@Composable
private fun AppManageRow(
    app: AppEntry,
    favorite: Boolean,
    hidden: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    first: Boolean,
    firstRequester: FocusRequester?,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveFavorite: (Int) -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .focusGroup()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
        } else {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), color = HomeColors.TextDim)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = if (hidden) HomeColors.TextFaint else HomeColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (app.versionName.isBlank()) app.packageName else "${app.packageName} · v${app.versionName}",
                color = HomeColors.TextFaint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            MiniAction(
                icon = Icons.Rounded.ArrowUpward,
                label = "Subir",
                enabled = favoriteIndex > 0,
                onClick = { onMoveFavorite(-1) },
                modifier = if (first && favoriteIndex == 0) Modifier.focusRequester(firstRequester!!) else Modifier,
            )
            MiniAction(
                icon = Icons.Rounded.ArrowDownward,
                label = "Bajar",
                enabled = favoriteIndex in 0 until favoriteCount - 1,
                onClick = { onMoveFavorite(1) },
            )
        }
        MiniAction(
            icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            label = if (favorite) "Quitar de favoritas" else "Fijar app",
            onClick = onToggleFavorite,
            modifier = if (first && !(favorite && favoriteIndex == 0)) Modifier.focusRequester(firstRequester!!) else Modifier,
        )
        MiniAction(
            icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            label = if (hidden) "Mostrar" else "Ocultar",
            onClick = onToggleHidden,
        )
        if (app.canUninstall) {
            MiniAction(
                icon = Icons.Rounded.DeleteOutline,
                label = "Desinstalar ${app.label}",
                onClick = onUninstall,
            )
        }
    }
}

@Composable
internal fun ManageRowsDialog(
    rows: List<RowEntry>,
    config: HomeConfig,
    onMove: (String, Int) -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberInitialFocus()
    PersonalizePanel(
        title = "Filas del Inicio",
        subtitle = "Arriba/Abajo cambia el orden · ojo oculta la fila",
        onDismiss = onDismiss,
        first = first,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                val index = config.rowOrder.indexOf(row.id)
                val hidden = row.id in config.hiddenRows
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .focusGroup()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.label,
                            color = if (hidden) HomeColors.TextFaint else HomeColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (hidden) "Oculta" else "Visible",
                            color = HomeColors.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    MiniAction(
                        icon = Icons.Rounded.ArrowUpward,
                        label = "Subir",
                        enabled = index > 0,
                        onClick = { onMove(row.id, -1) },
                        modifier = if (row.id == rows.firstOrNull()?.id) Modifier.focusRequester(first) else Modifier,
                    )
                    MiniAction(
                        icon = Icons.Rounded.ArrowDownward,
                        label = "Bajar",
                        enabled = index in 0 until config.rowOrder.lastIndex,
                        onClick = { onMove(row.id, 1) },
                    )
                    MiniAction(
                        icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        label = if (hidden) "Mostrar" else "Ocultar",
                        onClick = { onToggle(row.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HomeButton(label = "Restablecer filas", onClick = onReset, icon = Icons.Rounded.RestartAlt, primary = false)
    }
}

@Composable
fun DisplaySettingsDialog(
    config: HomeConfig,
    onChange: (DisplayConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberInitialFocus()
    val display = config.display
    PersonalizePanel(
        title = "Apariencia",
        subtitle = "OK cambia cada opción · los cambios se aplican al instante",
        onDismiss = onDismiss,
        first = first,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ChoiceRow(
                    label = "Densidad",
                    value = when (display.density) {
                        LauncherDensity.COMPACT -> "Compacta"
                        LauncherDensity.COMFORT -> "Cómoda"
                        LauncherDensity.CINEMA -> "Cine"
                    },
                    onClick = {
                        onChange(
                            display.copy(
                                density = when (display.density) {
                                    LauncherDensity.COMPACT -> LauncherDensity.COMFORT
                                    LauncherDensity.COMFORT -> LauncherDensity.CINEMA
                                    LauncherDensity.CINEMA -> LauncherDensity.COMPACT
                                },
                            ),
                        )
                    },
                    modifier = Modifier.focusRequester(first),
                )
            }
            item {
                ChoiceRow(
                    label = "Animaciones",
                    value = when (display.animations) {
                        LauncherAnimations.AUTO -> "Auto"
                        LauncherAnimations.LIGHT -> "Ligeras"
                        LauncherAnimations.FULL -> "Completas"
                    },
                    onClick = {
                        onChange(
                            display.copy(
                                animations = when (display.animations) {
                                    LauncherAnimations.AUTO -> LauncherAnimations.LIGHT
                                    LauncherAnimations.LIGHT -> LauncherAnimations.FULL
                                    LauncherAnimations.FULL -> LauncherAnimations.AUTO
                                },
                            ),
                        )
                    },
                )
            }
            item {
                ChoiceRow(
                    label = "Reloj",
                    value = if (display.clock24h) "24 h" else "12 h",
                    onClick = { onChange(display.copy(clock24h = !display.clock24h)) },
                )
            }
            item {
                ChoiceRow(
                    label = "Mostrar reloj",
                    value = if (display.showClock) "Sí" else "No",
                    onClick = { onChange(display.copy(showClock = !display.showClock)) },
                )
            }
            item {
                ChoiceRow(
                    label = "Guía AHORA",
                    value = if (display.showEpg) "Sí" else "No",
                    onClick = { onChange(display.copy(showEpg = !display.showEpg)) },
                )
            }
            item {
                ChoiceRow(
                    label = "Tarjetas por fila",
                    value = "${display.itemsPerRow}",
                    onClick = {
                        val next = DisplayConfig.ITEM_OPTIONS.firstOrNull { it > display.itemsPerRow }
                            ?: DisplayConfig.ITEM_OPTIONS.first()
                        onChange(display.copy(itemsPerRow = next))
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HomeButton(label = "Restablecer apariencia", onClick = onReset, icon = Icons.Rounded.RestartAlt, primary = false)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(onClick = onClick, shape = HomeShapes.panel, modifier = modifier.fillMaxWidth()) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = HomeColors.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, color = HomeColors.BlueBright, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PersonalizeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = Modifier.height(34.dp),
        focusedScale = 1.04f,
    ) {
        Box(
            Modifier
                .height(34.dp)
                .background(if (selected) HomeColors.Blue else HomeColors.Surface)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) Color.White else HomeColors.TextDim,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        modifier = modifier.size(36.dp),
        focusedScale = 1.05f,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    !enabled -> HomeColors.TextFaint.copy(alpha = 0.35f)
                    focused -> Color.White
                    else -> HomeColors.TextDim
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
