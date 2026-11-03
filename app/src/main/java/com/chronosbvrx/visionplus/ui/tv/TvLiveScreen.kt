package com.chronosbvrx.visionplus.ui.tv

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.live.ChannelLogo
import com.chronosbvrx.visionplus.ui.live.LiveCopy
import com.chronosbvrx.visionplus.ui.live.LiveFavorites
import com.chronosbvrx.visionplus.ui.live.rememberLiveState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Categorías rápidas del carrusel; los nombres coinciden con `LiveTaxonomy.genreFor`. */
private val LIVE_CATEGORIES = listOf(
    "Todo" to null,
    "Deportes" to "Deportes",
    "24/7 Series" to "Series",
    "Noticias" to "Noticias",
    "Infantil" to "Infantiles",
    "Películas 24/7" to "Películas",
)

@Composable
fun TvLiveScreen(onPlay: (LiveChannel) -> Unit) {
    TvLiveScreen(onPlay = { ch, _, _ -> onPlay(ch) })
}

/**
 * «En vivo» de la tele: hero cinematográfico del canal enfocado, filtros de país y
 * categoría, y un carrusel horizontal de tarjetas 16:10. Sin teclado fijo ni rieles
 * laterales: el buscador vive en un modal y el mando recorre todo.
 */
@Composable
fun TvLiveScreen(
    onPlay: (LiveChannel, List<String>, String?) -> Unit,
    returnChannelCode: String? = null,
) {
    val state = rememberLiveState()
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var countryPicker by rememberSaveable { mutableStateOf(false) }
    var selectedCountry by rememberSaveable { mutableStateOf<String?>("México") }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }
    var heroChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var epg by remember { mutableStateOf<Map<String, X4KApi.EpgNow>>(emptyMap()) }
    var clockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val entryRequester = remember { FocusRequester() }
    val returnRequester = remember { FocusRequester() }
    val landing = rememberLandingFocus()

    val allChannels = state.channels
    val allNames = remember(allChannels) { allChannels.map { it.name } }
    val countries = remember(allNames) { LiveTaxonomy.countriesOf(allNames) }
    val genres = remember(allNames) { LiveTaxonomy.genresOf(allNames) }

    LaunchedEffect(countries) {
        if (selectedCountry != null && !countries.contains(selectedCountry)) selectedCountry = null
    }
    LaunchedEffect(allChannels) {
        // La muestra debe incluir los canales que el usuario ve primero. El orden del
        // portal no es el de la parrilla: si tomáramos los primeros 200 crudos, Canal 5
        // y Las Estrellas podían quedarse sin EPG aunque la guía sí exista.
        val sample = allChannels
            .distinctBy { it.url }
            .distinctBy { LiveTaxonomy.channelKey(it.name).ifBlank { it.url } }
            .sortedWith(
                compareBy(
                    { LiveTaxonomy.popularityRank(it.name) },
                    { LiveTaxonomy.countryFor(it.name) != "México" },
                    { it.name },
                ),
            )
            .take(200)
            .map { it.url.removePrefix("xtvlive:") to it.name }
        if (sample.isNotEmpty()) {
            var loaded: Map<String, X4KApi.EpgNow> = emptyMap()
            // El VPS puede tardar o rate-limitar la primera ráfaga; reintentamos sin
            // bloquear la parrilla (las tarjetas caen a la categoría mientras llega).
            for (attempt in 0 until 3) {
                loaded = withContext(Dispatchers.IO) {
                    runCatching { X4KApi.epgBulkFor(context, sample) }.getOrDefault(emptyMap())
                }
                if (loaded.isNotEmpty()) break
                delay(1_500)
            }
            epg = loaded
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clockMs = System.currentTimeMillis()
        }
    }

    val channels = remember(
        allChannels,
        state.query,
        selectedCountry,
        selectedGenre,
        favoritesOnly,
    ) {
        allChannels.asSequence()
            .filter { channel ->
                (selectedCountry == null || LiveTaxonomy.countryFor(channel.name) == selectedCountry) &&
                    (selectedGenre == null || LiveTaxonomy.genreFor(channel.name) == selectedGenre) &&
                    (!favoritesOnly || LiveFavorites.has(channel.url)) &&
                    (state.query.isBlank() || channel.name.contains(state.query.trim(), ignoreCase = true))
            }
            // Una sola tarjeta por URL y por canal: «CANAL 5 HD» y «CANAL 5 FHD»
            // no deben ocupar dos de las 4–5 tarjetas visibles (el launcher ya lo hacía).
            .distinctBy { it.url }
            .distinctBy { LiveTaxonomy.channelKey(it.name).ifBlank { it.url } }
            .sortedWith(
                compareBy(
                    { LiveTaxonomy.popularityRank(it.name) },
                    { LiveTaxonomy.countryFor(it.name) != "México" },
                    { it.name },
                ),
            )
            .toList()
    }

    LaunchedEffect(channels) {
        if (heroChannel == null || channels.none { it.url == heroChannel?.url }) {
            heroChannel = channels.firstOrNull()
        }
        if (focused != null && channels.none { it.url == focused?.url }) focused = null
    }
    LaunchedEffect(channels.size, returnChannelCode) {
        repeat(25) {
            if (focused != null) return@LaunchedEffect
            val returning = returnChannelCode != null &&
                channels.any { it.url.removePrefix("xtvlive:") == returnChannelCode }
            runCatching {
                if (returning) returnRequester.requestFocus() else entryRequester.requestFocus()
            }
            delay(80)
        }
        runCatching { landing.request() }
    }
    LaunchedEffect(searchOpen, countryPicker) {
        if (!searchOpen && !countryPicker) {
            delay(120)
            runCatching { entryRequester.requestFocus() }
        }
    }

    val hero = focused?.takeIf { candidate -> channels.any { it.url == candidate.url } }
        ?: heroChannel?.takeIf { candidate -> channels.any { it.url == candidate.url } }
        ?: channels.firstOrNull()
    val heroEpg = hero?.let { epg[it.url.removePrefix("xtvlive:")] }
    val activeCodes = remember(channels) { channels.map { it.url.removePrefix("xtvlive:") } }
    val scope = when {
        selectedCountry != null -> "TV · $selectedCountry"
        selectedGenre != null -> selectedGenre
        else -> "TV en vivo"
    }

    fun play(channel: LiveChannel) {
        // Un solo OK abre el canal. El hero ya se actualiza con el foco del mando,
        // así que no hace falta un primer clic de "preview".
        onPlay(channel, activeCodes, scope)
    }

    BackHandler(enabled = searchOpen) { searchOpen = false; state.query = "" }

    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .padding(horizontal = 48.dp, vertical = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(0.54f)) {
            TvLiveHero(hero, heroEpg, clockMs, Modifier.fillMaxSize())
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TvLiveFilterPill(
                label = "País: ${selectedCountry ?: "Todos"}",
                icon = Icons.Default.Language,
                onClick = { countryPicker = true },
                modifier = Modifier.landingFocus(landing),
            )
            // Botón de búsqueda propio del bloque En vivo, a un paso del filtro
            // de país (el icono suelto del extremo era difícil de alcanzar).
            TvCompactAction(
                label = "Buscar",
                icon = Icons.Default.Search,
                height = 38.dp,
                onClick = { searchOpen = true },
            )
            Box(Modifier.weight(1f)) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 2.dp, end = 12.dp),
                ) {
                    items(LIVE_CATEGORIES, key = { it.first }) { (label, genre) ->
                        TvLiveCategoryChip(
                            label = label,
                            selected = selectedGenre == genre && !favoritesOnly,
                            onClick = {
                                favoritesOnly = false
                                selectedGenre = genre
                            },
                        )
                    }
                }
                // Difuminado lateral: la última categoría se desvanece en vez de cortarse
                // en seco contra los botones de acción.
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(28.dp)
                        .height(38.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, VisionBlack),
                            ),
                        ),
                )
            }
            Spacer(Modifier.width(6.dp))
            TvRoundIcon(Icons.Default.Star, "Favoritos", {
                favoritesOnly = !favoritesOnly
                selectedGenre = null
            }, size = 38.dp)
            TvRoundIcon(Icons.Default.Refresh, LiveCopy.REFRESH, { state.refresh() }, size = 38.dp)
        }

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(0.46f), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        state.loading -> "Cargando canales…"
                        state.query.isNotBlank() -> LiveCopy.NO_RESULTS
                        favoritesOnly -> "Sin favoritos todavía"
                        else -> LiveCopy.NO_CHANNELS
                    },
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth().weight(0.46f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.url }) { index, channel ->
                    TvLiveChannelCard(
                        channel = channel,
                        number = channel.number ?: (index + 1),
                        epg = epg[channel.url.removePrefix("xtvlive:")],
                        nowMs = clockMs,
                        favorite = LiveFavorites.has(channel.url),
                        onClick = { play(channel) },
                        onLongClick = {
                            LiveFavorites.toggle(channel.url)
                            Toast.makeText(
                                context,
                                if (LiveFavorites.has(channel.url)) "Agregado a favoritos" else "Quitado de favoritos",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) focused = channel }
                            .then(
                                if (channel == channels.firstOrNull()) {
                                    Modifier.focusRequester(entryRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (returnChannelCode != null &&
                                    channel.url.removePrefix("xtvlive:") == returnChannelCode
                                ) Modifier.focusRequester(returnRequester) else Modifier,
                            ),
                    )
                }
            }
        }
    }

    if (searchOpen) {
        TvLiveSearchDialog(
            query = state.query,
            onQuery = { state.query = it },
            channels = allChannels,
            countries = countries,
            selectedCountry = selectedCountry,
            onCountry = { selectedCountry = it },
            onPlay = { channel ->
                searchOpen = false
                state.query = ""
                play(channel)
            },
            onDismiss = { searchOpen = false; state.query = "" },
        )
    }
    if (countryPicker) {
        TvLiveCountryDialog(
            countries = countries,
            selected = selectedCountry,
            onPick = { selectedCountry = it; countryPicker = false },
            onDismiss = { countryPicker = false },
        )
    }
}

@Composable
private fun TvLiveSearchDialog(
    query: String,
    onQuery: (String) -> Unit,
    channels: List<LiveChannel>,
    countries: List<String>,
    selectedCountry: String?,
    onCountry: (String?) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // `rememberDialogFocus` reintenta hasta que la ventana del diálogo realmente
    // tiene foco. Un `requestFocus()` único a los 260 ms se perdía en TV boxes y
    // dejaba el mando sin destino: ni teclado ni botón Dictar.
    val keyboardFocus = rememberDialogFocus()
    val voiceFocus = remember { FocusRequester() }
    val currentCountries by rememberUpdatedState(countries)
    val currentOnQuery by rememberUpdatedState(onQuery)
    val currentOnCountry by rememberUpdatedState(onCountry)
    val acceptSpeech: (String) -> Unit = { raw ->
        val spoken = raw.trim()
        if (spoken.isNotBlank()) {
            val country = countryFromSpeech(spoken, currentCountries)
            if (country != null) {
                currentOnCountry(country)
                currentOnQuery("")
            } else {
                currentOnQuery(spoken)
            }
        }
    }
    val acceptSpeechState = rememberUpdatedState(acceptSpeech)

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { acceptSpeechState.value(it) }
        }
    }

    // Fallback para televisiones sin la actividad de Google: reconocimiento
    // nativo. Solo se crea cuando hace falta y se destruye al cerrar el modal.
    val speechRecognizer = remember(context) {
        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        }.getOrNull()
    }
    val speechListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { acceptSpeechState.value(it) }
            }
            override fun onError(error: Int) {
                Toast.makeText(
                    context,
                    "No te escuché. Intenta otra vez o escribe con el mando.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.let { runCatching { it.setRecognitionListener(speechListener) } }
        onDispose { speechRecognizer?.let { runCatching { it.destroy() } } }
    }

    val nativeSpeech: () -> Unit = {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Toast.makeText(context, "Dictado no disponible en este equipo", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el canal o el país…")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            runCatching { recognizer.startListening(intent) }.onFailure {
                Toast.makeText(context, "No se pudo iniciar el dictado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            nativeSpeech()
        } else {
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val launchVoiceIntent: () -> Boolean = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el canal o el país…")
        }
        try {
            voiceLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    val requestNativeSpeech: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            nativeSpeech()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val startVoice = {
        // Primero la actividad estándar (no pide permiso a la app). Si el equipo
        // no la trae, se usa el reconocedor nativo con permiso de micrófono.
        if (!launchVoiceIntent()) requestNativeSpeech()
    }

    val normalizedQuery = query.trim()
    val results = remember(normalizedQuery, selectedCountry, channels) {
        liveSearchResults(channels, normalizedQuery, selectedCountry).take(40)
    }
    // Predicciones: mientras escribe, los primeros aciertos también se ofrecen
    // como atajos para no pelear con una letra equivocada en el teclado.
    val suggestions = remember(normalizedQuery, results) {
        if (normalizedQuery.isBlank()) emptyList() else results.take(5)
    }

    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .padding(horizontal = 32.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = Color(0xF2141C2C)),
                border = Border(
                    BorderStroke(1.dp, VisionBlue.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(24.dp),
                ),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 520.dp)
                    .modalFocusTrap(),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Buscar canales",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Todo el catálogo o filtra por país. Escribe, dicta o usa el mando.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                            )
                        }
                        TvCompactAction(
                            "Dictar",
                            icon = Icons.Default.Mic,
                            onClick = startVoice,
                            modifier = Modifier
                                .focusRequester(voiceFocus)
                                .focusProperties { down = keyboardFocus.requester },
                        )
                        Spacer(Modifier.width(8.dp))
                        TvCompactAction("Cerrar", onClick = onDismiss)
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        item(key = "all-countries") {
                            TvLiveCategoryChip(
                                label = "Todos",
                                selected = selectedCountry == null,
                                onClick = { onCountry(null) },
                                modifier = Modifier.focusProperties { down = keyboardFocus.requester },
                            )
                        }
                        items(countries, key = { "search-country-$it" }) { country ->
                            TvLiveCategoryChip(
                                label = country,
                                selected = selectedCountry == country,
                                onClick = { onCountry(country) },
                                modifier = Modifier.focusProperties { down = keyboardFocus.requester },
                            )
                        }
                    }

                    if (suggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                "Quizá:",
                                style = MaterialTheme.typography.labelSmall,
                                color = VisionTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(suggestions, key = { "suggest-${it.url}" }) { channel ->
                                    TvLiveCategoryChip(
                                        label = channel.name,
                                        selected = false,
                                        onClick = { onQuery(channel.name) },
                                        modifier = Modifier.focusProperties { down = keyboardFocus.requester },
                                    )
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(0.47f).fillMaxHeight()) {
                            Text(
                                "TECLADO",
                                style = MaterialTheme.typography.labelSmall,
                                color = VisionBlueBright,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(5.dp))
                            TvKeyboard(
                                text = query,
                                onTextChange = onQuery,
                                firstKey = Modifier
                                    .dialogFocus(keyboardFocus)
                                    .focusProperties { up = voiceFocus },
                                modifier = Modifier.fillMaxWidth().height(224.dp),
                            )
                            Text(
                                "Usa el mando para escribir. «Dictar» busca por voz.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VisionTextSecondary,
                                modifier = Modifier.padding(top = 7.dp),
                            )
                        }
                        Column(Modifier.weight(0.53f).fillMaxHeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "RESULTADOS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionBlueBright,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${results.size} canales",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionTextSecondary,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            if (results.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (query.isBlank()) "Escribe un canal o elige un país"
                                        else "Sin coincidencias. Prueba con el nombre o el país.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VisionTextSecondary,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    items(results, key = { "search-${it.url}" }) { channel ->
                                        TvLiveSearchResult(
                                            channel = channel,
                                            country = LiveTaxonomy.countryFor(channel.name),
                                            group = channel.effectiveGroup(),
                                            onClick = { onPlay(channel) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLiveSearchResult(
    channel: LiveChannel,
    country: String,
    group: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurfaceHigh,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF090D16),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(11.dp)),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ChannelLogo(
                url = channel.logo,
                name = channel.name,
                modifier = Modifier.size(32.dp),
                maxUpscale = 1.8f,
                initialsStyle = MaterialTheme.typography.labelMedium,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(country, group).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    LiveTaxonomy.countryCode(country),
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionBlue,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "OK",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** «Estados Unidos»/«USA» → «EE. UU.»; el resto se busca por el nombre del canal. */
private fun countryFromSpeech(spoken: String, countries: List<String>): String? {
    val text = spoken.trim().lowercase(Locale.ROOT)
    val aliases = mapOf(
        "mexico" to "México", "méxico" to "México",
        "estados unidos" to "EE. UU.", "usa" to "EE. UU.", "eua" to "EE. UU.",
        "colombia" to "Colombia", "argentina" to "Argentina",
        "chile" to "Chile", "peru" to "Perú", "perú" to "Perú",
        "ecuador" to "Ecuador", "bolivia" to "Bolivia",
        "venezuela" to "Venezuela", "paraguay" to "Paraguay",
        "uruguay" to "Uruguay", "costa rica" to "Costa Rica",
        "panama" to "Panamá", "panamá" to "Panamá",
        "honduras" to "Honduras", "guatemala" to "Guatemala",
        "el salvador" to "El Salvador", "nicaragua" to "Nicaragua",
        "republica dominicana" to "R. Dominicana", "puerto rico" to "Puerto Rico",
        "espana" to "España", "españa" to "España",
        "brasil" to "Brasil", "canada" to "Canadá", "canadá" to "Canadá",
    )
    for ((needle, country) in aliases) {
        if (text == needle || text.endsWith(" $needle") || text.startsWith("$needle ")) {
            if (countries.contains(country)) return country
        }
    }
    return countries.firstOrNull { text == it.lowercase(Locale.ROOT) }
}

@Composable
private fun TvLiveCountryDialog(
    countries: List<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    TvDialog("País", onDismiss = onDismiss) {
        LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "all") {
                TvActionOption("Todos", trailing = { if (selected == null) Icon(VisionIcons.Check, contentDescription = null, tint = VisionBlue) }) {
                    onPick(null)
                }
            }
            items(countries, key = { it }) { country ->
                TvActionOption(
                    country,
                    trailing = { if (selected == country) Icon(VisionIcons.Check, contentDescription = null, tint = VisionBlue) },
                ) { onPick(country) }
            }
        }
        TvCompactAction("Cerrar", onClick = onDismiss)
    }
}


