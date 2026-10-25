package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La tecla OK de un mando repite mientras se mantiene. Si el menú se abre a mitad de la
 * pulsación, la ventana nueva recibiría el KeyUp y activaría su primer botón: este candado se
 * come EXACTAMENTE ese KeyUp, lo reciba la tarjeta o el diálogo. Se vence solo para no dejar
 * una tecla muda si el equipo nunca entrega el KeyUp.
 */
internal object TvPressGuard {
    private var pendingAt = 0L

    fun expectKeyUp() {
        pendingAt = System.currentTimeMillis()
    }

    fun takeKeyUp(): Boolean {
        if (pendingAt == 0L) return false
        val expired = System.currentTimeMillis() - pendingAt > 2_500L
        pendingAt = 0L
        return !expired
    }
}

/** Cuánto hay que mantener OK para que salga el menú de opciones (como en un launcher). */
internal const val LONG_PRESS_MS = 550L

/**
 * La tarjeta enfocable del sistema. Un solo lugar define cómo se siente el mando en TODA la
 * pantalla: anillo blanco, sombra azul y un escalado corto (que se apaga en equipos modestos).
 *
 * `onLongClick` funciona con el dedo Y con el mando: se mantiene OK (o se pulsa Menú/Info en
 * el control) y salen las opciones de la app o del canal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = HomeShapes.card,
    focusedScale: Float = 1.045f,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val reduced = LocalReducedEffects.current
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduced) focusedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "focusScale",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color.White, shape)
                } else {
                    Modifier.border(1.dp, HomeColors.Stroke, shape)
                },
            )
            // El observador va ANTES del clickable: así ve el nodo enfocable que crea.
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent.keyCode
                val isOk = native == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    native == android.view.KeyEvent.KEYCODE_ENTER ||
                    native == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                // El KeyUp del OK largo que abrió un menú no debe activar NADA (ni esta
                // tarjeta ni el primer botón del diálogo que acaba de recibir el foco).
                if (event.type == KeyEventType.KeyUp && isOk && TvPressGuard.takeKeyUp()) {
                    return@onPreviewKeyEvent true
                }
                if (onLongClick == null) return@onPreviewKeyEvent false
                val isMenu = native == android.view.KeyEvent.KEYCODE_MENU ||
                    native == android.view.KeyEvent.KEYCODE_INFO
                // El botón Menú/Info del mando abre las opciones de una vez.
                if (event.type == KeyEventType.KeyDown && isMenu) {
                    longPressJob?.cancel()
                    longPressJob = null
                    longPressFired = false
                    onLongClick.invoke()
                    return@onPreviewKeyEvent true
                }
                if (!isOk) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            longPressFired = false
                            suppressClick = false
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(LONG_PRESS_MS)
                                if (focused) {
                                    longPressFired = true
                                    suppressClick = true
                                    TvPressGuard.expectKeyUp()
                                    onLongClick.invoke()
                                }
                            }
                        } else if (!longPressFired) {
                            // Hay mandos que no esperan al temporizador: la repetición
                            // sostenida ya es un OK largo.
                            longPressJob?.cancel()
                            longPressJob = null
                            longPressFired = true
                            suppressClick = true
                            TvPressGuard.expectKeyUp()
                            onLongClick.invoke()
                        }
                        false
                    }

                    KeyEventType.KeyUp -> {
                        longPressJob?.cancel()
                        longPressJob = null
                        false
                    }

                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    // Tras un OK largo, el KeyUp no debe abrir además la app/canal.
                    if (suppressClick) suppressClick = false else onClick()
                },
                onLongClick = onLongClick,
            ),
    ) {
        content(focused)
    }
}

/** Botón del hero y de los paneles: el primario es blanco (como el mando de una tele). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Rounded.PlayArrow,
    primary: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val reduced = LocalReducedEffects.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduced) 1.06f else 1f,
        animationSpec = tween(120),
        label = "buttonScale",
    )
    val background = when {
        primary && focused -> Color.White
        primary -> Color.White.copy(alpha = 0.94f)
        focused -> Color.White.copy(alpha = 0.24f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val foreground = if (primary) Color(0xFF05070C) else Color.White
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(HomeShapes.pill)
            .background(background)
            .then(
                if (focused) {
                    Modifier.border(2.dp, if (primary) HomeColors.Blue else Color.White, HomeShapes.pill)
                } else {
                    Modifier
                },
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(label, color = foreground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Encabezado de hilera: título + acción «Ver todo» opcional. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionModifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HomeColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            HomeButton(
                label = actionLabel,
                onClick = onAction,
                icon = null,
                primary = false,
                modifier = actionModifier,
            )
        }
    }
}

/** Barra de avance fina (3 dp) que no roba foco. */
@Composable
fun ThinProgress(progress: Int, modifier: Modifier = Modifier, color: Color = HomeColors.Blue) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                .background(color),
        )
    }
}

/** Píldora de texto (badge, «EN VIVO», género). */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HomeColors.Blue,
    textColor: Color = Color.White,
    dot: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(HomeShapes.pill)
            .background(color)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(textColor))
        }
        Text(
            text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** Monograma de respaldo mientras la imagen viaja o si el título no tiene arte. */
@Composable
fun Monogram(text: String, modifier: Modifier = Modifier, size: Int = 34) {
    val initials = text.trim().split(' ').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "V" }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            initials,
            color = Color.White.copy(alpha = 0.16f),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = size.sp),
            fontWeight = FontWeight.Black,
        )
    }
}

/** Sombra inferior para que el texto siempre se lea sobre cualquier arte. */
fun bottomScrim(alpha: Float = 0.92f): Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
)
