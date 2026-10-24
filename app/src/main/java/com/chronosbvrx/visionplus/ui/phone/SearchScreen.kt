package com.chronosbvrx.visionplus.ui.phone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.akaLine
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.search.RecentSearches
import com.chronosbvrx.visionplus.data.search.SearchFilter
import com.chronosbvrx.visionplus.data.search.matchesSearchFilter
import com.chronosbvrx.visionplus.data.search.suggestTitles
import com.chronosbvrx.visionplus.data.searchFilms
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * «Buscar» en el móvil: campo integrado en la barra (sin un header aparte), filtros Todo /
 * Películas / Series, **autocompletar** con títulos completos y búsquedas recientes locales.
 * La búsqueda de verdad sigue igual: servidor (índice completo) con respaldo local.
 */
@Composable
fun SearchScreen(rows: List<CatalogRow>, onBack: () -> Unit, onOpenFilm: (Film) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // La consulta que trae Vision+ Home (texto o voz) se toma al abrir.
    val seed = remember { com.chronosbvrx.visionplus.ui.SearchSeed.take() }
    var text by rememberSaveable { mutableStateOf(seed) }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    var recents by remember { mutableStateOf(RecentSearches.list(context)) }
    // El catálogo inicial (todos los films) se prepara en segundo plano: con el
    // índice completo en el hilo de UI la pantalla tardaba en abrir en gama baja.
    val initialFilms by produceState(emptyList<Film>(), rows) {
        value = withContext(Dispatchers.Default) { allFilms(rows).take(60) }
    }
    // Solo los TÍTULOS para autocompletar (texto barato; no se guardan películas).
    val knownTitles by produceState(emptyList<String>(), rows) {
        value = withContext(Dispatchers.Default) { allFilms(rows).map { it.title }.take(4000) }
    }
    var results by remember { mutableStateOf<List<Film>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

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
        delay(220)
        // 1. Intentar con el servidor VPS (rápido, indexado en Postgres, catálogo completo)
        val serverResults = runCatching {
            RemoteIndexSource.search(q, limit = 60)
        }.getOrDefault(emptyList())

        val found = if (serverResults.isNotEmpty()) {
            serverResults
        } else {
            // 2. Respaldo local optimizado en Dispatchers.Default
            withContext(Dispatchers.Default) {
                searchFilms(rows, q).take(60)
            }
        }
        // Modo niños: la búsqueda del servidor no sabe del bloqueo; se filtra aquí.
        results = if (com.chronosbvrx.visionplus.data.kids.KidsMode.locked) {
            found.filter { it.kind == "kids" }
        } else {
            found
        }
        isSearching = false
    }

    // Recientes: solo la consulta ya asentada (deja de teclear y hay resultados), no cada letra.
    LaunchedEffect(text, results, isSearching) {
        val q = text.trim()
        if (!isSearching && q.length >= 2 && results.isNotEmpty()) {
            delay(1800)
            RecentSearches.add(context, q)
            recents = RecentSearches.list(context)
        }
    }

    val suggestions = remember(text, results, knownTitles) {
        if (text.isBlank()) emptyList()
        else suggestTitles(text, results.map { it.title } + knownTitles, limit = 6)
    }
    val shown = remember(results, filter) {
        results.filter { matchesSearchFilter(it.kind, filter) }.distinctBy { it.id }
    }

    Column(Modifier.fillMaxSize().background(VisionBlack).statusBarsPadding()) {
        // Barra: volver + campo integrado (el buscador ES el campo, sin título gigante).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(VisionSurfaceHigh, RoundedCornerShape(23.dp))
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(VisionBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        RecentSearches.add(context, text)
                        recents = RecentSearches.list(context)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    "Buscar películas y series…",
                                    color = VisionTextSecondary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = "" }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Limpiar",
                            tint = VisionTextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                IconButton(onClick = { startVoiceRecognition() }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Buscar por voz",
                        tint = VisionBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Filtros: Todo · Películas · Series.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { option ->
                SearchPill(
                    label = option.label,
                    selected = filter == option,
                    onClick = { filter = option },
                )
            }
        }

        when {
            text.isNotBlank() && suggestions.isNotEmpty() -> {
                SuggestionStrip(
                    title = "Sugerencias",
                    items = suggestions,
                    onPick = {
                        text = it
                        focusManager.clearFocus()
                    },
                )
            }
            text.isBlank() && recents.isNotEmpty() -> {
                SuggestionStrip(
                    title = "Recientes",
                    items = recents,
                    trailing = {
                        SearchPill(label = "Limpiar", selected = false, onClick = {
                            RecentSearches.clear(context)
                            recents = emptyList()
                        })
                    },
                    onPick = { text = it },
                )
            }
        }

        Text(
            when {
                isSearching -> "Buscando…"
                text.isBlank() -> "Películas y series"
                else -> "Resultados (${shown.size})"
            },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (shown.isEmpty() && !isSearching && text.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            VisionIcons.SearchOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Sin resultados para \"$text\"",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Prueba sin acentos, con menos palabras o por actor/director.",
                            color = VisionTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { film ->
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine(),
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    altTitle = film.akaLine().ifBlank { null },
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
}

/** Píldora del buscador: filtro seleccionado en azul, el resto en gris. */
@Composable
private fun SearchPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) VisionBlue.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** Fila de atajos: «Sugerencias» (autocompletar) o «Recientes». */
@Composable
private fun SuggestionStrip(
    title: String,
    items: List<String>,
    trailing: (@Composable () -> Unit)? = null,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { suggestion ->
                Surface(
                    onClick = { onPick(suggestion) },
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            suggestion,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
