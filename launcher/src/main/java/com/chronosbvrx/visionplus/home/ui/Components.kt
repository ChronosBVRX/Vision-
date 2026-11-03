package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
 * pulsación, la ventana nueva recibiría el KeyUp y activaría su primer botón («Abrir» en una
 * app): este candado se come ese KeyUp, lo reciba la tarjeta o el diálogo.
 *
 * La versión anterior vencía a los 2.5 s. Eso fallaba justo con la pulsación sostenida: la
 * persona abre el menú, sigue apretando mientras lo lee y, al soltar después de ese tiempo,
 * el KeyUp ya no se comía y la app se abría sola (el menú parecía cerrarse). Ahora el candado
 * NO vence por tiempo: come todos los KeyUp hasta que llega un OK nuevo y deliberado —un
 * KeyDown con `repeatCount == 0` DESPUÉS de haber visto el KeyUp de la pulsación que abrió el
 * menú—. Las repeticiones del mando (`repeatCount > 0`) nunca lo desarman.
 */
internal class PressReleaseGuard(
    /**
     * Silencio mínimo entre dos OK para considerarlos pulsaciones distintas. Los mandos
     * repiten cada 40-120 ms; un botón que se acaba de soltar y se vuelve a pulsar deja un
     * hueco perceptible. El reloj se inyecta en las pruebas.
     */
    private val quietMs: Long = 400L,
    private val clock: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    private var armed = false
    private var lastOkAt = 0L

    fun expectRelease() {
        armed = true
        lastOkAt = clock()
    }

    fun consumeRelease(): Boolean {
        if (!armed) return false
        lastOkAt = clock()
        return true
    }

    /** Hay un OK largo pendiente de soltar: ningún click debe dispararse todavía. */
    fun isArmed(): Boolean = armed

    /**
     * Un OK nuevo solo puede desarmar el candado si el mando estuvo callado [quietMs]. Así se
     * cubren los dos tipos de control remoto que rompían el OK largo:
     * - los que repiten con `repeatCount > 0`, y
     * - los que mandan cada repetición como un `KeyDown` nuevo con `repeatCount == 0`.
     * En ambos, mientras la persona siga apretando los eventos llegan seguidos y el candado
     * permanece armado; al soltar, el primer OK deliberado (después del silencio) vuelve a
     * funcionar con normalidad.
     */
    fun noteKeyDown(repeatCount: Int) {
        if (!armed) return
        val now = clock()
        if (repeatCount == 0 && now - lastOkAt >= quietMs) {
            armed = false
        } else {
            // Sigue siendo la misma pulsación (o una repetición): mantiene vivo el candado.
            lastOkAt = now
        }
    }
}

internal object TvPressGuard {
    private val guard = PressReleaseGuard()

    fun expectKeyUp() = guard.expectRelease()

    fun takeKeyUp(): Boolean = guard.consumeRelease()

    /** Un OK nuevo (ya soltado el anterior) cancela el candado. */
    fun noteKeyDown(repeatCount: Int) = guard.noteKeyDown(repeatCount)

    /** Un click con el candado armado es la cola del OK largo, no una pulsación nueva. */
    fun isArmed(): Boolean = guard.isArmed()
}

/** ¿Es la tecla OK del mando/teclado? */
internal fun isOkKeyCode(nativeKeyCode: Int): Boolean =
    nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

/** Cuánto hay que mantener OK para que salga el menú de opciones (como en un launcher). */
internal const val LONG_PRESS_MS = 550L

/**
 * Una fila que se «apaga» cuando el mando se posa en una de sus tarjetas. El estado vive en la
 * fila, no en cada tarjeta: así todas las demás bajan al 75 % y la enfocada es la única que
 * manda (10-foot UI).
 */
val LocalCardRowActive = compositionLocalOf { false }

@Composable
fun CardFocusScope(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var rowActive by remember { mutableStateOf(false) }
    Box(modifier = modifier.onFocusChanged { rowActive = it.hasFocus }) {
        CompositionLocalProvider(LocalCardRowActive provides rowActive) {
            content()
        }
    }
}

/**
 * La tarjeta enfocable del sistema. Un solo lugar define cómo se siente el mando en TODA la
 * pantalla: escala suave elástica, borde blanco nítido y sombra ambiental. Las tarjetas que no
 * tienen el foco bajan su opacidad para dirigir la vista al elemento activo.
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
    focusedScale: Float = 1.06f,
    /** Las apps se pintan a sangre: sin borde cuando no tienen el foco. */
    showIdleBorder: Boolean = true,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val reduced = LocalReducedEffects.current
    val rowActive = LocalCardRowActive.current
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduced) focusedScale else 1f,
        animationSpec = if (reduced) {
            tween(durationMillis = 120)
        } else {
            tween(
                durationMillis = 200,
                easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f),
            )
        },
        label = "focusScale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (rowActive && !focused) 0.75f else 1f,
        animationSpec = tween(durationMillis = if (reduced) 100 else 160),
        label = "cardFocusDim",
    )
    val interaction = remember { MutableInteractionSource() }
    val ringWidth = when {
        focused -> 2.dp
        showIdleBorder -> 1.dp
        else -> 0.dp
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .then(
                if (focused && !reduced) {
                    Modifier.shadow(
                        elevation = 24.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.White.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.85f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                Color.White,
                                HomeColors.Cyan.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    showIdleBorder -> Modifier.background(HomeColors.Stroke)
                    else -> Modifier
                },
            )
            .padding(ringWidth)
            .clip(shape)
            // El observador va ANTES del clickable: así ve el nodo enfocable que crea.
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent.keyCode
                val isOk = isOkKeyCode(native)
                // Un OK nuevo y deliberado (después de soltar el que abrió el menú) rearma el
                // click normal; las repeticiones del mando no cuentan.
                if (isOk && event.type == KeyEventType.KeyDown) {
                    TvPressGuard.noteKeyDown(event.nativeKeyEvent.repeatCount)
                }
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
                    // OJO: no se arma el candado —la tecla que se suelta es Menú, no OK— para
                    // que el primer OK dentro del menú funcione a la primera.
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
                    // Tras un OK largo, el KeyUp no debe abrir además la app/canal. Se
                    // comprueban las DOS vías: la tarjeta que abrió el menú (`suppressClick`)
                    // y la acción del diálogo que acaba de recibir el foco (candado global).
                    if (suppressClick || TvPressGuard.isArmed()) {
                        suppressClick = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick,
            ),
    ) {
        content(focused)
    }
}

/**
 * Botón del hero y de los paneles. El primario es SIEMPRE blanco sólido con texto oscuro
 * (alto contraste al enfocar); el secundario es vidrio esmerilado: blanco al 10 %, borde fino
 * y un velo que se aclara con el foco.
 */
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
        targetValue = if (focused && !reduced) 1.045f else 1f,
        animationSpec = if (reduced) {
            tween(120)
        } else {
            tween(200, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))
        },
        label = "buttonScale",
    )
    val outerBrush = when {
        primary && focused -> Brush.linearGradient(
            listOf(Color.White, HomeColors.Cyan.copy(alpha = 0.92f), Color.White),
        )
        primary -> Brush.linearGradient(
            listOf(Color.White, Color(0xFFE8F4FF), Color.White.copy(alpha = 0.92f)),
        )
        focused -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.78f),
                HomeColors.Cyan.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.24f),
            ),
        )
        else -> Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.22f)),
        )
    }
    val innerBrush = when {
        primary -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F7FF)))
        focused -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f)),
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.055f)),
        )
    }
    val foreground = if (primary) Color(0xFF05070C) else Color.White
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused) {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = HomeShapes.button,
                        clip = false,
                        ambientColor = HomeColors.Cyan.copy(alpha = 0.26f),
                        spotColor = Color.Black.copy(alpha = 0.78f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(HomeShapes.button)
            .background(outerBrush)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(1.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(HomeShapes.buttonInner)
                .background(innerBrush)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
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
fun ThinProgress(
    progress: Int,
    modifier: Modifier = Modifier,
    color: Color = HomeColors.Cyan,
    height: Dp = 3.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.58f)),
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
