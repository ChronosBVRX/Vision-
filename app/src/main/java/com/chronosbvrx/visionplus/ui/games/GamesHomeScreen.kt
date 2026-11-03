package com.chronosbvrx.visionplus.ui.games

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GameCodes
import com.chronosbvrx.visionplus.data.games.GameDeepLink
import com.chronosbvrx.visionplus.data.games.GameInfo
import com.chronosbvrx.visionplus.data.games.GamePack
import com.chronosbvrx.visionplus.data.games.GameStore
import com.chronosbvrx.visionplus.data.games.NarratorMood
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entrada a Vision+ Play en el teléfono. La primera pantalla es el selector de juegos
 * (jugadores, duración y cómo se juega); abajo quedan el QR de la tele y el código
 * para unirse a una sala ya creada. Todo el flujo es a pantalla completa.
 */
@Composable
fun GamesHomeScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = RemoteConfig.flag(context, "games_enabled", true)
    var name by rememberSaveable { mutableStateOf(GameStore.nickname(context)) }
    var code by rememberSaveable { mutableStateOf("") }
    var spectator by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(GameStore.recent(context)) }

    GameFullscreenEffect()

    fun joinRoom(rawCode: String? = null) {
        if (busy) return
        val cleanCode = GameCodes.normalize(rawCode ?: code)
        val cleanName = name.trim()
        if (cleanCode == null) {
            error = "El código debe tener 6 caracteres (sin I, L, O, 0 ni 1)."
            return
        }
        if (cleanName.length < 2) {
            error = "Escribe un apodo de 2 a 14 caracteres."
            return
        }
        error = ""
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { GameClient.join(cleanCode, cleanName, spectator) }
            busy = false
            when (result) {
                is GameClient.Result.Ok -> {
                    GameStore.saveNickname(context, cleanName)
                    GameStore.savePlayer(context, cleanCode, result.value.playerToken)
                    GameStore.remember(context, cleanCode)
                    recent = GameStore.recent(context)
                    onOpenRoom(cleanCode)
                }
                is GameClient.Result.Error -> error = result.message
            }
        }
    }

    val scanOptions = remember {
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Escanea el QR de la tele")
            setBeepEnabled(false)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = GameDeepLink.codeFromScan(result.contents)
        if (scanned == null) {
            error = "Ese QR no es de una sala de Vision+ Play."
        } else {
            code = scanned
            joinRoom(scanned)
        }
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        FiestaLobbyBackdrop(Modifier.fillMaxSize(), VisionBlue)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(VisionIcons.Back, contentDescription = "Volver")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "VISION+ PLAY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = VisionTextPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        GameStageBadge("EN VIVO", VisionLiveRed)
                    }
                    Text(
                        "La noche empieza aquí",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                }
                Text(
                    "Sala ${recent.firstOrNull()?.let(GameCodes::format) ?: "nueva"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorView(
                    mood = NarratorMood.Welcome,
                    size = 112.dp,
                    animated = true,
                    alive = true,
                )
                Spacer(Modifier.width(10.dp))
                NarratorBubble("Elige un juego. Yo pongo la voz y el ritmo.")
            }

            if (!enabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VisionSurface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Los juegos no están disponibles por ahora. El resto de Vision+ sigue igual.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 14) name = it },
                label = { Text("Tu apodo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = spectator, onCheckedChange = { spectator = it })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Entrar como público", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Ideal si la sala ya está llena: votas sin competir por puntos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.92f)),
                border = BorderStroke(1.dp, VisionBlue.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "La sala se crea desde la tele",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Abre Vision+ en el televisor, entra a Juegos y elige uno de los 7 modos. " +
                            "Aquí solo te unes con el QR o el código.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                    Text(
                        "La tele pone el plató, el reloj y la voz; tu teléfono es el mando.",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionBlueBright,
                    )
                }
            }

            HorizontalDivider(color = VisionSurfaceHigh)
            Text("Ya tienes una sala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Escanea el QR de la tele o escribe el código de 6 caracteres.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        error = "Escribe un apodo de 2 a 14 caracteres."
                    } else {
                        error = ""
                        scanLauncher.launch(scanOptions)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(VisionIcons.Qr, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Escanear QR de la tele")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(7) },
                    label = { Text("Código") },
                    placeholder = { Text("ABC-234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { joinRoom() },
                    enabled = !busy && GameCodes.isValid(code) && name.trim().length >= 2,
                ) {
                    Text("Unirse")
                }
            }
            Text(
                "Seis caracteres. No se usan la I, la L, la O, el 0 ni el 1.",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )

            if (error.isNotBlank()) {
                Text(error, color = VisionLiveRed, style = MaterialTheme.typography.bodySmall)
            }

            if (recent.isNotEmpty()) {
                HorizontalDivider(color = VisionSurfaceHigh)
                Text("Tus salas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                recent.forEach { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VisionSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            GameCodes.format(room),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onOpenRoom(room) }) { Text("Entrar") }
                    }
                }
            }

            HorizontalDivider(color = VisionSurfaceHigh)
            Text("Voz de la presentadora", style = MaterialTheme.typography.bodyLarge)
            Text(
                "La voz y la música suenan en la tele, no en los teléfonos. Así la sala entera " +
                    "escucha al mismo tiempo y nadie se pierde la broma.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            Text(
                "La tele crea la sala y muestra el QR. El primero que entre es el anfitrión " +
                    "y lleva el ritmo de la partida desde su teléfono.",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
internal fun GameInfoCard(
    info: GameInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(info.accent)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) VisionSurfaceHigh else VisionSurface,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp)) {
            Image(
                painter = painterResource(gameArtRes(info.id)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, if (selected) VisionSurfaceHigh else VisionSurface),
                        ),
                    ),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(gameIcon(info.id), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(info.tagline, style = MaterialTheme.typography.bodySmall, color = accent)
                }
                if (selected) {
                    Text("ELEGIDO", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Text(info.description, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameMetric(VisionIcons.Players, info.playersLabel, accent)
                GameMetric(VisionIcons.Timer, info.timeLabel, accent)
                GameMetric(VisionIcons.Trophy, info.roundsLabel, accent)
            }
        }
    }
}

@Composable
private fun GameMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
    }
}

internal fun gameIcon(id: String) = when (id) {
    "charadas" -> VisionIcons.Charadas
    "wager" -> VisionIcons.Wager
    "impostor" -> VisionIcons.Impostor
    "dibujo" -> VisionIcons.Drawing
    "subasta" -> VisionIcons.Auction
    "fibbage" -> VisionIcons.Fibbage
    else -> VisionIcons.Games
}
