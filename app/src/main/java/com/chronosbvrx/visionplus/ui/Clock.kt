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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Dónde vive el reloj flotante: SOLO en la Smart TV (píldora arriba al centro, sin foco, para
 * que el D-pad nunca caiga en él). En el móvil estorba: ahí la hora va como texto pequeño
 * dentro de la barra superior (`VisionClockText`), nunca encima de ella.
 *
 * Apagado en los reproductores (ahí manda el contenido) y en el buscador (el campo ocupa
 * la parte superior).
 */
internal fun showsFloatingClock(isTv: Boolean, route: Route): Boolean =
    isTv &&
        route !is Route.Player &&
        route !is Route.LivePlayer &&
        route !is Route.Search

/** El tic de la hora mostrada: despierta justo al cambiar el minuto, nunca cada segundo. */
@Composable
private fun rememberClockText(): String {
    var text by remember { mutableStateOf(VisionClock.timeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(VisionClock.msToNextMinute() + 50L)
            text = VisionClock.timeText()
        }
    }
    return text
}

/**
 * La píldora del reloj para la navegación de Smart TV: hora del centro de México, sin foco
 * y sin toques — jamás se roba el D-pad ni estorba a los controles.
 */
@Composable
fun VisionClockBadge(modifier: Modifier = Modifier) {
    val text = rememberClockText()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "CDMX",
            style = MaterialTheme.typography.labelMedium,
            color = VisionTextSecondary,
        )
    }
}

/**
 * La hora del menú del móvil: texto plano y pequeño dentro de la barra superior (junto al
 * logo), sin fondo, borde ni etiqueta — se lee como un dato de la barra, no como un objeto
 * flotante encima de las pantallas. La zona horaria viaja en la descripción accesible para
 * no ensuciar la barra.
 */
@Composable
fun VisionClockText(modifier: Modifier = Modifier) {
    val text = rememberClockText()
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = "$text, hora del centro de México" },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.62f),
        letterSpacing = 0.6.sp,
        maxLines = 1,
    )
}
