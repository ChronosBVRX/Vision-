package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.live.ChannelLogo
import com.chronosbvrx.visionplus.ui.live.LiveCopy
import com.chronosbvrx.visionplus.ui.live.rememberLiveState
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * "En vivo" on the phone: the playlist chips (the free lists plus the ones the person added),
 * genre rail (fixed TV order) and country filter chips, then the channel grid.
 * XTV channels are classified by LiveTaxonomy; M3U lists keep their group-title if present.
 */
@Composable
fun LiveScreen(contentPadding: PaddingValues, onPlay: (LiveChannel) -> Unit) {
    LiveScreen(contentPadding, onPlay = { ch, _, _ -> onPlay(ch) })
}

@Composable
fun LiveScreen(
    contentPadding: PaddingValues,
    onPlay: (LiveChannel, List<String>, String?) -> Unit,
) {
    val state = rememberLiveState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCountry by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = { Text(LiveCopy.SEARCH_HINT) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { state.query = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpiar") }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { state.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = LiveCopy.REFRESH, tint = Color.White) }
            val selected = state.selected
            if (selected != null && !selected.builtIn) {
                IconButton(onClick = { confirmingDelete = true }) { Icon(Icons.Default.Delete, contentDescription = LiveCopy.DELETE_LIST, tint = Color.White) }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.playlists, key = { it.id }) { playlist ->
                CategoryChip(playlist.name, state.selected?.id == playlist.id) { state.pick(playlist) }
            }
            item(key = "add") {
                FilterChip(
                    selected = false,
                    onClick = { adding = true },
                    label = { Text("Agregar") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }

        val searching = state.query.isNotBlank()
        // Memoizado: con 1,000+ canales, recalcular gÃ©neros/paÃ­ses/filtros en cada
        // recomposiciÃ³n (teclear, cambiar chip) congelaba el hilo de UI.
        val baseChannels = remember(state.channels, state.query, state.group) {
            if (searching) {
                state.channels.filter { it.name.contains(state.query.trim(), ignoreCase = true) }
            } else {
                state.shown
            }
        }
        val genres = remember(baseChannels) { LiveTaxonomy.genresOf(baseChannels.map { it.name }) }
        val countries = remember(baseChannels) { LiveTaxonomy.countriesOf(baseChannels.map { it.name }) }

        // PaÃ­s: chips horizontales
        if (countries.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all_countries") {
                    CountryChip(label = "Todos", selected = selectedCountry == null, onClick = { selectedCountry = null })
                }
                items(countries, key = { it }) { country ->
                    CountryChip(label = country, selected = selectedCountry == country, onClick = { selectedCountry = country })
                }
            }
        }

        // GÃ©nero: chips horizontales (orden TV)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "fav") {
                CategoryChip("Favoritos", selected = false, onClick = { })
            }
            items(genres, key = { it }) { genre ->
                CategoryChip(genre, selectedGenre == genre, onClick = { selectedGenre = genre })
            }
            item(key = "all_genres") {
                CategoryChip(LiveCopy.ALL_GROUPS, selectedGenre == null, onClick = { selectedGenre = null })
            }
        }

        if (state.loading && state.channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VisionBlue)
            }
            return@Column
        }
        val error = state.error
        if (error != null && state.channels.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No se pudo cargar la lista", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(error, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 6.dp))
                Button(onClick = { state.refresh() }, modifier = Modifier.padding(top = 16.dp)) { Text("Reintentar") }
            }
            return@Column
        }
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VisionBlue)
        }

        val channels = remember(baseChannels, selectedGenre, selectedCountry) {
            baseChannels.filter { ch ->
                (selectedGenre == null || ch.effectiveGroup() == selectedGenre) &&
                    (selectedCountry == null || LiveTaxonomy.countryFor(ch.name) == selectedCountry)
            }
        }
        val shown = channels
        val activeScopeTitle = when {
            searching -> "BÃºsqueda: ${state.query.trim()}"
            selectedCountry != null && selectedGenre != null -> "$selectedCountry Â· $selectedGenre"
            selectedCountry != null -> selectedCountry
            selectedGenre != null -> selectedGenre
            else -> null
        }
        val activeChannelCodes = remember(shown, activeScopeTitle) {
            if (activeScopeTitle != null) {
                shown.map { it.url.removePrefix("xtvlive:") }
            } else {
                emptyList()
            }
        }

        if (shown.isEmpty()) {
            EmptyState(LiveCopy.NO_RESULTS, subtitle = LiveCopy.NO_RESULTS_BODY)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isLandscapeTablet()) 4 else 2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.url }) { channel ->
                ChannelCard(channel = channel, onClick = { onPlay(channel, activeChannelCodes, activeScopeTitle) })
            }
        }
    }

    if (adding) {
        AddPlaylistDialog(
            onDismiss = { adding = false },
            onAdd = { name, url ->
                adding = false
                state.add(name, url)
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(LiveCopy.DELETE_LIST) },
            text = { Text("Se va a quitar Â«${state.selected?.name}Â» del telÃ©fono. La lista en internet no se toca.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    state.selected?.let { state.remove(it) }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** A chip of En vivo: a playlist or a group of channels. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VisionBlue,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        ),
    )
}

/** Chip de paÃ­s (estilo mÃ³vil: surface, seleccionado en azul). */
@Composable
private fun CountryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VisionBlue,
            selectedLabelColor = Color.White,
        ),
    )
}

/** A channel: its logo (or its initials), its name and its group. */
@Composable
private fun ChannelCard(channel: LiveChannel, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(VisionBlue.copy(alpha = 0.25f), Color(0xFF10161F)))),
            contentAlignment = Alignment.Center,
        ) {
            // El logo se dibuja a su tamaÃ±o nativo (nunca mÃ¡s de 2Ã—): los del proveedor son de
            // 90Ã—60 px y estirarlos a la tarjeta los dejaba borrosos y gigantes.
            ChannelLogo(
                url = channel.logo,
                name = channel.name,
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            channel.effectiveGroup(),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The dialog that takes a name (optional) and an M3U address. */
@Composable
private fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val trimmed = url.trim()
    val valid = trimmed.startsWith("http://") || trimmed.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LiveCopy.ADD_LIST) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("DirecciÃ³n de la lista") },
                    placeholder = { Text("https://â€¦/lista.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(LiveCopy.ADD_LIST_BODY, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, trimmed) }, enabled = valid) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
