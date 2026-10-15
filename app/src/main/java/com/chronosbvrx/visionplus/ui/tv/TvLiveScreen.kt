package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.ui.live.LiveCopy
import com.chronosbvrx.visionplus.ui.live.LiveFavorites
import com.chronosbvrx.visionplus.ui.live.rememberLiveState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Width of the left column: the group rail, or the keyboard while searching. */
private val SIDE_COLUMN = 240.dp
private val SEARCH_COLUMN = 290.dp

/** Width of the preview panel. */
private val PANEL_WIDTH = 340.dp

/**
 * "En vivo" on the TV: playlist tabs on top (with Agregar, Buscar and Recargar as round icons), the
 * group rail on the left (Favoritos first, then the groups of the chosen list), a compact channel
 * list in the centre and, on the right, a preview of the focused channel. The channels are real HLS
 * streams parsed from the M3U lists.
 */
@Composable
fun TvLiveScreen(onPlay: (LiveChannel) -> Unit) {
    TvLiveScreen(onPlay = { ch, _, _ -> onPlay(ch) })
}

@Composable
fun TvLiveScreen(
    onPlay: (LiveChannel, List<String>, String?) -> Unit,
    returnChannelCode: String? = null,
) {
    val state = rememberLiveState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selectedCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }
    val landing = rememberLandingFocus()
    BackHandler(enabled = searchOpen) { searchOpen = false; state.query = "" }
    // Al volver del reproductor: la tarjeta exacta del canal abierto (la lista tarda
    // en pintar en gama baja); si no aparece, cae a la primera visible o al destino.
    val returnRequester = remember { FocusRequester() }
    val entryRequester = remember { FocusRequester() }

    val searching = searchOpen && state.query.isNotBlank()
    val allChannels = state.channels
    // Taxonomía: nombres memoizados una sola vez para géneros, conteos y países
    val allNames = remember(allChannels) { allChannels.map { it.name } }
    val genres = remember(allNames) { LiveTaxonomy.genresOf(allNames) }
    val genreCounts = remember(allNames) { allNames.groupingBy { LiveTaxonomy.genreFor(it) }.eachCount() }
    val countries = remember(allNames) { LiveTaxonomy.countriesOf(allNames) }

    // Canales filtrados memoizados para no recalcular 1000+ canales en cada movimiento de D-pad
    val channels = remember(
        allChannels,
        state.group,
        favoritesOnly,
        state.query,
        searching,
        selectedCountry
    ) {
        val base = when {
            searching -> allChannels.filter { it.name.contains(state.query.trim(), ignoreCase = true) }
            favoritesOnly -> state.shown.filter { LiveFavorites.has(it.url) }
            else -> state.shown
        }
        val filtered = if (selectedCountry != null) {
            base.filter { LiveTaxonomy.countryFor(it.name) == selectedCountry }
        } else {
            base
        }
        filtered.distinctBy { it.url }
    }

    val activeScopeTitle = remember(selectedCountry, state.group, favoritesOnly, searching, state.query) {
        when {
            searching -> "Búsqueda: ${state.query.trim()}"
            favoritesOnly -> "Favoritos"
            selectedCountry != null && state.group != null -> "$selectedCountry · ${state.group}"
            selectedCountry != null -> selectedCountry
            state.group != null -> state.group
            else -> null
        }
    }

    val activeChannelCodes = remember(channels, activeScopeTitle) {
        if (activeScopeTitle != null) {
            channels.map { it.url.removePrefix("xtvlive:") }
        } else {
            emptyList()
        }
    }

    val firstChannel = channels.firstOrNull()

    rememberReturnFocus(
        filmId = returnChannelCode,
        requester = returnRequester,
        landing = landing,
        contentKey = channels.size,
        entryRequester = entryRequester,
    )

    // El canal a mostrar en la vista previa a la derecha
    val shown = remember(focused, channels) {
        focused?.takeIf { ch -> channels.any { it.url == ch.url } } ?: firstChannel
    }

    // Cambiar de lista, grupo o búsqueda no debe dejar el mando sin foco.
    LaunchedEffect(state.selected?.id, state.group, favoritesOnly, state.query, allChannels.size) {
        delay(150)
        if (!FocusRescue.screenHasFocus && channels.isNotEmpty()) landing.request()
    }

    LaunchedEffect(state.selected?.id, state.group) {
        selectedCountry = null
    }

    Column(Modifier.fillMaxSize().background(VisionBlack).padding(horizontal = 48.dp, vertical = 27.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("En vivo", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(end = 16.dp))
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(state.playlists, key = { it.id }) { playlist ->
                    TvPlaylistTab(playlist.name, selected = state.selected?.id == playlist.id, onClick = {
                        favoritesOnly = false
                        state.pick(playlist)
                    })
                }
            }
            TvRoundIcon(Icons.Default.Add, LiveCopy.ADD_LIST, { adding = true })
            Spacer(Modifier.width(12.dp))
            TvRoundIcon(Icons.Default.Search, "Buscar", { searchOpen = !searchOpen; state.query = "" })
            Spacer(Modifier.width(12.dp))
            TvRoundIcon(Icons.Default.Refresh, LiveCopy.REFRESH, { state.refresh() })
            val selected = state.selected
            if (selected != null && !selected.builtIn) {
                Spacer(Modifier.width(12.dp))
                TvRoundIcon(Icons.Default.Delete, LiveCopy.DELETE_LIST, { confirmingDelete = true })
            }
        }
        Spacer(Modifier.height(16.dp))
        // País: chips horizontales
        if (countries.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(40.dp).fillMaxWidth()
            ) {
                item(key = "all_countries") {
                    TvCountryChip(
                        label = "Todos",
                        selected = selectedCountry == null,
                        onClick = { selectedCountry = null },
                    )
                }
                items(countries, key = { it }) { country ->
                    TvCountryChip(
                        label = country,
                        selected = selectedCountry == country,
                        onClick = {
                            selectedCountry = if (selectedCountry == country) null else country
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (searchOpen) {
                Column(Modifier.width(SEARCH_COLUMN).fillMaxHeight().padding(top = 8.dp)) {
                    val keyboardFocus = rememberDialogFocus()
                    Text(
                        state.query.ifBlank { LiveCopy.SEARCH_HINT },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.query.isBlank()) VisionTextSecondary else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    TvKeyboard(text = state.query, onTextChange = { state.query = it }, firstKey = Modifier.dialogFocus(keyboardFocus), modifier = Modifier.weight(1f))
                    if (state.query.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        TvCompactAction(label = "Borrar búsqueda", modifier = Modifier.fillMaxWidth()) { searchOpen = false; state.query = "" }
                    }
                }
            } else {
                LazyColumn(Modifier.width(SIDE_COLUMN).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item(key = "fav") {
                        TvRailRow(
                            label = "Favoritos",
                            icon = Icons.Default.Star,
                            count = LiveFavorites.urls.size,
                            selected = favoritesOnly,
                            onClick = {
                                favoritesOnly = true
                                state.selectGroup(null)
                            },
                            modifier = if (channels.isEmpty()) Modifier.landingFocus(landing) else Modifier,
                        )
                    }
                    genres.forEach { genre ->
                        item(key = "g:$genre") {
                            TvRailRow(
                                label = genre,
                                icon = null,
                                count = genreCounts[genre] ?: 0,
                                selected = !favoritesOnly && state.group == genre,
                                onClick = {
                                    favoritesOnly = false
                                    state.selectGroup(genre)
                                },
                            )
                        }
                    }
                    item(key = "all") {
                        TvRailRow(LiveCopy.ALL_GROUPS, null, allChannels.size, !favoritesOnly && state.group == null, {
                            favoritesOnly = false
                            state.selectGroup(null)
                        })
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                when {
                    state.loading && state.channels.isEmpty() -> item {
                        Column(Modifier.padding(top = 24.dp)) {
                            Text("Cargando canales…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(state.selected?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        }
                    }
                    state.error != null && state.channels.isEmpty() -> item {
                        Column(Modifier.padding(top = 24.dp)) {
                            Text("No se pudo cargar la lista", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                            Spacer(Modifier.height(8.dp))
                            TvCompactAction(label = LiveCopy.REFRESH) { state.refresh() }
                        }
                    }
                    channels.isEmpty() -> item {
                        val (title, body) = when {
                            searching -> LiveCopy.NO_RESULTS to LiveCopy.NO_RESULTS_BODY
                            favoritesOnly -> "Sin favoritos todavía" to "Mantén OK sobre un canal para agregarlo."
                            else -> LiveCopy.NO_CHANNELS to LiveCopy.NO_CHANNELS_BODY
                        }
                        Column(Modifier.padding(top = 24.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        }
                    }
                }
                items(channels, key = { it.url }) { channel ->
                    TvChannelRow(
                        channel = channel,
                        favorite = LiveFavorites.has(channel.url),
                        onClick = { onPlay(channel, activeChannelCodes, activeScopeTitle) },
                        onLongClick = { LiveFavorites.toggle(channel.url) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) focused = channel }
                            .then(if (channel == firstChannel) Modifier.landingFocus(landing) else Modifier)
                            .then(
                                if (returnChannelCode != null && channel.url.removePrefix("xtvlive:") == returnChannelCode) {
                                    Modifier.focusRequester(returnRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (channel == firstChannel) Modifier.focusRequester(entryRequester)
                                else Modifier
                            ),
                    )
                }
            }
            TvLivePreview(shown, Modifier.width(PANEL_WIDTH).fillMaxHeight())
        }
    }

    if (adding) {
        TvAddPlaylistDialog(
            onDismiss = { adding = false },
            onAdd = { name: String, url: String ->
                adding = false
                state.add(name, url)
            },
        )
    }
    if (confirmingDelete) {
        TvDialog(LiveCopy.DELETE_LIST, onDismiss = { confirmingDelete = false }) {
            Text(
                "Se va a quitar «${state.selected?.name}» del aparato. La lista en internet no se toca.",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionOption("Cancelar") { confirmingDelete = false }
                TvActionOption("Quitar") {
                    confirmingDelete = false
                    state.selected?.let { state.remove(it) }
                }
            }
        }
    }
}

/** Chip de país (estilo TV: fondo surface, seleccionado en azul, foco con aro blanco). */
@Composable
fun TvCountryChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(36.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue else VisionSurface,
            focusedContainerColor = VisionSurface.copy(alpha = 0.9f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Color.White), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The TV dialog that takes an M3U address (and an optional name). */
@Composable
fun TvAddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val focus = rememberDialogFocus()
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    TvDialog(LiveCopy.ADD_LIST, onDismiss = onDismiss) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Dirección de la lista") },
            placeholder = { Text("https://…/lista.m3u") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().dialogFocus(focus),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(LiveCopy.ADD_LIST_BODY, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionOption("Cancelar") { onDismiss() }
            TvActionOption("Agregar") { if (valid) onAdd(name, url.trim()) }
        }
    }
}