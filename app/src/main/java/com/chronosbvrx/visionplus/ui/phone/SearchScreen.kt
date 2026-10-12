package com.chronosbvrx.visionplus.ui.phone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.searchFilms
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** "Buscar" on the phone: filters the full catalog with instant VPS search and voice dictation. */
@Composable
fun SearchScreen(rows: List<CatalogRow>, onBack: () -> Unit, onOpenFilm: (Film) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val initialFilms = remember(rows) { allFilms(rows).take(60) }
    var results by remember { mutableStateOf<List<Film>>(initialFilms) }
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

    LaunchedEffect(text, rows) {
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

        if (serverResults.isNotEmpty()) {
            results = serverResults
        } else {
            // 2. Respaldo local optimizado en Dispatchers.Default
            results = withContext(Dispatchers.Default) {
                searchFilms(rows, q).take(60)
            }
        }
        isSearching = false
    }

    Column(Modifier.fillMaxSize().background(VisionBlack).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text("Buscar", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Buscar películas y series…") },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (text.isNotEmpty()) {
                                IconButton(onClick = { text = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                }
                            }
                            IconButton(onClick = { startVoiceRecognition() }) {
                                Icon(Icons.Default.Mic, contentDescription = "Buscar por voz", tint = VisionBlue)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        isSearching -> "Buscando…"
                        text.isBlank() -> "Películas y series"
                        else -> "Resultados (${results.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            if (results.isEmpty() && !isSearching && text.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Sin resultados para \"$text\"", color = VisionTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(results, key = { it.id }) { film ->
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine(),
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
}

