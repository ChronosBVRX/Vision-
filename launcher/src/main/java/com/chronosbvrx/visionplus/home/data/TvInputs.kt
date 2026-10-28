package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Qué clase de fuente es: el sintonizador de la tele, un HDMI o cualquier otra entrada. */
enum class TvSourceKind { TUNER, HDMI, OTHER }

/**
 * Una entrada de video del TELEVISOR (TV abierta, HDMI 1, AV…), no un canal de Vision+.
 *
 * Un launcher de tele de verdad enseña las fuentes del equipo: aquí vive lo que reporta
 * `TvInputManager`, ya ordenado y con nombre legible. El icono viaja resuelto para que la
 * tarjeta no tenga que volver a consultar el sistema mientras el mando se mueve.
 */
data class TvInputSource(
    val id: String,
    val label: String,
    val kind: TvSourceKind,
    val port: Int = 0,
    val packageName: String = "",
    val icon: ImageBitmap? = null,
    val passthrough: Boolean = false,
)

/** El nombre corto de la tarjeta: «TV abierta», «HDMI 1», «AV»… */
internal fun tvInputLabel(raw: String, kind: TvSourceKind, port: Int): String {
    val clean = raw.trim()
    if (kind == TvSourceKind.HDMI) {
        // Varios firmware no exponen el número de puerto: se saca del propio nombre
        // («HDMI3 (ARC)» → HDMI 3) para que la tarjeta no quede con un nombre sucio.
        val number = port.takeIf { it > 0 } ?: hdmiPortIn(clean)
        return if (number > 0) "HDMI $number" else clean.ifBlank { "HDMI" }
    }
    return when {
        clean.isNotBlank() -> clean
        kind == TvSourceKind.TUNER -> "TV abierta"
        else -> "Entrada de video"
    }
}

/**
 * El número de HDMI que aparece en un texto («HDMI 1», «HDMI3 (ARC)», «hdmi-2»).
 * Cero si no hay número: nunca se inventa un puerto.
 */
internal fun hdmiPortIn(text: String): Int {
    val normalized = text.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ")
    val match = Regex("""hdmi\s*([1-9])""").find(normalized) ?: return 0
    return match.groupValues[1].toIntOrNull() ?: 0
}

/** La línea que explica qué es, sin tecnicismos. */
internal fun tvInputSubtitle(item: TvInputSource): String = when (item.kind) {
    TvSourceKind.TUNER -> "Sintonizador de la tele · TV abierta"
    TvSourceKind.HDMI -> {
        val number = item.port.takeIf { it > 0 } ?: hdmiPortIn(item.label)
        if (number > 0) "Entrada HDMI $number" else "Entrada HDMI"
    }
    TvSourceKind.OTHER -> "Entrada de video del televisor"
}

/** El tipo del sistema (`TvInputInfo.TYPE_*`) traducido a lo que el launcher entiende. */
internal fun tvSourceKind(type: Int): TvSourceKind = when (type) {
    TvInputInfo.TYPE_TUNER -> TvSourceKind.TUNER
    TvInputInfo.TYPE_HDMI -> TvSourceKind.HDMI
    else -> TvSourceKind.OTHER
}

/**
 * Orden de una tele: **TV abierta primero**, luego los HDMI por número y al final el resto
 * por nombre. Es el mismo orden que espera cualquiera al pulsar «Entradas».
 *
 * Algunos firmware anuncian la MISMA entrada dos veces (una por app): se colapsa por puerto.
 */
internal fun orderTvInputs(items: List<TvInputSource>): List<TvInputSource> = items
    .distinctBy { sourceKey(it) }
    .sortedWith(
        compareBy<TvInputSource> {
            when (it.kind) {
                TvSourceKind.TUNER -> 0
                TvSourceKind.HDMI -> 1
                TvSourceKind.OTHER -> 2
            }
        }
            .thenBy { if (it.kind == TvSourceKind.HDMI) it.port.takeIf { p -> p > 0 } ?: hdmiPortIn(it.label) else 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )

/** Identidad física de la fuente: un HDMI repetido es el mismo puerto, no dos tarjetas. */
internal fun sourceKey(item: TvInputSource): String {
    val port = if (item.kind == TvSourceKind.HDMI) {
        item.port.takeIf { it > 0 } ?: hdmiPortIn(item.label)
    } else {
        0
    }
    return if (port > 0) "hdmi:$port" else "${item.kind}:${item.id.ifBlank { item.label.lowercase() }}"
}

/** Entradas que el usuario dejó visibles, en el orden de la tele. */
internal fun visibleInputs(items: List<TvInputSource>, hidden: Set<String>): List<TvInputSource> =
    orderTvInputs(items.filterNot { it.id in hidden })

/** «TV abierta» si el equipo tiene sintonizador; la usa el atajo del centro de control. */
internal fun tunerOf(items: List<TvInputSource>): TvInputSource? =
    items.firstOrNull { it.kind == TvSourceKind.TUNER }

/**
 * Las fuentes de video del televisor.
 *
 * No pide permisos ni escribe nada: solo lee lo que el equipo ya expone y lo abre con el
 * intent que ese firmware entienda. En un teléfono o en el emulador la lista sale vacía y la
 * interfaz lo dice con honestidad (no inventa entradas).
 */
class TvInputsRepository(context: Context) {
    private val app = context.applicationContext

    /** Lectura completa (bloquea con los iconos): siempre desde IO. */
    suspend fun inputs(): List<TvInputSource> = withContext(Dispatchers.IO) { readInputs() }

    private fun readInputs(): List<TvInputSource> = runCatching {
        val manager = app.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return@runCatching emptyList()
        manager.tvInputList.orEmpty().mapNotNull { info -> sourceOf(info) }.let(::orderTvInputs)
    }.getOrDefault(emptyList())

    private fun sourceOf(info: TvInputInfo): TvInputSource? = runCatching {
        val kind = tvSourceKind(info.type)
        val raw = runCatching { info.loadLabel(app).toString() }.getOrDefault("")
        val id = info.id.orEmpty()
        val port = if (kind == TvSourceKind.HDMI) {
            hdmiPortIn(raw).takeIf { it > 0 } ?: hdmiPortIn(id)
        } else {
            0
        }
        TvInputSource(
            id = id,
            label = tvInputLabel(raw, kind, port),
            kind = kind,
            port = port,
            packageName = runCatching { info.serviceInfo?.packageName.orEmpty() }.getOrDefault(""),
            icon = runCatching {
                info.loadIcon(app)?.toBitmap(width = 128, height = 128)?.asImageBitmap()
            }.getOrNull(),
            passthrough = info.isPassthroughInput,
        )
    }.getOrNull()

    /**
     * Abre la fuente con la ruta más directa que tenga ese televisor:
     *
     * 1. El URI oficial de la entrada passthrough (lo que usa el propio menú de HDMI).
     * 2. La app dueña del sintonizador / de la entrada (el «TV» del fabricante).
     * 3. Los ajustes del sistema, para no dejar el botón muerto.
     */
    fun open(item: TvInputSource): Boolean {
        if (item.passthrough && item.id.isNotBlank()) {
            val uri = runCatching { TvContract.buildChannelUriForPassthroughInput(item.id) }.getOrNull()
            if (uri != null && start(Intent(Intent.ACTION_VIEW, uri))) return true
        }
        val fromPackage = launchablePackage(item.packageName)
        if (fromPackage != null && start(fromPackage)) return true
        // Último recurso útil: la pantalla de entradas del sistema no existe como tal en
        // todos los firmware, así que se abre Ajustes en vez de dejar la tecla sin efecto.
        return start(Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    /**
     * Para el sintonizador también sirve: el «TV» del equipo suele ser la app dueña del
     * servicio de entrada, y ese es el intent que abre la TV abierta.
     */
    private fun launchablePackage(packageName: String): Intent? {
        if (packageName.isBlank()) return null
        val pm = app.packageManager
        return runCatching { pm.getLeanbackLaunchIntentForPackage(packageName) }.getOrNull()
            ?: runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()
    }

    private fun start(intent: Intent): Boolean = runCatching {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
