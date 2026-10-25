package com.chronosbvrx.visionplus.home.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.home.contract.FeedItem
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.FeedRepository
import com.chronosbvrx.visionplus.home.data.filterApps
import kotlinx.coroutines.delay

/**
 * El buscador del sistema: busca títulos en el catálogo completo (a través de Vision+) **y**
 * en las apps instaladas. Un launcher de verdad te deja abrir Netflix escribiendo «net» sin
 * recorrer el cajón de apps.
 *
 * En la tele se escribe con el teclado del mando (y se puede dictar); en el teléfono sale el
 * teclado del sistema.
 */
@Composable
fun SearchSurface(
    isTv: Boolean,
    repo: FeedRepository,
    onDismiss: () -> Unit,
    onOpen: (FeedItem) -> Unit,
    apps: List<AppEntry> = emptyList(),
    onApp: (AppEntry) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var searched by remember { mutableStateOf(false) }
        val fieldFocus = remember { FocusRequester() }
        val firstKey = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val appHits = remember(query, apps) { filterApps(apps, query) }

        val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) query = spoken
        }

        LaunchedEffect(Unit) {
            delay(160)
            if (isTv) {
                runCatching { firstKey.requestFocus() }
            } else {
                runCatching { fieldFocus.requestFocus() }
                runCatching { keyboard?.show() }
            }
        }

        // Debounce: se busca mientras se escribe, sin saturar.
        LaunchedEffect(query) {
            val clean = query.trim()
            if (clean.isBlank()) {
                results = emptyList()
                searched = false
                searching = false
                return@LaunchedEffect
            }
            searching = true
            delay(260)
            results = repo.search(clean)
            searching = false
            searched = true
        }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Buscar",
                        color = HomeColors.Text,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "en Vision+",
                        color = HomeColors.TextFaint,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    HomeButton(label = "Cerrar", onClick = onDismiss, icon = Icons.Rounded.Close, primary = false)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(HomeShapes.pill)
                            .background(HomeColors.Surface)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = HomeColors.TextFaint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isBlank()) {
                                Text(
                                    if (isTv) "Escribe con el mando o dicta un título…" else "Busca películas y series…",
                                    color = HomeColors.TextFaint,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it.take(60) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = HomeColors.Text,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(HomeColors.BlueBright),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(fieldFocus),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    HomeButton(
                        label = "Dictar",
                        onClick = {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                            runCatching { voice.launch(intent) }
                        },
                        icon = Icons.Rounded.Mic,
                    )
                }

                if (isTv) {
                    Spacer(Modifier.height(12.dp))
                    TvKeyboard(
                        firstKey = firstKey,
                        onKey = { key ->
                            query = when (key) {
                                "⌫" -> query.dropLast(1)
                                "Espacio" -> (query + " ").take(60)
                                "Limpiar" -> ""
                                else -> (query + key).take(60)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (appHits.isNotEmpty()) {
                        Text(
                            "Apps instaladas",
                            color = HomeColors.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(5.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 2.dp),
                        ) {
                            rowItems(appHits, key = { it.packageName }) { app ->
                                AppHitChip(app = app, onClick = { onApp(app) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            query.isBlank() -> SearchHint(
                                "Escribe o dicta un título",
                                "Busca películas y series, o abre una app instalada escribiendo su nombre.",
                            )
                            searching && results.isEmpty() -> SearchHint("Buscando…", "Consultando el catálogo completo.")
                            searched && results.isEmpty() && appHits.isEmpty() -> SearchHint(
                                "Sin resultados para «${query.trim()}»",
                                "Prueba con otro nombre, el título original o una app instalada.",
                            )
                            searched && results.isEmpty() -> SearchHint(
                                "Sin títulos en Vision+",
                                "Arriba están las apps instaladas que coinciden.",
                            )
                            else -> SearchResults(results, isTv = isTv, onOpen = onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHitChip(app: AppEntry, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = Modifier.height(38.dp),
        focusedScale = 1.03f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxHeight()
                .background(if (focused) HomeColors.SurfaceHigh else HomeColors.Surface)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                app.label,
                color = HomeColors.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResults(items: List<FeedItem>, isTv: Boolean, onOpen: (FeedItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (isTv) {
            when {
                maxWidth >= 1200.dp -> 7
                maxWidth >= 960.dp -> 6
                else -> 5
            }
        } else {
            3
        }
        val hPad = if (isTv) 0.dp else 4.dp
        val gap = 12.dp
        val cell = (maxWidth - hPad * 2 - gap * (columns - 1)) / columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(items, key = { it.ref.ifBlank { it.title } }) { item ->
                PosterCard(
                    item = item,
                    width = cell,
                    height = cell * 1.5f,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchHint(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = HomeColors.TextDim, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = HomeColors.TextFaint, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Teclado de mando: 3 filas de letras + acciones. Cada tecla es enfocable. */
@Composable
private fun TvKeyboard(firstKey: FocusRequester, onKey: (String) -> Unit) {
    val rows = listOf(
        "QWERTYUIOP".map { it.toString() },
        "ASDFGHJKLÑ".map { it.toString() },
        "ZXCVBNM".map { it.toString() } + "⌫",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { index, key ->
                    KeyboardKey(
                        label = key,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0 && key == "Q") Modifier.focusRequester(firstKey) else Modifier),
                        onPress = { onKey(key) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyboardKey(label = "Espacio", modifier = Modifier.weight(3f), onPress = { onKey("Espacio") })
            KeyboardKey(label = "Limpiar", modifier = Modifier.weight(1.4f), onPress = { onKey("Limpiar") })
        }
    }
}

@Composable
private fun KeyboardKey(label: String, modifier: Modifier = Modifier, onPress: () -> Unit) {
    FocusableSurface(
        onClick = onPress,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(9.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(HomeColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = HomeColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
