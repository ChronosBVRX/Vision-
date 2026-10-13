package com.chronosbvrx.visionplus.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.isFinished
import com.chronosbvrx.visionplus.data.shouldKeep
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
        }
    }
    private var controls: View? = null
    private var loadingOverlay: View? = null
    private var errorOverlay: View? = null
    private var picker: View? = null
    private var audioBtn: TextView? = null
    private var subsBtn: TextView? = null
    private var qualityBtn: TextView? = null
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
    private var curSeasonIndex = 0
    private var curAspect = 0
    private var epIds = ArrayList<String>()
    private var seasonIds = ArrayList<String>()
    private var seasonNames = ArrayList<String>()
    private var nextOverlay: View? = null
    private var nextVisible = false
    private var nextDismissed = false
    private var endedShown = false
    private var nextCountdown = 0
    private var nextCountdownView: TextView? = null
    private var nextPlayBtn: View? = null
    private var nextTarget: NextEp? = null

    private data class NextEp(
        val code: String,
        val seriesId: String,
        val displayTitle: String,
        val playerTitle: String,
        val epIndex: Int,
        val seasonIndex: Int,
        val newEpIds: List<String>? = null,
        val newEpNames: List<String>? = null,
    )

    /** TV: the controls become D-pad targets, so the remote never gets stuck. */
    private val isTv by lazy { DeviceKind.isTelevision(this) }
    private val playerControls = ArrayList<View>()
    private var lastPickerSourceBtn: View? = null

    private val isModalActive: Boolean
        get() = picking || resumeOverlay != null || nextVisible

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
    private var errorShown = false
    private var picking = false
    private var declaredLanguages: List<String> = emptyList()
    private var subtitleLanguages: List<String> = emptyList()
    private var subtitlesOff = false

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = false
    private var scrubbing = false
    private var swallowWakeupKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
    private var lastControlsWakeupMs: Long = 0L

    /** La espera de marca se está desvaneciendo (evita relanzar el fundido en cada sondeo). */
    private var loadingFading = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPlayerActive = true
        startedAtMs = System.currentTimeMillis()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        contentRoot = root
        setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val mode = intent.getStringExtra("mode") ?: "vod"
        val code = intent.getStringExtra("code").orEmpty()
        val rawTitle = intent.getStringExtra("title").orEmpty().ifBlank { code }
        liveMode = mode == "live"
        val title = if (liveMode) X4KApi.displayChannelName(code, rawTitle) else rawTitle
        curCode = code
        curTitle = title
        if (liveMode) {
            liveGroupTitle = intent.getStringExtra("channelGroup").orEmpty()
            val codes = intent.getStringArrayListExtra("channelCodes")
            // Resolver lista completa de canales en segundo plano para no bloquear el arranque del reproductor
            Thread {
                val allLive = X4KApi.cachedLive(applicationContext)
                val resolvedArr = if (!codes.isNullOrEmpty()) {
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
                liveChannels = resolvedArr
                var idx = findChannelIndex(curCode.ifBlank { code })
                if (idx < 0 && resolvedArr.length() > 0) idx = 0
                liveIndex = idx
                runOnUiThread {
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
        subtitleLanguages = intent.getStringExtra("subsLangs").orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotBlank() }
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
                        if (declaredDurationMs <= 0L || (d > declaredDurationMs && declaredDurationMs < 60_000L)) {
                            declaredDurationMs = d
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
            seasonIds.clear()
            seasonNames.clear()
            curEpIndex = 0
            curSeasonIndex = 0
        }
        resumeMs = intent.getLongExtra("resumeMs", 0L)
        intentPoster = intent.getStringExtra("posterUrl").orEmpty()
        if (!liveMode && curSeries.isNotBlank() && (epIds.isEmpty() || epNames.isEmpty())) {
            Thread {
                val programs = kotlinx.coroutines.runBlocking {
                    X4KApi.episodeProgramsFor(this@X4KPlayerAty, curSeries)
                }
                if (programs.isNotEmpty()) {
                    runOnUiThread {
                        if (epIds.isEmpty()) {
                            epIds = ArrayList(programs.map { it.contentId })
                            val targetCode = curCode.ifBlank { code }
                            val idx = epIds.indexOf(targetCode)
                            if (idx >= 0) curEpIndex = idx
                        }
                        if (epNames.isEmpty()) {
                            epNames = ArrayList(programs.map { it.name })
                        }
                        updateNextButtonState()
                    }
                }
            }.start()
        }

        var lastBackPressTime = 0L

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

        if (code.isBlank() || mediasJson.isBlank()) {
            finish()
            return
        }

        // The brand pre-roll belongs to films; live TV opens straight on the signal.
        // En Smart TV o dispositivos modestos (Chromecast, Fire TV, Amlogic) se omite el VideoView
        // de la intro para evitar que dos decodificadores de hardware compitan antes de reproducir.
        val skipIntroForPerformance = com.chronosbvrx.visionplus.DevicePerformance.isLowEndDevice(this)
        if (!liveMode && !skipIntroForPerformance) showIntro(root)

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
            mountUi(root, mode, code, title, aspect, medias)
        } else {
            Thread {
                try {
                    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
                    val ok = X4KTitan.ensureConfigured(
                        context = this,
                        userId = "967111326",
                        devId = "2392fe5186d83d73508a2c8c60a26ef6",
                        androidId = androidId,
                        entriesJson = resolvedEntries,
                        playParams = resolvedParams,
                    )
                    Log.i(TAG, "ensureConfigured=$ok")
                    runOnUiThread { mountUi(root, mode, code, title, aspect, medias) }
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

    /** Builds the view + the premium overlays and starts playback. */
    private fun mountUi(
        root: FrameLayout,
        mode: String,
        code: String,
        title: String,
        aspect: Int,
        medias: List<X4KTitan.MediaSpec>,
    ) {
        val v = X4KTitan.newVideoView(this) ?: run {
            finish()
            return
        }
        root.addView(
            v,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        playerView = v
        curCode = code
        curTitle = title
        curBaseTitle = title.substringBefore(" · T").substringBefore(" · C").ifBlank { title }
        curAspect = if (aspect == 0 && com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultAspect != 0) {
            com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultAspect
        } else {
            aspect
        }
        curMedias = ArrayList(medias)
        val preferredQ = com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality
        val initialMedia = if (preferredQ != "Auto") {
            medias.firstOrNull { it.quality.contains(preferredQ, ignoreCase = true) } ?: medias.firstOrNull()
        } else {
            medias.firstOrNull()
        }
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
                if (X4KTitan.play(v, mode, code, title, medias)) {
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
                    showError()
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
    private inner class IconView(context: Context, private val mode: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            when (mode) {
                MODE_PLAY -> {
                    val p = Path().apply {
                        moveTo(w * 0.30f, h * 0.22f)
                        lineTo(w * 0.80f, h * 0.50f)
                        lineTo(w * 0.30f, h * 0.78f)
                        close()
                    }
                    canvas.drawPath(p, paint)
                }
                MODE_PAUSE -> {
                    val barW = w * 0.16f
                    canvas.drawRoundRect(w * 0.28f, h * 0.22f, w * 0.28f + barW, h * 0.78f, 4f, 4f, paint)
                    canvas.drawRoundRect(w * 0.56f, h * 0.22f, w * 0.56f + barW, h * 0.78f, 4f, 4f, paint)
                }
                MODE_REW, MODE_FWD -> {
                    val forward = mode == MODE_FWD
                    for (k in 0..1) {
                        val x0 = if (forward) 0.16f + 0.30f * k else 0.84f - 0.30f * k
                        val dx = if (forward) 0.26f else -0.26f
                        val p = Path().apply {
                            moveTo(w * x0, h * 0.30f)
                            lineTo(w * (x0 + dx), h * 0.50f)
                            lineTo(w * x0, h * 0.70f)
                            close()
                        }
                        canvas.drawPath(p, paint)
                    }
                }
            }
        }
    }

    private fun icon(mode: Int): View = IconView(this, mode)

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
        box.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })
        box.addView(TextView(this).apply {
            text = if (liveMode) "Preparando la señal…" else "Preparando tu película…"
            setTextColor(0x80FFFFFF.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
        })
        root.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        loadingOverlay = box
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

    private fun showError() {
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
            val liveDot = TextView(this).apply {
                text = "\u25CF EN VIVO"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFF5A5A.toInt())
                background = roundBackground(0x33FF5A5A.toInt(), 5)
                setPadding(dp(7), dp(2), dp(7), dp(2))
            }
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
            val channels = pill("▤ Canales") { showChannelPicker() }
            channels.tvControl()
            channelsBtn = channels
            buttons.addView(channels)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))

            val fav = pill(if (isCurrentFav()) "★ Favorito" else "☆ Añadir a favoritos") { toggleCurrentFav() }
            fav.tvControl()
            favBtn = fav
            updateFavButtonState()
            buttons.addView(fav)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))

            val info = pill("ℹ Información") { showChannelInfo() }
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
            if (isSeriesContent(epIds.size, seasonIds.size)) {
                val nextEp = pill("⏭ Sig. episodio") {
                    if (canExecuteControlAction()) playNextEpisodeFromControls()
                }
                nextEp.tvControl()
                nextEpBtn = nextEp
                buttons.addView(nextEp)
                buttons.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))

                val episodes = pill("☰ Capítulos") { showEpisodePicker() }
                episodes.tvControl()
                episodesBtn = episodes
                buttons.addView(episodes)
                buttons.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                updateNextButtonState()
            }
            val firstAudio = declaredLanguages.firstOrNull().orEmpty()
            val audioName = trackDisplayLang(firstAudio, null).takeIf { it.isNotBlank() }
                ?.let { languageLabel(it, "Idioma") } ?: "Idioma"
            val audio = pill("◉ $audioName") {
                showLanguagePicker(forSubtitle = false)
            }
            audio.tvControl()
            val subs = pill("□ Subtítulos") { showLanguagePicker(forSubtitle = true) }
            subs.tvControl()
            val qDisplay = formatQualityShort(curQuality)
            val quality = pill("⚙ $qDisplay") { showQualityPicker() }
            quality.tvControl()
            buttons.addView(audio)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            buttons.addView(subs)
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            buttons.addView(quality)
            playPauseBtn = playPause
            audioBtn = audio
            subsBtn = subs
            qualityBtn = quality
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
                val audio = audioBtn
                val subs = subsBtn
                val quality = qualityBtn
                if (rewind != null && playPause != null && forward != null && audio != null && subs != null) {
                    val rewindId = View.generateViewId().also { rewind.id = it }
                    val playId = View.generateViewId().also { playPause.id = it }
                    val forwardId = View.generateViewId().also { forward.id = it }
                    val audioId = View.generateViewId().also { audio.id = it }
                    val subsId = View.generateViewId().also { subs.id = it }
                    val qualityId = if (quality != null) View.generateViewId().also { quality.id = it } else -1
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
                    val nextView = nextEpBtn
                    val nextId = if (nextView != null) View.generateViewId().also { nextView.id = it } else -1
                    val epsView = episodesBtn
                    val epsId = if (epsView != null) View.generateViewId().also { epsView.id = it } else -1
                    val firstPillId = when {
                        nextId != -1 -> nextId
                        epsId != -1 -> epsId
                        else -> audioId
                    }
                    val upId = seekId

                    rewind.nextFocusUpId = upId
                    rewind.nextFocusRightId = playId
                    rewind.nextFocusDownId = rewindId

                    playPause.nextFocusLeftId = rewindId
                    playPause.nextFocusRightId = forwardId
                    playPause.nextFocusUpId = upId
                    playPause.nextFocusDownId = playId

                    forward.nextFocusLeftId = playId
                    forward.nextFocusRightId = firstPillId
                    forward.nextFocusUpId = upId
                    forward.nextFocusDownId = forwardId

                    if (nextId != -1) {
                        nextView?.nextFocusLeftId = forwardId
                        nextView?.nextFocusRightId = if (epsId != -1) epsId else audioId
                        nextView?.nextFocusUpId = upId
                        nextView?.nextFocusDownId = nextId
                    }

                    if (epsId != -1) {
                        epsView?.nextFocusLeftId = if (nextId != -1) nextId else forwardId
                        epsView?.nextFocusRightId = audioId
                        epsView?.nextFocusUpId = upId
                        epsView?.nextFocusDownId = epsId
                    }
                    val pillLeftId = when {
                        epsId != -1 -> epsId
                        nextId != -1 -> nextId
                        else -> forwardId
                    }
                    audio.nextFocusLeftId = pillLeftId
                    audio.nextFocusRightId = subsId
                    audio.nextFocusUpId = upId
                    audio.nextFocusDownId = audioId

                    subs.nextFocusLeftId = audioId
                    subs.nextFocusRightId = if (qualityId != -1) qualityId else subsId
                    subs.nextFocusUpId = upId
                    subs.nextFocusDownId = subsId

                    if (qualityId != -1) {
                        quality?.nextFocusLeftId = subsId
                        quality?.nextFocusRightId = qualityId
                        quality?.nextFocusUpId = upId
                        quality?.nextFocusDownId = qualityId
                    }

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
        if (!liveMode) {
            seekBar = seek
            timeNow = now
            timeTotal = total
        }
    }

    private fun pill(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = roundBackground(surface)
        setOnClickListener {
            if (isModalActive || canExecuteControlAction()) onClick()
        }
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

    private fun showLanguagePicker(forSubtitle: Boolean) {
        lastPickerSourceBtn = if (forSubtitle) subsBtn else audioBtn
        val v = playerView ?: return
        val options = ArrayList<Pair<String, () -> Unit>>()
        val engineTracks = X4KTitan.tracks().filter { it.first == if (forSubtitle) "subtitle" else "audio" }
        val declared = if (forSubtitle) subtitleLanguages else declaredLanguages

        if (forSubtitle) {
            options += "Desactivados" to {
                subtitlesOff = true
                X4KTitan.selectTrack("subtitle", -1)
                subsBtn?.text = "\u25A1 Subtítulos"
            }
        }
        if (engineTracks.isNotEmpty()) {
            // El motor a veces da el códec ("aac") en vez del idioma: se empareja con lo
            // declarado por el portal cuando los conteos coinciden; si no, cada pista usa
            // lo suyo y las etiquetas repetidas se distinguen por su id.
            val pairByIndex = engineTracks.size == declared.size
            val resolved = engineTracks.mapIndexed { index, (_, lang, _) ->
                trackDisplayLang(lang, declared.getOrNull(index)?.takeIf { pairByIndex })
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
                    X4KTitan.selectTrack(if (forSubtitle) "subtitle" else "audio", id)
                    if (forSubtitle) {
                        subtitlesOff = false
                        subsBtn?.text = "\u25A1 $pillText"
                    } else {
                        audioBtn?.text = "\u25C9 $pillText"
                    }
                }
            }
        } else {
            declared.forEachIndexed { index, lang ->
                options += languageLabel(lang, "Pista ${index + 1}") to {
                    if (forSubtitle) {
                        subtitlesOff = false
                        X4KTitan.selectTrack("subtitle", index + 1)
                        subsBtn?.text = "\u25A1 " + languageLabel(lang, "Subtítulos")
                    } else {
                        X4KTitan.selectAudio(v, lang, index)
                        audioBtn?.text = "\u25C9 " + languageLabel(lang, "Idioma")
                    }
                }
            }
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

    // ---------------------------------------------------------------- quality picker

    private fun showQualityPicker() {
        if (liveMode) return
        lastPickerSourceBtn = qualityBtn
        val v = playerView ?: return
        val distinctQualities = curMedias.distinctBy { it.quality.ifBlank { it.name } }

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
                val label = (if (isCurrent) "✓ " else "  ") + formatQualityLabel(spec.quality) + (if (isCurrent) " (Actual)" else "")
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
                text = "● $qName (Máxima calidad disponible)"
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
        qualityBtn?.text = "⚙ " + formatQualityShort(target.quality)
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
            if (curPos > 0L) {
                handler.postDelayed({ playerView?.let { X4KTitan.seekTo(it, curPos) } }, 250L)
            }
        }
    }

    // ---------------------------------------------------------------- siguiente capítulo

    private fun onEpisodeEnded() {
        if (liveMode || endedShown) return
        endedShown = true
        if (curSeries.isNotBlank()) {
            UserLibrary.markEpisodeWatched(this, curSeries, curEpIndex + 1)
        }
        if (curCode.isNotBlank()) {
            PlaybackHistory.delete(this, curCode)
        }
        showNextCard()
    }

    /** "Siguiente capítulo" estilo premium al terminar un episodio (series con continuidad). */
    private fun showNextCard() {
        if (liveMode || nextVisible) return
        if (!isSeriesContent(epIds.size, seasonIds.size)) return
        val hasEp = curEpIndex + 1 < epIds.size
        val hasSeason = curSeries.isNotBlank() && curSeasonIndex + 1 < seasonIds.size
        if (!hasEp && !hasSeason) return
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
        val epTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        card.addView(epTitle)
        val countdown = TextView(this).apply {
            setTextColor(0xFF9BA8BD.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        }
        card.addView(countdown)
        nextCountdownView = countdown
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playBtn = TextView(this).apply {
            text = "  ▶ Reproducir ahora  "
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(blue, 14)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { playNext(auto = false) }
        }
        playBtn.tvControl(blue, 14)
        row.addView(playBtn)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val cancelBtn = TextView(this).apply {
            text = "  Quedarse aquí  "
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
        nextOverlay = dim
        nextVisible = true
        nextDismissed = false
        setBackgroundFocusBlocked(true)
        if (isTv) playBtn.post { playBtn.requestFocus() }
        if (hasEp) {
            val nextIdx = curEpIndex + 1
            val rawName = epNames.getOrNull(nextIdx)?.trim().orEmpty()
            val cleanName = rawName.takeIf {
                it.isNotBlank() &&
                    !it.equals("Capítulo ${nextIdx + 1}", ignoreCase = true) &&
                    !it.equals("Capitulo ${nextIdx + 1}", ignoreCase = true) &&
                    !it.equals("Episodio ${nextIdx + 1}", ignoreCase = true) &&
                    !it.equals("${nextIdx + 1}", ignoreCase = true)
            }.orEmpty()
            val displayLabel = "Capítulo ${nextIdx + 1}" + (if (cleanName.isNotBlank()) " · $cleanName" else "")
            val seasonPrefix = if (seasonIds.size > 1) " · ${seasonLabel(curSeasonIndex)}" else ""
            val playerTitle = "${curBaseTitle.ifBlank { curTitle }}$seasonPrefix · C${nextIdx + 1}"
            nextTarget = NextEp(
                code = epIds[nextIdx],
                seriesId = curSeries,
                displayTitle = displayLabel,
                playerTitle = playerTitle,
                epIndex = nextIdx,
                seasonIndex = curSeasonIndex,
            )
            epTitle.text = displayLabel
            nextCountdown = 5
            tickNext()
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
                    val cleanName = first.name.trim().takeIf {
                        it.isNotBlank() &&
                            !it.equals("Capítulo 1", ignoreCase = true) &&
                            !it.equals("Capitulo 1", ignoreCase = true) &&
                            !it.equals("Episodio 1", ignoreCase = true) &&
                            !it.equals("1", ignoreCase = true)
                    }.orEmpty()
                    val displayLabel = "$sLabel · Capítulo 1" + (if (cleanName.isNotBlank()) " · $cleanName" else "")
                    val playerTitle = "${curBaseTitle.ifBlank { curTitle }} · $sLabel · C1"
                    nextTarget = NextEp(
                        code = first.contentId,
                        seriesId = seasonId,
                        displayTitle = displayLabel,
                        playerTitle = playerTitle,
                        epIndex = 0,
                        seasonIndex = nextSeasonIdx,
                        newEpIds = programs.map { it.contentId },
                        newEpNames = programs.map { it.name },
                    )
                    epTitle.text = displayLabel
                    nextCountdown = 5
                    tickNext()
                }
            }.start()
        }
    }

    private fun tickNext() {
        handler.removeCallbacks(nextTick)
        val target = nextTarget
        if (!nextVisible || nextDismissed || target == null) return
        nextCountdownView?.text = "Reproduciendo automáticamente en ${nextCountdown} s…"
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
        playEpisode(
            code = target.code,
            series = target.seriesId,
            title = target.playerTitle,
            epIdx = target.epIndex,
            seasonIdx = target.seasonIndex,
            auto = auto,
        )
    }

    private fun hideNext() {
        handler.removeCallbacks(nextTick)
        nextOverlay?.let { root().removeView(it) }
        nextOverlay = null
        nextVisible = false
        nextCountdownView = null
        nextTarget = null
        nextPlayBtn = null
        setBackgroundFocusBlocked(false)
        if (isTv && introView == null && !errorShown) {
            playPauseBtn?.post { playPauseBtn?.requestFocus() }
        }
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
            "⏭ Sig. C${curEpIndex + 2}"
        } else if (hasNext && curSeasonIndex + 1 < seasonIds.size) {
            "⏭ Sig. ${seasonLabel(curSeasonIndex + 1)}"
        } else {
            "⏭ Sig. episodio"
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
                        val playerTitle = "${curBaseTitle.ifBlank { curTitle }} · $sLabel · C1"
                        playEpisode(
                            code = first.contentId,
                            series = seasonId,
                            title = playerTitle,
                            epIdx = 0,
                            seasonIdx = nextSeasonIdx,
                            auto = false,
                        )
                    }
                }.start()
            }
        }
    }

    /** Reproduce otro capítulo en el mismo motor/vista: la serie nunca sale del reproductor. */
    private fun playEpisode(code: String, series: String, title: String, epIdx: Int, seasonIdx: Int, auto: Boolean = false) {
        val v = playerView ?: return
        hideNext()
        hideResumeDialog()
        nextDismissed = false
        endedShown = false

        // Detener inmediatamente la reproducción previa para que positionMs()
        // no retorne la posición del capítulo anterior mientras carga el nuevo.
        X4KTitan.stop(v)

        // Asignar de inmediato la nueva identidad del episodio antes del hilo de red
        curCode = code
        curTitle = title
        curSeries = series
        curEpIndex = epIdx
        curSeasonIndex = seasonIdx
        topTitle?.text = title

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
        loadingOverlay?.alpha = 1f
        loadingOverlay?.visibility = View.VISIBLE
        firstFrame = false
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
                    showError()
                    return@runOnUiThread
                }
                var medias = runCatching { parseMedias(prep.mediasJson) }.getOrDefault(ArrayList())
                var played = medias.isNotEmpty() && X4KTitan.play(v, "vod", code, title, medias)
                if (!played) {
                    // Segundo intento si el primer cuadro falló
                    medias = runCatching { parseMedias(prep.mediasJson) }.getOrDefault(ArrayList())
                    played = medias.isNotEmpty() && X4KTitan.play(v, "vod", code, title, medias)
                }
                if (!played) {
                    showError()
                    return@runOnUiThread
                }
                curMedias = ArrayList(medias)
                val preferredQ = com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality
                val chosenMedia = if (preferredQ != "Auto") {
                    medias.firstOrNull { it.quality.contains(preferredQ, ignoreCase = true) } ?: medias.firstOrNull()
                } else {
                    medias.firstOrNull()
                }
                currentMediaName = chosenMedia?.name.orEmpty()
                curQuality = chosenMedia?.quality.orEmpty()
                qualityBtn?.text = "⚙ " + formatQualityShort(curQuality)
                declaredLanguages = medias.first().audio
                    .split(',').map { it.trim() }.filter { it.isNotBlank() }
                subtitleLanguages = prep.subsLangs
                    .split(',').map { it.trim() }.filter { it.isNotBlank() }
                if (prep.durationMs > 0 && declaredDurationMs <= 0L) {
                    declaredDurationMs = prep.durationMs
                }
                topTitle?.text = title
                timeTotal?.text = if (declaredDurationMs > 0) fmt(declaredDurationMs) else "--:--"
                seekBar?.progress = 0
                timeNow?.text = "00:00"
                UserLibrary.markEpisodeWatched(this@X4KPlayerAty, series, epIdx + 1)
                updateNextButtonState()

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
    private fun saveHistory(immediate: Boolean = false) {
        if (liveMode) return
        val key = curCode
        if (key.isBlank()) return
        val pos = X4KTitan.positionMs().coerceAtLeast(0L)
        val total = if (declaredDurationMs > 0) declaredDurationMs else durationMs
        if (isFinished(pos, total)) {
            PlaybackHistory.delete(this, key)
            return
        }
        if (!shouldKeep(pos, total)) return
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

    /** Al primer cuadro: reanuda lo pedido desde Inicio, o pregunta si quedó a medias. */
    private fun onFirstFrame() {
        if (liveMode) return
        if (resumeMs > 0 && !resumeApplied) {
            val at = resumeMs
            resumeApplied = true
            resumeAsked = true
            resumeMs = 0
            playerView?.let { X4KTitan.seekTo(it, at) }
            Toast.makeText(this, "Continuando en " + formatResume(at), Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingAutoResume) {
            pendingAutoResume = false
            resumeAsked = true
            val entry = PlaybackHistory.get(this, curCode)
            if (entry != null && shouldKeep(entry.positionMs, entry.durationMs)) {
                playerView?.let { X4KTitan.seekTo(it, entry.positionMs) }
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
            text = "  ▶ Continuar  "
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(blue)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { acceptResume() }
        }
        goBtn.tvControl()
        row.addView(goBtn)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val startBtn = TextView(this).apply {
            text = "  Desde el inicio  "
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
        scheduleHide()
    }

    private fun restartResume() {
        hideResumeDialog()
        playerView?.let {
            X4KTitan.seekTo(it, 0)
            X4KTitan.resume(it)
        }
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
        epgLine?.text = ""
        updateFavButtonState()
        updateLiveBanner()
        handler.removeCallbacks(hideRunnable)
        showControlsBriefly()
        loadingFading = false
        loadingOverlay?.alpha = 1f
        loadingOverlay?.visibility = View.VISIBLE
        firstFrame = false
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
        fav.text = if (isFav) "★ Favorito" else "☆ Añadir a favoritos"
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
        val targets = ArrayList<Int>()
        for (i in 0 until arr.length()) {
            val ch = arr.optJSONObject(i) ?: continue
            val num = ch.optString("channelNumber")
            val cCode = ch.optString("channelCode")
            val name = X4KApi.displayChannelName(cCode, ch.optString("name"))
            val isFav = LiveFavorites.has(this, "xtvlive:$cCode")
            val star = if (isFav) "★ " else ""
            labels += if (num.isBlank()) "$star$name" else "$star$num  ·  $name"
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
            text = "✕  Cerrar"
            setTextColor(0xFFF4F7FC.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundBackground(surface, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { dismissPicker() }
        }
        closeBtn.tvControl(surface, 14)
        val closeId = View.generateViewId().also { closeBtn.id = it }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

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
                    text = if (current) "●  ${seasonLabel(idx)}" else seasonLabel(idx)
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
            card.addView(TextView(this).apply {
                text = "Cargando capítulos de la temporada…"
                setTextColor(0xFF9BA8BD.toInt())
                textSize = 14f
                setPadding(dp(16), dp(18), dp(16), dp(18))
            })
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
                    setStroke(dp(3), Color.WHITE)
                }
                adapter = object : android.widget.BaseAdapter() {
                    override fun getCount(): Int = epIds.size
                    override fun getItem(position: Int): Any = epIds[position]
                    override fun getItemId(position: Int): Long = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val isCurrent = position == curEpIndex
                        val isWatched = UserLibrary.isEpisodeWatched(this@X4KPlayerAty, curSeries, position + 1)
                        val rawName = epNames.getOrNull(position)?.trim().orEmpty()
                        val cleanName = rawName.takeIf {
                            it.isNotBlank() &&
                                !it.equals("Capítulo ${position + 1}", ignoreCase = true) &&
                                !it.equals("Capitulo ${position + 1}", ignoreCase = true) &&
                                !it.equals("Episodio ${position + 1}", ignoreCase = true) &&
                                !it.equals("${position + 1}", ignoreCase = true)
                        }.orEmpty()

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
                            setPadding(dp(14), dp(12), dp(14), dp(12))
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

                        // Columna central: título y estado
                        val textCol = LinearLayout(this@X4KPlayerAty).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(14), 0, dp(12), 0)
                        }
                        textCol.addView(TextView(this@X4KPlayerAty).apply {
                            text = "Capítulo ${position + 1}" + (if (cleanName.isNotBlank()) " · $cleanName" else "")
                            setTextColor(if (isCurrent) Color.WHITE else 0xFFF4F7FC.toInt())
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        })
                        textCol.addView(TextView(this@X4KPlayerAty).apply {
                            text = when {
                                isCurrent -> "● Reproduciendo ahora"
                                isWatched -> "✓ Capítulo visto"
                                else -> "Disponible en orden secuencial"
                            }
                            setTextColor(
                                when {
                                    isCurrent -> blueBright
                                    isWatched -> 0xFF7AD9A8.toInt()
                                    else -> 0xFF9BA8BD.toInt()
                                },
                            )
                            textSize = 12f
                            setPadding(0, dp(2), 0, 0)
                        })
                        rowLayout.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                        // Píldora derecha de acción
                        val actionPill = TextView(this@X4KPlayerAty).apply {
                            text = when {
                                isCurrent -> "▶ Viendo"
                                isWatched -> "↺ Repetir"
                                else -> "▶ Reproducir"
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
                    )
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
        val name = when (code.lowercase().trim()) {
            "spa", "es", "esp" -> "Español"
            "eng", "en" -> "Inglés"
            "por", "pt" -> "Portugués"
            "fra", "fre", "fr" -> "Francés"
            "deu", "ger", "de" -> "Alemán"
            "ita", "it" -> "Italiano"
            "jpn", "ja" -> "Japonés"
            "kor", "ko" -> "Coreano"
            "zho", "chi", "zh" -> "Chino"
            "rus", "ru" -> "Ruso"
            "ara", "ar" -> "Árabe"
            "hin", "hi" -> "Hindi"
            else -> ""
        }
        return name.ifBlank { if (code.isBlank()) role else code.trim().replaceFirstChar { it.uppercase() } }
    }

    // ---------------------------------------------------------------- behavior

    private fun wireGestures(root: FrameLayout) {
        val catcher = View(this).apply {
            setOnClickListener { if (!errorShown && !picking && introView == null) toggleControls() }
            isFocusable = false
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
    }

    private fun exitPlayer() {
        playerView?.let { X4KTitan.stop(it) }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        finish()
    }

    private val hideRunnable = Runnable {
        if (!scrubbing && firstFrame && !errorShown && !picking && resumeOverlay == null && !nextVisible) {
            // Que no quede el foco en una vista oculta: el mando no debe perderse.
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
        val button = playPauseBtn as? FrameLayout ?: return
        button.removeAllViews()
        button.addView(
            icon(if (playing) MODE_PAUSE else MODE_PLAY),
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER),
        )
    }

    private val poller = object : Runnable {
        override fun run() {
            val v = playerView
            if (v != null) {
                val pos = com.chronosbvrx.visionplus.data.xuper.X4KTitan.positionMs().coerceAtLeast(0L)
                if (!liveMode) {
                    val engineDur = com.chronosbvrx.visionplus.data.xuper.X4KTitan.durationMs(v)
                    if (!isBufferWindowDuration(engineDur, pos, declaredDurationMs, liveMode)) {
                        if (engineDur > 0L) {
                            if (engineDur != durationMs) {
                                durationMs = engineDur
                            }
                            if (declaredDurationMs <= 0L) {
                                declaredDurationMs = engineDur
                            }
                        }
                    }
                }
                val total = resolveStableTotalDuration(declaredDurationMs, durationMs, pos, liveMode)
                val hasLivePlayback = liveMode && (pos > 0 || X4KTitan.isPlaying(v) || (System.currentTimeMillis() - startedAtMs > 1200))
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
                if (!firstFrame && !errorShown &&
                    System.currentTimeMillis() - startedAtMs > 20_000
                ) {
                    showError()
                }
                if (!scrubbing) {
                    if (total > 0) {
                        seekBar?.progress = ((pos * 1000) / total).toInt().coerceIn(0, 1000)
                    }
                    timeNow?.text = fmt(pos)
                }
                timeTotal?.text = if (total > 0) fmt(total) else "--:--"
                refreshPlayIcon()
                if (!liveMode) {
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
            if (event.action == KeyEvent.ACTION_UP || event.isCanceled) {
                swallowWakeupKeyCode = KeyEvent.KEYCODE_UNKNOWN
                return true
            }
            if (event.keyCode == swallowWakeupKeyCode) {
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
        if (nextVisible && !picking && introView == null && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY && keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE) {
            // La tarjeta de "siguiente" está al mando: el D-pad la navega y OK la confirma.
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
                togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                if (liveMode) switchChannel(liveIndex - 1)
                else performSeekBy(-10_000L)
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (inModal || introView != null || onControl) return super.onKeyDown(keyCode, event)
                if (liveMode) switchChannel(liveIndex + 1)
                else performSeekBy(10_000L)
                showControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (inModal || introView != null) return super.onKeyDown(keyCode, event)
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
        if (errorShown || picking || nextVisible) return
        val wasHiddenOrUnfocused = !controlsVisible || (isTv && !hasFocusedControl())
        if (wasHiddenOrUnfocused) {
            lastControlsWakeupMs = android.os.SystemClock.uptimeMillis()
        }
        controlsVisible = true
        controls?.visibility = View.VISIBLE
        refreshPlayIcon()
        if (isTv && introView == null) {
            if (!hasFocusedControl()) {
                val target = if (liveMode) channelsBtn else playPauseBtn
                target?.requestFocus()
                target?.post {
                    if (controlsVisible && !isModalActive && !hasFocusedControl()) {
                        target.requestFocus()
                    }
                }
            }
        }
        scheduleHide()
    }

    override fun onDestroy() {
        isPlayerActive = false
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
    }
}
