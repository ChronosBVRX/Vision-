package com.chronosbvrx.visionplus.ui.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.chronosbvrx.visionplus.DeviceKind
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.Related
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.markers.EpisodeMarkers
import com.chronosbvrx.visionplus.data.markers.autoSkipTarget
import com.chronosbvrx.visionplus.data.markers.canShowSkipPill
import com.chronosbvrx.visionplus.data.markers.creditsCardStartMs
import com.chronosbvrx.visionplus.data.markers.skipIntroWindow
import com.chronosbvrx.visionplus.data.markers.skipRecapWindow
import com.chronosbvrx.visionplus.data.markers.validPostCredits
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.isFinished
import com.chronosbvrx.visionplus.data.remote.RemoteBus
import com.chronosbvrx.visionplus.data.remote.RemoteKeys
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import com.chronosbvrx.visionplus.data.shouldCountWatchedOnSkip
import com.chronosbvrx.visionplus.data.shouldKeep
import com.chronosbvrx.visionplus.data.subs.SrtCue
import com.chronosbvrx.visionplus.data.subs.SubEntry
import com.chronosbvrx.visionplus.data.subs.SubsStore
import com.chronosbvrx.visionplus.data.subs.cueAt
import com.chronosbvrx.visionplus.data.subs.distinctLanguages
import com.chronosbvrx.visionplus.data.subs.languageName
import com.chronosbvrx.visionplus.data.subs.orderSubsForPicker
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.data.xuper.X4KTitan
import com.chronosbvrx.visionplus.data.xuper.dedupeTrackLabels
import com.chronosbvrx.visionplus.data.xuper.trackDisplayLang
import com.chronosbvrx.visionplus.ui.live.LiveFavorites
import org.json.JSONArray
import org.json.JSONObject

/**
 * Xuper X4K player, in its own process (`:x4k`): Vision's blue, premium surface — the brand
 * pre-roll first, a branded wait, and a full transport (rewind / play / forward, live progress
 * with the film's real total, audio **and subtitle** languages) over the provider's own engine.
 */
class X4KPlayerAty : ComponentActivity() {

    private var playerView: View? = null

    private var playPauseBtn: View? = null
    private var playIconBtn: View? = null
    private var playIconState: Boolean? = null
    private var seekBar: SeekBar? = null
    private var timeNow: TextView? = null
    private var timeTotal: TextView? = null
    private var previewSeekMs: Long = -1L
    private val commitSeekRunnable = Runnable {
        if (previewSeekMs >= 0L) {
            val target = previewSeekMs
            previewSeekMs = -1L
            scrubbing = false
            playerView?.let { com.chronosbvrx.visionplus.data.xuper.X4KTitan.seekTo(it, target) }
            noteAppSeek()
        }
    }
    private var controls: View? = null
    private var loadingOverlay: View? = null
    private var loadingTitleView: TextView? = null
    private var loadingSubView: TextView? = null
    private var loadingEpNameView: TextView? = null
    private var errorOverlay: View? = null
    private var picker: View? = null
    private var audioBtn: TextView? = null
    private var subsBtn: TextView? = null
    private var qualityBtn: TextView? = null
    // Píldoras de opciones reales (siguiente/capítulos/audio/subtítulos/calidad): solo se
    // muestra la que tiene algo que elegir y la cadena de foco del mando se re-cablea para
    // que las ocultas no dejen un destino muerto.
    private var optionPillChain = ArrayList<View>()
    private var forwardNavView: View? = null
    private var seekNavView: SeekBar? = null
    private var playPauseNavView: View? = null
    private var curMedias = ArrayList<X4KTitan.MediaSpec>()
    private var currentMediaName = ""
    private var curQuality = ""
    private var introView: View? = null
    private var pendingPlay: (() -> Unit)? = null
    private var contentRoot: FrameLayout? = null
    private var introSkip: View? = null
    private var errorRetry: View? = null
    private var channelsBtn: View? = null
    private var episodesBtn: View? = null
    private var nextEpBtn: TextView? = null
    private var epNames = ArrayList<String>()
    private var curBaseTitle = ""

    // Historial («Seguir viendo»): minuto guardado, diálogo de continuar y reanudación.
    private var resumeMs = 0L
    private var resumeApplied = false
    private var resumeAsked = false
    private var pendingAutoResume = false
    private var pendingResumeEntry: ResumeEntry? = null
    private var resumeOverlay: View? = null
    private var intentPoster = ""
    private var lastHistorySave = 0L

    // Continuidad de series ("siguiente capítulo" sin salir): solo VOD con contexto de episodios.
    private var curCode = ""
    private var curTitle = ""
    private var curSeries = ""
    private var curEpIndex = 0
    private var curEpNumber = 0
    private var curSeasonIndex = 0
    private var curAspect = 0
    private var epIds = ArrayList<String>()
    private var epNumbers = ArrayList<Int>()
    private var seasonIds = ArrayList<String>()
    private var seasonNames = ArrayList<String>()
    private var nextOverlay: View? = null
    private var nextVisible = false
    private var suggestOverlay: View? = null
    private var suggestVisible = false
    private var movieSuggestShown = false
    private var nextDismissed = false
    private var nextPendingAtEnd = false
    private var endedShown = false
    /** Ya se guardó este título como terminado: un onStop posterior no debe degradarlo. */
    private var finishedSaved = false
    private var nextCountdown = 0
    private var nextCountdownView: TextView? = null
    private var nextPlayBtn: View? = null
    private var nextTarget: NextEp? = null
    private var nextAutoAdvance = true
    private var nextCreditsMode = false

    // Marcadores de salto (intro / resumen / créditos / post-créditos). Cada capítulo
    // tiene SU plan: nunca se reutiliza la ventana del primero de la temporada.
    private var markIntroStartMs = -1L
    private var markIntroEndMs = -1L
    private var markRecapStartMs = -1L
    private var markRecapEndMs = -1L
    private var markCreditsStartMs = -1L
    private var markCreditsEndMs = -1L
    private var markPostCreditsStartMs = -1L
    private var markPostCreditsEndMs = -1L
    private var markEpisodeRef = ""
    private var markEpisodeNumber = 0
    private var markEpisodeLengthSec = 0
    private var markersFetchGen = 0
    private var introSkipped = false
    private var recapSkipped = false
    private var creditsCardShown = false
    private var postCreditsShown = false
    private var introPill: TextView? = null
    private var introPillId = -1
    private var postCreditsNotice: TextView? = null
    private var skipIntroMode = "button"

    private data class NextEp(
        val code: String,
        val seriesId: String,
        val displayTitle: String,
        val playerTitle: String,
        val epIndex: Int,
        val epNumber: Int,
        val seasonIndex: Int,
        val newEpIds: List<String>? = null,
        val newEpNames: List<String>? = null,
        val newEpNumbers: List<Int>? = null,
    )

    /** TV: the controls become D-pad targets, so the remote never gets stuck. */
    private val isTv by lazy { DeviceKind.isTelevision(this) }
    private val playerControls = ArrayList<View>()
    private var lastPickerSourceBtn: View? = null

    private val isModalActive: Boolean
        get() = picking || resumeOverlay != null || nextVisible || suggestVisible

    private fun setBackgroundFocusBlocked(blocked: Boolean) {
        val mode = if (blocked) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        (controls as? ViewGroup)?.descendantFocusability = mode
    }

    /**
     * Contenedor modal con trampa de foco (focus trap): impide que el D-pad se escape
     * del diálogo hacia los controles del reproductor que están por debajo.
     */
    private inner class ModalDimLayout(context: Context) : FrameLayout(context) {
        init {
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        override fun focusSearch(focused: View?, direction: Int): View? {
            val next = super.focusSearch(focused, direction)
            // Si el siguiente foco sale del modal o es nulo, mantenemos el foco atrapado en el elemento actual.
            if (next == null || !isDescendant(next)) {
                return focused
            }
            return next
        }

        private fun isDescendant(v: View): Boolean {
            var p: ViewParent? = v.parent
            while (p != null) {
                if (p === this) return true
                p = p.parent
            }
            return false
        }
    }

    // Live mode: the channel line, the one being watched, and its guide line.
    private var liveMode = false
    private var liveChannels: JSONArray? = null
    private var liveIndex = -1
    private var liveGroupTitle: String = ""
    private var topTitle: TextView? = null
    private var epgLine: TextView? = null
    private var favBtn: TextView? = null
    private var infoBtn: TextView? = null
    private var currentEpgItems: List<X4KApi.EpgItem> = emptyList()
    private var liveChannelTitleView: TextView? = null
    private var liveChannelNumView: TextView? = null
    private var liveChannelGroupView: TextView? = null
    private var liveEpgNowView: TextView? = null
    private var liveEpgNextView: TextView? = null
    private var liveEpgProgView: ProgressBar? = null

    @Volatile
    private var durationMs: Long = -1L

    /** The portal's own running time: authoritative for the bar's total. */
    private var declaredDurationMs: Long = 0L

    private var firstFrame = false
    private var startedAtMs = 0L
    /** Re-arranques internos del motor por falta de primer cuadro (sin mostrar error). */
    private var softRestarts = 0

    // Detector de saltos de contenido (telemetría «PLAYER_FRAME_SKIP»): la posición no puede
    // avanzar más que el reloj de pared. Es el caso reportado «no es pausa, es un salto».
    private var skipProbePosMs = -1L
    private var skipProbeAtMs = 0L
    private var skipProbeMuteUntilMs = 0L
    private var lastSkipReportAtMs = 0L

    /** El motor en vivo dio señal real (posición o decodificador) al menos una vez. */
    private var liveSignalSeen = false
    private var errorShown = false
    private var picking = false
    private var declaredLanguages: List<String> = emptyList()

    // Subtítulos del proveedor (servicio del portal → SRT → overlay), como el APK oficial:
    // el motor nativo no sabe ni elegirlos ni pintarlos.
    private var subsView: TextView? = null
    private var subsEntries: List<SubEntry> = emptyList()
    private var subsLoaded = false
    private var subsCurrent: SubEntry? = null
    private var subsCues: List<SrtCue> = emptyList()
    private var subsLoadToken = ""
    private var subsPickerWaiting = false

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = false
    private var scrubbing = false
    private var swallowWakeupKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
    private var lastControlsWakeupMs: Long = 0L
    /** Cuándo se armó el swallow: un mando que no manda el UP no puede dejar la tecla muerta. */
    private var swallowWakeupAtMs: Long = 0L

    /** La espera de marca se está desvaneciendo (evita relanzar el fundido en cada sondeo). */
    private var loadingFading = false

    /** «Camino B»: generación de la reproducción — invalida hilos y retrys de la anterior. */
    @Volatile
    private var playGen = 0

    /** El auto-reparado del motor pidió arranque limpio: a la salida se cierra y reinicia el proceso. */
    private var hardResetNext = false

    /** Salida ya en curso (evita dobles rutas Atrás/Volver). */
    private var exiting = false

    /** Marca del doble Atrás para salir. */
    private var lastBackPressTime = 0L

    /** Control remoto (teléfono → tele): teclas que llegan por el broadcast interno. */
    private var remoteReceiver: BroadcastReceiver? = null
    private var lastRemoteNow = ""

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        residentInstance = this
        // Contexto de diagnóstico del proceso :x4k: sus ANR eran los únicos sin pantalla.
        com.chronosbvrx.visionplus.data.diag.Diagnostics.screen("Player")
        com.chronosbvrx.visionplus.data.diag.Diagnostics.phase("reproductor-arranque")
        // Identidad del portal en este proceso (:x4k tiene su propia memoria).
        com.chronosbvrx.visionplus.data.xuper.X4KApi.initSlots(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        contentRoot = root
        setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        installBackCallback()
        installRemoteReceiver()
        handleLaunchIntent(intent)
    }

    /**
     * «Camino B» (estilo app del proveedor): una sola vista del motor para toda la vida del
     * proceso `:x4k`. Cada reproducción nueva reutiliza esa MISMA vista (stop → play
     * in-place, el patrón del zapping que el motor sí admite) y solo se reconstruyen las
     * capas de UI por encima: el proceso, el motor y la vista quedan calientes y abrir un
     * canal o título deja de pagar el arranque en frío de cada vez.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "reproductor residente: vista reutilizada para el nuevo play")
        // Los retrys y sondeos de la reproducción anterior ya no mandan.
        handler.removeCallbacksAndMessages(null)
        playerView?.let { runCatching { X4KTitan.stop(it) } }
        handleLaunchIntent(intent)
    }

    /**
     * Monta una reproducción (la primera en `onCreate` o la siguiente en `onNewIntent`) sobre
     * la vista del motor: estado y overlays se rehacen por completo en cada llamada.
     */
    private fun handleLaunchIntent(intent: Intent) {
        isPlayerActive = true
        exiting = false
        startedAtMs = System.currentTimeMillis()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        applyImmersiveMode()
        playGen += 1
        resetForNewPlay()
        if (playerView != null) teardownPlaySurfaces()

        val mode = intent.getStringExtra("mode") ?: "vod"
        val code = intent.getStringExtra("code").orEmpty()
        val rawTitle = intent.getStringExtra("title").orEmpty().ifBlank { code }
        liveMode = mode == "live"
        com.chronosbvrx.visionplus.data.diag.Diagnostics.phase(
            if (liveMode) "reproductor-vivo" else "reproductor-vod",
        )
        val title = if (liveMode) X4KApi.displayChannelName(code, rawTitle) else rawTitle
        curCode = code
        curTitle = title
        if (liveMode) {
            liveGroupTitle = intent.getStringExtra("channelGroup").orEmpty()
            val codes = intent.getStringArrayListExtra("channelCodes")
            // Resolver lista completa de canales en segundo plano para no bloquear el arranque del reproductor
            Thread {
                val gen = playGen
                val allLive = X4KApi.cachedLive(applicationContext)
                var resolvedArr = if (!codes.isNullOrEmpty()) {
                    val codeMap = HashMap<String, JSONObject>(allLive.length())
                    for (i in 0 until allLive.length()) {
                        val obj = allLive.optJSONObject(i) ?: continue
                        val cCode = obj.optString("channelCode")
                        if (cCode.isNotBlank()) codeMap[cCode] = obj
                    }
                    val filteredArr = JSONArray()
                    for (cCode in codes) {
                        val obj = codeMap[cCode] ?: continue
                        filteredArr.put(obj)
                    }
                    if (filteredArr.length() > 0) filteredArr else allLive
                } else {
                    allLive
                }
                // Sin caché en este proceso (las prefs no cruzan a `:x4k`): respaldo
                // con los códigos del intent para no dejar el selector en "0 canales".
                if (resolvedArr.length() == 0 && !codes.isNullOrEmpty()) {
                    resolvedArr = X4KApi.liveStubsForCodes(codes)
                }
                if (gen != playGen) return@Thread
                liveChannels = resolvedArr
                var idx = findChannelIndex(curCode.ifBlank { code })
                if (idx < 0 && resolvedArr.length() > 0) idx = 0
                liveIndex = idx
                runOnUiThread {
                    if (gen != playGen) return@runOnUiThread
                    updateLiveBanner()
                    updateFavButtonState()
                }
            }.start()
        }
        val aspect = intent.getIntExtra("aspect", 0)
        declaredDurationMs = intent.getLongExtra("durationMs", 0L)
        if (declaredDurationMs <= 0L && !liveMode && code.isNotBlank()) {
            val histEntry = PlaybackHistory.get(this, code)
            if (histEntry != null && histEntry.durationMs > 60_000L) {
                declaredDurationMs = histEntry.durationMs
            }
        }
        val mediasJson = intent.getStringExtra("mediasB64")?.let {
            runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) }.getOrNull()
        } ?: intent.getStringExtra("medias").orEmpty()
        val entriesJson = intent.getStringExtra("entriesB64")?.let {
            runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) }.getOrNull()
        }
        val playParams = intent.getStringExtra("playParamsB64")?.let {
            runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) }.getOrNull()
        }.orEmpty()
        curSeries = intent.getStringExtra("seriesId").orEmpty()
        if (declaredDurationMs <= 0L && !liveMode) {
            val gen = playGen
            Thread {
                var d = 0L
                if (code.isNotBlank()) {
                    val detail = kotlinx.coroutines.runBlocking { X4KApi.detailFor(this@X4KPlayerAty, code) }
                    d = X4KApi.extractDurationMs(detail)
                    if (d <= 0L) {
                        val sItem = kotlinx.coroutines.runBlocking { X4KApi.itemInfo(this@X4KPlayerAty, code) }
                        d = X4KApi.extractDurationMs(sItem)
                    }
                }
                if (d <= 0L && curSeries.isNotBlank()) {
                    val detailSeries = kotlinx.coroutines.runBlocking { X4KApi.detailFor(this@X4KPlayerAty, curSeries) }
                    d = X4KApi.extractDurationMs(detailSeries)
                    if (d <= 0L) {
                        val sSeries = kotlinx.coroutines.runBlocking { X4KApi.itemInfo(this@X4KPlayerAty, curSeries) }
                        d = X4KApi.extractDurationMs(sSeries)
                    }
                }
                if (d > 0L) {
                    runOnUiThread {
                        if (gen != playGen) return@runOnUiThread
                        if (declaredDurationMs <= 0L || (d > declaredDurationMs && declaredDurationMs < 60_000L)) {
                            declaredDurationMs = d
                            markEpisodeLengthSec = (d / 1000L).toInt().coerceAtLeast(0)
                            timeTotal?.text = fmt(d)
                            val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
                            if (!scrubbing && d > 0) {
                                seekBar?.progress = ((pos * 1000) / d).toInt().coerceIn(0, 1000)
                            }
                        }
                    }
                }
            }.start()
        }
        intent.getStringArrayListExtra("epIds")?.let { epIds = it }
        intent.getStringArrayListExtra("epNames")?.let { epNames = it }
        intent.getIntegerArrayListExtra("epNumbers")?.let { epNumbers = it }
        curEpIndex = intent.getIntExtra("epIndex", 0)
        intent.getStringArrayListExtra("seasonIds")?.let { seasonIds = it }
        intent.getStringArrayListExtra("seasonNames")?.let { seasonNames = it }
        curSeasonIndex = intent.getIntExtra("seasonIndex", 0)
        // Normalización serie vs película: historiales o intents viejos pueden traer un
        // seriesId suelto con 0-1 capítulos; sin 2+ capítulos o 2+ temporadas es película.
        if (epIds.size <= 1 && seasonIds.size <= 1) {
            curSeries = ""
            epIds.clear()
            epNames.clear()
            epNumbers.clear()
            seasonIds.clear()
            seasonNames.clear()
            curEpIndex = 0
            curSeasonIndex = 0
        }
        curEpNumber = epNumbers.getOrNull(curEpIndex)?.takeIf { it > 0 } ?: (curEpIndex + 1)
        resumeMs = intent.getLongExtra("resumeMs", 0L)
        intentPoster = intent.getStringExtra("posterUrl").orEmpty()
        // Marcadores de salto: viajan por el intent (las prefs no cruzan a `:x4k`) y el
        // modo lo lee cada proceso de su archivo (el ajuste pudo cambiar sin reiniciar).
        skipIntroMode = com.chronosbvrx.visionplus.ui.settings.VisionSettings.skipIntroModeOf(this)
        markEpisodeRef = com.chronosbvrx.visionplus.data.markers.normalizeMarkersRef(
            intent.getStringExtra("markersRef").orEmpty().ifBlank { curSeries },
        )
        markEpisodeNumber = intent.getIntExtra("markersEp", 0).takeIf { it > 0 } ?: curEpNumber
        markEpisodeLengthSec = intent.getIntExtra("markersLengthSec", 0)
            .takeIf { it > 0 }
            ?: (declaredDurationMs / 1000L).toInt().coerceAtLeast(0)
        val intentMarkers = intent.getStringExtra("markersJson").orEmpty()
        applyMarkers(intentMarkers)
        if (intentMarkers.isBlank()) fetchMarkersForCurrent(playGen)
        if (!liveMode && curSeries.isNotBlank() && (epIds.isEmpty() || epNames.isEmpty())) {
            val gen = playGen
            Thread {
                val programs = kotlinx.coroutines.runBlocking {
                    X4KApi.episodeProgramsFor(this@X4KPlayerAty, curSeries)
                }
                if (programs.isNotEmpty()) {
                    runOnUiThread {
                        if (gen != playGen) return@runOnUiThread
                        if (epIds.isEmpty()) {
                            epIds = ArrayList(programs.map { it.contentId })
                            val targetCode = curCode.ifBlank { code }
                            val idx = epIds.indexOf(targetCode)
                            if (idx >= 0) curEpIndex = idx
                        }
                        if (epNames.isEmpty()) {
                            epNames = ArrayList(programs.map { it.name })
                        }
                        if (epNumbers.isEmpty()) {
                            epNumbers = ArrayList(programs.map { it.number })
                        }
                        curEpNumber = epNumbers.getOrNull(curEpIndex)?.takeIf { it > 0 } ?: (curEpIndex + 1)
                        if (markEpisodeNumber <= 0) markEpisodeNumber = curEpNumber
                        // Con la lista real ya resuelta, la espera corrige el capítulo si hacía falta.
                        applyLoadingContent(curTitle)
                        updateNextButtonState()
                    }
                }
            }.start()
        }

        if (code.isBlank() || mediasJson.isBlank()) {
            finish()
            return
        }

        // The brand pre-roll belongs to films; live TV opens straight on the signal.
        // En Smart TV o dispositivos modestos (Chromecast, Fire TV, Amlogic) se omite el VideoView
        // de la intro para evitar que dos decodificadores de hardware compitan antes de reproducir.
        val skipIntroForPerformance = com.chronosbvrx.visionplus.DevicePerformance.isLowEndDevice(this)
        if (!liveMode && !skipIntroForPerformance) showIntro(root())

        val medias = runCatching { parseMedias(mediasJson) }.getOrDefault(ArrayList())
        if (medias.isNotEmpty()) {
            declaredLanguages = medias.first().audio
                .split(',').map { it.trim() }.filter { it.isNotBlank() }
        }
        val apiPrefs = getSharedPreferences("x4k_api", Context.MODE_PRIVATE)
        val resolvedEntries = if (!entriesJson.isNullOrBlank()) entriesJson else apiPrefs.getString("entries_json", null)
        val resolvedParams = if (playParams.isNotBlank()) playParams else apiPrefs.getString("play_params", "").orEmpty()

        // Si el motor :x4k ya fue precalentado por X4KWarmupReceiver, montamos la superficie de video de inmediato en 0 ms.
        if (X4KTitan.isConfigured(resolvedEntries, resolvedParams)) {
            Log.i(TAG, "motor :x4k ya precalentado; montando UI en 0 ms")
            mountUi(root(), mode, code, title, aspect, medias)
        } else {
            val gen = playGen
            Thread {
                try {
                    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
                    val ok = X4KTitan.ensureConfigured(
                        context = this,
                        userId = com.chronosbvrx.visionplus.data.xuper.X4KApi.userIdOf(),
                        devId = com.chronosbvrx.visionplus.data.xuper.X4KApi.snOf(),
                        androidId = androidId,
                        entriesJson = resolvedEntries,
                        playParams = resolvedParams,
                    )
                    Log.i(TAG, "ensureConfigured=$ok")
                    runOnUiThread {
                        if (gen != playGen) return@runOnUiThread
                        mountUi(root(), mode, code, title, aspect, medias)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "falló: ${e.message}", e)
                    finish()
                }
            }.start()
        }
    }

    /** Vision's own pre-roll first, centered on black; the film only starts when it is done. */
    private fun showIntro(root: FrameLayout) {
        val wrap = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = VideoView(this).apply {
            setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.brand_intro}"))
            setOnPreparedListener { mp ->
                mp.isLooping = false
                requestLayout()
                start()
            }
            setOnCompletionListener { removeIntro() }
            setOnErrorListener { _, _, _ ->
                removeIntro()
                true
            }
        }
        wrap.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        val skip = TextView(this).apply {
            text = "Omitir  ›"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundBackground(0x66000000)
            visibility = View.GONE
            setOnClickListener { removeIntro() }
        }
        wrap.addView(skip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END,
        ).apply {
            marginEnd = dp(16)
            bottomMargin = dp(110)
        })
        skip.tvControl()
        introSkip = skip
        handler.postDelayed({
            skip.visibility = View.VISIBLE
            if (isTv) skip.requestFocus()
        }, 2000)
        root.addView(wrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        introView = wrap
    }

    /** Dismisses the pre-roll, hands the screen to the branded wait and starts the film. */
    private fun removeIntro() {
        val wrap = introView
        introView = null
        introSkip = null
        if (wrap != null) {
            // Fundido: el pre-roll se desvanece sobre la espera de marca, sin corte.
            wrap.animate().alpha(0f).setDuration(450).withEndAction { root().removeView(wrap) }.start()
        }
        // Now the branded wait takes over; controls stay hidden until requested.
        if (!firstFrame && !errorShown) {
            loadingFading = false
            introPill?.visibility = View.GONE
            loadingOverlay?.alpha = 1f
            loadingOverlay?.visibility = View.VISIBLE
        }
        hideControls()
        val start = pendingPlay
        pendingPlay = null
        if (start != null) {
            if (loadingOverlay?.visibility != View.VISIBLE) loadingOverlay?.visibility = View.VISIBLE
            start.invoke()
        }
    }

    /**
     * Deja el reproductor listo para OTRA reproducción sobre la misma vista: apaga la
     * identidad y los diálogos de la anterior. La vista del motor y sus overlays se
     * reconstruyen después. Cualquier dato viejo aquí muerde: marcadores de salto,
     * capítulos o diálogos de la reproducción previa sobre el título nuevo.
     */
    private fun resetForNewPlay() {
        // Diálogos/estados modales de la reproducción previa.
        picking = false
        nextVisible = false
        suggestVisible = false
        suggestOverlay = null
        movieSuggestShown = false
        nextDismissed = false
        nextOverlay = null
        nextCountdownView = null
        nextPlayBtn = null
        nextTarget = null
        resumeOverlay = null
        picker = null
        pendingPlay = null
        lastPickerSourceBtn = null
        controlsVisible = false
        swallowWakeupKeyCode = KeyEvent.KEYCODE_UNKNOWN
        swallowWakeupAtMs = 0L
        // Banderas de reproducción.
        firstFrame = false
        errorShown = false
        loadingFading = false
        softRestarts = 0
        skipProbePosMs = -1L
        skipProbeAtMs = 0L
        skipProbeMuteUntilMs = 0L
        lastSkipReportAtMs = 0L
        liveSignalSeen = false
        endedShown = false
        finishedSaved = false
        nextPendingAtEnd = false
        creditsCardShown = false
        postCreditsShown = false
        introSkipped = false
        recapSkipped = false
        scrubbing = false
        previewSeekMs = -1L
        durationMs = -1L
        declaredDurationMs = 0L
        declaredLanguages = emptyList()
        lastHistorySave = 0L
        // Contexto de la reproducción (cada play lo vuelve a leer del intent).
        curCode = ""
        curTitle = ""
        curBaseTitle = ""
        curSeries = ""
        curEpIndex = 0
        curSeasonIndex = 0
        epIds = ArrayList()
        epNames = ArrayList()
        epNumbers = ArrayList()
        seasonIds = ArrayList()
        seasonNames = ArrayList()
        curMedias = ArrayList()
        currentMediaName = ""
        curQuality = ""
        curAspect = 0
        intentPoster = ""
        resumeMs = 0L
        resumeApplied = false
        resumeAsked = false
        pendingAutoResume = false
        pendingResumeEntry = null
        // En vivo.
        liveMode = false
        liveChannels = null
        liveIndex = -1
        liveGroupTitle = ""
        currentEpgItems = emptyList()
        // Marcadores de salto (intro/créditos): no cruzan entre reproducciones.
        markIntroStartMs = -1L
        markIntroEndMs = -1L
        markRecapStartMs = -1L
        markRecapEndMs = -1L
        markCreditsStartMs = -1L
        markCreditsEndMs = -1L
        markPostCreditsStartMs = -1L
        markPostCreditsEndMs = -1L
        markEpisodeRef = ""
        markEpisodeNumber = 0
        markEpisodeLengthSec = 0
        markersFetchGen += 1
        // Subtítulos del proveedor: el programa siguiente trae su propia lista.
        stopSubsTicker()
        subsEntries = emptyList()
        subsLoaded = false
        subsCurrent = null
        subsCues = emptyList()
        subsLoadToken = ""
        subsPickerWaiting = false
    }

    /** Quita TODAS las capas de UI de la reproducción anterior; la vista del motor se queda. */
    private fun teardownPlaySurfaces() {
        val root = root()
        val keep = playerView
        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i) !== keep) root.removeViewAt(i)
        }
        controls = null
        loadingOverlay = null
        loadingTitleView = null
        loadingSubView = null
        loadingEpNameView = null
        errorOverlay = null
        errorRetry = null
        introView = null
        introSkip = null
        introPill = null
        postCreditsNotice = null
        subsView = null
        topTitle = null
        epgLine = null
        favBtn = null
        infoBtn = null
        liveChannelTitleView = null
        liveChannelNumView = null
        liveChannelGroupView = null
        liveEpgNowView = null
        liveEpgNextView = null
        liveEpgProgView = null
        playPauseBtn = null
        seekBar = null
        timeNow = null
        timeTotal = null
        audioBtn = null
        subsBtn = null
        qualityBtn = null
        optionPillChain.clear()
        forwardNavView = null
        seekNavView = null
        playPauseNavView = null
        channelsBtn = null
        episodesBtn = null
        nextEpBtn = null
        playerControls.clear()
    }

    /** Atrás: cierra diálogos, esconde controles y al doble toque sale (dejando la vista residente). */
    private fun installBackCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (resumeOverlay != null) {
                    // Atrás en el diálogo = continuar donde quedó (no destructivo).
                    acceptResume()
                    return
                }
                if (picking) {
                    dismissPicker()
                    return
                }
                if (suggestVisible) {
                    hideSuggestions()
                    return
                }
                if (nextVisible) {
                    nextDismissed = true
                    hideNext()
                    return
                }
                if (!errorShown && controlsVisible) {
                    hideControls()
                    lastBackPressTime = System.currentTimeMillis()
                    return
                }
                // Leaving during the pre-roll just leaves: the film never starts.
                if (introView != null) {
                    pendingPlay = null
                    introView?.let { root().removeView(it) }
                    introView = null
                    exitPlayer()
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2500L) {
                    exitPlayer()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@X4KPlayerAty, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /**
     * El control remoto del teléfono también alcanza al reproductor, que vive en su propio
     * proceso (`:x4k`). El menú y el reproductor nunca están al frente a la vez, así que cuando
     * el reproductor manda se registra aquí para recibir las teclas por el broadcast interno y
     * reinyectarlas como si vinieran del mando de la tele.
     */
    private fun installRemoteReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val code = intent?.getIntExtra(RemoteBus.EXTRA_KEY_CODE, KeyEvent.KEYCODE_UNKNOWN)
                    ?: KeyEvent.KEYCODE_UNKNOWN
                val action = intent?.getStringExtra(RemoteBus.EXTRA_KEY_ACTION) ?: RemoteProto.ACTION_TAP
                handleRemoteKey(code, action)
            }
        }
        remoteReceiver = receiver
        runCatching {
            val filter = IntentFilter(RemoteBus.ACTION_REMOTE_KEY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
        }
    }

    /** Una tecla del teléfono, con las mismas reglas que un mando: lista blanca y volumen aparte. */
    private fun handleRemoteKey(code: Int, action: String) {
        if (!RemoteKeys.isAllowed(code)) return
        if (RemoteKeys.isVolume(code)) {
            applyRemoteVolume(code)
            return
        }
        // Atrás se atiende por el despachador real (el mismo del mando): así respeta los dos
        // pasos (cerrar diálogo / ocultar controles / salir) sin depender del «tracking» de
        // teclas sintéticas.
        if (code == KeyEvent.KEYCODE_BACK) {
            runCatching { onBackPressedDispatcher.onBackPressed() }
            return
        }
        dispatchRemoteKey(code, action)
    }

    /** Una pulsación sintética: abajo y arriba con el MISMO `downTime` (lo pide el sistema). */
    private fun dispatchRemoteKey(code: Int, action: String) {
        val down = android.os.SystemClock.uptimeMillis()
        fun ev(a: Int) = KeyEvent(down, android.os.SystemClock.uptimeMillis(), a, code, 0)
        runCatching {
            when (action) {
                RemoteProto.ACTION_DOWN -> dispatchKeyEvent(ev(KeyEvent.ACTION_DOWN))
                RemoteProto.ACTION_UP -> dispatchKeyEvent(ev(KeyEvent.ACTION_UP))
                else -> {
                    dispatchKeyEvent(ev(KeyEvent.ACTION_DOWN))
                    dispatchKeyEvent(ev(KeyEvent.ACTION_UP))
                }
            }
        }
    }

    private fun applyRemoteVolume(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val direction = when (code) {
                KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                else -> AudioManager.ADJUST_TOGGLE_MUTE
            }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    /** Le dice al teléfono qué se está viendo (otro proceso → broadcast al principal). */
    private fun publishRemoteNow(text: String) {
        if (text.isBlank() || text == lastRemoteNow) return
        lastRemoteNow = text
        RemoteBus.publishScreenRemote(this, text)
    }

    /** Builds the view + the premium overlays and starts playback. */
    private fun mountUi(
        root: FrameLayout,
        mode: String,
        code: String,
        title: String,
        aspect: Int,
        medias: List<X4KTitan.MediaSpec>,
    ) {
        // «Camino B»: la vista del motor es residente — se crea UNA sola vez por proceso y
        // las reproducciones siguientes reutilizan la MISMA (el motor solo admite un
        // programa por vista; el patrón del zapping, stop → play in-place, es el que sí
        // funciona). Todo lo demás (controles, espera, overlays) se rehace por reproducción.
        val v = playerView ?: X4KTitan.newVideoView(this)?.also { created ->
            root.addView(
                created,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            // Marca para el principal — archivo, no SharedPreferences: la copia cacheada
            // del otro proceso puede no verla a tiempo (lección de PlaybackHistory). Indica
            // que este proceso ya usó el motor con vista; una descarga headless aquí
            // mezclaría historias del motor, así que el gestor devuelve el proceso a limpio
            // antes de encargar nada.
            runCatching { java.io.File(filesDir, "x4k_view_played").createNewFile() }
        } ?: run {
            finish()
            return
        }
        playerView = v
        if (!liveMode) buildSubtitleOverlay(root)
        curCode = code
        curTitle = title
        curBaseTitle = title.substringBefore(" · T").substringBefore(" · C").ifBlank { title }
        curAspect = if (aspect == 0 && com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultAspect != 0) {
            com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultAspect
        } else {
            aspect
        }
        // La «Calidad preferida» manda desde el PRIMER cuadro: `X4KTitan.play` arranca con la
        // primera media de la lista, así que la elegida se coloca al frente (antes solo se
        // pintaba la etiqueta y el motor seguía en la calidad del portal).
        val orderedMedias = orderMediasByQuality(
            medias,
            { it.quality },
            com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality,
        )
        curMedias = ArrayList(orderedMedias)
        val initialMedia = orderedMedias.firstOrNull()
        currentMediaName = initialMedia?.name.orEmpty()
        curQuality = initialMedia?.quality.orEmpty()
        X4KTitan.bindView(v)

        X4KTitan.setPlayerListener(
            v,
            X4KTitan.makePlayerListener(
                onPosition = { pos ->
                    if (pos > 0 && !firstFrame) {
                        handler.post(poller)
                    }
                },
                onDuration = { dur ->
                    val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
                    if (!isBufferWindowDuration(dur, pos, declaredDurationMs, liveMode)) {
                        if (dur > 0L) {
                            durationMs = dur
                            if (declaredDurationMs <= 0L) declaredDurationMs = dur
                            runOnUiThread {
                                val total = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
                                timeTotal?.text = if (total > 0L) fmt(total) else "--:--"
                                if (!scrubbing && total > 0) {
                                    seekBar?.progress = ((pos * 1000) / total).toInt().coerceIn(0, 1000)
                                }
                            }
                        }
                    }
                },
                onCompletion = {
                    runOnUiThread {
                        if (!liveMode && firstFrame && System.currentTimeMillis() - startedAtMs > 5_000L) {
                            onEpisodeEnded()
                        }
                    }
                },
            ),
        )

        buildControls(root, title)
        buildLoading(root, title)
        buildError(root)
        wireGestures(root)
        if (!liveMode && code.isNotBlank()) loadSubs(code)

        // While the pre-roll plays, it owns the screen: everything else stays out of its way.
        if (introView != null) {
            loadingOverlay?.visibility = View.GONE
            controls?.visibility = View.GONE
            controlsVisible = false
            introView?.let { root.bringChildToFront(it) }
        }

        val startPlayback: () -> Unit = {
            fun attemptPlay(retriesLeft: Int) {
                startedAtMs = System.currentTimeMillis()
                if (X4KTitan.play(v, mode, code, title, orderedMedias)) {
                    if (!liveMode) {
                        // El cuadro entra con un fundido: pre-roll → película se sienten una sola secuencia.
                        v.alpha = 0f
                        v.animate().alpha(1f).setStartDelay(250).setDuration(650).start()
                    } else {
                        v.alpha = 1f
                    }
                    // The engine may reset the render surface on start: re-apply the aspect a few times.
                    listOf(200L, 700L, 1500L, 3000L, 5000L).forEach { delay ->
                        handler.postDelayed({ playerView?.let { X4KTitan.setAspect(it, aspect) } }, delay)
                    }
                    handler.post(poller)
                    if (liveMode && code.isNotBlank()) {
                        loadEpg(code)
                    }
                } else if (retriesLeft > 0) {
                    // El motor puede tardar un instante en aceptar el play tras el boot
                    // (proceso :x4k recién levantado): un segundo intento evita el error visible.
                    Log.w(TAG, "play devolvió false; reintentando…")
                    handler.postDelayed({ attemptPlay(retriesLeft - 1) }, 900L)
                } else {
                    Log.e(TAG, "play devolvió false")
                    showError("motor-no-arranca")
                }
            }
            attemptPlay(1)
        }
        if (introView == null) startPlayback() else pendingPlay = startPlayback
    }

    // ---------------------------------------------------------------- palette & icons

    private val blue = 0xFF2D7DF6.toInt()
    private val blueBright = 0xFF6BA6FF.toInt()
    private val surface = 0xFF1B2538.toInt()

    /** A monochrome transport icon drawn in code: play, pause, or the ±10 s double arrow. */
    /**
     * Los iconos de transporte del reproductor usan los mismos vectores `ic_vp_*` de toda
     * la app (gemelos Material Symbols): una sola familia visual, cero emojis.
     */
    private fun icon(mode: Int): View = android.widget.ImageView(this).apply {
        setImageResource(
            when (mode) {
                MODE_PLAY -> R.drawable.ic_vp_play
                MODE_PAUSE -> R.drawable.ic_vp_pause
                MODE_REW -> R.drawable.ic_vp_rewind
                else -> R.drawable.ic_vp_forward
            },
        )
        setColorFilter(Color.WHITE)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    }

    private fun roundBackground(color: Int, radius: Int = 22): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    /** Aro blanco para el foco: lo enfocado se distingue de lejos, no solo con el zoom. */
    private fun ringBackground(color: Int, radius: Int = 22): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        setStroke(dp(3), Color.WHITE)
    }

    // ---------------------------------------------------------------- overlays

    private fun buildLoading(root: FrameLayout, title: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_vision_wordmark)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(180), dp(36)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        box.addView(ProgressBar(this, null, android.R.attr.progressBarStyle).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(blueBright)
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { topMargin = dp(26) })
        val titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        loadingTitleView = titleView
        box.addView(titleView)
        val subView = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        loadingSubView = subView
        box.addView(subView)
        val epNameView = TextView(this).apply {
            textSize = 12f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        loadingEpNameView = epNameView
        box.addView(epNameView)
        // La firma del autor, como los estudios en el cine: presente en cada reproducción.
        box.addView(TextView(this).apply {
            text = "VISION+  ·  POR CHRONOSBVRX"
            textSize = 10f
            setTextColor(0x8CFFFFFF.toInt())
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, dp(44), 0, 0)
        })
        applyLoadingContent(title)
        root.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        loadingOverlay = box
    }

    /**
     * La espera habla del contenido real: película, serie con su temporada/capítulo exacto o
     * canal en vivo. Se refresca en cada cambio de episodio o de canal para que el título
     * nunca quede congelado en el primero que se reprodujo.
     */
    private fun applyLoadingContent(rawTitle: String) {
        val isSeries = !liveMode && (isSeriesContent(epIds.size, seasonIds.size) || curSeries.isNotBlank())
        val card = resolveLoadingCardText(
            isLive = liveMode,
            isSeries = isSeries,
            baseTitle = curBaseTitle,
            rawTitle = rawTitle,
            seasonLabel = if (seasonIds.size > 1) seasonLabel(curSeasonIndex) else "",
            episodeIndex = curEpIndex,
            episodeName = epNames.getOrNull(curEpIndex).orEmpty(),
        )
        loadingTitleView?.text = card.title
        loadingSubView?.text = card.subtitle
        loadingSubView?.setTextColor(if (card.accent) blueBright else 0x80FFFFFF.toInt())
        loadingSubView?.typeface = if (card.accent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        loadingEpNameView?.text = card.episodeName
        loadingEpNameView?.visibility = if (card.episodeName.isBlank()) View.GONE else View.VISIBLE
        // El control remoto refleja lo que la tele está viendo (canal, serie con su capítulo…).
        publishRemoteNow(listOf(card.title, card.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
    }

    private fun buildError(root: FrameLayout) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2070B13.toInt())
            visibility = View.GONE
        }
        box.addView(TextView(this).apply {
            text = "No se pudo iniciar la reproducción"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "Revisa tu conexión e inténtalo de nuevo."
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, dp(16))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val retry = TextView(this).apply {
            text = "  Reintentar  "
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(blue)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { recreate() }
        }
        retry.tvControl()
        errorRetry = retry
        row.addView(retry)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val leave = TextView(this).apply {
            text = "  Volver  "
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        leave.tvControl()
        row.addView(leave)
        if (isTv) {
            retry.id = View.generateViewId()
            leave.id = View.generateViewId()
            retry.nextFocusRightId = leave.id
            leave.nextFocusLeftId = retry.id
        }
        box.addView(row)
        root.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        errorOverlay = box
    }

    /**
     * Re-arranque interno silencioso: el motor aceptó el `play` pero no dio señal
     * (medido en cajas lentas: la primera pasada del P2P se queda corta y el
     * «Reintentar» manual sí arranca). Para el usuario equivale a esperar un poco
     * más; no se pinta error ni se pierde el estado del reproductor.
     */
    private fun softRestartPlayback() {
        val v = playerView ?: return
        if (curMedias.isEmpty()) return
        softRestarts++
        Log.w(TAG, "sin primer cuadro: re-arranque interno #$softRestarts (${if (liveMode) "live" else "vod"} $curCode)")
        com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
            "PLAYER_SOFT_RESTART", "modo=${if (liveMode) "live" else "vod"} intento=$softRestarts",
        )
        runCatching { X4KTitan.stop(v) }
        firstFrame = false
        loadingFading = false
        introPill?.visibility = View.GONE
        loadingOverlay?.alpha = 1f
        loadingOverlay?.visibility = View.VISIBLE
        startedAtMs = System.currentTimeMillis()
        handler.postDelayed({
            if (isFinishing || isDestroyed || firstFrame) return@postDelayed
            val ok = X4KTitan.play(v, if (liveMode) "live" else "vod", curCode, curTitle, curMedias)
            Log.i(TAG, "re-arranque interno #$softRestarts ok=$ok")
        }, 700L)
    }

    private fun showError(stage: String = "") {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.engine("error")
        com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
            "PLAYER_ERROR", "etapa=${stage.ifBlank { "desconocida" }} modo=${if (liveMode) "live" else "vod"}",
        )
        errorShown = true
        removeIntro()
        handler.removeCallbacks(hideRunnable)
        loadingOverlay?.visibility = View.GONE
        errorOverlay?.visibility = View.VISIBLE
        controlsVisible = true
        controls?.visibility = View.VISIBLE
        if (isTv) errorRetry?.post { errorRetry?.requestFocus() }
    }

    // ---------------------------------------------------------------- controls

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun buildControls(root: FrameLayout, title: String) {
        val overlay = FrameLayout(this).apply {
            isClickable = false
            isFocusable = false
        }

        // Top bar: title, below the status bar, with a soft blue-black scrim.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xB3070B13.toInt())
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        top.addView(titleView)
        topTitle = titleView
        val epg = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(0xB3FFFFFF.toInt())
            maxLines = 1
            setPadding(dp(46), 0, dp(12), dp(6))
            visibility = if (liveMode) View.VISIBLE else View.GONE
        }
        epgLine = epg
        val topColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(epg, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val topWrap = FrameLayout(this).apply {
            setPadding(0, statusBarHeight(), 0, 0)
            setBackgroundColor(0x66070B13.toInt())
        }
        topWrap.addView(topColumn, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        overlay.addView(
            topWrap,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        // Bottom panel: seek arriba y una sola fila de transporte + pills (ancha, compacta).
        val hPad = if (isTv) dp(48) else dp(16)
        val bPad = if (isTv) dp(28) else dp(10)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xD9070B13.toInt())
            setPadding(hPad, dp(8), hPad, bPad)
            clipChildren = false
            clipToPadding = false
        }

        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val now = TextView(this).apply {
            text = "00:00"
            textSize = 13f
            setTextColor(Color.WHITE)
            isSingleLine = true
            includeFontPadding = false
            minWidth = dp(44)
        }
        val seek = SeekBar(this).apply {
            max = 1000
            splitTrack = false
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x3DFFFFFF)
            thumbTintList = android.content.res.ColorStateList.valueOf(blueBright)
            minimumHeight = dp(24)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
                        val targetMs = if (total > 0) (progress.toLong() * total) / 1000L else 0L
                        timeNow?.text = fmt(targetMs)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    scrubbing = true
                    scheduleHide()
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
                    val targetMs = if (total > 0 && sb != null) (sb.progress.toLong() * total) / 1000L else 0L
                    playerView?.let { com.chronosbvrx.visionplus.data.xuper.X4KTitan.seekTo(it, targetMs) }
                    noteAppSeek()
                    scrubbing = false
                    scheduleHide()
                }
            })
        }
        val total = TextView(this).apply {
            text = if (declaredDurationMs > 0) fmt(declaredDurationMs) else "--:--"
            textSize = 13f
            setTextColor(Color.WHITE)
            isSingleLine = true
            includeFontPadding = false
            minWidth = dp(44)
            gravity = Gravity.END
        }
        seekRow.addView(now, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        seekRow.addView(
            seek,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            },
        )
        seekRow.addView(total, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (liveMode) {
            // Live has no timeline: a red badge instead of a growing fake duration.
            seekRow.visibility = View.GONE
        }
        bottom.addView(
            seekRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(6)
            },
        )
        if (liveMode) {
            val liveBanner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0xF0080E1B.toInt())
                    setStroke(dp(1), 0x552D7DF6.toInt())
                }
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }

            // Fila 1: Número de canal + Nombre + Grupo/País + Badge "● EN VIVO"
            val chRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val chNumBadge = TextView(this).apply {
                text = currentChannelNumBadge()
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = roundBackground(blue, 6)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            val chTitle = TextView(this).apply {
                text = currentChannelName()
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
                maxLines = 1
            }
            val grpTag = TextView(this).apply {
                text = currentChannelGroupTag()
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(blueBright)
                background = roundBackground(0x332D7DF6.toInt(), 5)
                setPadding(dp(7), dp(2), dp(7), dp(2))
            }
            val liveDot = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundBackground(0x33FF5A5A.toInt(), 5)
                setPadding(dp(7), dp(3), dp(7), dp(3))
            }
            liveDot.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFFFF5A5A.toInt())
                    }
                },
                LinearLayout.LayoutParams(dp(7), dp(7)),
            )
            liveDot.addView(TextView(this).apply {
                text = "EN VIVO"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFF5A5A.toInt())
                letterSpacing = 0.08f
                setPadding(dp(6), 0, 0, 0)
            })
            liveChannelNumView = chNumBadge
            liveChannelTitleView = chTitle
            liveChannelGroupView = grpTag

            chRow.addView(chNumBadge)
            chRow.addView(chTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            chRow.addView(grpTag)
            chRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            chRow.addView(liveDot)
            liveBanner.addView(chRow)

            // Fila 2: EPG Ahora
            val epgNowRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            val epgNowBadge = TextView(this).apply {
                text = "AHORA:"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(blueBright)
                setPadding(0, 0, dp(6), 0)
            }
            val epgNowText = TextView(this).apply {
                text = "Cargando programación…"
                textSize = 12f
                setTextColor(Color.WHITE)
                maxLines = 1
            }
            liveEpgNowView = epgNowText
            epgNowRow.addView(epgNowBadge)
            epgNowRow.addView(epgNowText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            liveBanner.addView(epgNowRow)

            // Barra fina azul de progreso EPG
            val prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                progressTintList = android.content.res.ColorStateList.valueOf(blue)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x26FFFFFF)
            }
            liveEpgProgView = prog
            liveBanner.addView(prog, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
                topMargin = dp(2)
                bottomMargin = dp(3)
            })

            // Fila 3: EPG Después
            val epgNextRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val epgNextBadge = TextView(this).apply {
                text = "DESPUÉS:"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0x80FFFFFF.toInt())
                setPadding(0, 0, dp(6), 0)
            }
            val epgNextText = TextView(this).apply {
                text = ""
                textSize = 11f
                setTextColor(0xB3FFFFFF.toInt())
                maxLines = 1
            }
            liveEpgNextView = epgNextText
            epgNextRow.addView(epgNextBadge)
            epgNextRow.addView(epgNextText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            liveBanner.addView(epgNextRow)

            bottom.addView(liveBanner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            })
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (liveMode) Gravity.CENTER else Gravity.CENTER_VERTICAL
        }

        var rewindBtn: View? = null
        var fwdBtn: View? = null

        if (liveMode) {
            val channels = pill("Canales", R.drawable.ic_vp_live_tv) { showChannelPicker() }
            channels.tvControl()
            channelsBtn = channels
            buttons.addView(channels)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))

            val fav = pill(
                if (isCurrentFav()) "Favorito" else "Añadir a favoritos",
                if (isCurrentFav()) R.drawable.ic_vp_star else R.drawable.ic_vp_star_border,
            ) { toggleCurrentFav() }
            fav.tvControl()
            favBtn = fav
            updateFavButtonState()
            buttons.addView(fav)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))

            val info = pill("Información", R.drawable.ic_vp_info) { showChannelInfo() }
            info.tvControl()
            infoBtn = info
            buttons.addView(info)
        } else {
            val rewind = skipButton(
                MODE_REW,
                "-10 s",
            ) {
                performSeekBy(-10_000L)
            }
            val playPause = FrameLayout(this).apply {
                background = roundBackground(blue, 28)
                setOnClickListener { if (canExecuteControlAction()) togglePlay() }
            }
            playPause.addView(icon(MODE_PAUSE), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            playPause.tvControl(blue, 28)
            val forward = skipButton(
                MODE_FWD,
                "+10 s",
            ) {
                performSeekBy(10_000L)
            }
            rewind.tvControl(surface)
            forward.tvControl(surface)
            rewindBtn = rewind
            fwdBtn = forward
            buttons.addView(rewind)
            buttons.addView(playPause, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            })
            buttons.addView(forward)
            buttons.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            // Píldoras de opciones: se crean siempre (la visibilidad se decide con lo que el
            // título REALMENTE trae) y el espacio entre ellas va en el margen, para que una
            // oculta no deje un hueco.
            fun addPill(v: View) {
                buttons.addView(
                    v,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = dp(8) },
                )
            }
            if (isSeriesContent(epIds.size, seasonIds.size)) {
                val nextEp = pill("Sig. episodio", R.drawable.ic_vp_skip_next) {
                    if (canExecuteControlAction()) playNextEpisodeFromControls()
                }
                nextEp.tvControl()
                nextEpBtn = nextEp
                addPill(nextEp)

                val episodes = pill("Capítulos", R.drawable.ic_vp_playlist) { showEpisodePicker() }
                episodes.tvControl()
                episodesBtn = episodes
                addPill(episodes)
                updateNextButtonState()
            }
            val firstAudio = declaredLanguages.firstOrNull().orEmpty()
            val audioName = trackDisplayLang(firstAudio, null).takeIf { it.isNotBlank() }
                ?.let { languageLabel(it, "Idioma") } ?: "Idioma"
            val audio = pill(audioName, R.drawable.ic_vp_volume_up) {
                showLanguagePicker(forSubtitle = false)
            }
            audio.tvControl()
            val subs = pill("Subtítulos", R.drawable.ic_vp_subtitles) { showLanguagePicker(forSubtitle = true) }
            subs.tvControl()
            val qDisplay = formatQualityShort(curQuality)
            val quality = pill(qDisplay, R.drawable.ic_vp_tune) { showQualityPicker() }
            quality.tvControl()
            addPill(audio)
            addPill(subs)
            addPill(quality)
            playPauseBtn = playPause
            audioBtn = audio
            subsBtn = subs
            qualityBtn = quality
            playPauseNavView = playPause
            forwardNavView = forward
            optionPillChain = ArrayList<View>().apply {
                nextEpBtn?.let { add(it) }
                episodesBtn?.let { add(it) }
                add(audio)
                add(subs)
                add(quality)
            }
        }

        // En pantallas angostas (móvil vertical) la fila rueda a los lados; en TV y en
        // apaisado cabe entera y el scroll no interviene.
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(
                buttons,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        bottom.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) })
        overlay.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        if (isTv) {
            if (liveMode) {
                val chanView = channelsBtn
                val favView = favBtn
                val infoView = infoBtn
                val chanId = View.generateViewId().also { chanView?.id = it }
                val favId = View.generateViewId().also { favView?.id = it }
                val infoId = View.generateViewId().also { infoView?.id = it }

                chanView?.nextFocusUpId = chanId
                chanView?.nextFocusLeftId = chanId
                chanView?.nextFocusRightId = favId
                chanView?.nextFocusDownId = chanId

                favView?.nextFocusUpId = favId
                favView?.nextFocusLeftId = chanId
                favView?.nextFocusRightId = infoId
                favView?.nextFocusDownId = favId

                infoView?.nextFocusUpId = infoId
                infoView?.nextFocusLeftId = favId
                infoView?.nextFocusRightId = infoId
                infoView?.nextFocusDownId = infoId
            } else {
                val rewind = rewindBtn
                val playPause = playPauseBtn
                val forward = fwdBtn
                if (rewind != null && playPause != null && forward != null) {
                    val rewindId = View.generateViewId().also { rewind.id = it }
                    val playId = View.generateViewId().also { playPause.id = it }
                    val forwardId = View.generateViewId().also { forward.id = it }
                    seek.isFocusable = true
                    seek.isFocusableInTouchMode = false
                    playerControls.add(seek)
                    seek.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
                        seek.thumbTintList = android.content.res.ColorStateList.valueOf(if (focused) Color.WHITE else blueBright)
                        if (!focused && previewSeekMs >= 0L) {
                            handler.removeCallbacks(commitSeekRunnable)
                            commitSeekRunnable.run()
                        }
                        if (focused && controlsVisible && !picking && !nextVisible && introView == null && !errorShown) scheduleHide()
                    }
                    seek.setOnKeyListener { _, keyCode, event ->
                        if (!canExecuteControlAction()) return@setOnKeyListener true
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
                                    val current = if (previewSeekMs >= 0L) previewSeekMs else com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
                                    val step = com.chronosbvrx.visionplus.ui.player.calculateScrubStepMs(total, event.repeatCount)
                                    val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step
                                    val newPos = if (total > 0) (current + delta).coerceIn(0L, total) else (current + delta).coerceAtLeast(0L)
                                    scrubbing = true
                                    previewSeekMs = newPos
                                    if (total > 0) {
                                        seek.progress = ((newPos * 1000) / total).toInt().coerceIn(0, 1000)
                                    }
                                    timeNow?.text = fmt(newPos)
                                    handler.removeCallbacks(commitSeekRunnable)
                                    handler.postDelayed(commitSeekRunnable, 350L)
                                    scheduleHide()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (previewSeekMs >= 0L) {
                                        handler.removeCallbacks(commitSeekRunnable)
                                        commitSeekRunnable.run()
                                    }
                                    playPauseBtn?.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (previewSeekMs >= 0L) {
                                        handler.removeCallbacks(commitSeekRunnable)
                                        commitSeekRunnable.run()
                                    } else {
                                        togglePlay()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                if (previewSeekMs >= 0L) {
                                    handler.removeCallbacks(commitSeekRunnable)
                                    handler.postDelayed(commitSeekRunnable, 150L)
                                }
                                true
                            } else {
                                keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                            }
                        } else {
                            false
                        }
                    }
                    val seekId = View.generateViewId().also { seek.id = it }
                    val upId = seekId
                    seekNavView = seek
                    playPauseNavView = playPause
                    forwardNavView = forward

                    rewind.nextFocusUpId = upId
                    rewind.nextFocusRightId = playId
                    rewind.nextFocusDownId = rewindId

                    playPause.nextFocusLeftId = rewindId
                    playPause.nextFocusRightId = forwardId
                    playPause.nextFocusUpId = upId
                    playPause.nextFocusDownId = playId

                    forward.nextFocusLeftId = playId
                    forward.nextFocusRightId = forwardId
                    forward.nextFocusUpId = upId
                    forward.nextFocusDownId = forwardId

                    // La cadena siguiente/capítulos/audio/subtítulos/calidad se arma con las
                    // píldoras que SÍ tienen opciones: una oculta no deja un destino muerto
                    // y el mando nunca se queda atorado.
                    rewireOptionPillFocus()
                    // La píldora «Omitir intro» enlaza su foco en buildIntroPill/updateIntroPill:
                    // aquí todavía no existe (se crea al final de buildControls) y un enlace
                    // tardío apuntaba a un id muerto: el mando jamás podía llegar a ella.

                    seek.nextFocusUpId = seekId
                    seek.nextFocusDownId = playId
                    seek.nextFocusLeftId = seekId
                    seek.nextFocusRightId = seekId
                }
            }
        }
        overlay.setOnClickListener { }
        overlay.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        controls = overlay
        if (!liveMode && isSeriesContent(epIds.size, seasonIds.size)) {
            buildIntroPill(root)
        }
        if (!liveMode) {
            seekBar = seek
            timeNow = now
            timeTotal = total
            // Audio/subtítulos/calidad: solo las píldoras que tienen algo que elegir.
            applyOptionPillVisibility()
        }
    }

    /**
     * Muestra solo las píldoras con opciones REALES del título: audio si hay más de un idioma,
     * subtítulos si el programa trae al menos uno y calidad si hay más de una resolución. Con
     * una sola opción el botón se oculta: un menú de una sola entrada (o vacío) es una promesa
     * falsa. Se re-evalúa al abrir, al llegar la lista de subtítulos y en el primer cuadro (el
     * motor ya conoce sus pistas).
     */
    private fun applyOptionPillVisibility() {
        if (liveMode || playerView == null) return
        val engineAudio = X4KTitan.tracks().count { it.first == "audio" }
        audioBtn?.visibility = if (shouldShowAudioPicker(declaredLanguages, engineAudio)) View.VISIBLE else View.GONE
        val subsCount = if (subsLoaded) distinctLanguages(subsEntries).size else 0
        subsBtn?.visibility = if (shouldShowSubtitlePicker(subsLoaded, subsCount)) View.VISIBLE else View.GONE
        val qualities = distinctQualityChoices(curMedias, { it.quality }, { it.name }).size
        qualityBtn?.visibility = if (shouldShowQualityPicker(qualities)) View.VISIBLE else View.GONE
        // Si el foco estaba en una píldora que acaba de ocultarse (p. ej. la lista de
        // subtítulos llegó vacía con los controles abiertos), vuelve al play/pausa.
        val focused = currentFocus
        if (controlsVisible && (focused === audioBtn || focused === subsBtn || focused === qualityBtn)) {
            playPauseNavView?.requestFocus()
        }
        rewireOptionPillFocus()
    }

    /**
     * Re-cablea la fila de píldoras del mando para que salte solo entre las VISIBLES. Las
     * píldoras ocultas (sin opciones) no pueden quedar como destino: el D-pad se tragaría
     * la tecla en el salto y el mando parecería muerto.
     */
    private fun rewireOptionPillFocus() {
        val forward = forwardNavView ?: return
        if (forward.id == View.NO_ID) forward.id = View.generateViewId()
        val upId = seekNavView?.id ?: -1
        val visible = optionPillChain.filter { it.visibility == View.VISIBLE }
        visible.forEach { pill -> if (pill.id == View.NO_ID) pill.id = View.generateViewId() }
        forward.nextFocusRightId = visible.firstOrNull()?.id ?: forward.id
        visible.forEachIndexed { index, pill ->
            pill.nextFocusLeftId = if (index == 0) forward.id else visible[index - 1].id
            pill.nextFocusRightId = if (index == visible.lastIndex) pill.id else visible[index + 1].id
            pill.nextFocusUpId = upId
            pill.nextFocusDownId = pill.id
        }
    }

    private fun pill(label: String, iconRes: Int = 0, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = roundBackground(surface)
        if (iconRes != 0) withIcon(iconRes)
        setOnClickListener {
            if (isModalActive || canExecuteControlAction()) onClick()
        }
    }

    /**
     * Un icono del set vectorial `ic_vp_*` dentro de una etiqueta del reproductor, con el
     * tinte de la casa. Es lo que sustituye a los emojis en píldoras y avisos.
     */
    private fun TextView.withIcon(iconRes: Int, tint: Int = Color.WHITE): TextView {
        setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        compoundDrawableTintList = android.content.res.ColorStateList.valueOf(tint)
        compoundDrawablePadding = dp(6)
        return this
    }

    private fun skipButton(mode: Int, label: String, onClick: () -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener {
                if (canExecuteControlAction()) onClick()
            }
        }
        col.addView(icon(mode), LinearLayout.LayoutParams(dp(30), dp(30)))
        col.addView(TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        return col
    }

    // ---------------------------------------------------------------- language pickers

    /** Audio: pistas del motor o idiomas declarados por el portal (el motor sí sabe elegirlas). */
    private fun audioOptions(options: MutableList<Pair<String, () -> Unit>>) {
        val v = playerView ?: return
        val engineTracks = X4KTitan.tracks().filter { it.first == "audio" }
        if (engineTracks.size > 1) {
            // El motor a veces da el códec ("aac") en vez del idioma: se empareja con lo
            // declarado por el portal cuando los conteos coinciden; si no, cada pista usa
            // lo suyo y las etiquetas repetidas se distinguen por su id.
            val pairByIndex = engineTracks.size == declaredLanguages.size
            val resolved = engineTracks.mapIndexed { index, (_, lang, _) ->
                trackDisplayLang(lang, declaredLanguages.getOrNull(index)?.takeIf { pairByIndex })
            }
            val rawLabels = engineTracks.mapIndexed { index, (_, lang, id) ->
                val r = resolved[index]
                if (r.isNotBlank()) languageLabel(r, "Pista $id")
                else if (lang.isNotBlank()) "Pista $id · ${lang.trim().uppercase()}"
                else "Pista $id"
            }
            val labels = dedupeTrackLabels(rawLabels, engineTracks.map { it.third })
            engineTracks.forEachIndexed { index, (_, _, id) ->
                val pillText = resolved[index].takeIf { it.isNotBlank() }
                    ?.let { languageLabel(it, labels[index]) } ?: labels[index]
                options += labels[index] to {
                    X4KTitan.selectTrack("audio", id)
                    com.chronosbvrx.visionplus.ui.settings.VisionSettings.setAudioLang(
                        this, curSeries.ifBlank { curCode }, resolved[index].ifBlank { labels[index] },
                    )
                    audioBtn?.text = "\u25C9 $pillText"
                }
            }
        } else {
            declaredLanguages.forEachIndexed { index, lang ->
                options += languageLabel(lang, "Pista ${index + 1}") to {
                    X4KTitan.selectAudio(v, lang, index)
                    com.chronosbvrx.visionplus.ui.settings.VisionSettings.setAudioLang(
                        this, curSeries.ifBlank { curCode }, lang,
                    )
                    audioBtn?.text = "\u25C9 " + languageLabel(lang, "Idioma")
                }
            }
        }
    }

    /** Subtítulos: la lista del servicio del proveedor para ESTE programa, con «Desactivados». */
    private fun subtitleOptions(options: MutableList<Pair<String, () -> Unit>>) {
        val settings = com.chronosbvrx.visionplus.ui.settings.VisionSettings
        val langs = orderSubsForPicker(distinctLanguages(subsEntries), subsCurrent?.langKey.orEmpty())
        options += (if (subsCurrent == null) "\u2713 Desactivados" else "Desactivados") to { disableSubs() }
        when {
            langs.isNotEmpty() -> langs.forEach { entry ->
                val name = languageName(entry.language).ifBlank { entry.language.trim().uppercase() }
                    .ifBlank { "Pista ${entry.index + 1}" }
                val mark = if (subsCurrent?.langKey == entry.langKey) "\u2713 " else ""
                options += (mark + name) to { activateSubs(curCode, entry) }
            }
            subsLoaded -> options += "Sin subtítulos disponibles" to { }
            else -> {
                // La lista aún viaja: se avisa y, cuando llega, el selector se reabre solo.
                subsPickerWaiting = true
                options += "Buscando subtítulos\u2026" to { }
                if (curCode.isNotBlank()) loadSubs(curCode, force = true)
            }
        }
        // Ajustes reales del overlay (lo pinta la app): tamaño, posición y sincronía.
        options += "Tamaño: ${settings.subSizeLabel(settings.subSizeOf(this))}" to {
            settings.setSubSize(this, settings.nextSubSize(settings.subSizeOf(this)))
            applySubsMargins()
            reopenPicker()
        }
        val atTop = settings.subPositionOf(this) == settings.SUB_POS_TOP
        options += (if (atTop) "Posición: arriba" else "Posición: abajo") to {
            settings.setSubPosition(this, if (atTop) settings.SUB_POS_BOTTOM else settings.SUB_POS_TOP)
            applySubsMargins()
            reopenPicker()
        }
        val offset = settings.subOffsetMsOf(this)
        options += "Sincronía: ${formatOffset(offset)} (−0.5 s)" to {
            settings.setSubOffsetMs(this, offset - 500)
            reopenPicker()
        }
        options += "Sincronía: ${formatOffset(offset)} (+0.5 s)" to {
            settings.setSubOffsetMs(this, offset + 500)
            reopenPicker()
        }
    }

    private fun formatOffset(ms: Int): String =
        com.chronosbvrx.visionplus.data.subs.formatSubsOffset(ms)

    private fun reopenPicker() {
        val wasSubs = lastPickerSourceBtn == subsBtn
        dismissPicker()
        // El click que llamó a la acción descarta el selector al terminar: reabrir en el
        // siguiente ciclo para no pelear con ese descarte.
        handler.post {
            runCatching {
                if (!isFinishing && !isDestroyed) showLanguagePicker(forSubtitle = wasSubs)
            }
        }
    }

    private fun showLanguagePicker(forSubtitle: Boolean) {
        lastPickerSourceBtn = if (forSubtitle) subsBtn else audioBtn
        playerView ?: return
        val options = ArrayList<Pair<String, () -> Unit>>()
        subsPickerWaiting = false
        if (forSubtitle) {
            // El reproductor nativo no resuelve subtítulos (la llave «subtitle» del SDK es un
            // no-op): se piden al servicio del portal y se pintan en la app, como el APK oficial.
            subtitleOptions(options)
        } else {
            audioOptions(options)
        }
        if (options.isEmpty()) return

        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setOnClickListener { dismissPicker() }
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF121A2A.toInt())
            }
            setPadding(dp(8), dp(14), dp(8), dp(10))
            setOnClickListener { }
            isFocusable = false
        }
        card.addView(TextView(this).apply {
            text = if (forSubtitle) "Subtítulos" else "Idioma de audio"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), 0, dp(16), dp(10))
        })
        val optionViews = ArrayList<View>()
        options.forEachIndexed { index, (label, action) ->
            val option = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    action()
                    dismissPicker()
                }
            }
            option.tvControl()
            option.id = View.generateViewId()
            if (index > 0) {
                val previous = optionViews[index - 1]
                previous.nextFocusDownId = option.id
                option.nextFocusUpId = previous.id
            }
            optionViews += option
            card.addView(option)
            if (index != options.lastIndex) {
                card.addView(View(this).apply { setBackgroundColor(0x1AFFFFFF) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            }
        }
        if (optionViews.isNotEmpty()) {
            optionViews.first().nextFocusUpId = optionViews.first().id
            optionViews.last().nextFocusDownId = optionViews.last().id
        }
        dim.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            marginStart = dp(36)
            marginEnd = dp(36)
        })
        root().addView(
            dim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        picker = dim
        picking = true
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
        if (isTv) optionViews.firstOrNull()?.let { first -> first.post { first.requestFocus() } }
    }

    private fun dismissPicker() {
        val d = picker ?: return
        root().removeView(d)
        picker = null
        picking = false
        lastControlsWakeupMs = android.os.SystemClock.uptimeMillis()
        setBackgroundFocusBlocked(false)
        if (isTv && introView == null && !errorShown) {
            val target = lastPickerSourceBtn ?: (if (liveMode) channelsBtn else playPauseBtn)
            target?.post { target.requestFocus() }
        }
        lastPickerSourceBtn = null
        scheduleHide()
    }

    // ---------------------------------------------------------------- subtitles (provider)

    /** Overlay del subtítulo: sobre el video y bajo los controles, sin foco (el mando no lo pisa). */
    private fun buildSubtitleOverlay(root: FrameLayout) {
        val settings = com.chronosbvrx.visionplus.ui.settings.VisionSettings
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = settings.subSizeSp(settings.subSizeOf(this@X4KPlayerAty), isTv)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(if (isTv) 6f else 4f, 0f, if (isTv) 2f else 1f, 0xE6000000.toInt())
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(if (isTv) 64 else 20), 0, dp(if (isTv) 64 else 20), 0)
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (settings.subPositionOf(this) == com.chronosbvrx.visionplus.ui.settings.VisionSettings.SUB_POS_TOP) Gravity.TOP else Gravity.BOTTOM,
            ).apply {
                if (settings.subPositionOf(this@X4KPlayerAty) == com.chronosbvrx.visionplus.ui.settings.VisionSettings.SUB_POS_TOP) {
                    topMargin = subsTopMargin()
                } else {
                    bottomMargin = subsBottomMargin()
                }
            },
        )
        subsView = tv
    }

    /** Los subtítulos suben cuando la barra de controles está visible (solo abajo). */
    private fun subsBottomMargin(): Int =
        if (controlsVisible) {
            if (isTv) dp(150) else dp(120)
        } else {
            if (isTv) dp(64) else dp(56)
        }

    private fun subsTopMargin(): Int = if (isTv) dp(64) else dp(56)

    private fun applySubsMargins() {
        val view = subsView ?: return
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val settings = com.chronosbvrx.visionplus.ui.settings.VisionSettings
        if (settings.subPositionOf(this) == com.chronosbvrx.visionplus.ui.settings.VisionSettings.SUB_POS_TOP) {
            params.gravity = Gravity.TOP
            if (params.topMargin != subsTopMargin()) {
                params.topMargin = subsTopMargin()
                view.layoutParams = params
            }
        } else {
            params.gravity = Gravity.BOTTOM
            if (params.bottomMargin != subsBottomMargin()) {
                params.bottomMargin = subsBottomMargin()
                view.layoutParams = params
            }
        }
        val wantSize = settings.subSizeSp(settings.subSizeOf(this), isTv)
        if (view.textSize != wantSize * resources.displayMetrics.scaledDensity) {
            view.textSize = wantSize
        }
    }

    /** El idioma de audio elegido en la serie se reaplica solo (sin re-elegir). */
    private fun maybeRestoreAudioLang() {
        val seriesKey = curSeries.ifBlank { curCode }
        if (seriesKey.isBlank() || declaredLanguages.isEmpty()) return
        val saved = com.chronosbvrx.visionplus.ui.settings.VisionSettings.audioLangOf(this, seriesKey)
        if (saved.isBlank()) return
        val v = playerView ?: return
        val idx = declaredLanguages.indexOfFirst {
            it.equals(saved, ignoreCase = true) || trackDisplayLang(it, null).equals(saved, ignoreCase = true)
        }
        if (idx < 0) return
        if (X4KTitan.selectAudio(v, declaredLanguages[idx], idx)) {
            audioBtn?.text = "\u25C9 " + languageLabel(declaredLanguages[idx], "Idioma")
        }
    }

    /** Lista del servicio para [cid] (en hilo aparte); al llegar reabre el aviso y restaura. */
    private fun loadSubs(cid: String, force: Boolean = false) {
        if (liveMode || cid.isBlank()) return
        subsLoadToken = cid
        Thread {
            val list = SubsStore.loadList(this, cid, force)
            runOnUiThread {
                if (subsLoadToken != cid || curCode != cid) return@runOnUiThread
                subsEntries = list
                subsLoaded = true
                Log.i(TAG, "subs list cid=$cid n=${list.size}")
                // La píldora de subtítulos aparece solo si el programa trae alguno.
                applyOptionPillVisibility()
                if (subsPickerWaiting && picking) {
                    subsPickerWaiting = false
                    dismissPicker()
                    showLanguagePicker(forSubtitle = true)
                }
                maybeRestoreSubs(cid)
            }
        }.start()
    }

    /** La elección guardada del título (o de su serie) se reaplica al abrir el capítulo. */
    private fun maybeRestoreSubs(cid: String) {
        if (subsCurrent != null) return
        val saved = SubsStore.selection(this, cid, curSeries)
        if (saved.isBlank()) return
        val entry = distinctLanguages(subsEntries)
            .firstOrNull { it.langKey == saved.trim().lowercase() } ?: return
        activateSubs(cid, entry)
    }

    /** Descarga (o reusa) el SRT elegido, lo parsea y enciende el overlay. */
    private fun activateSubs(cid: String, entry: SubEntry) {
        if (cid.isBlank()) return
        val label = languageName(entry.language).ifBlank { entry.language.trim().uppercase() }
            .ifBlank { "Subtítulos" }
        subsBtn?.text = "\u25A1 $label\u2026"
        Thread {
            val file = SubsStore.ensureSrtFile(this, cid, entry)
            val cues = file?.let { SubsStore.readCues(it) }.orEmpty()
            runOnUiThread {
                if (curCode != cid) return@runOnUiThread
                if (cues.isEmpty()) {
                    Log.w(TAG, "subtítulos sin cues ($cid · ${entry.language})")
                    subsBtn?.text = "\u25A1 No disponible"
                    return@runOnUiThread
                }
                subsCurrent = entry
                subsCues = cues
                subsView?.tag = null
                SubsStore.saveSelection(this, cid, curSeries, entry.langKey)
                subsBtn?.text = "\u25A1 $label"
                Log.i(TAG, "subs activo '${entry.language}' cues=${cues.size} overlay=${subsView != null}")
                startSubsTicker()
            }
        }.start()
    }

    /** «Desactivados»: apaga el overlay y lo recuerda para este título. */
    private fun disableSubs(save: Boolean = true) {
        stopSubsTicker()
        subsCurrent = null
        subsCues = emptyList()
        subsView?.apply {
            text = ""
            visibility = View.GONE
            tag = null
        }
        subsBtn?.text = "\u25A1 Subtítulos"
        if (save && curCode.isNotBlank()) SubsStore.saveSelection(this, curCode, curSeries, "")
    }

    /**
     * El tic de subtítulos: pinta el cue activo y agenda el próximo cambio (fin del cue o
     * inicio del siguiente, tope 1 s). Un fallo de pintado no mata el tic ni la app.
     */
    private val subsTick = object : Runnable {
        override fun run() {
            var delay: Long? = null
            try {
                delay = paintSubsOnce()
            } catch (e: Exception) {
                Log.w(TAG, "subs tic falló: ${e.message}")
                delay = 1000L
            }
            if (delay != null) handler.postDelayed(this, delay)
        }
    }

    /** Pinta el estado actual; null cuando no hay nada que mantener encendido. */
    private fun paintSubsOnce(): Long? {
        val view = subsView ?: run {
            Log.w(TAG, "subs overlay ausente")
            return null
        }
        if (subsCues.isEmpty()) return null
        val offset = com.chronosbvrx.visionplus.ui.settings.VisionSettings.subOffsetMsOf(this)
        val pos = com.chronosbvrx.visionplus.data.subs.lookupWithOffset(
            X4KTitan.positionMs().coerceAtLeast(0L), offset,
        )
        val cue = cueAt(subsCues, pos)
        if (cue == null) {
            if (view.visibility != View.GONE) {
                view.text = ""
                view.visibility = View.GONE
                view.tag = null
            }
        } else if (view.tag !== cue) {
            // Si el HTML del proveedor viniera roto, se pinta el texto plano: nunca en blanco.
            view.text = runCatching {
                Html.fromHtml(cue.text.replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY)
            }.getOrDefault(cue.text)
            view.tag = cue
            view.visibility = View.VISIBLE
        }
        return SubsStore.tickDelayMs(subsCues, pos)
    }

    private fun startSubsTicker() {
        handler.removeCallbacks(subsTick)
        handler.post(subsTick)
    }

    private fun stopSubsTicker() {
        handler.removeCallbacks(subsTick)
    }

    // ---------------------------------------------------------------- quality picker

    private fun showQualityPicker() {
        if (liveMode) return
        lastPickerSourceBtn = qualityBtn
        val v = playerView ?: return
        val distinctQualities = distinctQualityChoices(curMedias, { it.quality }, { it.name })

        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setOnClickListener { dismissPicker() }
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF121A2A.toInt())
                setStroke(dp(1), 0x332D7DF6.toInt())
            }
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setOnClickListener { }
            isFocusable = false
        }
        card.addView(TextView(this).apply {
            text = "Calidad y Resolución"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, dp(12), dp(4))
        })
        card.addView(TextView(this).apply {
            text = if (distinctQualities.size > 1) "Selecciona la resolución para la reproducción:" else "Resolución actual de la transmisión:"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12f
            setPadding(dp(12), 0, dp(12), dp(10))
        })

        val optionViews = ArrayList<View>()
        if (distinctQualities.size > 1) {
            distinctQualities.forEachIndexed { index, spec ->
                val isCurrent = (spec.name == currentMediaName) || (spec.quality == curQuality && currentMediaName.isBlank())
                val label = formatQualityLabel(spec.quality) + if (isCurrent) "  ·  Actual" else ""
                val option = TextView(this).apply {
                    text = label
                    setTextColor(if (isCurrent) blueBright else Color.WHITE)
                    textSize = 14f
                    typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    background = roundBackground(if (isCurrent) 0x2E2D7DF6 else surface, 10)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnClickListener {
                        dismissPicker()
                        if (!isCurrent) {
                            switchQuality(spec)
                        }
                    }
                }
                option.tvControl(if (isCurrent) 0x2E2D7DF6 else surface, 10)
                option.id = View.generateViewId()
                if (index > 0) {
                    val prev = optionViews[index - 1]
                    prev.nextFocusDownId = option.id
                    option.nextFocusUpId = prev.id
                }
                optionViews += option
                card.addView(option)
                if (index != distinctQualities.lastIndex) {
                    card.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)))
                }
            }
        } else {
            val singleSpec = curMedias.firstOrNull()
            val qName = formatQualityLabel(singleSpec?.quality.orEmpty())
            val singleView = TextView(this).apply {
                text = "$qName  ·  Máxima calidad disponible"
                setTextColor(blueBright)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                background = roundBackground(0x2E2D7DF6, 10)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            card.addView(singleView)
            card.addView(TextView(this).apply {
                text = "Este contenido se transmite en su calidad nativa óptima."
                setTextColor(0x80FFFFFF.toInt())
                textSize = 11f
                setPadding(dp(12), dp(8), dp(12), dp(12))
            })
            val closeBtn = pill("Cerrar") { dismissPicker() }.apply {
                tvControl(surface)
                id = View.generateViewId()
            }
            optionViews += closeBtn
            card.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
            })
        }

        if (optionViews.isNotEmpty()) {
            optionViews.first().nextFocusUpId = optionViews.first().id
            optionViews.last().nextFocusDownId = optionViews.last().id
        }

        dim.addView(card, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.72f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        root().addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        picker = dim
        picking = true
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
        if (isTv) optionViews.firstOrNull()?.let { first -> first.post { first.requestFocus() } }
    }

    private fun switchQuality(target: X4KTitan.MediaSpec) {
        val v = playerView ?: return
        val curPos = X4KTitan.positionMs().coerceAtLeast(0L)
        currentMediaName = target.name
        curQuality = target.quality
        qualityBtn?.text = formatQualityShort(target.quality)
        Toast.makeText(this, "Cambiando a ${formatQualityShort(target.quality)}…", Toast.LENGTH_SHORT).show()

        // 1. Intento directo con el método nativo ed.e.setMedia(mediaCode)
        val switched = X4KTitan.setMedia(v, target.name)
        if (!switched) {
            // 2. Fallback sin fricción: reordenar la lista de medias y reiniciar con seek transparente
            val reordered = ArrayList<X4KTitan.MediaSpec>()
            reordered.add(target)
            reordered.addAll(curMedias.filter { it.name != target.name })
            curMedias = reordered
            X4KTitan.play(v, "vod", curCode, curTitle, reordered)
            noteAppSeek()
            if (curPos > 0L) {
                handler.postDelayed({ playerView?.let { X4KTitan.seekTo(it, curPos) } }, 250L)
            }
        }
    }

    // ---------------------------------------------------------------- marcadores de salto

    /**
     * Marcadores del capítulo (intro / resumen / créditos / post-créditos) tal como llegaron
     * en el intent. El intro se repite en toda la temporada: la misma ventana sirve para los
     * capítulos siguientes de la misma fila; al cambiar de temporada se limpia (cambian).
     */
    private fun applyMarkers(json: String) {
        markIntroStartMs = -1L
        markIntroEndMs = -1L
        markRecapStartMs = -1L
        markRecapEndMs = -1L
        markCreditsStartMs = -1L
        markCreditsEndMs = -1L
        markPostCreditsStartMs = -1L
        markPostCreditsEndMs = -1L
        introSkipped = false
        recapSkipped = false
        creditsCardShown = false
        postCreditsShown = false
        if (json.isNotBlank()) {
            runCatching {
                val root = JSONObject(json)
                root.optJSONObject("intro")?.let { seg ->
                    if (!seg.isNull("s")) markIntroStartMs = seg.optLong("s", -1L)
                    markIntroEndMs = seg.optLong("e", -1L)
                }
                root.optJSONObject("recap")?.let { seg ->
                    if (!seg.isNull("s")) markRecapStartMs = seg.optLong("s", -1L)
                    markRecapEndMs = seg.optLong("e", -1L)
                }
                root.optJSONObject("credits")?.let { seg ->
                    markCreditsStartMs = seg.optLong("s", -1L)
                    markCreditsEndMs = seg.optLong("e", -1L)
                }
                root.optJSONObject("postCredits")?.let { seg ->
                    markPostCreditsStartMs = seg.optLong("s", -1L)
                    markPostCreditsEndMs = seg.optLong("e", -1L)
                }
            }
        }
        if (markIntroEndMs <= 0L) markIntroStartMs = -1L
        if (markRecapEndMs <= 0L) markRecapStartMs = -1L
        if (markCreditsStartMs <= 0L) markCreditsEndMs = -1L
        if (markPostCreditsStartMs <= 0L) markPostCreditsEndMs = -1L
        Log.i(
            TAG,
            "marcadores cap=$markEpisodeNumber ref=$markEpisodeRef " +
                "recap=$markRecapStartMs-$markRecapEndMs intro=$markIntroStartMs-$markIntroEndMs " +
                "credits=$markCreditsStartMs-$markCreditsEndMs post=$markPostCreditsStartMs",
        )
    }

    /** El video ya se dibujó y la pantalla de carga (logo/spinner) ya se desvaneció. */
    private fun videoIsReady(): Boolean =
        firstFrame && !loadingFading && loadingOverlay?.visibility != View.VISIBLE

    /** Foto de los campos del motor para aplicar la política pura (sin duplicar reglas). */
    private fun currentMarkers(): EpisodeMarkers = EpisodeMarkers(
        introStartMs = markIntroStartMs,
        introEndMs = markIntroEndMs,
        recapStartMs = markRecapStartMs,
        recapEndMs = markRecapEndMs,
        creditsStartMs = markCreditsStartMs,
        creditsEndMs = markCreditsEndMs,
        postCreditsStartMs = markPostCreditsStartMs,
        postCreditsEndMs = markPostCreditsEndMs,
    )

    /** Pide el plan del capítulo actual en segundo plano y lo aplica si sigue vigente. */
    private fun fetchMarkersForCurrent(gen: Int = playGen) {
        val ref = markEpisodeRef
        val ep = markEpisodeNumber
        if (ref.isBlank() || ep <= 0) return
        val token = ++markersFetchGen
        Thread {
            val json = runCatching {
                kotlinx.coroutines.runBlocking {
                    com.chronosbvrx.visionplus.data.markers.MarkersStore.fetchForPlayer(
                        context = this@X4KPlayerAty,
                        ref = ref,
                        ep = ep,
                        lengthSeconds = markEpisodeLengthSec,
                    )
                }
            }.getOrDefault("")
            if (json.isBlank()) return@Thread
            runOnUiThread {
                if (gen != playGen || token != markersFetchGen) return@runOnUiThread
                if (ref != markEpisodeRef || ep != markEpisodeNumber) return@runOnUiThread
                applyMarkers(json)
                prefetchNextMarkers(ref, ep)
            }
        }.start()
    }

    /** Deja listos los siguientes capítulos en la caché de disco (maratón sin esperas). */
    private fun prefetchNextMarkers(ref: String, fromEp: Int) {
        if (ref.isBlank() || fromEp <= 0) return
        Thread {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    com.chronosbvrx.visionplus.data.markers.MarkersStore.prefetch(
                        context = this@X4KPlayerAty,
                        ref = ref,
                        episodes = (fromEp + 1)..(fromEp + 2),
                        lengthSeconds = markEpisodeLengthSec,
                    )
                }
            }
        }.start()
    }

    /**
     * Prepara el plan del capítulo que empieza. Cada episodio tiene SUS tiempos (AoT T2 E1
     * no es E2); nunca se reutiliza la ventana del primero de la temporada. Mientras llega
     * el plan nuevo no se auto-salta nada: mejor no saltar que cortar contenido.
     */
    private fun beginMarkersForEpisode(series: String, epNumber: Int, lengthSec: Int = 0) {
        markEpisodeRef = com.chronosbvrx.visionplus.data.markers.normalizeMarkersRef(series)
        markEpisodeNumber = if (epNumber > 0) epNumber else 1
        markEpisodeLengthSec = lengthSec.coerceAtLeast(0)
        applyMarkers("")
        if (markEpisodeRef.isNotBlank()) fetchMarkersForCurrent()
    }

    /** El botón «Omitir intro / resumen» vive sobre los controles, sin taparlos (estilo Netflix). */
    private fun buildIntroPill(root: FrameLayout) {
        val pill = TextView(this).apply {
            text = "Omitir intro"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = roundBackground(0xCC0A0F18.toInt(), 12)
            id = View.generateViewId()
            visibility = View.GONE
            isFocusable = false
            setOnClickListener { if (canExecuteControlAction()) skipIntroNow() }
        }
        pill.withIcon(R.drawable.ic_vp_forward)
        pill.tvControl(0xCC0A0F18.toInt(), 12)
        pill.isFocusable = false
        introPillId = pill.id
        introPill = pill
        // Enlace de foco: «abajo» regresa a play/pausa (izquierda/derecha/arriba se quedan
        // en la píldora). El enlace de ENTRADA (barra → píldora) lo refresca updateIntroPill
        // con cada cambio de visibilidad.
        wireSkipPillFocus(null, pill, pillFocusable = false, playPauseId = playPauseBtn?.id ?: View.NO_ID)
        root.addView(
            pill,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                rightMargin = dp(24)
                bottomMargin = dp(if (isTv) 138 else 118)
            },
        )
        // Aviso discreto de escena post-créditos: no es botón (no roba foco), se muestra
        // solo unos segundos al entrar a los créditos y se esconde solo.
        val notice = TextView(this).apply {
            text = "Hay escena después de los créditos"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundBackground(0xCC0A0F18.toInt(), 10)
            visibility = View.GONE
            isFocusable = false
            isClickable = false
        }
        notice.withIcon(R.drawable.ic_vp_movie)
        postCreditsNotice = notice
        root.addView(
            notice,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(if (isTv) 150 else 128)
            },
        )
    }

    /** Qué salto ofrece la píldora ahora: el resumen primero (suele ir antes que el intro). */
    private fun skipTargetNow(pos: Long): Pair<String, Long>? {
        val markers = currentMarkers()
        if (!recapSkipped) {
            val recap = skipRecapWindow(markers)
            if (recap != null && pos in recap) return "Omitir resumen" to markers.recapEndMs
        }
        if (!introSkipped) {
            val intro = skipIntroWindow(markers)
            if (intro != null && pos in intro) return "Omitir intro" to markers.introEndMs
        }
        return null
    }

    private fun skipIntroNow() {
        val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
        val target = skipTargetNow(pos) ?: return
        if (target.first.contains("resumen")) recapSkipped = true else introSkipped = true
        performSeekBy(target.second - pos)
        updateIntroPill(target.second)
    }

    /** Visible durante la ventana del intro o del resumen; con controles visibles es un blanco del D-pad. */
    private fun updateIntroPill(pos: Long) {
        val pill = introPill ?: return
        val target = skipTargetNow(pos)
        // La píldora NO sale durante la pantalla de carga (logo + spinner) ni sobre el
        // pre-roll: solo cuando el motor ya pintó el video y el overlay se desvaneció.
        val videoReady = videoIsReady()
        val blocked = errorShown || picking || nextOverlay != null || introView != null || resumeOverlay != null
        val show = canShowSkipPill(
            hasTarget = target != null,
            buttonMode = skipIntroMode == com.chronosbvrx.visionplus.ui.settings.VisionSettings.SKIP_INTRO_BUTTON,
            videoReady = videoReady,
            blocked = blocked,
        )
        if (target != null) pill.text = target.first
        val focusable = show && isTv && controlsVisible
        val hadFocus = pill.hasFocus()
        pill.visibility = if (show) View.VISIBLE else View.GONE
        pill.isFocusable = focusable
        pill.isFocusableInTouchMode = false
        // D-pad: «arriba» desde la barra entra a la píldora mientras esté visible y enfocable;
        // oculta, «arriba» se queda en la barra. La píldora sale por «abajo» (play/pausa).
        wireSkipPillFocus(seekBar, pill, focusable, playPauseBtn?.id ?: View.NO_ID)
        if (!focusable && hadFocus) {
            pill.clearFocus()
            if (isTv && controlsVisible && !picking && nextOverlay == null && introView == null && !errorShown) {
                playPauseBtn?.requestFocus()
            }
        }
    }

    /**
     * Modo «Automático»: salta en cuanto la posición entra a la ventana, pero solo si el
     * video está reproduciendo (no pausado/buffering) y el plan tiene un inicio conocido.
     * Si el plan llegó tarde, la política pura evita cortar contenido.
     */
    private fun maybeAutoSkipIntro(pos: Long, playingNow: Boolean) {
        if (skipIntroMode != com.chronosbvrx.visionplus.ui.settings.VisionSettings.SKIP_INTRO_AUTO) return
        if (!videoIsReady() || scrubbing || !playingNow) return
        if (errorShown || picking || nextOverlay != null || introView != null || resumeOverlay != null) return
        val duration = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
        val target = autoSkipTarget(
            markers = currentMarkers(),
            positionMs = pos,
            durationMs = duration,
            playing = playingNow,
        ) ?: return
        if (!com.chronosbvrx.visionplus.data.markers.shouldApplyAutoSkip(
                target.kind, recapSkipped, introSkipped,
            )
        ) {
            return
        }
        if (target.kind == "recap") recapSkipped = true else introSkipped = true
        // +1 s: el seek cae en el keyframe anterior y, sin este margen, el siguiente tic
        // volvía a ver la posición dentro del intro y repetía el salto cada 500 ms.
        val destination = target.endMs + 1_000L
        Log.i(TAG, "auto-skip ${target.kind}: pos=$pos -> $destination")
        performSeekBy(destination - pos)
    }

    /** Aviso de post-créditos: unos segundos al entrar a los créditos (películas y series). */
    private fun maybeShowPostCredits(pos: Long) {
        val notice = postCreditsNotice ?: return
        if (postCreditsShown || !videoIsReady()) return
        val duration = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
        val markers = currentMarkers()
        if (!validPostCredits(markers, duration)) return
        if (pos < markers.postCreditsStartMs) return
        postCreditsShown = true
        notice.visibility = View.VISIBLE
        handler.postDelayed({ notice.visibility = View.GONE }, 9_000L)
    }

    /** Con marcador de créditos, la tarjeta de «siguiente capítulo» sale al empezar el cierre. */
    private fun maybeShowCreditsCard(pos: Long) {
        if (creditsCardShown || !videoIsReady() || scrubbing) return
        if (errorShown || picking || nextOverlay != null || endedShown || resumeOverlay != null) return
        val duration = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
        val start = creditsCardStartMs(currentMarkers(), duration) ?: return
        if (pos < start || !hasNextEpisode()) return
        creditsCardShown = true
        showNextCard(creditsMode = true)
    }

    // ---------------------------------------------------------------- siguiente capítulo

    private fun onEpisodeEnded() {
        if (liveMode || endedShown) return
        endedShown = true
        if (curSeries.isNotBlank()) {
            UserLibrary.markEpisodeWatched(this, curSeries, curEpIndex + 1)
        }
        if (curCode.isNotBlank()) {
            saveHistory(immediate = true, forceFinished = true)
        }
        // Si la tarjeta ya estaba esperando al final, ahora sí arranca la cuenta regresiva.
        if (nextVisible && nextTarget != null && nextPendingAtEnd) {
            nextPendingAtEnd = false
            if (nextAutoAdvance) {
                nextCountdown = 5
                tickNext()
            } else {
                nextCountdownView?.text = "Elige qué sigue"
            }
            return
        }
        showNextCard()
        // Película suelta: al terminar, un modal con títulos del mismo género/estilo.
        // Las series conservan su tarjeta de «siguiente capítulo».
        if (curSeries.isBlank()) showMovieSuggestions()
    }

    /**
     * «Siguiente capítulo» estilo premium: al terminar el episodio o —con marcador de
     * créditos— al empezar el cierre. Con el auto-avance apagado la tarjeta espera sin
     * cuenta regresiva (el switch de Ajustes manda de verdad).
     */
    private fun showNextCard(creditsMode: Boolean = false) {
        if (liveMode || nextVisible) return
        if (!isSeriesContent(epIds.size, seasonIds.size)) return
        val hasEp = curEpIndex + 1 < epIds.size
        val hasSeason = curSeries.isNotBlank() && curSeasonIndex + 1 < seasonIds.size
        if (!hasEp && !hasSeason) return
        nextCreditsMode = creditsMode
        nextAutoAdvance = com.chronosbvrx.visionplus.ui.settings.VisionSettings.autoPlayNextOf(this)
        val duration = resolveStableTotalDuration(declaredDurationMs, durationMs, 0L, liveMode)
        val postCredits = validPostCredits(currentMarkers(), duration)
        val fastCredits = com.chronosbvrx.visionplus.ui.settings.VisionSettings.nextOnCreditsOf(this)
        // Seguro: la tarjeta sale en créditos pero el siguiente espera al final (no corta
        // escenas post-créditos ni endings). Rápido: empieza al terminar los créditos.
        nextPendingAtEnd = creditsMode && (!fastCredits || postCredits)
        nextCountdown = when {
            !nextAutoAdvance -> 0
            nextPendingAtEnd -> 0
            creditsMode -> 10
            else -> 5
        }
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xB304070D.toInt())
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF0F1626.toInt())
                setStroke(dp(2), 0x802D7DF6.toInt())
            }
            setPadding(dp(28), dp(24), dp(28), dp(22))
            isFocusable = false
        }
        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        badgeRow.addView(TextView(this).apply {
            text = "SIGUIENTE CAPÍTULO"
            setTextColor(blueBright)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x2E2D7DF6)
                setStroke(dp(1), 0x662D7DF6)
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        if (curBaseTitle.isNotBlank()) {
            badgeRow.addView(TextView(this).apply {
                text = "  ·  $curBaseTitle"
                setTextColor(0xFF9BA8BD.toInt())
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        card.addView(badgeRow)
        if (markPostCreditsStartMs > 0L) {
            card.addView(TextView(this).apply {
                text = "Hay escena después de los créditos"
                setTextColor(blueBright)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(10))
                withIcon(R.drawable.ic_vp_movie, blueBright)
            })
        }
        // Cuerpo: arte del capítulo a la izquierda y el texto a la derecha (nombre
        // real + sinopsis + cuenta). Antes el póster iba arriba y la tarjeta se veía
        // vacía: el portal solo nombra «Serie T2_05» y ese texto se limpiaba a "".
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        // La miniatura al lado del texto solo en la tele (en el teléfono la tarjeta
        // es angosta: el nombre y la sinopsis llenan el ancho).
        val showArt = isTv
        val art = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        fun loadArt(url: String) {
            if (url.isBlank() || !showArt) return
            runCatching {
                val req = coil.request.ImageRequest.Builder(this)
                    .data(url)
                    .size(360)
                    .target(
                        onSuccess = { art.setImageDrawable(it); art.visibility = View.VISIBLE },
                        onError = { if (art.drawable == null) art.visibility = View.GONE },
                    )
                    .build()
                coil.Coil.imageLoader(this).enqueue(req)
            }
        }
        if (showArt) {
            body.addView(art, LinearLayout.LayoutParams(dp(110), dp(165)).apply { rightMargin = dp(18) })
            if (intentPoster.isNotBlank()) loadArt(intentPoster)
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val epTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        textCol.addView(epTitle)
        // Sinopsis corta (TMDB es-MX, guardada en el VPS): oculta hasta que llega.
        val synopsis = TextView(this).apply {
            setTextColor(0xFF9BA8BD.toInt())
            textSize = 13.5f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8))
            visibility = View.GONE
        }
        textCol.addView(synopsis)
        val countdown = TextView(this).apply {
            setTextColor(0xFF9BA8BD.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        }
        textCol.addView(countdown)
        body.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(body)
        nextCountdownView = countdown
        // Nombre real y sinopsis del capítulo: una petición servida por el VPS
        // (copia en RAM del espejo); si no hay metadatos, la tarjeta queda como
        // siempre — el enriquecimiento nunca puede empeorar lo que ya se ve.
        fun enrichNext(code: String, seasonRef: String, label: String) {
            if (code.isBlank() || seasonRef.isBlank()) return
            Thread {
                val info = kotlinx.coroutines.runBlocking {
                    com.chronosbvrx.visionplus.data.mirror.EpisodeMirror.info(seasonRef, code)
                } ?: return@Thread
                runOnUiThread {
                    if (!nextVisible || nextTarget?.code != code) return@runOnUiThread
                    if (info.title.isNotBlank()) epTitle.text = "$label · ${info.title}"
                    if (info.overview.isNotBlank()) {
                        synopsis.text = info.overview
                        synopsis.visibility = View.VISIBLE
                    }
                    if (showArt && intentPoster.isBlank() && info.still.isNotBlank()) {
                        // Sin póster de serie: la miniatura real del capítulo (16:9).
                        art.layoutParams = LinearLayout.LayoutParams(dp(110), dp(62))
                            .apply { rightMargin = dp(18) }
                        loadArt(info.still)
                    }
                }
            }.start()
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playBtn = TextView(this).apply {
            text = "Reproducir ahora"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(blue, 14)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { playNext(auto = false) }
            withIcon(R.drawable.ic_vp_play)
        }
        playBtn.tvControl(blue, 14)
        row.addView(playBtn)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val cancelBtn = TextView(this).apply {
            text = if (creditsMode) "Ver créditos" else "Quedarse aquí"
            setTextColor(0xFFF4F7FC.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(surface, 14)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { nextDismissed = true; hideNext() }
        }
        cancelBtn.tvControl(surface, 14)
        row.addView(cancelBtn)
        if (isTv) {
            playBtn.id = View.generateViewId()
            cancelBtn.id = View.generateViewId()
            playBtn.nextFocusRightId = cancelBtn.id
            cancelBtn.nextFocusLeftId = playBtn.id
            playBtn.nextFocusUpId = playBtn.id
            playBtn.nextFocusDownId = playBtn.id
            cancelBtn.nextFocusUpId = cancelBtn.id
            cancelBtn.nextFocusDownId = cancelBtn.id
            nextPlayBtn = playBtn
        }
        card.addView(row)
        dim.addView(
            card,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * (if (isTv) 0.72f else 0.9f)).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root().addView(
            dim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        nextOverlay = dim
        nextVisible = true
        nextDismissed = false
        setBackgroundFocusBlocked(true)
        if (isTv) playBtn.post { playBtn.requestFocus() }
        if (hasEp) {
            val nextIdx = curEpIndex + 1
            val rawName = epNames.getOrNull(nextIdx)?.trim().orEmpty()
            val cleanName = cleanEpisodeName(rawName, nextIdx, curBaseTitle)
            val displayLabel = "Capítulo ${nextIdx + 1}" + (if (cleanName.isNotBlank()) " · $cleanName" else "")
            val seasonPrefix = if (seasonIds.size > 1) " · ${seasonLabel(curSeasonIndex)}" else ""
            val playerTitle = "${curBaseTitle.ifBlank { curTitle }}$seasonPrefix · C${nextIdx + 1}"
            nextTarget = NextEp(
                code = epIds[nextIdx],
                seriesId = curSeries,
                displayTitle = displayLabel,
                playerTitle = playerTitle,
                epIndex = nextIdx,
                epNumber = epNumbers.getOrNull(nextIdx)?.takeIf { it > 0 } ?: (nextIdx + 1),
                seasonIndex = curSeasonIndex,
            )
            epTitle.text = displayLabel
            tickNext()
            enrichNext(
                epIds[nextIdx],
                seasonIds.getOrNull(curSeasonIndex)?.takeIf { it.isNotBlank() } ?: curSeries,
                "Capítulo ${nextIdx + 1}",
            )
        } else {
            val nextSeasonIdx = curSeasonIndex + 1
            val sLabel = seasonLabel(nextSeasonIdx)
            epTitle.text = sLabel
            countdown.text = "Preparando primer capítulo de la siguiente temporada…"
            Thread {
                val seasonId = seasonIds[nextSeasonIdx]
                val programs = kotlinx.coroutines.runBlocking {
                    X4KApi.episodeProgramsFor(this@X4KPlayerAty, seasonId)
                }
                val first = programs.firstOrNull()
                runOnUiThread {
                    if (!nextVisible) return@runOnUiThread
                    if (first == null) {
                        epTitle.text = "Fin de la temporada"
                        countdown.text = ""
                        return@runOnUiThread
                    }
                    val cleanName = cleanEpisodeName(first.name, 0, curBaseTitle)
                    val displayLabel = "$sLabel · Capítulo 1" + (if (cleanName.isNotBlank()) " · $cleanName" else "")
                    val playerTitle = "${curBaseTitle.ifBlank { curTitle }} · $sLabel · C1"
                    nextTarget = NextEp(
                        code = first.contentId,
                        seriesId = seasonId,
                        displayTitle = displayLabel,
                        playerTitle = playerTitle,
                        epIndex = 0,
                        epNumber = first.number.takeIf { it > 0 } ?: 1,
                        seasonIndex = nextSeasonIdx,
                        newEpIds = programs.map { it.contentId },
                        newEpNames = programs.map { it.name },
                        newEpNumbers = programs.map { it.number },
                    )
                    epTitle.text = displayLabel
                    tickNext()
                    enrichNext(first.contentId, seasonId, "$sLabel · Capítulo 1")
                }
            }.start()
        }
    }

    private fun tickNext() {
        handler.removeCallbacks(nextTick)
        val target = nextTarget
        if (!nextVisible || nextDismissed || target == null) return
        if (!nextAutoAdvance) {
            nextCountdownView?.text = "Elige qué sigue"
            return
        }
        if (nextPendingAtEnd) {
            nextCountdownView?.text = "Siguiente capítulo al terminar"
            return
        }
        nextCountdownView?.text = if (nextCreditsMode) {
            "Siguiente capítulo en ${nextCountdown} s…"
        } else {
            "Reproduciendo automáticamente en ${nextCountdown} s…"
        }
        if (nextCountdown <= 0) {
            playNext(auto = true)
            return
        }
        nextCountdown--
        handler.postDelayed(nextTick, 1000)
    }

    private val nextTick = Runnable { tickNext() }

    private fun playNext(auto: Boolean = false) {
        val target = nextTarget ?: return
        target.newEpIds?.let { epIds = ArrayList(it) }
        target.newEpNames?.let { epNames = ArrayList(it) }
        target.newEpNumbers?.let { epNumbers = ArrayList(it) }
        playEpisode(
            code = target.code,
            series = target.seriesId,
            title = target.playerTitle,
            epIdx = target.epIndex,
            seasonIdx = target.seasonIndex,
            epNumber = target.epNumber,
            auto = auto,
        )
    }

    private fun hideNext() {
        handler.removeCallbacks(nextTick)
        nextOverlay?.let { root().removeView(it) }
        nextOverlay = null
        nextVisible = false
        nextPendingAtEnd = false
        nextCountdownView = null
        nextTarget = null
        nextPlayBtn = null
        setBackgroundFocusBlocked(false)
        if (isTv && introView == null && !errorShown) {
            playPauseBtn?.post { playPauseBtn?.requestFocus() }
        }
    }

    // ---------------------------------------------------------------- sugerencias al terminar

    /**
     * Al terminar una película, «Más como esta»: títulos del mismo género/estilo.
     * Se calculan en segundo plano (el intent de una película suelta no lleva el
     * catálogo) y se pintan en un modal con foco D-pad. Sin resultados no se muestra.
     */
    private fun showMovieSuggestions() {
        if (liveMode || suggestVisible || movieSuggestShown) return
        if (errorShown || introView != null || resumeOverlay != null || picking || nextVisible) return
        if (isSeriesContent(epIds.size, seasonIds.size)) return
        val code = curCode
        val title = curTitle
        if (code.isBlank() || title.isBlank()) return
        movieSuggestShown = true
        val genre = runCatching { X4KApi.metaFor(this, code)?.optString("gen").orEmpty() }
            .getOrDefault("")
        val xtvRef = if (code.startsWith("xtv:")) code else "xtv:$code"
        val current = Film(
            id = xtvRef,
            title = title,
            year = 0,
            durationMin = (declaredDurationMs / 60_000L).toInt(),
            synopsis = "",
            posterUrl = intentPoster,
            videoUrl = xtvRef,
            sourceUrl = "xtv",
            kind = "movie",
        )
        handler.postDelayed({
            Thread {
                val picks = runCatching {
                    kotlinx.coroutines.runBlocking {
                        Related.forFilm(this@X4KPlayerAty, current, genre, limit = 8)
                    }
                }.getOrDefault(emptyList())
                runOnUiThread {
                    if (picks.isEmpty() || curCode != code || suggestVisible) return@runOnUiThread
                    // Si el usuario ya rebobinó y está viendo otra cosa, no lo interrumpas.
                    if (playerView?.let { X4KTitan.isPlaying(it) } == true) return@runOnUiThread
                    buildSuggestOverlay(picks)
                }
            }.start()
        }, 900L)
    }

    private fun buildSuggestOverlay(picks: List<Film>) {
        if (suggestVisible || picks.isEmpty()) return
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xB304070D.toInt())
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF0F1626.toInt())
                setStroke(dp(2), 0x802D7DF6.toInt())
            }
            setPadding(dp(26), dp(22), dp(26), dp(18))
        }
        card.addView(TextView(this).apply {
            text = "MÁS COMO ESTA"
            setTextColor(blueBright)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x2E2D7DF6)
                setStroke(dp(1), 0x662D7DF6)
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        card.addView(TextView(this).apply {
            text = "Películas del mismo género o estilo"
            setTextColor(0xFF9BA8BD.toInt())
            textSize = 13f
            setPadding(0, dp(8), 0, dp(12))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        val cards = ArrayList<View>()
        for (film in picks) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val art = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF1B2538.toInt())
            }
            item.addView(art, LinearLayout.LayoutParams(dp(116), dp(174)))
            item.addView(TextView(this).apply {
                text = film.title
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            }, LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT))
            val meta = listOfNotNull(
                film.year.takeIf { it > 0 }?.toString(),
                film.score.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.1f", it) },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                item.addView(TextView(this).apply {
                    text = meta
                    setTextColor(0xFF9BA8BD.toInt())
                    textSize = 11f
                    setPadding(0, dp(2), 0, 0)
                }, LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            item.isFocusable = true
            item.isFocusableInTouchMode = true
            item.setOnClickListener { playSuggestion(film) }
            item.tvControl(0x2E2D7DF6, 12)
            runCatching {
                val req = coil.request.ImageRequest.Builder(this)
                    .data(film.posterUrl)
                    .size(232)
                    .target(
                        onSuccess = { art.setImageDrawable(it) },
                        onError = { art.setImageResource(R.drawable.ic_vision_mark) },
                    )
                    .build()
                coil.Coil.imageLoader(this).enqueue(req)
            }
            row.addView(item)
            if (film !== picks.last()) row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            cards += item
        }
        val close = TextView(this).apply {
            text = "Cerrar"
            setTextColor(0xFFF4F7FC.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(surface, 14)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { hideSuggestions() }
        }
        close.tvControl(surface, 14)
        close.id = View.generateViewId()
        for (i in cards.indices) {
            val v = cards[i]
            v.id = View.generateViewId()
            if (i > 0) v.nextFocusLeftId = cards[i - 1].id
            if (i < cards.lastIndex) v.nextFocusRightId = cards[i + 1].id
            v.nextFocusDownId = close.id
        }
        close.nextFocusUpId = cards.firstOrNull()?.id ?: close.id
        close.nextFocusLeftId = close.id
        close.nextFocusRightId = close.id
        close.nextFocusDownId = close.id
        card.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        card.addView(
            close,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = dp(10)
            },
        )
        dim.addView(
            card,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * (if (isTv) 0.82f else 0.92f)).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root().addView(
            dim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        suggestOverlay = dim
        suggestVisible = true
        setBackgroundFocusBlocked(true)
        if (isTv) {
            val first = cards.firstOrNull()
            first?.post { first.requestFocus() }
        }
    }

    private fun hideSuggestions() {
        suggestOverlay?.let { root().removeView(it) }
        suggestOverlay = null
        suggestVisible = false
        setBackgroundFocusBlocked(false)
        if (isTv && introView == null && !errorShown) {
            playPauseBtn?.post { playPauseBtn?.requestFocus() }
        }
    }

    private fun playSuggestion(film: Film) {
        hideSuggestions()
        val ref = film.videoUrl
        if (!ref.startsWith("xtv:")) return
        Thread {
            val ok = runCatching {
                kotlinx.coroutines.runBlocking {
                    X4KApi.playVod(
                        context = this@X4KPlayerAty,
                        contentId = ref,
                        title = film.title,
                        posterUrl = film.posterUrl,
                        fallbackDurationMs = film.durationMin * 60_000L,
                    )
                }
            }.getOrDefault(false)
            if (!ok) {
                runOnUiThread {
                    Toast.makeText(this@X4KPlayerAty, "No se pudo abrir la sugerencia", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun hasNextEpisode(): Boolean {
        return hasNextEpisodeAvailable(
            isLive = liveMode,
            curEpIndex = curEpIndex,
            epCount = epIds.size,
            curSeries = curSeries,
            curSeasonIndex = curSeasonIndex,
            seasonCount = seasonIds.size,
        )
    }

    private fun updateNextButtonState() {
        val btn = nextEpBtn ?: return
        val hasNext = hasNextEpisode()
        btn.alpha = if (hasNext) 1f else 0.45f
        btn.text = if (hasNext && curEpIndex + 1 < epIds.size) {
            "Sig. C${curEpIndex + 2}"
        } else if (hasNext && curSeasonIndex + 1 < seasonIds.size) {
            "Sig. ${seasonLabel(curSeasonIndex + 1)}"
        } else {
            "Sig. episodio"
        }
    }

    private fun playNextEpisodeFromControls() {
        if (!hasNextEpisode()) {
            Toast.makeText(this, "Este es el último capítulo disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val hasEp = curEpIndex + 1 < epIds.size
        if (hasEp) {
            val nextIdx = curEpIndex + 1
            val seasonPrefix = if (seasonIds.size > 1) " · ${seasonLabel(curSeasonIndex)}" else ""
            val playerTitle = "${curBaseTitle.ifBlank { curTitle }}$seasonPrefix · C${nextIdx + 1}"
            playEpisode(
                code = epIds[nextIdx],
                series = curSeries,
                title = playerTitle,
                epIdx = nextIdx,
                seasonIdx = curSeasonIndex,
                epNumber = epNumbers.getOrNull(nextIdx)?.takeIf { it > 0 } ?: (nextIdx + 1),
                auto = false,
            )
        } else {
            val nextSeasonIdx = curSeasonIndex + 1
            if (nextSeasonIdx < seasonIds.size) {
                val seasonId = seasonIds[nextSeasonIdx]
                val sLabel = seasonLabel(nextSeasonIdx)
                Toast.makeText(this, "Cargando $sLabel…", Toast.LENGTH_SHORT).show()
                Thread {
                    val programs = kotlinx.coroutines.runBlocking {
                        X4KApi.episodeProgramsFor(this@X4KPlayerAty, seasonId)
                    }
                    val first = programs.firstOrNull()
                    runOnUiThread {
                        if (first == null) {
                            Toast.makeText(this@X4KPlayerAty, "Fin de la serie", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                        epIds = ArrayList(programs.map { it.contentId })
                        epNames = ArrayList(programs.map { it.name })
                        epNumbers = ArrayList(programs.map { it.number })
                        val playerTitle = "${curBaseTitle.ifBlank { curTitle }} · $sLabel · C1"
                        playEpisode(
                            code = first.contentId,
                            series = seasonId,
                            title = playerTitle,
                            epIdx = 0,
                            seasonIdx = nextSeasonIdx,
                            epNumber = first.number.takeIf { it > 0 } ?: 1,
                            auto = false,
                        )
                    }
                }.start()
            }
        }
    }

    /** Reproduce otro capítulo en el mismo motor/vista: la serie nunca sale del reproductor. */
    private fun playEpisode(
        code: String,
        series: String,
        title: String,
        epIdx: Int,
        seasonIdx: Int,
        epNumber: Int = 0,
        auto: Boolean = false,
    ) {
        val v = playerView ?: return
        hideNext()
        hideSuggestions()
        hideResumeDialog()
        movieSuggestShown = false
        nextDismissed = false
        endedShown = false
        finishedSaved = false

        // El capítulo que se deja conserva su minuto exacto: sin este cierre quedaba el último
        // tic (hasta 10 s atrás) y al saltar los créditos con «Sig.» el avance parecía perdido.
        flushOutgoingEpisode()

        // Detener inmediatamente la reproducción previa para que positionMs()
        // no retorne la posición del capítulo anterior mientras carga el nuevo.
        X4KTitan.stop(v)

        // Asignar de inmediato la nueva identidad del episodio antes del hilo de red
        curCode = code
        curTitle = title
        curSeries = series
        curEpIndex = epIdx
        curEpNumber = if (epNumber > 0) epNumber
        else epNumbers.getOrNull(epIdx)?.takeIf { it > 0 } ?: (epIdx + 1)
        curSeasonIndex = seasonIdx
        topTitle?.text = title
        // La espera muestra el capítulo que de verdad se abre (no el primero de la sesión).
        applyLoadingContent(title)
        // Marcadores del capítulo NUEVO (no los del anterior de la temporada). La
        // duración real del nuevo aún no se conoce aquí; el servidor usa su fallback.
        beginMarkersForEpisode(series, curEpNumber, 0)
        // Subtítulos: el capítulo nuevo es otro programId en el servicio — soltar los previos
        // (la lista y la elección se recuperan al arrancar el nuevo, más abajo).
        disableSubs(save = false)
        subsEntries = emptyList()
        subsLoaded = false
        // El capítulo nuevo aún no tiene su lista: la píldora de subtítulos se oculta hasta
        // saber si este programa trae alguno.
        applyOptionPillVisibility()

        // Reiniciar duración del capítulo previo para que el nuevo episodio tome su duración exacta.
        durationMs = -1L
        declaredDurationMs = 0L

        // Al seleccionar o pasar al siguiente capítulo, NUNCA preguntar "¿Reanudar o desde el principio?".
        // Comenzamos SIEMPRE desde el principio (00:00).
        resumeMs = 0L
        resumeApplied = true
        resumeAsked = true
        pendingAutoResume = false
        pendingResumeEntry = null

        // Limpiar cualquier registro histórico previo para arrancar limpio en 00:00
        PlaybackHistory.delete(this, code)

        loadingFading = false
        introPill?.visibility = View.GONE
        loadingOverlay?.alpha = 1f
        loadingOverlay?.visibility = View.VISIBLE
        firstFrame = false
        softRestarts = 0
        liveSignalSeen = false
        startedAtMs = System.currentTimeMillis()
        hideControls()

        Thread {
            var prep = kotlinx.coroutines.runBlocking {
                X4KApi.prepareVod(this@X4KPlayerAty, code, title, series, 0L)
            }
            if (prep == null) {
                Thread.sleep(450)
                prep = kotlinx.coroutines.runBlocking {
                    X4KApi.prepareVod(this@X4KPlayerAty, code, title, series, 0L)
                }
            }
            runOnUiThread {
                if (prep == null) {
                    showError("sin-medios")
                    return@runOnUiThread
                }
                var medias = runCatching { parseMedias(prep.mediasJson) }.getOrDefault(ArrayList())
                medias = ArrayList(
                    orderMediasByQuality(
                        medias,
                        { it.quality },
                        com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality,
                    ),
                )
                var played = medias.isNotEmpty() && X4KTitan.play(v, "vod", code, title, medias)
                if (!played) {
                    // Segundo intento si el primer cuadro falló
                    medias = ArrayList(
                        orderMediasByQuality(
                            runCatching { parseMedias(prep.mediasJson) }.getOrDefault(ArrayList()),
                            { it.quality },
                            com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality,
                        ),
                    )
                    played = medias.isNotEmpty() && X4KTitan.play(v, "vod", code, title, medias)
                }
                if (!played) {
                    showError("motor-no-arranca")
                    return@runOnUiThread
                }
                curMedias = ArrayList(medias)
                val chosenMedia = medias.firstOrNull()
                currentMediaName = chosenMedia?.name.orEmpty()
                curQuality = chosenMedia?.quality.orEmpty()
                qualityBtn?.text = formatQualityShort(curQuality)
                declaredLanguages = medias.first().audio
                    .split(',').map { it.trim() }.filter { it.isNotBlank() }
                applyOptionPillVisibility()
                maybeRestoreAudioLang()
                // Subtítulos del capítulo nuevo: lista propia del servicio para este programa.
                loadSubs(code)
                if (prep.durationMs > 0 && declaredDurationMs <= 0L) {
                    declaredDurationMs = prep.durationMs
                }
                topTitle?.text = title
                timeTotal?.text = if (declaredDurationMs > 0) fmt(declaredDurationMs) else "--:--"
                seekBar?.progress = 0
                timeNow?.text = "00:00"
                UserLibrary.markEpisodeWatched(this@X4KPlayerAty, series, epIdx + 1)
                updateNextButtonState()
                // El motor tarda un instante en exponer pistas: reintentar la restauración
                // del idioma una vez que ya hay audio elegible.
                handler.postDelayed({ maybeRestoreAudioLang() }, 1500L)

                v.alpha = 0f
                v.animate().alpha(1f).setStartDelay(250).setDuration(650).start()
                listOf(200L, 700L, 1500L, 3000L, 5000L).forEach { delay ->
                    handler.postDelayed({ playerView?.let { X4KTitan.setAspect(it, curAspect) } }, delay)
                }
            }
        }.start()
    }

    private fun root(): FrameLayout =
        contentRoot ?: window.decorView.findViewById(android.R.id.content)

    /** Reconstruye las medias del portal desde el JSON que viaja en el intent. */
    private fun parseMedias(mediasJson: String): ArrayList<X4KTitan.MediaSpec> {
        val medias = ArrayList<X4KTitan.MediaSpec>()
        val arr = JSONArray(mediasJson)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            medias += X4KTitan.MediaSpec(
                name = o.optString("n"),
                license = o.optString("l"),
                audio = o.optString("a"),
                quality = o.optString("q"),
                encodeFormat = o.optString("e"),
                videoFormat = o.optString("f"),
            )
        }
        return medias
    }

    // ---------------------------------------------------------------- historial

    /** Guarda el minuto (en vivo no cuenta). Con commit al salir: el proceso muere después. */
    private fun saveHistory(immediate: Boolean = false, forceFinished: Boolean = false) {
        if (liveMode) return
        val key = curCode
        if (key.isBlank()) return
        val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
        val rawPos = X4KTitan.positionMs().coerceAtLeast(0L)
        // Al llegar a los créditos/terminar no siempre el motor reporta el último ms:
        // se guarda como terminado con la duración conocida para que «Historial» lo
        // muestre como Visto, en vez de borrar el recuerdo.
        val pos = if (forceFinished && total > 0L) total else rawPos
        val finished = forceFinished || isFinished(pos, total)
        if (finished) {
            finishedSaved = true
        } else if (finishedSaved) {
            // Ya estaba terminado: el stop/exit posterior no puede devolverlo a «Seguir viendo».
            return
        }
        if (!finished && !shouldKeep(pos, total)) return
        if (pos <= 0L && total <= 0L) return
        val series = isSeriesContent(epIds.size, seasonIds.size)
        PlaybackHistory.save(
            this,
            ResumeEntry(
                key = key,
                title = curTitle.ifBlank { curBaseTitle },
                posterUrl = intentPoster,
                videoUrl = "",
                xtvCode = key,
                seriesId = if (series) curSeries else "",
                episodeIds = if (series) epIds.toList() else emptyList(),
                episodeIndex = if (series) curEpIndex else 0,
                seasonIds = if (series) seasonIds.toList() else emptyList(),
                seasonNames = if (series) seasonNames.toList() else emptyList(),
                seasonIndex = if (series) curSeasonIndex else 0,
                positionMs = pos,
                durationMs = total,
                updatedAt = System.currentTimeMillis(),
            ),
            immediate,
        )
    }

    /**
     * Cierra el capítulo actual antes de saltar a otro («Sig.», selector o automático): guarda
     * su minuto con commit —el tic periódico pudo quedar hasta 10 s atrás y al detener el motor
     * la posición se pierde— y, si el salto fue cerca del final, lo marca visto.
     */
    private fun flushOutgoingEpisode() {
        if (liveMode || curCode.isBlank()) return
        var atCredits = false
        runCatching {
            val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
            val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
            // Con marcador de créditos (o cerca del final) el capítulo ya terminó aunque los
            // créditos duren más de 2 min: la regla general sola no alcanzaría.
            atCredits = markCreditsStartMs > 0L && pos >= markCreditsStartMs - 5_000L
            if (curSeries.isNotBlank() && (atCredits || shouldCountWatchedOnSkip(pos, total))) {
                UserLibrary.markEpisodeWatched(this, curSeries, curEpIndex + 1)
            }
        }
        saveHistory(immediate = true, forceFinished = atCredits)
    }

    /** Al primer cuadro: reanuda lo pedido desde Inicio, o pregunta si quedó a medias. */
    private fun onFirstFrame() {
        // Cuánto se sintió desde el toque hasta el primer cuadro, y cuánto fue motor: en una
        // caja de TV es el dato que dice si el costo está en el portal, en el arranque del
        // motor o en la señal. Solo se reporta lo lento (>3 s) para no llenar la telemetría.
        runCatching {
            val firstFrameMs = System.currentTimeMillis() - startedAtMs
            if (startedAtMs > 0L && firstFrameMs > 3_000L) {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "PLAYER_START",
                    "firstFrameMs=$firstFrameMs bootMs=${com.chronosbvrx.visionplus.data.xuper.X4KTitan.lastBootMs} live=$liveMode",
                )
            }
        }
        // El motor pintó: la racha de fallos silenciosos vuelve a cero (auto-reparado).
        com.chronosbvrx.visionplus.data.xuper.PlayerRecovery.noteSuccess(this)
        if (liveMode) return
        // El motor ya expone sus pistas reales: la píldora de audio se queda solo si hay
        // más de una (y los subtítulos, si el servicio los dio).
        applyOptionPillVisibility()
        handler.postDelayed({ applyOptionPillVisibility() }, 1_500L)
        if (resumeMs > 0 && !resumeApplied) {
            val at = resumeMs
            resumeApplied = true
            resumeAsked = true
            resumeMs = 0
            playerView?.let { X4KTitan.seekTo(it, at) }
            noteAppSeek()
            Toast.makeText(this, "Continuando en " + formatResume(at), Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingAutoResume) {
            pendingAutoResume = false
            resumeAsked = true
            val entry = PlaybackHistory.get(this, curCode)
            if (entry != null && shouldKeep(entry.positionMs, entry.durationMs)) {
                playerView?.let { X4KTitan.seekTo(it, entry.positionMs) }
                noteAppSeek()
                Toast.makeText(this, "Continuando en " + formatResume(entry.positionMs), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!resumeAsked) {
            resumeAsked = true
            val entry = PlaybackHistory.get(this, curCode)
            val isSeries = isSeriesContent(epIds.size, seasonIds.size)
            if (entry != null && shouldPromptResume(
                    isLive = liveMode,
                    isSeries = isSeries,
                    hasEntry = true,
                    shouldKeepEntry = shouldKeep(entry.positionMs, entry.durationMs),
                    resumeExplicitlyRequested = false,
                )
            ) {
                if (com.chronosbvrx.visionplus.ui.settings.VisionSettings.autoResume) {
                    resumeMs = entry.positionMs
                    resumeApplied = true
                    playerView?.let { X4KTitan.seekTo(it, entry.positionMs) }
                    noteAppSeek()
                    Toast.makeText(this, "Continuando en " + formatResume(entry.positionMs), Toast.LENGTH_SHORT).show()
                } else {
                    pendingResumeEntry = entry
                    playerView?.let { X4KTitan.pause(it) }
                    showResumeDialog(entry)
                }
            }
        }
    }

    /** "¿Continuar viendo?" al reabrir a medias: continuar o desde el inicio, con mando. */
    private fun showResumeDialog(entry: ResumeEntry) {
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF121A2A.toInt())
            }
            setPadding(dp(26), dp(22), dp(26), dp(20))
            isFocusable = false
        }
        card.addView(TextView(this).apply {
            text = entry.title
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.addView(TextView(this).apply {
            text = "Continuar en " + formatResume(entry.positionMs) +
                (if (entry.durationMs > 0) " de " + formatResume(entry.durationMs) else "")
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
            setPadding(0, 0, 0, dp(14))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val goBtn = TextView(this).apply {
            text = "Continuar"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(blue)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { acceptResume() }
            withIcon(R.drawable.ic_vp_play)
        }
        goBtn.tvControl()
        row.addView(goBtn)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val startBtn = TextView(this).apply {
            text = "Desde el inicio"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { restartResume() }
        }
        startBtn.tvControl()
        row.addView(startBtn)
        if (isTv) {
            goBtn.id = View.generateViewId()
            startBtn.id = View.generateViewId()
            goBtn.nextFocusRightId = startBtn.id
            startBtn.nextFocusLeftId = goBtn.id
            goBtn.nextFocusUpId = goBtn.id
            goBtn.nextFocusDownId = goBtn.id
            startBtn.nextFocusUpId = startBtn.id
            startBtn.nextFocusDownId = startBtn.id
        }
        card.addView(row)
        dim.addView(
            card,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root().addView(
            dim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        resumeOverlay = dim
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
        if (isTv) goBtn.post { goBtn.requestFocus() }
    }

    private fun acceptResume() {
        val entry = pendingResumeEntry
        hideResumeDialog()
        playerView?.let {
            if (entry != null) X4KTitan.seekTo(it, entry.positionMs)
            X4KTitan.resume(it)
        }
        noteAppSeek()
        scheduleHide()
    }

    private fun restartResume() {
        hideResumeDialog()
        playerView?.let {
            X4KTitan.seekTo(it, 0)
            X4KTitan.resume(it)
        }
        noteAppSeek()
        scheduleHide()
    }

    private fun hideResumeDialog() {
        resumeOverlay?.let { root().removeView(it) }
        resumeOverlay = null
        pendingResumeEntry = null
        setBackgroundFocusBlocked(false)
        if (isTv && introView == null && !errorShown) {
            playPauseBtn?.post { playPauseBtn?.requestFocus() }
        }
    }

    // ---------------------------------------------------------------- live zapping

    private fun findChannelIndex(code: String): Int {
        val arr = liveChannels ?: return -1
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("channelCode") == code) return i
        }
        return -1
    }

    /** The channel's best free address, as the portal hands them out. */
    private fun channelMedias(ch: org.json.JSONObject): ArrayList<X4KTitan.MediaSpec> {
        val addrs = ch.optJSONArray("liveAddressList")
        var best: org.json.JSONObject? = null
        if (addrs != null) {
            for (j in 0 until addrs.length()) {
                val a = addrs.optJSONObject(j) ?: continue
                if (a.optString("tag") != "free") continue
                if (best == null || a.optInt("quality") >= best.optInt("quality")) best = a
            }
        }
        val out = ArrayList<X4KTitan.MediaSpec>()
        best?.let { b ->
            out += X4KTitan.MediaSpec(
                name = b.optString("playCode"),
                license = b.optString("license"),
                quality = b.optString("quality"),
            )
        }
        return out
    }

    /**
     * Switches the live channel in place: the very same view and engine take the new stream
     * (the provider's own zapping), so it starts in a second — no reload, no rebuild.
     */
    private fun switchChannel(index: Int) {
        val v = playerView ?: return
        val arr = liveChannels ?: return
        if (arr.length() == 0) return
        val targetIndex = when {
            index < 0 -> arr.length() - 1
            index >= arr.length() -> 0
            else -> index
        }
        val ch = arr.optJSONObject(targetIndex) ?: return
        val code = ch.optString("channelCode")
        val name = X4KApi.displayChannelName(code, ch.optString("name"))
        val medias = channelMedias(ch)
        if (medias.isEmpty()) return
        Log.i(TAG, "zap -> $name ($code) [$targetIndex]")
        liveIndex = targetIndex
        curCode = code
        curTitle = name
        topTitle?.text = name
        applyLoadingContent(name)
        epgLine?.text = ""
        updateFavButtonState()
        updateLiveBanner()
        handler.removeCallbacks(hideRunnable)
        showControlsBriefly()
        loadingFading = false
        introPill?.visibility = View.GONE
        loadingOverlay?.alpha = 1f
        loadingOverlay?.visibility = View.VISIBLE
        firstFrame = false
        softRestarts = 0
        liveSignalSeen = false
        startedAtMs = System.currentTimeMillis()
        if (X4KTitan.play(v, "live", code, name, medias)) {
            listOf(400L, 1200L, 2500L).forEach { d ->
                handler.postDelayed({ playerView?.let { X4KTitan.setAspect(it, 0) } }, d)
            }
            scheduleHide()
        }
        loadEpg(code)
    }

    private fun currentChannelCode(): String {
        val arr = liveChannels ?: return curCode
        val ch = arr.optJSONObject(liveIndex.coerceAtLeast(0)) ?: return curCode
        return ch.optString("channelCode").ifBlank { curCode }
    }

    private fun currentChannelName(): String {
        val arr = liveChannels ?: return X4KApi.displayChannelName(curCode, curTitle)
        val ch = arr.optJSONObject(liveIndex.coerceAtLeast(0)) ?: return X4KApi.displayChannelName(curCode, curTitle)
        val raw = ch.optString("name").ifBlank { curTitle }
        return X4KApi.displayChannelName(ch.optString("channelCode").ifBlank { curCode }, raw)
    }

    private fun currentChannelNumBadge(): String {
        val arr = liveChannels ?: return "CH 1"
        val ch = arr.optJSONObject(liveIndex.coerceAtLeast(0))
        val numStr = ch?.optString("channelNumber")?.trim().orEmpty()
        return if (numStr.isNotBlank() && numStr != "0") {
            "CH $numStr"
        } else {
            "CH ${(liveIndex + 1).coerceAtLeast(1)}"
        }
    }

    private fun currentChannelGroupTag(): String {
        if (liveGroupTitle.isNotBlank()) return liveGroupTitle.uppercase(java.util.Locale.getDefault())
        val name = currentChannelName()
        val genre = LiveTaxonomy.genreFor(name)
        return genre.ifBlank { "EN VIVO" }.uppercase(java.util.Locale.getDefault())
    }

    private fun updateLiveBanner() {
        if (!liveMode) return
        liveChannelNumView?.text = currentChannelNumBadge()
        liveChannelTitleView?.text = currentChannelName()
        liveChannelGroupView?.text = currentChannelGroupTag()
        liveEpgNowView?.text = "Cargando programación…"
        liveEpgNextView?.text = ""
        liveEpgProgView?.progress = 0
    }

    private fun isCurrentFav(): Boolean {
        val code = currentChannelCode()
        if (code.isBlank()) return false
        return LiveFavorites.has(this, "xtvlive:$code")
    }

    private fun updateFavButtonState() {
        val fav = favBtn ?: return
        val isFav = isCurrentFav()
        fav.text = if (isFav) "Favorito" else "Añadir a favoritos"
        fav.withIcon(
            if (isFav) R.drawable.ic_vp_star else R.drawable.ic_vp_star_border,
            if (isFav) 0xFFFFD700.toInt() else Color.WHITE,
        )
        fav.setTextColor(if (isFav) 0xFFFFD700.toInt() else Color.WHITE)
    }

    private fun toggleCurrentFav() {
        val code = currentChannelCode()
        if (code.isBlank()) return
        val isNowFav = LiveFavorites.toggle(this, "xtvlive:$code")
        updateFavButtonState()
        val chName = currentChannelName()
        Toast.makeText(
            this,
            if (isNowFav) "«$chName» añadido a Favoritos" else "«$chName» quitado de Favoritos",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** "Ahora: … (HH:MM–HH:MM) · Después: …" from the VPS EPG guide. */
    private fun loadEpg(code: String) {
        val chName = currentChannelName()
        currentEpgItems = emptyList()
        runOnUiThread {
            epgLine?.text = if (liveGroupTitle.isNotBlank()) "Bloque: $liveGroupTitle" else ""
            liveEpgNowView?.text = "Cargando guía..."
            liveEpgNextView?.text = ""
            liveEpgProgView?.progress = 0
        }
        Thread {
            val items = kotlinx.coroutines.runBlocking { X4KApi.epgFor(applicationContext, code, chName) }
            currentEpgItems = items
            if (items.isEmpty()) {
                runOnUiThread {
                    epgLine?.text = if (liveGroupTitle.isNotBlank()) "Bloque: $liveGroupTitle" else ""
                    liveEpgNowView?.text = "En emisión continua"
                    liveEpgNextView?.text = ""
                    liveEpgProgView?.progress = 0
                }
                return@Thread
            }
            val now = System.currentTimeMillis()
            val current = items.firstOrNull { it.startMs in 1..now && now < it.endMs }
            val next = items.firstOrNull { it.startMs > now }
            val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val parts = ArrayList<String>()
            current?.let { parts += "Ahora: ${it.name}  (${hm.format(java.util.Date(it.startMs))}–${hm.format(java.util.Date(it.endMs))})" }
            next?.let { parts += "Después: ${it.name}  ${hm.format(java.util.Date(it.startMs))}" }

            val pct = if (current != null && current.endMs > current.startMs) {
                val total = current.endMs - current.startMs
                val elapsed = now - current.startMs
                ((elapsed * 100) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }

            runOnUiThread {
                epgLine?.text = parts.joinToString("   ·   ").ifBlank {
                    if (liveGroupTitle.isNotBlank()) "Bloque: $liveGroupTitle" else ""
                }
                if (current != null) {
                    liveEpgNowView?.text = "${current.name} (${hm.format(java.util.Date(current.startMs))} - ${hm.format(java.util.Date(current.endMs))})"
                } else {
                    liveEpgNowView?.text = "En emisión continua"
                }
                if (next != null) {
                    liveEpgNextView?.text = "${next.name} (${hm.format(java.util.Date(next.startMs))})"
                } else {
                    liveEpgNextView?.text = ""
                }
                liveEpgProgView?.progress = pct
            }
        }.start()
    }

    /** The live line: picking a channel switches right there, no reload. */
    private fun showChannelPicker() {
        val arr = liveChannels ?: return
        Log.i(TAG, "picker: ${arr.length()} canales")
        if (arr.length() == 0) return
        lastPickerSourceBtn = channelsBtn
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setOnClickListener { dismissPicker() }
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF121A2A.toInt())
            }
            setPadding(dp(6), dp(12), dp(6), dp(10))
            setOnClickListener { }
            isFocusable = false
        }
        card.addView(TextView(this).apply {
            text = if (liveGroupTitle.isNotBlank()) "Canales · $liveGroupTitle" else "Canales en vivo"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), 0, dp(14), dp(10))
        })
        // A recycling list: a thousand channels scroll with no allocation storm, remote-ready.
        val labels = ArrayList<String>()
        val favFlags = ArrayList<Boolean>()
        val targets = ArrayList<Int>()
        for (i in 0 until arr.length()) {
            val ch = arr.optJSONObject(i) ?: continue
            val num = ch.optString("channelNumber")
            val cCode = ch.optString("channelCode")
            val name = X4KApi.displayChannelName(cCode, ch.optString("name"))
            val isFav = LiveFavorites.has(this, "xtvlive:$cCode")
            labels += if (num.isBlank()) name else "$num  ·  $name"
            favFlags += isFav
            targets += i
        }
        val listView = android.widget.ListView(this).apply {
            divider = null
            dividerHeight = 0
            choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            // La fila enfocada lleva aro blanco (relleno transparente para no tapar
            // el texto): sin selector el mando navega a ciegas.
            selector = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.WHITE)
            }
            adapter = object : android.widget.ArrayAdapter<String>(
                this@X4KPlayerAty, android.R.layout.simple_list_item_1, labels,
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(if (targets[position] == liveIndex) blueBright else Color.WHITE)
                    view.textSize = 14f
                    view.setPadding(dp(16), dp(12), dp(16), dp(12))
                    // El favorito lleva estrella dibujada, no un glifo en el texto.
                    if (favFlags.getOrElse(position) { false }) {
                        view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_vp_star, 0, 0, 0)
                        view.compoundDrawableTintList =
                            android.content.res.ColorStateList.valueOf(0xFFFFD700.toInt())
                        view.compoundDrawablePadding = dp(6)
                    } else {
                        view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                    }
                    return view
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                dismissPicker()
                switchChannel(targets[position])
            }
            setSelection(liveIndex.coerceAtLeast(0))
        }
        card.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        listView.post { listView.requestFocus() }
        dim.addView(card, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            (resources.displayMetrics.heightPixels * 0.7f).toInt(),
            Gravity.CENTER,
        ))
        root().addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        picker = dim
        picking = true
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
    }

    /** Muestra tarjeta de información y programación del canal sintonizado. */
    private fun showChannelInfo() {
        dismissPicker()
        val arr = liveChannels ?: return
        val ch = arr.optJSONObject(liveIndex.coerceAtLeast(0)) ?: return
        lastPickerSourceBtn = infoBtn
        val name = X4KApi.displayChannelName(ch.optString("channelCode"), ch.optString("name"))
        val num = ch.optString("channelNumber")
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setOnClickListener { dismissPicker() }
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF121A2A.toInt())
            }
            setPadding(dp(22), dp(20), dp(22), dp(20))
            setOnClickListener { }
            isFocusable = false
        }
        card.addView(TextView(this).apply {
            text = if (num.isNotBlank()) "$num · $name" else name
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        })
        val meta = ArrayList<String>()
        if (liveGroupTitle.isNotBlank()) meta += "Bloque: $liveGroupTitle"
        val chGroup = LiveTaxonomy.genreFor(name)
        if (chGroup.isNotBlank()) meta += chGroup
        val chCountry = LiveTaxonomy.countryFor(name)
        if (chCountry.isNotBlank()) meta += chCountry
        if (meta.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = meta.joinToString("  •  ")
                setTextColor(0x99FFFFFF.toInt())
                textSize = 12f
                setPadding(0, 0, 0, dp(12))
            })
        }

        val items = currentEpgItems
        val now = System.currentTimeMillis()
        val current = items.firstOrNull { it.startMs in 1..now && now < it.endMs }
        val upcoming = items.filter { it.startMs > now }.take(3)
        val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        if (current != null || upcoming.isNotEmpty()) {
            if (current != null) {
                card.addView(TextView(this).apply {
                    text = "AHORA: ${current.name}  (${hm.format(java.util.Date(current.startMs))} - ${hm.format(java.util.Date(current.endMs))})"
                    setTextColor(blueBright)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(6), 0, dp(4))
                })
                if (current.description.isNotBlank()) {
                    card.addView(TextView(this).apply {
                        text = current.description
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        maxLines = 5
                        setPadding(0, 0, 0, dp(8))
                    })
                }
            }
            if (upcoming.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = "A CONTINUACIÓN"
                    setTextColor(0x99FFFFFF.toInt())
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(8), 0, dp(4))
                })
                for (prog in upcoming) {
                    card.addView(TextView(this).apply {
                        text = "• ${prog.name}  (${hm.format(java.util.Date(prog.startMs))} - ${hm.format(java.util.Date(prog.endMs))})"
                        setTextColor(0xEEFFFFFF.toInt())
                        textSize = 13f
                        setPadding(0, dp(2), 0, dp(2))
                    })
                    if (prog.description.isNotBlank()) {
                        card.addView(TextView(this).apply {
                            text = prog.description
                            setTextColor(0x80FFFFFF.toInt())
                            textSize = 11f
                            maxLines = 2
                            setPadding(dp(12), 0, 0, dp(4))
                        })
                    }
                }
            }
        } else {
            card.addView(TextView(this).apply {
                text = "Guía de programación no disponible para este canal."
                setTextColor(0x80FFFFFF.toInt())
                textSize = 13f
                setPadding(0, dp(12), 0, dp(12))
            })
        }

        val closeBtn = pill("Cerrar") { dismissPicker() }.apply {
            tvControl(surface)
            if (isTv) {
                id = View.generateViewId()
                nextFocusUpId = id
                nextFocusDownId = id
                nextFocusLeftId = id
                nextFocusRightId = id
            }
        }
        card.addView(closeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(16)
            gravity = Gravity.END
        })
        closeBtn.post { closeBtn.requestFocus() }

        dim.addView(card, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.72f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        root().addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        picker = dim
        picking = true
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
    }

    // ---------------------------------------------------------------- episode picker

    /** Etiqueta de temporada del grupo ("T1", "Completa" ya vienen formateadas). */
    private fun seasonLabel(idx: Int): String =
        seasonNames.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "T${idx + 1}"

    /** Capítulos (y temporadas) sin salir del reproductor: solo series reales (2+ cap. o temp.). */
    private fun showEpisodePicker() {
        if (liveMode) return
        if (!isSeriesContent(epIds.size, seasonIds.size)) return
        lastPickerSourceBtn = episodesBtn
        openEpisodePicker()
    }

    private fun openEpisodePicker() {
        dismissPicker()
        val dim = ModalDimLayout(this).apply {
            setBackgroundColor(0xD904070D.toInt())
            setOnClickListener { dismissPicker() }
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF0F1626.toInt())
                setStroke(dp(2), 0x662D7DF6)
            }
            setPadding(dp(18), dp(18), dp(18), dp(14))
            setOnClickListener { }
            isFocusable = false
        }

        // Cabecera Pro Vision+: Badge + Título + Subtítulo + Botón Cerrar
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), dp(12))
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val overlineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        overlineRow.addView(TextView(this).apply {
            text = "SERIE · VISION+"
            setTextColor(blueBright)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(0x2E2D7DF6)
                setStroke(dp(1), 0x662D7DF6)
            }
            setPadding(dp(8), dp(2), dp(8), dp(2))
        })
        val watchedCount = (1..epIds.size).count { UserLibrary.isEpisodeWatched(this, curSeries, it) }
        overlineRow.addView(TextView(this).apply {
            text = "   ${seasonLabel(curSeasonIndex)} · ${epIds.size} capítulos" +
                (if (watchedCount > 0) " · $watchedCount vistos" else "")
            setTextColor(0xFF9BA8BD.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(overlineRow)
        titleCol.addView(TextView(this).apply {
            text = curBaseTitle.ifBlank { curTitle }
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        headerRow.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = TextView(this).apply {
            text = "Cerrar"
            setTextColor(0xFFF4F7FC.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(surface, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { dismissPicker() }
            withIcon(R.drawable.ic_vp_close)
        }
        closeBtn.tvControl(surface, 14)
        val closeId = View.generateViewId().also { closeBtn.id = it }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Barra de avance de la temporada: cuánto llevas visto, de un vistazo.
        if (epIds.isNotEmpty() && watchedCount > 0) {
            val frac = (watchedCount.toFloat() / epIds.size).coerceIn(0.02f, 1f)
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(6), 0, dp(6), dp(12))
            }
            bar.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(2).toFloat()
                        setColor(blueBright)
                    }
                },
                LinearLayout.LayoutParams(0, dp(3), frac),
            )
            bar.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(2).toFloat()
                        setColor(0x22FFFFFF)
                    }
                },
                LinearLayout.LayoutParams(0, dp(3), 1f - frac),
            )
            card.addView(bar)
        }

        // Barra selectora de temporadas con acabado Pro
        val seasonViews = ArrayList<View>()
        if (seasonIds.size > 1) {
            val seasonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(2), dp(6), dp(12))
            }
            for (idx in seasonIds.indices) {
                val current = idx == curSeasonIndex
                val btn = TextView(this).apply {
                    text = seasonLabel(idx)
                    setTextColor(if (current) Color.WHITE else 0xFFD5E1F2.toInt())
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(if (current) blue else surface)
                        setStroke(dp(1), if (current) blueBright else 0x336BA6FF)
                    }
                    setPadding(dp(16), dp(9), dp(16), dp(9))
                    setOnClickListener {
                        if (!current) switchSeason(idx)
                    }
                }
                btn.tvControl(if (current) blue else surface, 14)
                btn.id = View.generateViewId()
                seasonViews += btn
                seasonRow.addView(btn)
                if (idx != seasonIds.lastIndex) {
                    seasonRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                }
            }
            for (i in seasonViews.indices) {
                seasonViews[i].nextFocusUpId = closeId
                if (i > 0) seasonViews[i].nextFocusLeftId = seasonViews[i - 1].id
                else seasonViews[i].nextFocusLeftId = seasonViews[i].id
                if (i < seasonViews.lastIndex) seasonViews[i].nextFocusRightId = seasonViews[i + 1].id
                else seasonViews[i].nextFocusRightId = closeId
            }
            closeBtn.nextFocusDownId = seasonViews.getOrElse(curSeasonIndex.coerceIn(0, seasonViews.lastIndex)) { seasonViews.first() }.id
            closeBtn.nextFocusLeftId = seasonViews.last().id
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(seasonRow)
            }
            card.addView(scroll)
        }

        // Separador sutil azulado
        card.addView(
            View(this).apply { setBackgroundColor(0x332D7DF6) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(8)
            },
        )

        if (epIds.isEmpty()) {
            val loadingRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(18), dp(16), dp(18))
            }
            loadingRow.addView(
                ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(blueBright)
                },
                LinearLayout.LayoutParams(dp(20), dp(20)),
            )
            loadingRow.addView(TextView(this).apply {
                text = "Preparando capítulos…"
                setTextColor(0xFF9BA8BD.toInt())
                textSize = 14f
                setPadding(dp(10), 0, 0, 0)
            })
            card.addView(loadingRow)
        } else {
            val listView = android.widget.ListView(this).apply {
                divider = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                dividerHeight = dp(8)
                choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
                setPadding(dp(4), dp(4), dp(4), dp(4))
                clipToPadding = false
                selector = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0x1F2D7DF6)
                    setStroke(dp(2), Color.WHITE)
                }
                adapter = object : android.widget.BaseAdapter() {
                    override fun getCount(): Int = epIds.size
                    override fun getItem(position: Int): Any = epIds[position]
                    override fun getItemId(position: Int): Long = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val isCurrent = position == curEpIndex
                        val isWatched = UserLibrary.isEpisodeWatched(this@X4KPlayerAty, curSeries, position + 1)
                        val rawName = epNames.getOrNull(position)?.trim().orEmpty()
                        val cleanName = episodeRowLabel(rawName, position + 1, curBaseTitle)

                        val rowLayout = LinearLayout(this@X4KPlayerAty).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = GradientDrawable().apply {
                                cornerRadius = dp(16).toFloat()
                                setColor(
                                    when {
                                        isCurrent -> 0x382D7DF6
                                        isWatched -> 0xFF131D31.toInt()
                                        else -> 0xFF162035.toInt()
                                    },
                                )
                                setStroke(
                                    dp(1),
                                    when {
                                        isCurrent -> blueBright
                                        isWatched -> 0x442D7DF6
                                        else -> 0x22FFFFFF
                                    },
                                )
                            }
                            setPadding(dp(11), dp(12), dp(14), dp(12))
                        }

                        // Acento izquierdo de la fila en curso (lenguaje de broadcast).
                        if (isCurrent) {
                            rowLayout.addView(
                                View(this@X4KPlayerAty).apply {
                                    background = GradientDrawable().apply {
                                        cornerRadius = dp(2).toFloat()
                                        setColor(blueBright)
                                    }
                                },
                                LinearLayout.LayoutParams(dp(3), dp(40)).apply { rightMargin = dp(9) },
                            )
                        }

                        // Insignia numérica del capítulo ("C1", "C2", ...)
                        val numBadge = TextView(this@X4KPlayerAty).apply {
                            text = "C${position + 1}"
                            setTextColor(Color.WHITE)
                            textSize = 13f
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            minWidth = dp(44)
                            background = GradientDrawable().apply {
                                cornerRadius = dp(11).toFloat()
                                setColor(
                                    when {
                                        isCurrent -> blue
                                        isWatched -> 0xFF223454.toInt()
                                        else -> 0xFF1F2B42.toInt()
                                    },
                                )
                            }
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                        }
                        rowLayout.addView(numBadge)

                        // Columna central: título real y estado (sin textos de relleno).
                        val textCol = LinearLayout(this@X4KPlayerAty).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(14), 0, dp(12), 0)
                        }
                        textCol.addView(TextView(this@X4KPlayerAty).apply {
                            text = cleanName
                            setTextColor(if (isCurrent) Color.WHITE else 0xFFF4F7FC.toInt())
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        })
                        if (isCurrent || isWatched) {
                            textCol.addView(TextView(this@X4KPlayerAty).apply {
                                text = if (isCurrent) "En reproducción" else "Visto"
                                setTextColor(if (isCurrent) blueBright else 0xFF7AD9A8.toInt())
                                textSize = 12f
                                setPadding(0, dp(2), 0, 0)
                                withIcon(
                                    if (isCurrent) R.drawable.ic_vp_play else R.drawable.ic_vp_check_circle,
                                    if (isCurrent) blueBright else 0xFF7AD9A8.toInt(),
                                )
                            })
                        }
                        rowLayout.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                        // Píldora derecha de acción, con su icono.
                        val actionPill = TextView(this@X4KPlayerAty).apply {
                            text = when {
                                isCurrent -> "Viendo"
                                isWatched -> "Repetir"
                                else -> "Reproducir"
                            }
                            setTextColor(if (isCurrent) Color.WHITE else blueBright)
                            textSize = 12f
                            typeface = Typeface.DEFAULT_BOLD
                            background = GradientDrawable().apply {
                                cornerRadius = dp(10).toFloat()
                                setColor(if (isCurrent) blue else 0x262D7DF6)
                                setStroke(dp(1), if (isCurrent) blueBright else 0x552D7DF6)
                            }
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                            withIcon(
                                if (isWatched) R.drawable.ic_vp_replay else R.drawable.ic_vp_play,
                                if (isCurrent) Color.WHITE else blueBright,
                            )
                        }
                        rowLayout.addView(actionPill)

                        return rowLayout
                    }
                }
                setOnItemClickListener { _, _, position, _ ->
                    dismissPicker()
                    UserLibrary.markEpisodeWatched(this@X4KPlayerAty, curSeries, position + 1)
                    val seasonPrefix = if (seasonIds.size > 1) " · ${seasonLabel(curSeasonIndex)}" else ""
                    playEpisode(
                        epIds[position],
                        curSeries,
                        "${curBaseTitle.ifBlank { curTitle }}$seasonPrefix · C${position + 1}",
                        position,
                        curSeasonIndex,
                        epNumber = epNumbers.getOrNull(position)?.takeIf { it > 0 } ?: (position + 1),
                    )
                }
                // Mantener presionado alterna visto/no-visto sin reproducir (maratón y control).
                setOnItemLongClickListener { _, _, position, _ ->
                    val watched = UserLibrary.isEpisodeWatched(this@X4KPlayerAty, curSeries, position + 1)
                    UserLibrary.markEpisodeWatched(this@X4KPlayerAty, curSeries, position + 1, !watched)
                    Toast.makeText(
                        this@X4KPlayerAty,
                        if (watched) "Capítulo ${position + 1}: marcado como no visto" else "Capítulo ${position + 1}: marcado como visto",
                        Toast.LENGTH_SHORT,
                    ).show()
                    openEpisodePicker()
                    true
                }
                setSelection(curEpIndex.coerceIn(0, epIds.size - 1))
            }
            listView.id = View.generateViewId()
            if (seasonViews.isNotEmpty()) {
                val activeSeasonBtn = seasonViews.getOrElse(curSeasonIndex.coerceIn(0, seasonViews.lastIndex)) { seasonViews.first() }
                listView.nextFocusUpId = activeSeasonBtn.id
                seasonViews.forEach { it.nextFocusDownId = listView.id }
            } else {
                listView.nextFocusUpId = closeId
                closeBtn.nextFocusDownId = listView.id
            }
            card.addView(listView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
            card.addView(TextView(this).apply {
                text = "Mantén OK para marcar como visto"
                setTextColor(0x80FFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(2))
            })
            listView.post {
                listView.setSelection(curEpIndex.coerceIn(0, epIds.size - 1))
                listView.requestFocus()
            }
        }
        dim.addView(card, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            (resources.displayMetrics.heightPixels * 0.78f).toInt(),
            Gravity.CENTER,
        ))
        root().addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        picker = dim
        picking = true
        setBackgroundFocusBlocked(true)
        handler.removeCallbacks(hideRunnable)
    }

    /** Cambia de temporada dentro del reproductor: trae sus capítulos y reabre el selector. */
    private fun switchSeason(idx: Int) {
        if (idx == curSeasonIndex || idx !in seasonIds.indices) return
        val seasonId = seasonIds[idx]
        dismissPicker()
        Thread {
            val programs = kotlinx.coroutines.runBlocking { X4KApi.episodeProgramsFor(this@X4KPlayerAty, seasonId) }
            runOnUiThread {
                if (programs.isNotEmpty()) {
                    curSeries = seasonId
                    epIds = ArrayList(programs.map { it.contentId })
                    epNames = ArrayList(programs.map { it.name })
                    curEpIndex = 0
                    curSeasonIndex = idx
                }
                openEpisodePicker()
            }
        }.start()
    }

    /** "spa" -> "Español"; falls back to a role label, then the raw code. */
    private fun languageLabel(code: String, role: String): String {
        val name = languageName(code)
        return name.ifBlank { if (code.isBlank()) role else code.trim().replaceFirstChar { it.uppercase() } }
    }

    // ---------------------------------------------------------------- behavior

    private fun wireGestures(root: FrameLayout) {
        val catcher = View(this).apply {
            isFocusable = false
        }
        if (!isTv && !liveMode) {
            // Móvil: un toque muestra los controles AL INSTANTE (ver MobileTapGestures: el
            // onDown que reclama el gesto era el eslabón perdido desde v2.5.77 — sin él el
            // UP nunca llegaba y el toque se sentía muerto). Ocultar espera el timeout del
            // doble toque, que a su vez hace ∓10 s en los lados.
            val gestures = MobileTapGestures(
                isBlocked = { errorShown || picking || introView != null },
                controlsVisible = { controlsVisible },
                screenWidth = {
                    catcher.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                },
                onShowControls = { toggleControls() },
                onHideControlsDelayed = {
                    handler.removeCallbacks(pendingControlHide)
                    handler.postDelayed(
                        pendingControlHide,
                        android.view.ViewConfiguration.getDoubleTapTimeout().toLong(),
                    )
                },
                onCancelPendingHide = { handler.removeCallbacks(pendingControlHide) },
                onSeekBy = { delta -> performSeekBy(delta) },
                onSeekToast = { label -> Toast.makeText(this@X4KPlayerAty, label, Toast.LENGTH_SHORT).show() },
            )
            attachMobileTapGestures(catcher, gestures)
        } else {
            catcher.setOnClickListener { if (!errorShown && !picking && introView == null) toggleControls() }
        }
        root.addView(
            catcher,
            root.indexOfChild(controls),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun toggleControls() {
        val c = controls ?: return
        controlsVisible = !controlsVisible
        c.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) scheduleHide()
        applySubsMargins()
        updateIntroPill(com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L))
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 4500)
    }

    private fun hideControls() {
        handler.removeCallbacks(hideRunnable)
        root().findFocus()?.clearFocus()
        controlsVisible = false
        controls?.visibility = View.GONE
        applySubsMargins()
        updateIntroPill(com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L))
    }

    private fun exitPlayer() {
        if (exiting) {
            // La actividad residente no debe encerrar a nadie: si vuelve a primer plano
            // sin reproducción (el caso del fondo negro), Atrás cierra su tarea. Antes
            // esta bandera hacía que Atrás fuera un no-op silencioso para siempre.
            if (shouldFinishIdleResidentOnExit(exiting, isPlayerActive)) {
                Log.i(TAG, "reproductor residente sin reproducción: cerrando la tarea")
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "PLAYER_RESIDENT_IDLE",
                    "la tarea del reproductor volvió al frente sin reproducción (se autocierra)",
                )
                runCatching { finishAndRemoveTask() }
            }
            return
        }
        exiting = true
        // Con el motor todavía vivo: al detenerlo la posición se pierde y el cierre guardaba
        // un minuto viejo (o nada). El commit deja el avance exacto antes de soltar el motor.
        stopSubsTicker()
        saveHistory(immediate = true)
        playerView?.let { X4KTitan.stop(it) }
        // Maratón sin internet: al salir, con WiFi se deja bajando solo el siguiente capítulo
        // (el motor es uno solo: descargar mientras se ve pelearía con la reproducción).
        maybeAutoDownloadNext()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        isPlayerActive = false
        when (exitActionFor(hardResetNext)) {
            PlayerExitAction.FINISH_AND_RELAUNCH -> {
                // Estado envenenado del motor (auto-reparado): arranque limpio como siempre.
                finish()
            }
            PlayerExitAction.HIDE_RESIDENT -> {
                // Reproductor residente (el truco bueno de la app del proveedor): la actividad
                // —con su vista del motor ya detenida— se queda viva detrás y la app principal
                // vuelve al frente; el próximo play reutiliza la misma vista (stop → play
                // in-place) sin levantar pantalla, vista ni proceso desde cero.
                val front = Intent().apply {
                    setClassName(this@X4KPlayerAty, "com.chronosbvrx.visionplus.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                runCatching { startActivity(front) }.onFailure { finish() }
            }
        }
    }

    /**
     * Baja el siguiente capítulo pendiente (solo WiFi, solo si falta y solo uno por salida).
     * La decisión se toma aquí, pero la descarga la encarga el proceso PRINCIPAL (con el
     * proceso `:x4k` devuelto a limpio si ya reprodujo con vista): un programa headless en
     * un proceso que ya usó la vista del motor mezclaría historias del motor.
     */
    private fun maybeAutoDownloadNext() {
        if (liveMode || curSeries.isBlank()) return
        val settings = com.chronosbvrx.visionplus.ui.settings.VisionSettings
        if (!settings.autoDownloadNextOf(this)) return
        if (!isWifiConnected()) return
        val nextIdx = curEpIndex + 1
        if (nextIdx >= epIds.size) return
        if (com.chronosbvrx.visionplus.data.UserLibrary.isEpisodeWatched(this, curSeries, nextIdx + 1)) return
        runCatching {
            val code = epIds[nextIdx]
            val spec = JSONObject().apply {
                put("id", "xtv:$code")
                put("code", code)
                put("title", curBaseTitle.ifBlank { curTitle })
                put("poster", intentPoster)
                put("seriesId", curSeries)
                put("epIndex", nextIdx)
                put("seasonIndex", curSeasonIndex)
                put("durationMs", declaredDurationMs)
            }
            // Archivo (no SharedPreferences): el principal lo lee fresco al volver del
            // reproductor; su copia cacheada de prefs podría no ver un valor recién escrito.
            java.io.File(filesDir, "x4k_pending_download.json").writeText(spec.toString())
            android.widget.Toast.makeText(this, "Bajando el siguiente capítulo con WiFi", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun isWifiConnected(): Boolean {
        return runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
    }

    private val hideRunnable = Runnable {
        if (!scrubbing && firstFrame && !errorShown && !picking && resumeOverlay == null && !nextVisible) {
            // Que no quede el foco en una vista oculta: el mando no debe perderse.
            hideControls()
        }
    }

    /**
     * Oculta los controles tras el timeout del doble toque en móvil: el segundo toque
     * (∓10 s) lo cancela y la barra no parpadea.
     */
    private val pendingControlHide = Runnable {
        if (!errorShown && !picking && introView == null && resumeOverlay == null && !nextVisible) {
            hideControls()
        }
    }

    private fun togglePlay() {
        val v = playerView ?: return
        if (X4KTitan.isPlaying(v)) {
            X4KTitan.pause(v)
            saveHistory()
        } else {
            X4KTitan.resume(v)
        }
        handler.postDelayed({ refreshPlayIcon() }, 350)
        scheduleHide()
    }

    private fun refreshPlayIcon() {
        val playing = playerView?.let { X4KTitan.isPlaying(it) } ?: false
        renderPlayIcon(playing)
    }

    private fun refreshPlayIcon(playing: Boolean) = renderPlayIcon(playing)

    /**
     * Reconstruir el botón (removeAllViews + addView) dos veces por segundo es basura para el
     * proceso del reproductor: solo se rehace cuando el estado o la instancia del botón cambian.
     */
    private fun renderPlayIcon(playing: Boolean) {
        val button = playPauseBtn as? FrameLayout ?: return
        if (playIconBtn === button && playIconState == playing) return
        playIconBtn = button
        playIconState = playing
        button.removeAllViews()
        button.addView(
            icon(if (playing) MODE_PAUSE else MODE_PLAY),
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER),
        )
    }

    /**
     * Un seek (del usuario o de la app) no es un salto de contenido: reinicia la línea base
     * del detector y lo silencia 3 s — el motor tarda un instante en reflejar la posición
     * pedida, y ese salto es legítimo.
     */
    private fun noteAppSeek() {
        skipProbePosMs = -1L
        skipProbeAtMs = 0L
        skipProbeMuteUntilMs = android.os.SystemClock.elapsedRealtime() + 3_000L
    }

    private val poller = object : Runnable {
        override fun run() {
            // Sin reproducción viva (salida normal o estacionado al fondo) el sondeo no tiene
            // nada que hacer: ni re-arranques internos ni errores en una pantalla que ya no se
            // ve. El próximo play lo vuelve a lanzar desde cero.
            if (!isPlayerActive) return
            val v = playerView
            if (v != null) {
                val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
                if (!liveMode && declaredDurationMs <= 0L) {
                    // La duración del motor solo hace falta mientras el catálogo no la declaró:
                    // ya estable, preguntarla cada 500 ms es trabajo (y cerrojos del motor) de más.
                    val engineDur = com.chronosbvrx.visionplus.data.xuper.X4KTitan.durationMs(v)
                    if (!isBufferWindowDuration(engineDur, pos, declaredDurationMs, liveMode)) {
                        if (engineDur > 0L) {
                            durationMs = engineDur
                            declaredDurationMs = engineDur
                        }
                    }
                }
                val total = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
                // Un solo sondeo de estado por tic: `isPlaying` también va al motor por reflexión.
                val playingNow = com.chronosbvrx.visionplus.data.xuper.X4KTitan.isPlaying(v)
                // Señal real del motor: posición avanzando o decodificador encendido.
                val engineSignal = pos > 0 || playingNow
                if (engineSignal) liveSignalSeen = true
                // En vivo, el temporizador solo libera la espera (algunos canales no
                // reportan posición); el ERROR no se guía por él, sino por la señal real.
                val hasLivePlayback = liveMode && (
                    engineSignal || System.currentTimeMillis() - startedAtMs > 12_000
                    )
                if (pos > 0 || hasLivePlayback) {
                    if (loadingOverlay?.visibility == View.VISIBLE && !loadingFading) {
                        loadingFading = true
                        val fadeMs = if (liveMode) 120L else 300L
                        loadingOverlay?.animate()?.alpha(0f)?.setDuration(fadeMs)
                            ?.withEndAction {
                                loadingOverlay?.visibility = View.GONE
                                loadingOverlay?.alpha = 1f
                                loadingFading = false
                            }?.start()
                    }
                    if (!firstFrame) {
                        firstFrame = true
                        errorShown = false
                        errorOverlay?.visibility = View.GONE
                        if (controlsVisible) scheduleHide()
                        onFirstFrame()
                    }
                }
                // Estancado: ni cuadro (VOD) ni señal del motor (vivo). Primero un
                // re-arranque interno silencioso (equivale al "Reintentar" que en las
                // cajas rápidas sí funciona); si tampoco, error + auto-reparado.
                val stalled = if (liveMode) !liveSignalSeen else !firstFrame
                val stalledFor = System.currentTimeMillis() - startedAtMs
                val stallLimit = if (softRestarts == 0) 25_000L else 35_000L
                if (stalled && !errorShown && stalledFor > stallLimit) {
                    if (softRestarts == 0) {
                        softRestartPlayback()
                    } else {
                        // Auto-reparado de cajas atascadas: el motor arrancó pero jamás
                        // pintó cuadro. Tras dos fallos seguidos se rota el motor a una
                        // generación nueva; el proceso principal aplicará sesión/SLB
                        // frescas al siguiente intento y el arranque limpio dejará de
                        // heredar el estado envenenado.
                        val racha = com.chronosbvrx.visionplus.data.xuper.PlayerRecovery.noteFailure(this@X4KPlayerAty)
                        if (com.chronosbvrx.visionplus.data.xuper.PlayerRecovery.shouldHeal(racha)) {
                            val gen = com.chronosbvrx.visionplus.data.xuper.PlayerRecovery.bumpEngineGen(this@X4KPlayerAty)
                            // El proceso residente corre con la generación vieja ya booteada: a la
                            // salida se cierra y se reinicia para estrenar la carpeta nueva.
                            hardResetNext = true
                            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                "PLAYER_SELF_HEAL", "gen=$gen racha=$racha modo=${if (liveMode) "live" else "vod"}",
                            )
                            Log.w(TAG, "motor atascado: rotado a generación $gen (racha=$racha)")
                        }
                        showError("sin-primer-cuadro")
                    }
                }
                // Salto de contenido (cuadros que nunca se mostraron): la posición avanza más
                // que el reloj de pared. Se mide y se reporta al diagnóstico; los seeks de la
                // app reinician la base para no confundir un salto pedido con uno del motor.
                if (!liveMode && firstFrame && !scrubbing && !errorShown) {
                    val probeAt = android.os.SystemClock.elapsedRealtime()
                    if (probeAt >= skipProbeMuteUntilMs) {
                        val excess = positionSkipExcessMs(skipProbePosMs, skipProbeAtMs, pos, probeAt)
                        if (excess > 0L && probeAt - lastSkipReportAtMs > 30_000L) {
                            lastSkipReportAtMs = probeAt
                            Log.w(TAG, "salto de contenido: ${excess}ms (pos=$pos)")
                            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                "PLAYER_FRAME_SKIP",
                                "modo=vod exceso=${excess}ms",
                            )
                        }
                    }
                    skipProbePosMs = pos
                    skipProbeAtMs = probeAt
                }
                if (!scrubbing) {
                    if (total > 0) {
                        val target = ((pos * 1000) / total).toInt().coerceIn(0, 1000)
                        if (seekBar?.progress != target) seekBar?.progress = target
                    }
                    val nowText = fmt(pos)
                    if (timeNow?.text?.toString() != nowText) timeNow?.text = nowText
                }
                val totalText = if (total > 0) fmt(total) else "--:--"
                if (timeTotal?.text?.toString() != totalText) timeTotal?.text = totalText
                refreshPlayIcon(playingNow)
                if (!liveMode) {
                    // Marcadores de salto: auto-omitir (si está activado), botón del intro o
                    // resumen, tarjeta de créditos y aviso de post-créditos — todo nace de la
                    // misma posición del tic.
                    maybeAutoSkipIntro(pos, playingNow)
                    updateIntroPill(pos)
                    maybeShowCreditsCard(pos)
                    maybeShowPostCredits(pos)
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastHistorySave > 10_000) {
                        lastHistorySave = nowMs
                        saveHistory()
                    }
                }
                if (!liveMode && !endedShown && total > 30000 && pos >= total - 2000) {
                    onEpisodeEnded()
                }
            }
            handler.postDelayed(this, if (!firstFrame) 80L else 500L)
        }
    }

    private fun performSeekBy(deltaMs: Long) {
        if (liveMode) return
        val v = playerView ?: return
        val currentPos = X4KTitan.positionMs().coerceAtLeast(0L)
        val total = resolveStableTotalDuration(declaredDurationMs, durationMs, currentPos, liveMode)
        val targetPos = if (total > 0) (currentPos + deltaMs).coerceIn(0L, total) else (currentPos + deltaMs).coerceAtLeast(0L)
        X4KTitan.seekTo(v, targetPos)
        noteAppSeek()
        if (total > 0) {
            seekBar?.progress = ((targetPos * 1000) / total).toInt().coerceIn(0, 1000)
        }
        timeNow?.text = fmt(targetPos)
        scheduleHide()
    }

    private fun progressMs(): Long {
        val total = resolveStableTotalDuration(declaredDurationMs, durationMs, 0L, liveMode)
        val sb = seekBar ?: return 0L
        return if (total > 0) (sb.progress.toLong() * total) / 1000 else 0L
    }

    private fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** TV: a control the D-pad can land on, with an obvious focused look (ring + lift). */
    private fun View.tvControl(focusFill: Int? = null, focusRadius: Int = 22): View {
        if (isTv) {
            isFocusable = true
            isFocusableInTouchMode = false
            playerControls.add(this)
            val idleBg = background
            val activeBg: android.graphics.drawable.Drawable? = when {
                this is TextView -> roundBackground(blue)
                focusFill != null -> ringBackground(focusFill, focusRadius)
                else -> null
            }
            setOnFocusChangeListener { _, focused ->
                animate()
                    .scaleX(if (focused) 1.09f else 1f)
                    .scaleY(if (focused) 1.09f else 1f)
                    .alpha(if (focused) 1f else 0.92f)
                    .setDuration(120)
                    .start()
                background = if (focused) (activeBg ?: idleBg) else idleBg
                if (focused && controlsVisible && !picking && !nextVisible && introView == null && !errorShown) {
                    scheduleHide()
                }
            }
        }
        return this
    }

    private fun applyImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // Red de seguridad del «Camino B»: la actividad se queda residente detrás, pero
        // si el sistema la trae al frente cuando YA no hay reproducción (salida normal,
        // proceso :x4k congelado o tarea huérfana) se cierra sola. Jamás un fondo negro
        // sin controles ni salida.
        if (!isPlayerActive) {
            Log.i(TAG, "reproductor residente sin reproducción al volver al frente: se cierra")
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                "PLAYER_RESIDENT_IDLE",
                "la tarea del reproductor volvió al frente sin reproducción (se autocierra)",
            )
            runCatching { finishAndRemoveTask() }
            return
        }
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersiveMode()
    }

    private fun hasFocusedControl(): Boolean {
        val focused = currentFocus ?: root().findFocus()
        return focused != null && playerControls.contains(focused)
    }

    private fun canExecuteControlAction(): Boolean {
        if (!controlsVisible) return false
        return com.chronosbvrx.visionplus.ui.player.canExecuteControlAction(
            android.os.SystemClock.uptimeMillis(),
            lastControlsWakeupMs,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (swallowWakeupKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            val nowMs = android.os.SystemClock.uptimeMillis()
            if (shouldClearSwallowedWakeup(nowMs, swallowWakeupAtMs)) {
                // El mando no mandó el UP (o se perdió): la tecla no puede quedar muerta.
                swallowWakeupKeyCode = KeyEvent.KEYCODE_UNKNOWN
            } else if (event.action == KeyEvent.ACTION_UP || event.isCanceled) {
                swallowWakeupKeyCode = KeyEvent.KEYCODE_UNKNOWN
                return true
            } else if (event.keyCode == swallowWakeupKeyCode) {
                // Repetición del mismo toque (tecla sostenida): se traga y se refresca el
                // vencimiento para no soltarla a media pulsación.
                swallowWakeupAtMs = nowMs
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            swallowWakeupKeyCode = KeyEvent.KEYCODE_UNKNOWN
            val action = resolvePlayerKeyDownAction(
                keyCode = event.keyCode,
                isLive = liveMode,
                isControlsVisible = controlsVisible,
                hasControlFocus = if (isTv) hasFocusedControl() else controlsVisible,
                isModalActive = isModalActive,
                isIntroActive = introView != null,
                isErrorShown = errorShown,
            )
            when (action) {
                PlayerKeyDownAction.WAKE_CONTROLS_ONLY -> {
                    swallowWakeupKeyCode = event.keyCode
                    swallowWakeupAtMs = android.os.SystemClock.uptimeMillis()
                    lastControlsWakeupMs = android.os.SystemClock.uptimeMillis()
                    showControlsBriefly()
                    return true
                }
                PlayerKeyDownAction.LIVE_SWITCH_NEXT_CHANNEL -> {
                    switchChannel(liveIndex + 1)
                    showControlsBriefly()
                    return true
                }
                PlayerKeyDownAction.LIVE_SWITCH_PREV_CHANNEL -> {
                    switchChannel(liveIndex - 1)
                    showControlsBriefly()
                    return true
                }
                PlayerKeyDownAction.PASS_TO_FOCUSED_CONTROL,
                PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM,
                PlayerKeyDownAction.DEDICATED_MEDIA_KEY -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * The remote, two lives:
     *  - a control holds the focus → the keys go to the focus system (navigate + activate);
     *  - no control focused (controls hidden) → first D-pad press ONLY brings the controls
     *    and lands focus without executing any action by itself.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val onControl = if (isTv) hasFocusedControl() else controlsVisible
        val inModal = isModalActive
        if ((nextVisible || suggestVisible) && !picking && introView == null &&
            keyCode != KeyEvent.KEYCODE_MEDIA_PLAY && keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE
        ) {
            // La tarjeta de "siguiente" o el modal de sugerencias está al mando:
            // el D-pad los navega y OK confirma.
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_GUIDE -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                // Regla inmutable: con controles ocultos o sin foco, el primer toque SOLO abre el menú.
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                // La repetición de una tecla sostenida no alterna play/pausa en cadena.
                if (event.repeatCount > 0) return true
                togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                if (liveMode) {
                    // Sostener la tecla no puede recorrer la lista de canales sin control.
                    if (event.repeatCount > 0) return true
                    switchChannel(liveIndex - 1)
                } else {
                    performSeekBy(-10_000L)
                }
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                if (liveMode) {
                    if (event.repeatCount > 0) return true
                    switchChannel(liveIndex + 1)
                } else {
                    performSeekBy(10_000L)
                }
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (inModal || introView != null) return super.onKeyDown(keyCode, event)
                // Sostener «siguiente» no salta varios capítulos.
                if (event.repeatCount > 0) return true
                if (hasNextEpisode()) {
                    playNextEpisodeFromControls()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (inModal) return super.onKeyDown(keyCode, event)
                if (introView != null) {
                    // El pre-roll solo tiene «Omitir»: cualquier flecha lo enfoca.
                    introSkip?.post { introSkip?.requestFocus() }
                    return true
                }
                if (!controlsVisible || !onControl) {
                    showControlsBriefly()
                    return true
                }
                if (liveMode) {
                    // El zapping es a dos toques: sostener la flecha no recorre los canales.
                    if (event.repeatCount > 0) return true
                    switchChannel(liveIndex + 1)
                    showControlsBriefly()
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (inModal) return super.onKeyDown(keyCode, event)
                if (introView != null) {
                    introSkip?.post { introSkip?.requestFocus() }
                    return true
                }
                if (!controlsVisible || !onControl) {
                    showControlsBriefly()
                    return true
                }
                if (liveMode) {
                    if (event.repeatCount > 0) return true
                    switchChannel(liveIndex - 1)
                    showControlsBriefly()
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playerView?.let { X4KTitan.resume(it) }
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playerView?.let { X4KTitan.pause(it) }
                showControlsBriefly()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Controls on screen for a moment, whatever hid them before. */
    private fun showControlsBriefly() {
        if (errorShown || picking || nextVisible || suggestVisible) return
        val wasHiddenOrUnfocused = !controlsVisible || (isTv && !hasFocusedControl())
        if (wasHiddenOrUnfocused) {
            lastControlsWakeupMs = android.os.SystemClock.uptimeMillis()
        }
        controlsVisible = true
        controls?.visibility = View.VISIBLE
        refreshPlayIcon()
        updateIntroPill(com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L))
        if (isTv && introView == null) {
            if (!hasFocusedControl()) {
                val target = if (liveMode) channelsBtn else playPauseBtn
                target?.requestFocus()
                retryControlFocus(target, attempts = 2)
            }
        }
        scheduleHide()
    }

    /**
     * Reintentos del foco de los controles: la petición hecha mientras la vista aún no
     * puede tomarlo (o la caja anda ocupada arrancando el motor) se descarta en silencio;
     * dos reintentos cortos la recuperan sin que el usuario tenga que volver a picar.
     */
    private fun retryControlFocus(target: View?, attempts: Int) {
        if (target == null || attempts <= 0) return
        target.postDelayed({
            if (controlsVisible && !isModalActive && !hasFocusedControl()) {
                target.requestFocus()
                retryControlFocus(target, attempts - 1)
            }
        }, 160L)
    }

    override fun onStop() {
        super.onStop()
        if (shouldParkPlaybackOnStop(isPlayerActive, exiting)) {
            // El reproductor quedó DETRÁS con reproducción viva (el control remoto trajo el
            // menú al frente para abrir otro título, el usuario salió a Inicio, se apagó la
            // tele): soltar el motor aquí evita el video sonando invisible en el menú.
            parkPlaybackInBackground()
        } else {
            // Cierre de seguridad al dejar la app (botón Inicio, pantalla apagada): el tic periódico
            // va con apply y el sistema puede matar el proceso en segundo plano sin pasar por
            // onDestroy; un commit deja el minuto en disco para la fila «Seguir viendo».
            saveHistory(immediate = true)
        }
        // El canal «Continuar viendo» del launcher de TV (Google TV) se actualiza al salir del
        // reproductor: este es el proceso que trae el minuto fresco del historial. La llamada es
        // silenciosa y con freno propio; si el proceso muere al salir (Atrás), el proceso
        // principal re-sincroniza al volver, ya con el commit final en disco.
        runCatching { com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext.syncAsync(this) }
    }

    /**
     * El video quedó invisible pero vivo: se suelta el motor con el minuto ya comprometido y
     * la actividad queda residente (la próxima reproducción reutiliza la vista, como siempre).
     * El auto-reparado del motor conserva su cierre duro para estrenar carpeta limpia.
     */
    private fun parkPlaybackInBackground() {
        android.util.Log.i(TAG, "reproductor al fondo con reproducción activa: se suelta el motor")
        exiting = true
        // El contador de «siguiente capítulo» no puede seguir vivo con el reproductor
        // detrás: jamás debe arrancar un episodio invisible en segundo plano.
        handler.removeCallbacks(nextTick)
        nextCountdown = 0
        nextPendingAtEnd = nextPendingAtEnd && nextVisible
        stopSubsTicker()
        saveHistory(immediate = true)
        playerView?.let { runCatching { X4KTitan.stop(it) } }
        isPlayerActive = false
        if (exitActionFor(hardResetNext) == PlayerExitAction.FINISH_AND_RELAUNCH) {
            runCatching { finish() }
        }
    }

    override fun onDestroy() {
        isPlayerActive = false
        if (residentInstance === this) residentInstance = null
        remoteReceiver?.let { runCatching { unregisterReceiver(it) } }
        remoteReceiver = null
        // Con commit: el proceso se mata al salir y el apply podría no alcanzar a escribir.
        saveHistory(immediate = true)
        handler.removeCallbacksAndMessages(null)
        playerView?.let { X4KTitan.stop(it) }
        super.onDestroy()
        // The engine refuses a second, different program inside one process (the provider's app
        // keeps a single view for its whole session; we create one per play). This player owns
        // its process alone, so leaving finishes it: every play boots as cleanly as the first.
        if (isFinishing) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    internal companion object {
        const val TAG = "X4KPlayerAty"
        const val MODE_PLAY = 0
        const val MODE_PAUSE = 1
        const val MODE_REW = 2
        const val MODE_FWD = 3

        @Volatile
        var isPlayerActive = false

        /** La actividad residente viva (una sola): la salida de la app la cierra con su tarea. */
        @Volatile
        private var residentInstance: X4KPlayerAty? = null

        /**
         * El proceso principal pidió salir de la app: si el reproductor residente sigue
         * vivo, se cierra aquí mismo (su tarea desaparece y el proceso se apaga con él al
         * terminarse la actividad). Sin residencia no hay nada que hacer.
         */
        internal fun finishResidentForExit() {
            val activity = residentInstance ?: return
            Log.i(TAG, "salida de la app: cerrando el reproductor residente")
            runCatching { activity.finishAndRemoveTask() }
        }
    }
}
