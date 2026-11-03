package com.chronosbvrx.visionplus.ui.remote

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.remote.RemoteClient
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import com.chronosbvrx.visionplus.data.remote.RemoteStatus
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * Barra flotante del teléfono: lo que la tele está reproduciendo, siempre a la vista mientras
 * se navega por la app. Tocar la barra abre el control remoto completo; el botón central
 * pausa o reanuda sin salir de la pantalla en la que estés.
 */
@Composable
fun RemoteNowBar(visible: Boolean, onOpen: () -> Unit) {
    val state by RemoteClient.state
    val np = state.nowPlaying
    val show = visible && state.status == RemoteStatus.CONNECTED && np.active
    if (!show) return

    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.updatedAt, np.playing) {
        while (np.playing) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val elapsed = if (np.playing && state.nowReceivedAt > 0L) {
        (tick - state.nowReceivedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val total = np.durationMs.coerceAtLeast(0L)
    val position = if (total > 0L) {
        (np.positionMs + elapsed).coerceIn(0L, total)
    } else {
        np.positionMs
    }
    val fraction = if (total > 0L) (position.toFloat() / total).coerceIn(0f, 1f) else 0f

    Surface(
        color = VisionSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClickLabel = "Abrir control remoto", onClick = onOpen),
    ) {
        Column {
            if (!np.live) {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = VisionBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
            Row(Modifier.height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = np.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        np.title.ifBlank { "En la tele" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (np.live) np.subtitle.ifBlank { "EN VIVO" } else np.subtitle.ifBlank { "En la tele" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (np.live) Color(0xFFFF6B6B) else VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { RemoteClient.sendCommand(RemoteProto.CMD_PLAY_PAUSE) }) {
                    Icon(
                        if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (np.playing) "Pausar la tele" else "Reanudar la tele",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
