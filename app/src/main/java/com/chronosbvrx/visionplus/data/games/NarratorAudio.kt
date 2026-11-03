package com.chronosbvrx.visionplus.data.games

import android.content.Context
import android.media.MediaPlayer
import com.chronosbvrx.visionplus.DeviceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.chronosbvrx.visionplus.R

/**
 * Voz de la presentadora. Suena en la TELE, que es la pantalla compartida; los
 * teléfonos quedan en silencio (solo muestran el bocadillo). En la tele no se
 * exige el modo timbre del teléfono: manda el volumen del televisor.
 */
object NarratorAudio {
    private var player: MediaPlayer? = null
    private var pending: Pair<Context, Int>? = null

    /** La tele usa esto para bajar los SFX mientras habla la presentadora. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    private val _hostSpeaking = MutableStateFlow(false)

    /** Estado observable para animar el retrato del presentador en la TV. */
    val isHostSpeaking: StateFlow<Boolean> = _hostSpeaking.asStateFlow()

    /** Acelera la música del plató en los últimos 10 segundos (nunca la voz). */
    fun setTensionMode(urgent: Boolean) = GameAmbient.setTensionMode(urgent)

    /** Qwen3-TTS ya entrega el ritmo y la dinámica de la toma elegida; no hace
     * falta acelerar la voz como se hacía con el motor anterior. */

    fun play(context: Context, resId: Int) {
        if (!DeviceKind.isTelevision(context)) return
        if (!GameStore.narratorEnabled(context)) return
        // Si la fase avanza rápido, la frase actual NO se corta: se encola la
        // siguiente y se reproduce al terminar. Así una partida de prueba con un
        // solo jugador no parte las locuciones por la mitad.
        if (player?.let { runCatching { it.isPlaying }.getOrDefault(false) } == true) {
            pending = context.applicationContext to resId
            return
        }
        playNow(context.applicationContext, resId)
    }

    private fun playNow(context: Context, resId: Int) {
        player?.let { runCatching { it.release() } }
        player = null
        GameAmbient.duck()
        GameSfx.duck()
        val next = runCatching {
            MediaPlayer.create(context, resId)?.apply {
                // No se toca `playbackParams` aquí: en algunos televisores cambiar los
                // parámetros justo después de crear el MediaPlayer recrea la pista de
                // audio y se come el primer fonema de la frase. La velocidad ya viene
                // grabada en la toma, así que no hace falta.
                setOnCompletionListener { finishCurrent() }
                setOnErrorListener { _, _, _ -> finishCurrent(); true }
                isSpeaking = true
                _hostSpeaking.value = true
                start()
            }
        }.getOrNull()
        player = next
        if (next == null) {
            isSpeaking = false
            _hostSpeaking.value = false
            pending = null
            GameAmbient.restore()
            GameSfx.restore()
        }
    }

    private fun finishCurrent() {
        player?.let { runCatching { it.release() } }
        player = null
        val queued = pending
        pending = null
        if (queued != null) {
            playNow(queued.first, queued.second)
        } else {
            isSpeaking = false
            _hostSpeaking.value = false
            GameAmbient.restore()
            GameSfx.restore()
        }
    }

    fun stop() {
        isSpeaking = false
        _hostSpeaking.value = false
        pending = null
        player?.let { runCatching { it.release() } }
        player = null
        GameAmbient.setTensionMode(false)
        GameSfx.restore()
    }
}

/**
 * Efectos secos del plató: tic del reloj, voto, secreto, reveal, martillo y
 * whoosh. Son WAV cortos sintetizados localmente; suenan solo en la tele y no
 * compiten con la voz: cada efecto se libera al terminar.
 */
object GameSfx {
    enum class Kind {
        Tick, Vote, Secret, Reveal, Hammer, Whoosh,
        Buzzer, Glass, Heartbeat, Safe, Fanfare,
    }

    private var player: MediaPlayer? = null

    private fun volume(): Float = if (NarratorAudio.isSpeaking) 0.22f else 0.70f

    fun play(context: Context, kind: Kind) {
        if (!DeviceKind.isTelevision(context)) return
        if (!GameStore.narratorEnabled(context)) return
        stop()
        val res = when (kind) {
            Kind.Tick -> R.raw.game_sfx_tick
            Kind.Vote -> R.raw.game_sfx_vote
            Kind.Secret -> R.raw.game_sfx_secret
            Kind.Reveal -> R.raw.game_sfx_reveal
            Kind.Hammer -> R.raw.game_sfx_hammer
            Kind.Whoosh -> R.raw.game_sfx_whoosh
            Kind.Buzzer -> R.raw.game_sfx_buzzer
            Kind.Glass -> R.raw.game_sfx_glass
            Kind.Heartbeat -> R.raw.game_sfx_heartbeat
            Kind.Safe -> R.raw.game_sfx_safe
            Kind.Fanfare -> R.raw.game_sfx_fanfare
        }
        val level = volume()
        player = runCatching {
            MediaPlayer.create(context, res)?.apply {
                setVolume(level, level)
                setOnCompletionListener { stop() }
                start()
            }
        }.getOrNull()
    }

    fun duck() {
        val level = 0.22f
        player?.let { runCatching { it.setVolume(level, level) } }
    }

    fun restore() {
        val level = 0.70f
        player?.let { runCatching { it.setVolume(level, level) } }
    }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}

/**
 * Música ambiental tenue de Vision+ Play. Vive en la tele, en bucle y bajita,
 * debajo de la voz: cuando la presentadora habla se atenúa un poco más y luego
 * vuelve a su nivel. Mismo interruptor y mismo destino que la voz.
 */
object GameAmbient {
    private var player: MediaPlayer? = null
    private var introPlayer: MediaPlayer? = null
    private var currentGame: String = ""

    private const val LEVEL = 0.20f
    private const val DUCKED = 0.07f
    @Volatile private var tension = false

    fun setTensionMode(urgent: Boolean) {
        tension = urgent
        applyRate()
    }

    private fun applyRate(speed: Float = if (tension) 1.12f else 1.0f) {
        player?.let { mp ->
            runCatching {
                // En algunos chips Android TV, tocar playbackParams con el
                // reproductor detenido distorsiona el audio. Solo se aplica
                // mientras suena y siempre preservando el pitch.
                if (mp.isPlaying) {
                    val params = mp.playbackParams.apply {
                        this.speed = speed
                        pitch = 1.0f
                    }
                    mp.playbackParams = params
                }
            }
        }
    }

    fun start(context: Context) {
        startFor(context, "")
    }

    /** Música propia del juego; cae al tema genérico si el recurso no existe. */
    fun startFor(context: Context, game: String) {
        if (!DeviceKind.isTelevision(context)) return
        if (!GameStore.narratorEnabled(context)) return
        if (player != null && currentGame == game) return
        stop()
        currentGame = game
        val named = if (game.isNotBlank()) {
            runCatching { context.resources.getIdentifier("fiesta_music_$game", "raw", context.packageName) }
                .getOrDefault(0)
        } else 0
        val res = if (named != 0) named else R.raw.game_theme
        player = runCatching {
            MediaPlayer.create(context, res)?.apply {
                isLooping = true
                setVolume(LEVEL, LEVEL)
                start()
                applyRate()
            }
        }.getOrNull()
    }

    fun playSting(context: Context, game: String) {
        if (!DeviceKind.isTelevision(context)) return
        if (!GameStore.narratorEnabled(context)) return
        val named = runCatching {
            context.resources.getIdentifier("fiesta_sting_$game", "raw", context.packageName)
        }.getOrDefault(0)
        if (named == 0) return
        introPlayer?.let { runCatching { it.release() } }
        introPlayer = runCatching {
            MediaPlayer.create(context, named)?.apply {
                setVolume(0.42f, 0.42f)
                setOnCompletionListener { runCatching { it.release() } }
                start()
            }
        }.getOrNull()
    }

    /** Sting corto de presentación al entrar a Juegos. */
    fun playIntro(context: Context) {
        if (!DeviceKind.isTelevision(context)) return
        if (!GameStore.narratorEnabled(context)) return
        introPlayer?.let { runCatching { it.release() } }
        introPlayer = runCatching {
            MediaPlayer.create(context, R.raw.game_intro)?.apply {
                setVolume(0.45f, 0.45f)
                setOnCompletionListener { runCatching { it.release() } }
                start()
            }
        }.getOrNull()
    }

    fun duck() {
        player?.let { runCatching { it.setVolume(DUCKED, DUCKED) } }
        // La voz manda: mientras habla el presentador, la música vuelve a 1.0x.
        applyRate(1.0f)
    }

    fun restore() {
        player?.let { runCatching { it.setVolume(LEVEL, LEVEL) } }
        // Si seguimos en los últimos 10 s, recupera la tensión.
        applyRate()
    }

    fun stop() {
        applyRate(1.0f)
        player?.let { runCatching { it.stop() } }
        player?.let { runCatching { it.release() } }
        player = null
        currentGame = ""
        tension = false
        introPlayer?.let { runCatching { it.release() } }
        introPlayer = null
    }
}
