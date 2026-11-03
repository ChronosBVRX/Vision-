package com.chronosbvrx.visionplus.data.remote

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * El puente entre el reproductor (`:x4k`) y el servidor del control remoto (proceso principal).
 *
 * El estado de reproducción cambia cada segundo. Un broadcast puede perderse (HyperOS/POCO lo
 * hace de forma intermitente), así que el reproductor y el menú hablan por [Messenger]: es
 * bidireccional, ordenado y no cuesta un archivo ni un socket extra.
 *
 * El reproductor recibe el [Messenger] del proceso principal en el intent que lo abre
 * ([EXTRA_HOST_MESSENGER]); con cada estado devuelve el suyo como `replyTo` y por ahí recibe
 * las órdenes tipadas (seek, play/pausa, siguiente/anterior).
 */
object RemoteBridge {
    const val EXTRA_HOST_MESSENGER = "remote_host_messenger"

    internal const val MSG_STATE = 1
    internal const val MSG_COMMAND = 2
    internal const val MSG_GONE = 3

    private const val KEY_KIND = "kind"
    private const val KEY_SCREEN = "screen"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_CHANNEL = "channel"
    private const val KEY_CHANNEL_LOGO = "channel_logo"
    private const val KEY_PROGRAM = "program"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_POSTER = "poster"
    private const val KEY_REF = "ref"
    private const val KEY_PLAYING = "playing"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"
    private const val KEY_CAN_SEEK = "can_seek"
    private const val KEY_SEASON = "season"
    private const val KEY_EPISODE = "episode"
    private const val KEY_HAS_NEXT = "has_next"
    private const val KEY_HAS_PREV = "has_prev"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_CMD = "cmd"
    private const val KEY_SEEK = "seek"

    private val hostHandler = Handler(Looper.getMainLooper()) { message ->
        onHostMessage(message)
        true
    }

    /** El buzón del proceso principal; se entrega al reproductor al abrirlo. */
    val hostMessenger: Messenger by lazy { Messenger(hostHandler) }

    @Volatile
    private var player: Messenger? = null

    @Volatile
    private var playerDeath: IBinder.DeathRecipient? = null

    /** ¿Hay un reproductor vivo publicando estado? */
    fun hasPlayer(): Boolean = player != null

    /**
     * El reproductor también puede pedir `X4KApi.playVod` desde `:x4k` (sugerencias). Ahí no se
     * debe fabricar un buzón local: el bueno es el del proceso principal y ya viene adjunto.
     */
    fun isMainProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        return name.isNullOrBlank() || name == context.packageName
    }

    private fun onHostMessage(message: Message) {
        when (message.what) {
            MSG_STATE -> {
                val state = nowPlayingFromBundle(message.data)
                bind(message.replyTo)
                RemoteBus.playback.value = state
            }
            MSG_GONE -> clear(message.replyTo?.binder)
        }
    }

    @Synchronized
    private fun bind(messenger: Messenger?) {
        if (messenger == null) return
        val current = player
        if (current?.binder === messenger.binder) return
        unbind()
        val death = object : IBinder.DeathRecipient {
            override fun binderDied() {
                clear(messenger.binder)
            }
        }
        // El enlace de muerte es una red de seguridad: si el binder no lo admite, el estado
        // y las órdenes siguen funcionando igual.
        runCatching { messenger.binder.linkToDeath(death, 0) }
        player = messenger
        playerDeath = death
    }

    @Synchronized
    private fun clear(binder: IBinder? = null) {
        val current = player ?: return
        if (binder != null && current.binder !== binder) return
        unbind()
        RemoteBus.playback.value = NowPlaying()
    }

    @Synchronized
    private fun unbind() {
        playerDeath?.let { death ->
            runCatching { player?.binder?.unlinkToDeath(death, 0) }
        }
        playerDeath = null
        player = null
    }

    /** Una barra arrastrada en el teléfono: salta a ese minuto exacto. */
    fun sendSeek(positionMs: Long) {
        send(Message.obtain(null, MSG_COMMAND).apply {
            data = Bundle().apply {
                putString(KEY_CMD, RemoteProto.SEEK)
                putLong(KEY_SEEK, positionMs.coerceAtLeast(0L))
            }
        })
    }

    /** Play/pausa, siguiente, anterior o detener, sin depender del foco del mando. */
    fun sendCommand(command: String) {
        if (command.isBlank()) return
        send(Message.obtain(null, MSG_COMMAND).apply {
            data = Bundle().apply { putString(KEY_CMD, command) }
        })
    }

    private fun send(message: Message) {
        val target = player ?: return
        runCatching { target.send(message) }
            .onFailure { clear(target.binder) }
    }

    /** El teléfono puede ver/controlar únicamente lo que la TV está reproduciendo ahora. */
    internal fun nowPlayingToBundle(value: NowPlaying): Bundle = Bundle().apply {
        putString(KEY_KIND, value.kind)
        putString(KEY_SCREEN, value.screen)
        putString(KEY_TITLE, value.title)
        putString(KEY_SUBTITLE, value.subtitle)
        putString(KEY_CHANNEL, value.channel)
        putString(KEY_CHANNEL_LOGO, value.channelLogo)
        putString(KEY_PROGRAM, value.program)
        putString(KEY_DESCRIPTION, value.description)
        putString(KEY_POSTER, value.poster)
        putString(KEY_REF, value.ref)
        putBoolean(KEY_PLAYING, value.playing)
        putLong(KEY_POSITION, value.positionMs)
        putLong(KEY_DURATION, value.durationMs)
        putBoolean(KEY_CAN_SEEK, value.canSeek)
        putInt(KEY_SEASON, value.seasonIndex)
        putInt(KEY_EPISODE, value.episodeIndex)
        putBoolean(KEY_HAS_NEXT, value.hasNext)
        putBoolean(KEY_HAS_PREV, value.hasPrev)
        putLong(KEY_UPDATED_AT, value.updatedAt)
    }

    internal fun nowPlayingFromBundle(bundle: Bundle?): NowPlaying {
        if (bundle == null) return NowPlaying()
        return NowPlaying(
            kind = bundle.getString(KEY_KIND).orEmpty().ifBlank { RemoteProto.KIND_IDLE },
            screen = bundle.getString(KEY_SCREEN).orEmpty(),
            title = bundle.getString(KEY_TITLE).orEmpty(),
            subtitle = bundle.getString(KEY_SUBTITLE).orEmpty(),
            channel = bundle.getString(KEY_CHANNEL).orEmpty(),
            channelLogo = bundle.getString(KEY_CHANNEL_LOGO).orEmpty(),
            program = bundle.getString(KEY_PROGRAM).orEmpty(),
            description = bundle.getString(KEY_DESCRIPTION).orEmpty(),
            poster = bundle.getString(KEY_POSTER).orEmpty(),
            ref = bundle.getString(KEY_REF).orEmpty(),
            playing = bundle.getBoolean(KEY_PLAYING, false),
            positionMs = bundle.getLong(KEY_POSITION, 0L),
            durationMs = bundle.getLong(KEY_DURATION, 0L),
            canSeek = bundle.getBoolean(KEY_CAN_SEEK, false),
            seasonIndex = bundle.getInt(KEY_SEASON, 0),
            episodeIndex = bundle.getInt(KEY_EPISODE, 0),
            hasNext = bundle.getBoolean(KEY_HAS_NEXT, false),
            hasPrev = bundle.getBoolean(KEY_HAS_PREV, false),
            updatedAt = bundle.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    /**
     * El lado del reproductor: publica su estado y recibe las órdenes del teléfono.
     *
     * Se instala en `X4KPlayerAty` (`:x4k`). Si el intent no trae el buzón del proceso principal
     * (por ejemplo, un lanzamiento viejo o una prueba), queda desconectado y el reproductor sigue
     * publicando por el broadcast de siempre.
     */
    class PlayerLink(private val listener: Listener) {
        interface Listener {
            fun onSeek(positionMs: Long)
            fun onCommand(command: String)
        }

        private val handler = Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MSG_COMMAND -> {
                    val cmd = message.data?.getString(KEY_CMD).orEmpty()
                    if (cmd == RemoteProto.SEEK) {
                        listener.onSeek(message.data?.getLong(KEY_SEEK, 0L) ?: 0L)
                    } else if (cmd.isNotBlank()) {
                        listener.onCommand(cmd)
                    }
                }
            }
            true
        }

        private val messenger = Messenger(handler)

        @Volatile
        private var host: Messenger? = null

        val attached: Boolean get() = host != null

        fun attach(intent: Intent?) {
            // Un intent de sugerencia/navegación puede no traer el buzón; en ese caso se
            // conserva el que ya estaba (el reproductor residente no pierde el control).
            intent?.messengerExtra(EXTRA_HOST_MESSENGER)?.let { host = it }
        }

        fun publish(state: NowPlaying) {
            val target = host ?: return
            runCatching {
                target.send(
                    Message.obtain(null, MSG_STATE).apply {
                        data = nowPlayingToBundle(state)
                        replyTo = messenger
                    },
                )
            }.onFailure { host = null }
        }

        fun detach() {
            val target = host ?: return
            runCatching {
                target.send(
                    Message.obtain(null, MSG_GONE).apply { replyTo = messenger },
                )
            }
            host = null
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.messengerExtra(key: String): Messenger? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Messenger::class.java)
    } else {
        getParcelableExtra(key)
    }
