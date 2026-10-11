package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.GhostRow
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Una sección (Películas, Series, Novelas, Anime) en el teléfono: sus hileras de género,
 * cada una solo con títulos de esa sección — lo mismo que el riel muestra en la TV.
 */
@Composable
fun SectionScreen(
    name: String,
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    val context = LocalContext.current
    var rows by remember(name) {
        mutableStateOf(RemoteIndexSource.cachedSection(context, name))
    }
    LaunchedEffect(name) {
        val fresh = RemoteIndexSource.section(context, name)
        if (fresh.isNotEmpty() || rows == null) {
            rows = fresh
        }
    }
    // Sin BackHandler propio: el global de PhoneApp ya vuelve atrás (evita el doble pop).
    val loaded = rows
    Column(Modifier.fillMaxSize().background(VisionBlack).statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        when {
            loaded == null -> {
                GhostRow()
                GhostRow()
                GhostRow()
            }
            loaded.isEmpty() -> {
                EmptyState(
                    title = "Sin contenido todavía",
                    subtitle = "Revisa tu conexión e intenta de nuevo.",
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(loaded, key = { it.id }) { row ->
                        SectionRow(row = row, onOpen = onOpenFilm)
                    }
                }
            }
        }
    }
}

/** Una hilera de género: su título y una fila horizontal de portadas. */
@Composable
private fun SectionRow(row: CatalogRow, onOpen: (Film) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        if (row.films.isEmpty()) {
            Text(
                "Sin títulos",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(start = 16.dp),
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(row.films, key = { it.id }) { film ->
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine().ifBlank { null },
                    modifier = Modifier.width(132.dp),
                    onClick = { onOpen(film) },
                )
            }
        }
    }
}
