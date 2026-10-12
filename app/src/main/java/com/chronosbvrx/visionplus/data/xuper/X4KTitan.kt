package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.Log
import android.view.View
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Lo que el motor reporta como "idioma" pero es el códec o "indefinido" (aac, opus…):
 * mostrarlo tal cual deja la lista como "Aac, Aac, Aac".
 */
internal val TRACK_CODECS = setOf(
    "aac", "ac3", "ec3", "mp3", "mp4a", "opus", "flac", "alac", "dts", "dtshd",
    "hevc", "avc", "h264", "h265", "srt", "ass", "ssa", "vtt", "sub", "pgs", "und",
)

/**
 * El código a mostrar para una pista: el del motor cuando es un idioma real (2-4 letras
 * que no son códec); si no, el declarado por el portal para esa posición (las pistas "aac,
 * aac" suelen ser español, inglés… en ese orden); vacío cuando no se sabe.
 */
internal fun trackDisplayLang(engineLang: String, declaredLang: String?): String {
    val e = engineLang.trim().lowercase()
    if (e.length in 2..4 && e.all { it.isLetter() } && e !in TRACK_CODECS) return e
    val d = declaredLang?.trim().orEmpty().lowercase()
    if (d.isNotEmpty()) return d
    return ""
}

/**
 * Etiquetas sin repetir para la lista: a partir de la segunda idéntica se le agrega
 * su pista para distinguirlas ("Español", "Español · Pista 4").
 */
internal fun dedupeTrackLabels(labels: List<String>, ids: List<Int>): List<String> {
    val seen = HashSet<String>()
    return labels.mapIndexed { index, label ->
        if (seen.add(label)) label else "$label · Pista ${ids.getOrElse(index) { index + 1 }}"
    }
}

/**
 * Xuper's own Titan SDK as shipped inside the X4K app (com.xuper.netxxus): the engine
 * (`com.titan.ranger.*`), its player view (`ed.e`) and the app-side facade (`yc.h`), carried
 * here as DEX assets with its native libraries.
 *
 * Boot sequence, exactly like the provider's app does it (y9/d.p + za/g):
 *  1. `yc.h.m.a().r(workPath, playerCallback, rangerCallback)` — engine start,
 *  2. `yc.h.m.a().B(Env(app, app_ver, user_id, dev_id, communication_key, params, android_id, port))`,
 *  3. `yc.h.m.a().A(list)` — CDN entries,
 *  4. `view.A(program, code, buss)` — playback.
 */
object X4KTitan {
    private const val TAG = "X4KTitan"

    const val COMMUNICATION_KEY = "568e194f-d170-4369-96e8-4a457f641880"
    const val APP_ID = "com.android.msandroid"
    const val APP_VER = "49902"

    private val DEX_ASSETS = listOf(
        "x4k/x4k-sdk-1.dex",
        "x4k/x4k-sdk-2.dex",
        "x4k/x4k-sdk-3.dex",
    )

    private val LIB_NAMES = listOf(
        "libranger-jni.so",
        "libijkffmpeg.so",
        "libijkplayer.so",
        "libijksdl.so",
        "libtnet-3.1.14.so",
        "libc++_shared.so",
    )

    @Volatile
    private var loader: ClassLoader? = null

    @Volatile
    private var booted = false

    @Volatile
    private var envSent = false

    @Volatile
    private var lastSentEntries: String = ""

    @Volatile
    private var lastSentParams: String = ""

    private var facade: Any? = null

    private var envClass: Class<*>? = null
    private var programClass: Class<*>? = null
    private var mediaClass: Class<*>? = null
    private var entryClass: Class<*>? = null
    private var viewClass: Class<*>? = null

    /** One playable address, as the provider's Media bean expects it. */
    data class MediaSpec(
        val name: String,
        val license: String,
        val audio: String = "",
        val quality: String = "",
        val encodeFormat: String = "",
        val videoFormat: String = "",
    )

    fun isLoaded(): Boolean = loader != null

    /** True when the engine is booted and already has the given SLB entries and play params. */
    fun isConfigured(entriesJson: String?, playParams: String): Boolean {
        return booted && envSent &&
            (entriesJson.isNullOrBlank() || entriesJson == lastSentEntries) &&
            playParams == lastSentParams
    }

    /** Boots the engine and sends SLB entries + Env only if not already configured with them. */
    fun ensureConfigured(
        context: Context,
        userId: String,
        devId: String,
        androidId: String,
        entriesJson: String?,
        playParams: String,
    ): Boolean {
        synchronized(this) {
            if (!boot(context)) return false
            if (!entriesJson.isNullOrBlank() && entriesJson != lastSentEntries) {
                if (sendEntriesCdnList(entriesJson)) {
                    lastSentEntries = entriesJson
                }
            }
            if (!envSent || playParams != lastSentParams) {
                if (sendEnv(userId = userId, devId = devId, androidId = androidId, params = playParams)) {
                    lastSentParams = playParams
                    envSent = true
                }
            }
            return true
        }
    }

    fun load(context: Context): Boolean {
        loader?.let { return true }
        synchronized(this) {
            loader?.let { return true }
            return try {
                val app = context.applicationContext
                val workDir = File(app.filesDir, "x4k").apply { mkdirs() }
                val paths = ArrayList<String>()
                DEX_ASSETS.forEachIndexed { index, asset ->
                    val file = File(workDir, "sdk-$index.dex")
                    if (!file.exists() || file.length() == 0L) {
                        app.assets.open(asset).use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    file.setReadOnly()
                    paths += file.absolutePath
                }
                val outDir = File(app.codeCacheDir, "x4k").apply { mkdirs() }
                val libDir = extractNativeLibs(app)
                val cl = runCatching {
                    DexClassLoader(
                        paths.joinToString(File.pathSeparator),
                        outDir.absolutePath,
                        libDir.absolutePath,
                        javaClass.classLoader,
                    )
                }.getOrElse {
                    Log.w(TAG, "DexClassLoader rechazado (${it.message}); cargando en memoria")
                    val buffers = DEX_ASSETS.map { asset ->
                        val bytes = app.assets.open(asset).use { input -> input.readBytes() }
                        java.nio.ByteBuffer.allocateDirect(bytes.size).apply {
                            put(bytes)
                            position(0)
                        }
                    }
                    dalvik.system.InMemoryDexClassLoader(buffers.toTypedArray(), javaClass.classLoader)
                }
                envClass = cl.loadClass("com.titan.ranger.bean.Env")
                programClass = runCatching { cl.loadClass("com.titan.ranger.bean.Program") }.getOrNull()
                mediaClass = runCatching { cl.loadClass("com.titan.ranger.bean.Media") }.getOrNull()
                entryClass = runCatching { cl.loadClass("com.titan.ranger.bean.Entry") }.getOrNull()
                viewClass = runCatching { cl.loadClass("ed.e") }.getOrNull()
                loader = cl
                Log.i(
                    TAG,
                    "SDK X4K cargado: env=${envClass != null} program=${programClass != null} " +
                        "media=${mediaClass != null} entry=${entryClass != null} view=${viewClass != null}",
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "no se pudo cargar el SDK X4K: ${e.message}")
                false
            }
        }
    }

    /** Copies the X4K .so set from assets into a private directory the loader can search. */
    private fun extractNativeLibs(app: Context): File {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val libDir = File(app.filesDir, "x4k-libs").apply { mkdirs() }
        LIB_NAMES.forEach { name ->
            val out = File(libDir, name)
            if (!out.exists() || out.length() == 0L) {
                runCatching {
                    app.assets.open("x4k/lib/$abi/$name").use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }.onFailure { Log.w(TAG, "lib $name no disponible: ${it.message}") }
            }
        }
        return libDir
    }

    /** The view receiving engine callbacks (wc.a/wc.b route into it, like the provider's bd.k does). */
    @Volatile
    private var activeView: View? = null

    fun bindView(view: View) {
        activeView = view
    }

    /** A no-op implementation of an SDK callback interface (kept for non-routing uses). */
    private fun proxyOf(interfaceName: String): Any? {
        val cl = loader ?: return null
        return try {
            val iface = cl.loadClass(interfaceName)
            Proxy.newProxyInstance(cl, arrayOf(iface), InvocationHandler { _, method, _ ->
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "proxy $interfaceName falló: ${e.message}")
            null
        }
    }

    /**
     * Routes every callback of the interface to the bound view (same method name + signature).
     * This is what the provider's own bd.k holder does, and it is essential: the video size
     * notification (view.l) is what makes the render surface measure with the right aspect.
     */
    private fun routingProxy(interfaceName: String): Any? {
        val cl = loader ?: return null
        return try {
            val iface = cl.loadClass(interfaceName)
            Proxy.newProxyInstance(cl, arrayOf(iface), InvocationHandler { _, method, args ->
                val name = method.name
                if (name != "hashCode" && name != "equals" && name != "toString") {
                    val target = activeView
                    if (target != null) {
                        val m = target.javaClass.methods.firstOrNull {
                            it.name == name && it.parameterTypes.contentEquals(method.parameterTypes)
                        }
                        if (m != null && m.returnType == java.lang.Void.TYPE) {
                            target.post {
                                runCatching { m.invoke(target, *(args ?: emptyArray<Any?>())) }
                                    .onFailure { Log.w(TAG, "callback $name falló: ${it.message}") }
                            }
                        }
                    }
                }
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "routingProxy $interfaceName falló: ${e.message}")
            null
        }
    }

    /**
     * The provider's own callback handler (bd.k.f3996a): its f()/g() return the two callback
     * instances the engine factory expects. Falls back to no-op proxies.
     */
    private fun callbackPair(cl: ClassLoader): Pair<Any?, Any?> {
        runCatching {
            val holderClass = cl.loadClass("bd.k")
            val holder = holderClass.getField("f3996a").get(null)
            val first = holder.javaClass.methods.first { it.name == "f" && it.parameterCount == 0 }
                .invoke(holder)
            val second = holder.javaClass.methods.first { it.name == "g" && it.parameterCount == 0 }
                .invoke(holder)
            if (first != null && second != null) {
                Log.i(TAG, "callbacks del proveedor (bd.k)")
                return first to second
            }
        }.onFailure { Log.w(TAG, "bd.k no utilizable: ${it.message}") }
        return routingProxy("wc.a") to routingProxy("wc.b")
    }

    /** Boots the engine: `yc.h.m.a().r(workPath, callbacks...)`. */
    fun boot(context: Context): Boolean {
        if (!load(context)) return false
        if (booted) return true
        synchronized(this) {
            if (booted) return true
            return try {
                val cl = loader ?: return false
                val facadeClass = cl.loadClass("yc.h")
                val companion = facadeClass.getField("m").get(null)
                val instance = companion.javaClass.methods.first { it.name == "a" && it.parameterCount == 0 }
                    .invoke(companion) ?: return false
                facade = instance
                val (cb1, cb2) = callbackPair(cl)
                if (cb1 == null || cb2 == null) {
                    Log.e(TAG, "sin callbacks, no se puede arrancar")
                    return false
                }
                val workPath = File(context.applicationContext.filesDir, "x4k-luna").apply { mkdirs() }.absolutePath
                instance.javaClass.methods.first { it.name == "r" && it.parameterCount == 3 }
                    .invoke(instance, workPath, cb1, cb2)
                booted = true
                Log.i(TAG, "motor X4K arrancado en $workPath")
                true
            } catch (e: Exception) {
                Log.e(TAG, "boot X4K falló: ${e.cause ?: e}")
                false
            }
        }
    }

    /** Hands the engine its session: `yc.h.m.a().B(Env(...))`. */
    fun sendEnv(
        userId: String,
        devId: String,
        androidId: String,
        params: String = "",
        appVer: String = APP_VER,
    ): Boolean {
        val cl = loader ?: return false
        val f = facade ?: return false
        return try {
            val env = envClass!!.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, String::class.java, java.lang.Integer.TYPE,
            ).newInstance(APP_ID, appVer, userId, devId, COMMUNICATION_KEY, params, androidId, -1)
            f.javaClass.methods.first { it.name == "B" && it.parameterCount == 1 }.invoke(f, env)
            Log.i(TAG, "env X4K enviado (user=$userId)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendEnv X4K falló: ${e.cause ?: e}")
            false
        }
    }

    /** CDN entries the engine expects (from the portal's getSlbInfo cdn_list). */
    fun newEntry(
        cdnIdMark: String,
        tag: String,
        ruleIdMark: String,
        mainAddr: String,
        mainAddrMark: String,
        sparedAddr: String,
        sparedAddrMark: String,
        slbFlag: String,
        link: String,
        signType: String,
        serial: Int,
        urls: List<String>,
        groupIdMark: String,
    ): Any? {
        val cl = loader ?: return null
        return try {
            entryClass!!.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, java.lang.Integer.TYPE, java.lang.Integer.TYPE,
                List::class.java, String::class.java,
            ).newInstance(
                cdnIdMark, tag, ruleIdMark, mainAddr, mainAddrMark, sparedAddr, sparedAddrMark,
                slbFlag, link, signType, serial, 1, urls, groupIdMark,
            )
        } catch (e: Exception) {
            Log.w(TAG, "newEntry falló: ${e.message}")
            null
        }
    }

    fun sendEntries(entries: List<Any>): Boolean {
        val f = facade ?: return false
        return try {
            f.javaClass.methods.first { it.name == "A" && it.parameterCount == 1 }.invoke(f, entries)
            Log.i(TAG, "entries X4K enviadas (${entries.size})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendEntries X4K falló: ${e.message}")
            false
        }
    }

    /**
     * Builds the engine entries straight from the portal's getSlbInfo `cdn_list`
     * (exactly like the provider's za.g.a does it).
     */
    fun sendEntriesCdnList(cdnListJson: String): Boolean {
        return try {
            val arr = org.json.JSONArray(cdnListJson)
            val entries = ArrayList<Any>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val urls = ArrayList<String>()
                val urlArr = c.optJSONArray("url_list") ?: org.json.JSONArray()
                for (j in 0 until urlArr.length()) {
                    urls += urlArr.getJSONObject(j).optString("url")
                }
                val first = urls.firstOrNull().orEmpty()
                fun q(key: String): String =
                    Regex("[?&]$key=([^&]+)").find(first)?.groupValues?.get(1).orEmpty()
                val serial = c.optInt("serial_number")
                val entry = newEntry(
                    cdnIdMark = c.optString("cdn_id_mark"),
                    tag = c.optString("tag"),
                    ruleIdMark = c.optString("rule_id_mark"),
                    mainAddr = c.optString("main_addr"),
                    mainAddrMark = c.optString("main_addr_mark"),
                    sparedAddr = c.optString("spared_addr"),
                    sparedAddrMark = c.optString("spared_addr_mark"),
                    slbFlag = if (c.optString("cdn_type") == "1") "slb" else "",
                    link = q("link"),
                    signType = q("sign_type"),
                    serial = arr.length() - serial,
                    urls = urls,
                    groupIdMark = c.optString("group_id_mark"),
                ) ?: continue
                entries += entry
            }
            sendEntries(entries)
        } catch (e: Exception) {
            Log.w(TAG, "sendEntriesCdnList falló: ${e.cause ?: e}")
            false
        }
    }

    /** The provider's player view (`ed.e`, a FrameLayout that manages its own surface). */
    fun newVideoView(context: Context): View? {
        if (!load(context)) return null
        return try {
            val view = viewClass!!.getConstructor(Context::class.java).newInstance(context) as View
            runCatching {
                view.javaClass.methods.first { it.name == "setAspectRatio" && it.parameterCount == 1 }
                    .invoke(view, 0)
            }
            view
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo crear la vista X4K", e)
            null
        }
    }

    private fun buildMedias(medias: List<MediaSpec>): List<Any> {
        val mClass = mediaClass ?: return emptyList()
        return medias.map { spec ->
            mClass.getConstructor(
                String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, String::class.java,
            ).newInstance(spec.name, spec.license, spec.audio, spec.quality, spec.encodeFormat, spec.videoFormat)
        }
    }

    /** Plays one program: `view.A(Program(code, buss, title, "-1", "-1", "user", medias, firstName, 0L), code, buss)`. */
    fun play(view: View, buss: String, code: String, title: String, medias: List<MediaSpec>): Boolean {
        val pClass = programClass ?: return false
        if (medias.isEmpty()) return false
        return try {
            val mediaObjs = buildMedias(medias)
            val program = pClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, List::class.java, String::class.java,
                java.lang.Long.TYPE,
            ).newInstance(code, buss, title, "-1", "-1", "user", mediaObjs, medias.first().name, 0L)
            view.javaClass.methods.first { it.name == "A" && it.parameterCount == 3 }
                .invoke(view, program, code, buss)
            Log.i(TAG, "play X4K $buss $code (${medias.size} medias, primera ${medias.first().name})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "play X4K falló: ${e.cause ?: e}")
            false
        }
    }

    /** The view's teardown. */
    fun stop(view: View) {
        runCatching { view.javaClass.methods.first { it.name == "B" && it.parameterCount == 0 }.invoke(view) }
    }

    /** Switches active video stream/resolution to the specified media code (`view.setMedia(mediaCode)`). */
    fun setMedia(view: View, mediaCode: String): Boolean = try {
        view.javaClass.methods.first { it.name == "setMedia" && it.parameterCount == 1 }
            .invoke(view, mediaCode)
        Log.i(TAG, "setMedia X4K exitoso: $mediaCode")
        true
    } catch (e: Exception) {
        Log.w(TAG, "setMedia X4K falló: ${e.cause ?: e}")
        false
    }

    /** Aspect mode (Bilibili measure helper scale): 0 fit, 1 fill, 2 wrap, 3 stretch, 4 16:9, 5 4:3. */
    fun setAspect(view: View, ratio: Int) {
        runCatching {
            view.javaClass.methods.first { it.name == "setAspectRatio" && it.parameterCount == 1 }
                .invoke(view, ratio)
        }
    }

    fun pause(view: View) {
        runCatching { view.javaClass.methods.first { it.name == "u" && it.parameterCount == 0 }.invoke(view) }
    }

    fun resume(view: View) {
        runCatching { view.javaClass.methods.first { it.name == "v" && it.parameterCount == 0 }.invoke(view) }
    }

    fun seekTo(view: View, ms: Long) {
        runCatching {
            view.javaClass.methods.first { it.name == "w" && it.parameterCount == 1 }
                .invoke(view, ms)
        }
    }

    /** Jumps [deltaMs] from the current position (negative rewinds). */
    fun seekBy(view: View, deltaMs: Long) {
        val pos = positionMs().coerceAtLeast(0L)
        seekTo(view, (pos + deltaMs).coerceAtLeast(0L))
    }

    /** The stream's audio tracks as (language, trackId) pairs. */
    fun audioTracks(view: View): List<Pair<String, Int>> = try {
        val list = view.javaClass.methods.first { it.name == "getAudioTracks" && it.parameterCount == 0 }
            .invoke(view) as? List<*> ?: emptyList<Any>()
        list.mapNotNull { track ->
            if (track == null) return@mapNotNull null
            val lang = runCatching {
                track.javaClass.methods.first { it.name == "getLang" && it.parameterCount == 0 }.invoke(track) as? String
            }.getOrNull().orEmpty()
            val id = runCatching {
                track.javaClass.methods.first { it.name == "getTrackId" && it.parameterCount == 0 }.invoke(track) as? Int
            }.getOrNull() ?: return@mapNotNull null
            lang to id
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Selects an audio track by language (falls back to the index), the provider's own rule. */
    fun selectAudio(view: View, lang: String, index: Int): Boolean = try {
        view.javaClass.methods.first {
            it.name == "x" && it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == java.lang.Integer.TYPE
        }.invoke(view, lang, index) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }

    /**
     * The engine's own track list for the playing stream: (kind, lang, id) triples where kind is
     * "audio", "subtitle" or "video". Comes from the facade's status bean (yc.h.p).
     */
    fun tracks(): List<Triple<String, String, Int>> {
        val f = facade ?: return emptyList()
        return try {
            val status = f.javaClass.methods.first {
                it.name == "p" && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
            }.invoke(f, 0) ?: return emptyList()
            val list = status.javaClass.methods.first { it.name == "f" && it.parameterCount == 0 }
                .invoke(status) as? List<*> ?: return emptyList()
            list.mapNotNull { item ->
                if (item == null) return@mapNotNull null
                val id = runCatching {
                    item.javaClass.methods.first { it.name == "b" && it.parameterCount == 0 }.invoke(item) as? Int
                }.getOrNull() ?: return@mapNotNull null
                val strings = item.javaClass.methods.filter {
                    it.parameterCount == 0 && it.returnType == String::class.java && it.name != "toString"
                }.mapNotNull { m -> runCatching { m.invoke(item) as? String }.getOrNull() }
                val kind = strings.firstOrNull { it == "subtitle" || it == "audio" || it == "video" } ?: "unknown"
                val lang = strings.firstOrNull { Regex("^[a-zA-Z]{2,4}$").matches(it) } ?: ""
                Triple(kind, lang, id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Selects one engine track: the facade's own setting key (`E(viewId, kind, trackId)`). */
    fun selectTrack(kind: String, trackId: Int): Boolean {
        val f = facade ?: return false
        return try {
            f.javaClass.methods.first {
                it.name == "E" && it.parameterCount == 3 &&
                    it.parameterTypes[1] == String::class.java && it.parameterTypes[2] == java.lang.Integer.TYPE
            }.invoke(f, 0, kind, trackId)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isPlaying(view: View): Boolean = try {
        view.javaClass.methods.first { it.name == "r" && it.parameterCount == 0 }.invoke(view) as? Boolean
            ?: false
    } catch (e: Exception) {
        false
    }

    /** Current position (ms), straight from the engine (-1 when unknown). */
    fun positionMs(): Long = try {
        val f = facade ?: return -1L
        val m = f.javaClass.methods.firstOrNull {
            it.name == "o" && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
        }
        (m?.invoke(f, 0) as? Number)?.toLong() ?: -1L
    } catch (e: Exception) {
        -1L
    }

    /** Engine duration (ms), queried from view, facade status bean, or active engine (-1 when unknown). */
    fun durationMs(view: View? = null): Long {
        return try {
            // 1. Consulta directa a la vista del reproductor (ed.e.getDuration -> p.c())
            if (view != null) {
                val m = view.javaClass.methods.firstOrNull {
                    (it.name == "getDuration" || it.name == "duration") && it.parameterCount == 0
                }
                val d = (m?.invoke(view) as? Number)?.toLong()
                if (d != null && d > 0L) return d
            }
            val f = facade
            if (f != null) {
                // 2. Consulta al bean uc.c vía yc.h.p(0).c()
                val status = f.javaClass.methods.firstOrNull {
                    it.name == "p" && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
                }?.invoke(f, 0)
                if (status != null) {
                    val durMethod = status.javaClass.methods.firstOrNull {
                        (it.name == "c" || it.name == "getDuration" || it.name == "duration" || it.name == "e" || it.name == "d") &&
                            it.parameterCount == 0 && (it.returnType == java.lang.Long.TYPE || it.returnType == java.lang.Integer.TYPE)
                    }
                    val d = (durMethod?.invoke(status) as? Number)?.toLong()
                    if (d != null && d > 0L) return d
                }
                // 3. Consulta al arreglo de motores yc.e (campo 'b' en yc.h) -> W() (IMediaPlayer.getDuration)
                val bField = runCatching {
                    f.javaClass.declaredFields.firstOrNull { it.name == "b" }?.apply { isAccessible = true }
                }.getOrNull()
                val playerArr = (bField?.get(f) as? Array<*>)
                val activeEngine = playerArr?.getOrNull(0)
                if (activeEngine != null) {
                    val wMethod = activeEngine.javaClass.methods.firstOrNull {
                        (it.name == "W" || it.name == "getDuration" || it.name == "duration") && it.parameterCount == 0
                    }
                    val wDur = (wMethod?.invoke(activeEngine) as? Number)?.toLong()
                    if (wDur != null && wDur > 0L) return wDur

                    // 4. Consulta directa a IMediaPlayer (campo 'n' en yc.e)
                    val nField = runCatching {
                        activeEngine.javaClass.declaredFields.firstOrNull { it.name == "n" }?.apply { isAccessible = true }
                    }.getOrNull()
                    val ijk = nField?.get(activeEngine)
                    if (ijk != null) {
                        val ijkDur = ijk.javaClass.methods.firstOrNull {
                            it.name == "getDuration" && it.parameterCount == 0
                        }?.invoke(ijk) as? Number
                        val d = ijkDur?.toLong()
                        if (d != null && d > 0L) return d
                    }
                }
            }
            -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * A `bd.a` listener proxy: the engine reports playback progress (`P3(positionMs)`),
     * duration (`V0(durationMs)`) and media completion (`z2()`) through it.
     */
    fun makePlayerListener(
        onPosition: (Long) -> Unit,
        onDuration: (Long) -> Unit,
        onCompletion: () -> Unit = {},
    ): Any? {
        val cl = loader ?: return null
        return try {
            val iface = cl.loadClass("bd.a")
            Proxy.newProxyInstance(cl, arrayOf(iface), InvocationHandler { _, method, args ->
                when (method.name) {
                    "P3" -> (args?.firstOrNull() as? Number)?.toLong()?.let(onPosition)
                    "V0" -> (args?.firstOrNull() as? Number)?.toLong()?.let(onDuration)
                    "z2" -> onCompletion()
                }
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "listener bd.a falló: ${e.message}")
            null
        }
    }

    fun setPlayerListener(view: View, listener: Any?) {
        if (listener == null) return
        runCatching {
            view.javaClass.methods.first { it.name == "setPlayerListener" && it.parameterCount == 1 }
                .invoke(view, listener)
        }
    }
}
