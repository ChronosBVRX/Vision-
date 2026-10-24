package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Icon
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.akaLine
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.searchFilms
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import java.util.Locale

/** A key of the on-screen keyboard: a character, space or backspace. */
private sealed interface Key {
    data class Char(val c: kotlin.Char) : Key
    data object Space : Key
    data object Backspace : Key
}

/** A-Z and 0-9 in six columns, then space and backspace. */
private val KEYBOARD_ROWS: List<List<Key>> = buildList {
    (('A'..'Z') + ('0'..'9')).map { Key.Char(it) }.chunked(6).forEach { add(it) }
    add(listOf(Key.Space, Key.Backspace))
}

private fun Key.label() = when (this) {
    is Key.Char -> c.toString()
    Key.Space -> "Espacio"
    Key.Backspace -> "Borrar"
}

private fun Key.span() = when (this) {
    is Key.Char -> 1
    Key.Space -> 4
    Key.Backspace -> 2
}

private fun apply(text: String, key: Key) = when (key) {
    is Key.Char -> text + key.c
    Key.Space -> "$text "
    Key.Backspace -> text.dropLast(1)
}

/**
 * The remote's alphabetic keyboard: dark keys, the focused one red. Key size follows the smaller of
 * what the width and the height allow, so the whole grid always fits. [firstKey] marks the first key
 * (where the screen puts focus).
 */
@Composable
internal fun TvKeyboard(text: String, onTextChange: (String) -> Unit, firstKey: Modifier, modifier: Modifier = Modifier) {
    val gap = 6.dp
    BoxWithConstraints(modifier) {
        val byWidth = (maxWidth - gap * 5) / 6
        val byHeight = (maxHeight - gap * (KEYBOARD_ROWS.size - 1)) / KEYBOARD_ROWS.size
        // Teclas compactas: antes crecían hasta llenar la columna y el teclado se comía la pantalla.
        val keySize = minOf(byWidth, byHeight, 46.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KEYBOARD_ROWS.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { c, key ->
                        val span = key.span()
                        Surface(
                            onClick = { onTextChange(apply(text, key)) },
                            modifier = Modifier
                                .width(keySize * span + gap * (span - 1))
                                .height(keySize)
                                .then(if (r == 0 && c == 0) firstKey else Modifier),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = VisionSurfaceHigh,
                                contentColor = Color.White,
                                focusedContainerColor = VisionBlue,
                                focusedContentColor = Color.White,
                            ),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(key.label(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Buscar" on the TV: the keyboard on the left, the matching films on the right as you type. */
@Composable
fun TvSearchScreen(rows: List<CatalogRow>, onOpenFilm: (Film) -> Unit, returnFilmId: String? = null) {
    val context = LocalContext.current
    // La consulta que trae Vision+ Home (texto o voz) se toma al abrir; después manda lo que
    // la persona escriba.
    val seed = remember { com.chronosbvrx.visionplus.ui.SearchSeed.take() }
    var text by rememberSaveable { mutableStateOf(seed) }
    // El catálogo inicial se prepara fuera del hilo de UI (el índice completo en
    // composición retrasaba la apertura del buscador en Chromecast/TV boxes).
    val initialFilms by androidx.compose.runtime.produceState(emptyList<Film>(), rows) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            allFilms(rows).take(60)
        }
    }
    var results by remember { mutableStateOf<List<Film>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(com.chronosbvrx.visionplus.data.search.RecentSearches.list(context)) }
    // Solo los TÍTULOS para autocompletar (texto barato; no se guardan películas).
    val knownTitles by androidx.compose.runtime.produceState(emptyList<String>(), rows) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            allFilms(rows).map { it.title }.take(4000)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val spokenMatches = activityResult.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = spokenMatches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                text = spoken
            }
        }
    }

    val startVoiceRecognition = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el título de una película o serie…")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(text, rows, initialFilms) {
        val q = text.trim()
        if (q.isBlank()) {
            results = initialFilms
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        // Debounce de 220ms: teclear con D-pad es instantáneo a 60 fps sin tirones
        kotlinx.coroutines.delay(220)
        // 1. Intentar con el servidor VPS (índice en Postgres, ~3 ms sin saturar CPU de la TV)
        val serverResults = runCatching {
            com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.search(q, limit = 60)
        }.getOrDefault(emptyList())

        if (serverResults.isNotEmpty()) {
            results = serverResults
        } else {
            // 2. Respaldo local optimizado en Dispatchers.Default
            results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                searchFilms(rows, q).take(60)
            }
        }
        // Modo niños: la búsqueda del servidor no sabe del bloqueo; se filtra aquí.
        if (com.chronosbvrx.visionplus.data.kids.KidsMode.locked) {
            results = results.filter { it.kind == "kids" }
        }
        isSearching = false
    }

    // Recientes: solo la consulta ya asentada (deja de teclear y hay resultados), no cada letra.
    LaunchedEffect(text, results, isSearching) {
        val q = text.trim()
        if (!isSearching && q.length >= 2 && results.isNotEmpty()) {
            kotlinx.coroutines.delay(1800)
            com.chronosbvrx.visionplus.data.search.RecentSearches.add(context, q)
            recents = com.chronosbvrx.visionplus.data.search.RecentSearches.list(context)
        }
    }

    // Autocompletar: títulos completos a partir de lo escrito (resultados + catálogo cargado).
    val suggestions = remember(text, results, knownTitles) {
        if (text.isBlank()) emptyList()
        else com.chronosbvrx.visionplus.data.search.suggestTitles(
            text,
            results.map { it.title } + knownTitles,
            limit = 6,
        )
    }

    val landing = rememberLandingFocus()
    // Al volver de una ficha: la tarjeta exacta que la abrió (el teclado queda de respaldo).
    // La lista se reconstruye al entrar y CAMBIA con cada búsqueda, así que el intento se
    // re-arma con los resultados reales y, si la tarjeta exacta ya no está, cae en la
    // primera tarjeta visible (no siempre al teclado).
    val returnRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val firstResultId = results.firstOrNull()?.id
    rememberReturnFocus(
        filmId = returnFilmId,
        requester = returnRequester,
        landing = landing,
        contentKey = results,
        entryRequester = firstResultRequester,
    )

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxHeight().width(320.dp).padding(20.dp)) {
                // Barra de texto de búsqueda
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VisionSurfaceHigh, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text.ifBlank { "Buscar películas o series…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (text.isBlank()) VisionTextSecondary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Acciones rápidas (Dictar por voz y Limpiar) accesibles con mando
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                ) {
                    Surface(
                        onClick = { startVoiceRecognition() },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = VisionSurfaceHigh,
                            contentColor = Color.White,
                            focusedContainerColor = VisionBlue,
                            focusedContentColor = Color.White,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Dictar por voz",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Dictar voz", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (text.isNotEmpty()) {
                        Surface(
                            onClick = { text = "" },
                            modifier = Modifier.height(42.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = VisionSurfaceHigh,
                                contentColor = Color.White,
                                focusedContainerColor = VisionBlue,
                                focusedContentColor = Color.White,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Limpiar texto",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Borrar", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                TvKeyboard(text = text, onTextChange = { text = it }, firstKey = Modifier.landingFocus(landing), modifier = Modifier.weight(1f))
            }
            Column(Modifier.fillMaxSize().padding(top = 20.dp, end = 24.dp)) {
                // Autocompletar (mientras escribe) o atajos de lo ya buscado (campo vacío).
                val showSuggestions = text.isNotBlank() && suggestions.isNotEmpty()
                val showRecents = !showSuggestions && text.isBlank() && recents.isNotEmpty()
                if (showSuggestions || showRecents) {
                    Text(
                        if (showSuggestions) "SUGERENCIAS" else "RECIENTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        if (showSuggestions) {
                            suggestions.forEach { suggestion ->
                                TvSearchChip(suggestion) { text = suggestion }
                            }
                        } else {
                            recents.forEach { recent ->
                                TvSearchChip(recent) { text = recent }
                            }
                            TvSearchChip("Limpiar") {
                                com.chronosbvrx.visionplus.data.search.RecentSearches.clear(context)
                                recents = emptyList()
                            }
                        }
                    }
                }
                Text(
                    when {
                        isSearching -> "Buscando…"
                        text.isBlank() -> "Películas"
                        else -> "Resultados (${results.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (results.isEmpty() && !isSearching && text.isNotBlank()) {
                    Text(
                        "Sin resultados para \"$text\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Text(
                        "Prueba sin acentos, con menos palabras o por actor/director.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, end = 8.dp, start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(results, key = { it.id }) { film ->
                        Column {
                            TvLandscapeCard(
                                title = film.title,
                                imageUrl = film.posterUrl,
                                cardHeight = 96.dp,
                                modifier = when {
                                    film.id == returnFilmId -> Modifier.focusRequester(returnRequester)
                                    film.id == firstResultId -> Modifier.focusRequester(firstResultRequester)
                                    else -> Modifier
                                },
                                onClick = { onOpenFilm(film) },
                            )
                            Text(
                                film.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            film.akaLine().takeIf { it.isNotBlank() }?.let { aka ->
                                Text(
                                    aka,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Atajo del buscador de la tele (sugerencia o reciente): aro blanco al enfocar, como el resto. */
@Composable
private fun TvSearchChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurfaceHigh,
            contentColor = Color.White,
            focusedContainerColor = VisionSurfaceHigh,
            focusedContentColor = Color.White,
            pressedContainerColor = VisionBlue,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
