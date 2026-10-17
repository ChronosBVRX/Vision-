package com.chronosbvrx.visionplus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

/**
 * El reloj de la casa: SIEMPRE en el horario del centro de México (el mismo de la
 * agenda deportiva y la EPG), aunque el equipo esté configurado en otra zona horaria.
 * Puro y probado: la UI solo pinta lo que esta capa decide.
 */
internal object VisionClock {
    const val ZONE_ID: String = "America/Mexico_City"

    private val zone: TimeZone = TimeZone.getTimeZone(ZONE_ID)

    /** "20:42" (24 h) en el horario central de México. */
    fun timeText(nowMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(nowMs))

    /**
     * Milisegundos hasta el próximo cambio de minuto: el tic del reloj despierta justo
     * cuando cambia la hora mostrada (sin gastar batería con un tic por segundo). El
     * mínimo protege contra relojes que saltan hacia atrás.
     */
    fun msToNextMinute(nowMs: Long = System.currentTimeMillis()): Long {
        val remainder = nowMs % 60_000L
        return (60_000L - remainder).coerceIn(250L, 60_000L)
    }
}

/**
 * La píldora del reloj para toda la navegación (menú móvil y TV): hora del centro de
 * México, sin foco y sin toques — jamás se roba el D-pad ni estorba a los controles.
 */
@Composable
fun VisionClockBadge(isTv: Boolean = false, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(VisionClock.timeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(VisionClock.msToNextMinute() + 50L)
            text = VisionClock.timeText()
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(50))
            .padding(
                horizontal = if (isTv) 14.dp else 12.dp,
                vertical = if (isTv) 6.dp else 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "CDMX",
            style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = VisionTextSecondary,
        )
    }
}
