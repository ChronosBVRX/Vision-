package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
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
    val gap = 8.dp
    BoxWithConstraints(modifier) {
        val byWidth = (maxWidth - gap * 5) / 6
        val byHeight = (maxHeight - gap * (KEYBOARD_ROWS.size - 1)) / KEYBOARD_ROWS.size
        val keySize = minOf(byWidth, byHeight)
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
                                Text(key.label(), style = MaterialTheme.typography.titleMedium)
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
    var text by rememberSaveable { mutableStateOf("") }
    // El catálogo inicial se prepara fuera del hilo de UI (el índice completo en
    // composición retrasaba la apertura del buscador en Chromecast/TV boxes).
    val initialFilms by androidx.compose.runtime.produceState(emptyList<Film>(), rows) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            allFilms(rows).take(60)
        }
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
            Column(Modifier.fillMaxHeight().width(380.dp).padding(24.dp)) {
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
            Column(Modifier.fillMaxSize().padding(top = 24.dp, end = 24.dp)) {
                Text(
                    when {
                        isSearching -> "Buscando…"
                        text.isBlank() -> "Películas"
                        else -> "Resultados"
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
                        }
                    }
                }
            }
        }
    }
}
