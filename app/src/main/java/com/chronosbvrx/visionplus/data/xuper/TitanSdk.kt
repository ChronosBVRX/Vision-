package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * The Titan player SDK of the provider's own app, carried inside Vision+: `com.titan.ranger.*`,
 * `com.titans.widget.TitanVideoView` and the engine bridge `q8.p0`, shipped as secondary DEX
 * files (assets/titan/titan-sdk-*.dex, extracted to files on first use) with the native libraries
 * in `jniLibs` (`libranger-jni.so` and the ijk/RenderScript/ed25519 set it loads).
 *
 * The engine is driven exactly like the provider's app does it (see App.java and t5/i.java):
 *  1. `p0.J(workPath, o8.b, o8.c)` + `p0.g0(o8.a)` — the engine and its three callbacks,
 *  2. `NativeJni.y(envJson)` — the session (app, user id, dev id, communication key, play params),
 *  3. `NativeJni.w(entriesJson, "miss")` — the SLB CDN entries,
 *  4. `TitanVideoView.B(program, channelCode, "live")` — the channel.
 *
 * Everything is reflection: the SDK classes live in the bundled DEX, never on this APK's
 * compile classpath.
 */
object TitanSdk {
    private const val TAG = "TitanSdk"
    private const val APP_ID = "com.msandroid.mobile"
    private const val APP_VER = "60507"

    /** The Magis wire key (a8.b.f251a): the engine's communication_key. */
    const val COMMUNICATION_KEY = "37a759c3-fc2b-413d-b424-0bb0e1505a5e"

    /** One playable address of a channel, as the provider's Media bean expects it. */
    data class MediaSpec(
        val name: String,
        val license: String,
        val quality: String,
        val format: String,
    )

    private val DEX_ASSETS = listOf(
        "titan/titan-sdk-1.dex",
        "titan/titan-sdk-2.dex",
        "titan/titan-sdk-3.dex",
    )

    @Volatile
    private var loader: ClassLoader? = null

    @Volatile
    private var booted = false

    private var p0Instance: Any? = null

    private var nativeJniClass: Class<*>? = null
    private var videoViewClass: Class<*>? = null
    private var p0Class: Class<*>? = null
    private var programClass: Class<*>? = null
    private var mediaClass: Class<*>? = null

    /** Extracts and loads the SDK once; safe to call from anywhere. */
    fun load(context: Context): Boolean {
        loader?.let { return true }
        synchronized(this) {
            loader?.let { return true }
            return try {
                val app = context.applicationContext
                val workDir = File(app.filesDir, "titan").apply { mkdirs() }
                val paths = ArrayList<String>()
                DEX_ASSETS.forEachIndexed { index, asset ->
                    val file = File(workDir, "sdk-$index.dex")
                    if (!file.exists() || file.length() == 0L) {
                        app.assets.open(asset).use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    // Android 14+ refuses to load writable dex files: lock them down.
                    file.setReadOnly()
                    paths += file.absolutePath
                }
                val outDir = File(app.codeCacheDir, "titan").apply { mkdirs() }
                // The engine loads its native libraries with System.loadLibrary from its own
                // class loader, which only searches directories it was told about: extract the
                // .so set from this APK into a private directory and hand that one over.
                val libDir = extractNativeLibs(app)
                val cl = runCatching {
                    DexClassLoader(
                        paths.joinToString(File.pathSeparator),
                        outDir.absolutePath,
                        libDir.absolutePath,
                        javaClass.classLoader,
                    )
                }.getOrElse {
                    // Some ROMs still refuse file-backed dex: load the same files in memory.
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
                nativeJniClass = cl.loadClass("com.titan.ranger.NativeJni")
                videoViewClass = runCatching { cl.loadClass("com.titans.widget.TitanVideoView") }.getOrNull()
                p0Class = runCatching { cl.loadClass("q8.p0") }.getOrNull()
                programClass = runCatching { cl.loadClass("com.titan.ranger.bean.Program") }.getOrNull()
                mediaClass = runCatching { cl.loadClass("com.titan.ranger.bean.Media") }.getOrNull()
                loader = cl
                Log.i(
                    TAG,
                    "SDK cargado: jni=${nativeJniClass != null} view=${videoViewClass != null} p0=${p0Class != null} " +
                        "program=${programClass != null} media=${mediaClass != null}",
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "no se pudo cargar el SDK: ${e.message}")
                false
            }
        }
    }

    fun isLoaded(): Boolean = loader != null

    /** Copies this APK's arm .so files into a private directory the SDK loader can search. */
    private fun extractNativeLibs(app: Context): File {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val libDir = File(app.filesDir, "titan-libs").apply { mkdirs() }
        runCatching {
            java.util.zip.ZipFile(app.applicationInfo.sourceDir).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("lib/$abi/") && entry.name.endsWith(".so")) {
                        val out = File(libDir, File(entry.name).name)
                        if (!out.exists() || out.length() != entry.size) {
                            zip.getInputStream(entry).use { input ->
                                out.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "no se pudieron extraer las libs: ${it.message}") }
        return libDir
    }

    private fun nativeInstance(): Any? = try {
        nativeJniClass?.getMethod("g")?.invoke(null)
    } catch (e: Exception) {
        Log.w(TAG, "NativeJni.g() falló: ${e.message}")
        null
    }

    private fun call(method: String, vararg args: Any?): Any? {
        val inst = nativeInstance() ?: return null
        return try {
            val m = inst.javaClass.methods.firstOrNull { it.name == method && it.parameterCount == args.size }
            m?.invoke(inst, *args)
        } catch (e: Exception) {
            Log.w(TAG, "NativeJni.$method falló: ${e.message}")
            null
        }
    }

    /** A no-op implementation of the SDK's callback interfaces (o8.a / o8.b / o8.c). */
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

    /** Boots the native engine and registers the three callbacks (the provider's App.init). */
    fun engineBoot(context: Context): Boolean {
        if (!load(context)) return false
        if (booted) return true
        synchronized(this) {
            if (booted) return true
            return try {
                val p0 = p0Class?.let { clazz ->
                    val companion = runCatching { clazz.getField("f19425l").get(null) }
                        .getOrElse { clazz.getField("l").get(null) }
                    companion.javaClass.getMethod("a").invoke(companion)
                } ?: return false
                p0Instance = p0
                val proxyB = proxyOf("o8.b") ?: return false
                val proxyC = proxyOf("o8.c") ?: return false
                val proxyA = proxyOf("o8.a") ?: return false
                val workPath = File(context.applicationContext.filesDir, "luna").apply { mkdirs() }.absolutePath
                p0.javaClass.methods.first { it.name == "J" && it.parameterCount == 3 }
                    .invoke(p0, workPath, proxyB, proxyC)
                p0.javaClass.methods.first { it.name == "g0" && it.parameterCount == 1 }
                    .invoke(p0, proxyA)
                booted = true
                Log.i(TAG, "motor arrancado en $workPath")
                true
            } catch (e: Exception) {
                Log.e(TAG, "engineBoot falló: ${e.cause ?: e}")
                false
            }
        }
    }

    /**
     * Hands the engine its session, through the engine's own path (p0.e0 → f0): that is what
     * stamps the Env with the internal HTTP server's port before it reaches the native side.
     */
    fun sendSession(userId: String, devId: String, params: String, androidId: String = "0000000000000000"): Boolean {
        val cl = loader ?: return false
        val p0 = p0Instance ?: return false
        return try {
            val envClass = cl.loadClass("com.titan.ranger.bean.Env")
            val env = envClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, String::class.java, java.lang.Integer.TYPE,
            ).newInstance(APP_ID, APP_VER, userId, devId, COMMUNICATION_KEY, params, androidId, -1)
            p0.javaClass.methods.first { it.name == "e0" && it.parameterCount == 1 }.invoke(p0, env)
            Log.i(TAG, "env enviado (user=$userId)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendSession falló: ${e.cause ?: e}")
            false
        }
    }

    /** Hands the engine the SLB CDN entries (the provider's t6.a.a → NativeJni.w). */
    fun sendEntries(entriesJson: String, cacheKey: String = "miss") {
        call("w", entriesJson, cacheKey)
        Log.i(TAG, "entries enviadas (${entriesJson.length} bytes)")
    }

    /** The provider's video view (a FrameLayout hosting the SDK player surface). */
    fun newVideoView(context: Context): View? {
        if (!load(context)) return null
        val cl = loader ?: return null
        return try {
            // The view demands a real resource-backed AttributeSet: inflate the stub layout
            // through a context whose class loader is the SDK's own.
            val wrapper = object : android.content.ContextWrapper(context.applicationContext) {
                override fun getClassLoader(): ClassLoader = cl
            }
            android.view.LayoutInflater.from(context)
                .cloneInContext(wrapper)
                .inflate(com.chronosbvrx.visionplus.R.layout.titan_video_stub, null, false)?.also { view ->
                // AR_ASPECT_FIT_PARENT (0): the frame fits inside the view, letterboxed, never stretched.
                runCatching {
                    view.javaClass.methods.first { it.name == "setAspectRatio" && it.parameterCount == 1 }
                        .invoke(view, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo crear TitanVideoView", e)
            null
        }
    }

    /**
     * Plays one live channel inside the view: builds the provider's Program (name = channel code,
     * business = "live", medias = its CDN addresses, media = the chosen one) and calls
     * `TitanVideoView.B(program, channelCode, "live")`.
     */
    fun playLive(view: View, channelCode: String, channelTitle: String, medias: List<MediaSpec>): Boolean {
        val pClass = programClass ?: return false
        val mClass = mediaClass ?: return false
        if (medias.isEmpty()) return false
        return try {
            val mediaObjs = medias.map { spec ->
                mClass.getConstructor(
                    String::class.java, String::class.java, String::class.java, String::class.java,
                    String::class.java, String::class.java, String::class.java,
                ).newInstance(spec.name, spec.license, "", spec.quality, "", spec.format, "")
            }
            val program = pClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, List::class.java, String::class.java,
                java.lang.Long.TYPE,
            ).newInstance(channelCode, "live", channelTitle, "-1", "live", "user", mediaObjs, medias.first().name, 0L)
            view.javaClass.methods.first { it.name == "B" && it.parameterCount == 3 }
                .invoke(view, program, channelCode, "live")
            Log.i(TAG, "play $channelCode (${medias.size} medias, primera ${medias.first().name})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "playLive falló: ${e.message}")
            false
        }
    }

    /** The view's teardown (the provider calls it when leaving the channel). */
    fun stop(view: View) {
        runCatching { view.javaClass.methods.first { it.name == "C" && it.parameterCount == 0 }.invoke(view) }
        runCatching { view.javaClass.methods.first { it.name == "D" && it.parameterCount == 0 }.invoke(view) }
    }

    /**
     * Plays one film/episode inside the view: the provider's Program with business "vod", the
     * content id as name, its media groups and the chosen one as `media`.
     */
    fun playVod(view: View, contentId: String, title: String, medias: List<MediaSpec>): Boolean {
        val pClass = programClass ?: return false
        val mClass = mediaClass ?: return false
        if (medias.isEmpty()) return false
        return try {
            val mediaObjs = medias.map { spec ->
                mClass.getConstructor(
                    String::class.java, String::class.java, String::class.java, String::class.java,
                    String::class.java, String::class.java, String::class.java,
                ).newInstance(spec.name, spec.license, "", spec.quality, "", spec.format, "")
            }
            val program = pClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, List::class.java, String::class.java,
                java.lang.Long.TYPE,
            ).newInstance(contentId, "vod", title, "1", "vod", "user", mediaObjs, medias.first().name, 0L)
            view.javaClass.methods.first { it.name == "B" && it.parameterCount == 3 }
                .invoke(view, program, contentId, "vod")
            Log.i(TAG, "vod $contentId (${medias.size} medias, primera ${medias.first().name})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "playVod falló: ${e.cause ?: e}")
            false
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
