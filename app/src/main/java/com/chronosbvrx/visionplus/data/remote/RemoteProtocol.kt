package com.chronosbvrx.visionplus.data.remote

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versión del cable: se sube si cambia el formato de los mensajes. */
const val REMOTE_PROTO = 1

/**
 * Un mensaje del control remoto: **un JSON por línea** sobre el socket TCP de la LAN. El teléfono
 * manda órdenes (`key`, `open`, `ping`); la tele contesta con su estado (`hello`, `now`, `pong`)
 * y con el emparejamiento (`needpin`, `ok`, `denied`).
 *
 * Los campos son opcionales y se omiten cuando van en null (`encodeDefaults = false`), así el
 * cable queda corto y legible.
 */
@Serializable
data class RemoteMsg(
    /** Tipo: una de las constantes de [RemoteProto]. */
    val t: String,
    val name: String? = null,
    val model: String? = null,
    val token: String? = null,
    val paired: Boolean? = null,
    val pin: String? = null,
    /** Código de tecla de Android (`KeyEvent.KEYCODE_*`). */
    val code: Int? = null,
    /** `tap` (abajo y arriba), `down` o `up`. */
    val action: String? = null,
    /** `vod` o `live`. */
    val kind: String? = null,
    /** Referencia del contenido (playCode XTV o código de canal). */
    val ref: String? = null,
    val title: String? = null,
    val poster: String? = null,
    /** URL de respaldo (videoUrl o dirección del canal). */
    val url: String? = null,
    /**
     * Contexto del capítulo cuando `ref` es el playCode de un episodio: la temporada activa
     * (su titleId) y sus vecinas, para que la tele reproduzca ESE capítulo y pueda seguir con
     * el siguiente sin volver al catálogo.
     */
    val series: String? = null,
    val seasonIndex: Int? = null,
    /** Índice del capítulo activo dentro de la temporada (para la tarjeta del teléfono). */
    val episodeIndex: Int? = null,
    val seasonIds: List<String>? = null,
    val seasonNames: List<String>? = null,
    /**
     * Minuto exacto que el teléfono está viendo (0/ausente = desde el principio). Viaja en
     * `open` para que la tele continúe donde se quedó el teléfono, no donde se quedó ella.
     */
    val pos: Long? = null,
    /** Código de sincronización que el teléfono le entrega a la tele (mensaje `sync`). */
    val syncCode: String? = null,
    /** Qué está viendo la tele (para el teléfono). */
    val screen: String? = null,
    /** Segunda línea del contenido: temporada/capítulo o contexto del canal. */
    val subtitle: String? = null,
    /** La tele está reproduciendo (true) o en pausa (false). */
    val playing: Boolean? = null,
    /** Minuto exacto de la reproducción en la tele. */
    val positionMs: Long? = null,
    /** Duración conocida del contenido (0 si aún no se sabe o es en vivo). */
    val durationMs: Long? = null,
    /** ¿La barra de progreso se puede mover? En vivo no. */
    val canSeek: Boolean? = null,
    /** Hay un capítulo siguiente/anterior en la serie. */
    val hasNext: Boolean? = null,
    val hasPrev: Boolean? = null,
    /** Cuándo se tomó el estado (epoch ms), para no pintar datos viejos. */
    val updatedAt: Long? = null,
    /** Una orden tipada del teléfono: playpause, next, previous, stop. */
    val cmd: String? = null,
    val reason: String? = null,
    val msg: String? = null,
    /** Puerto de control de la tele (va en la baliza del descubrimiento). */
    val port: Int? = null,
    /**
     * Identidad estable de la tele (sobrevive reinicios y cambios de dirección): el teléfono
     * recuerda su vínculo por aquí, no por la IP. Va en la baliza, en el `hello`, en el
     * `needpin` y en el `ok`.
     */
    val id: String? = null,
    val proto: Int = REMOTE_PROTO,
)

/**
 * Lo que la tele está reproduciendo, ya estructurado para que el teléfono pinte una tarjeta
 * «Ahora en la tele»: título, póster, minuto, duración, pausa y contexto de la serie.
 *
 * Es el mismo dato que viaja en el mensaje [RemoteProto.NOW]. Los campos son opcionales en el
 * cable para que un teléfono viejo siga leyendo solo el texto de [screen].
 */
data class NowPlaying(
    /** [RemoteProto.KIND_VOD], [RemoteProto.KIND_LIVE] o [RemoteProto.KIND_IDLE]. */
    val kind: String = RemoteProto.KIND_IDLE,
    /** Texto de una línea para el teléfono viejo (o el nombre de la pantalla si no hay play). */
    val screen: String = "",
    val title: String = "",
    val subtitle: String = "",
    /** Canal en vivo (nombre real del canal); vacío en VOD. */
    val channel: String = "",
    /** Logo real del canal en vivo (URL); vacío en VOD. */
    val channelLogo: String = "",
    /** Programa actual de la EPG (vivo) o el título del VOD. */
    val program: String = "",
    /** Sinopsis/descripción del programa actual o contexto del VOD. */
    val description: String = "",
    val poster: String = "",
    val ref: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = false,
    val seasonIndex: Int = 0,
    val episodeIndex: Int = 0,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    /** Epoch ms del momento en que la tele tomó el estado (0 = dato viejo/desconocido). */
    val updatedAt: Long = 0L,
) {
    val active: Boolean get() = kind == RemoteProto.KIND_VOD || kind == RemoteProto.KIND_LIVE
    val live: Boolean get() = kind == RemoteProto.KIND_LIVE

    /** Lo que se pinta en una línea cuando no hay tarjeta estructurada. */
    val line: String
        get() = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { screen }

    fun asMsg(): RemoteMsg = RemoteMsg(
        t = RemoteProto.NOW,
        screen = screen.ifBlank { line }.ifBlank { title },
        kind = kind.takeIf { it == RemoteProto.KIND_VOD || it == RemoteProto.KIND_LIVE },
        title = title.takeIf { it.isNotBlank() },
        subtitle = subtitle.takeIf { it.isNotBlank() },
        poster = poster.takeIf { it.isNotBlank() },
        ref = ref.takeIf { it.isNotBlank() },
        playing = playing.takeIf { active },
        positionMs = positionMs.takeIf { active },
        durationMs = durationMs.takeIf { active },
        canSeek = canSeek.takeIf { active },
        seasonIndex = seasonIndex.takeIf { active && it > 0 },
        episodeIndex = episodeIndex.takeIf { active && it > 0 },
        hasNext = hasNext.takeIf { active },
        hasPrev = hasPrev.takeIf { active },
        updatedAt = updatedAt.takeIf { it > 0 },
    )

    companion object {
        fun fromMsg(msg: RemoteMsg): NowPlaying {
            val kind = msg.kind.orEmpty()
            if (kind != RemoteProto.KIND_VOD && kind != RemoteProto.KIND_LIVE) {
                return NowPlaying(kind = RemoteProto.KIND_IDLE, screen = msg.screen.orEmpty())
            }
            return NowPlaying(
                kind = kind,
                screen = msg.screen.orEmpty(),
                title = msg.title.orEmpty(),
                subtitle = msg.subtitle.orEmpty(),
                poster = msg.poster.orEmpty(),
                ref = msg.ref.orEmpty(),
                playing = msg.playing == true,
                positionMs = msg.positionMs ?: 0L,
                durationMs = msg.durationMs ?: 0L,
                canSeek = msg.canSeek == true,
                seasonIndex = msg.seasonIndex ?: 0,
                episodeIndex = msg.episodeIndex ?: 0,
                hasNext = msg.hasNext == true,
                hasPrev = msg.hasPrev == true,
                updatedAt = msg.updatedAt ?: 0L,
            )
        }
    }
}

/** Tipos de mensaje y valores de sus campos. */
object RemoteProto {
    const val PROBE = "probe"
    const val HELLO = "hello"
    const val NEED_PIN = "needpin"
    const val OK = "ok"
    const val DENIED = "denied"
    const val KEY = "key"
    const val OPEN = "open"
    const val PIN = "pin"
    const val PING = "ping"
    const val PONG = "pong"
    const val NOW = "now"
    const val BYE = "bye"
    const val ERR = "err"
    const val SYNC = "sync"
    const val SYNC_OK = "syncok"

    /** Órdenes tipadas del teléfono al reproductor (no dependen del foco del mando). */
    const val CMD = "cmd"
    /** Salto absoluto a un minuto concreto (`pos`). */
    const val SEEK = "seek"

    const val ACTION_TAP = "tap"
    const val ACTION_DOWN = "down"
    const val ACTION_UP = "up"

    const val CMD_PLAY_PAUSE = "playpause"
    const val CMD_NEXT = "next"
    const val CMD_PREVIOUS = "previous"
    const val CMD_STOP = "stop"
    /** Abre en la tele su selector nativo de audio/subtítulos (se navega con el D-pad). */
    const val CMD_AUDIO = "audio"
    const val CMD_SUBTITLES = "subtitles"

    const val KIND_IDLE = "idle"
    const val KIND_VOD = "vod"
    const val KIND_LIVE = "live"

    const val DENIED_PIN = "pin"
    const val DENIED_BUSY = "busy"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(msg: RemoteMsg): String = json.encodeToString(RemoteMsg.serializer(), msg)

    /** Null cuando la línea no es un mensaje nuestro (ruido de red, otra cosa en el puerto). */
    fun decode(line: String?): RemoteMsg? {
        val text = line?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { json.decodeFromString(RemoteMsg.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.t.isNotBlank() }
    }
}

/**
 * Las teclas que el control remoto puede inyectar. **Lista blanca**, no negra: una tecla que no
 * esté aquí jamás llega al sistema (nada de POWER, APP_SWITCH o HOME del sistema, que sacarían a
 * la persona de la app o apagarían el televisor por accidente).
 */
object RemoteKeys {
    const val UP = KeyEvent.KEYCODE_DPAD_UP
    const val DOWN = KeyEvent.KEYCODE_DPAD_DOWN
    const val LEFT = KeyEvent.KEYCODE_DPAD_LEFT
    const val RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT
    const val CENTER = KeyEvent.KEYCODE_DPAD_CENTER
    const val ENTER = KeyEvent.KEYCODE_ENTER
    const val BACK = KeyEvent.KEYCODE_BACK
    const val MENU = KeyEvent.KEYCODE_MENU
    const val INFO = KeyEvent.KEYCODE_INFO
    const val GUIDE = KeyEvent.KEYCODE_GUIDE

    const val PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    const val PLAY = KeyEvent.KEYCODE_MEDIA_PLAY
    const val PAUSE = KeyEvent.KEYCODE_MEDIA_PAUSE
    const val STOP = KeyEvent.KEYCODE_MEDIA_STOP
    const val NEXT = KeyEvent.KEYCODE_MEDIA_NEXT
    const val PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS
    const val REWIND = KeyEvent.KEYCODE_MEDIA_REWIND
    const val FAST_FORWARD = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD

    const val CHANNEL_UP = KeyEvent.KEYCODE_CHANNEL_UP
    const val CHANNEL_DOWN = KeyEvent.KEYCODE_CHANNEL_DOWN

    const val VOLUME_UP = KeyEvent.KEYCODE_VOLUME_UP
    const val VOLUME_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN
    const val MUTE = KeyEvent.KEYCODE_VOLUME_MUTE

    val ALLOWED: Set<Int> = setOf(
        UP, DOWN, LEFT, RIGHT, CENTER, ENTER, BACK, MENU, INFO, GUIDE,
        PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS, REWIND, FAST_FORWARD,
        CHANNEL_UP, CHANNEL_DOWN,
        VOLUME_UP, VOLUME_DOWN, MUTE,
    )

    /** ¿Se puede inyectar esta tecla? Puro: es la puerta de seguridad del servidor. */
    fun isAllowed(code: Int): Boolean = code in ALLOWED

    /** ¿Es una tecla de volumen (la maneja el sistema, no la interfaz)? */
    fun isVolume(code: Int): Boolean = code == VOLUME_UP || code == VOLUME_DOWN || code == MUTE
}
