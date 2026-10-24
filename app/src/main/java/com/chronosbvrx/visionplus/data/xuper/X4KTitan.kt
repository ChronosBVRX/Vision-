package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.Log
import android.view.View
import com.chronosbvrx.visionplus.BuildConfig
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
 * Sello de la extracción del motor: versión de la app, tamaño y sha256 de cada DEX copiado.
 *
 * Sin sello no se puede saber si un `sdk-N.dex` quedó a medias (una copia interrumpida por
 * el sistema deja el archivo con bytes pero truncado) ni si viene de una versión anterior
 * del APK. Ese archivo truncado es el que producía el `VerifyError: Verifier rejected
 * class yc.h … can't resolve java.lang.String` visto en la tele de campo: el DEX abre, pero
 * una clase al final ya no verifica. El manifiesto permite detectarlo y volver a copiar.
 *
 * El sha256 cubre el hueco que dejaba el tamaño: una copia corrompida que mide lo mismo
 * (bytes cambiados en el sitio) pasaba el sello y la tele se quedaba sin reproducir para
 * siempre. Y distingue las dos causas cuando ART rechaza una clase: copia dañada
 * (`ENGINE_DEX_MISMATCH`) o bytes idénticos al APK (`ENGINE_VERIFY_FAIL`).
 */
internal const val ENGINE_MANIFEST_NAME = "engine-manifest.txt"

/** Archivo con la generación extraída: una RUTA de DEX nueva fuerza artefactos ART nuevos. */
internal const val ENGINE_REVISION_FILE = "x4k-rev"

/**
 * Marca de "hay que verificar el contenido": se enciende cuando el motor falló (o se reparó)
 * y se apaga en cuanto una carga verifica bien. Así los equipos sanos NO leen 17 MB de hash en
 * cada arranque del reproductor (en una caja lenta eso era hasta 1 s antes del primer cuadro).
 */
internal const val ENGINE_VERIFY_FLAG = "x4k-verify"

/** Tope de generaciones; al llegar aquí se vuelve a 0 (la carpeta vieja se borra igual). */
internal const val ENGINE_REVISION_MAX = 20

/** Sello leído del manifiesto (versión + tamaño/sha por DEX). */
internal data class EngineSeal(
    val version: Int,
    val sizes: Map<String, Long>,
    val hashes: Map<String, String>,
)

/** Texto del manifiesto: `vc=<versionCode>`, `<archivo>=<bytes>` y `sha:<archivo>=<hex>`. */
internal fun engineManifest(
    versionCode: Int,
    sizes: Map<String, Long>,
    hashes: Map<String, String> = emptyMap(),
): String = buildString {
    append("vc=").append(versionCode).append('\n')
    sizes.toSortedMap().forEach { (name, size) -> append(name).append('=').append(size).append('\n') }
    hashes.toSortedMap().forEach { (name, sha) -> append("sha:").append(name).append('=').append(sha).append('\n') }
}

/** Lee el manifiesto; texto vacío o ilegible = sin sello (se vuelve a extraer). */
internal fun parseEngineSeal(text: String): EngineSeal {
    var version = 0
    val sizes = LinkedHashMap<String, Long>()
    val hashes = LinkedHashMap<String, String>()
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val eq = line.indexOf('=')
        if (eq <= 0) return@forEach
        val key = line.substring(0, eq)
        val value = line.substring(eq + 1)
        when {
            key == "vc" -> version = value.toIntOrNull() ?: 0
            key.startsWith("sha:") -> {
                val name = key.removePrefix("sha:")
                if (value.length == 64) hashes[name] = value.lowercase()
            }
            else -> value.toLongOrNull()?.let { sizes[key] = it }
        }
    }
    return EngineSeal(version, sizes, hashes)
}

/** Compatibilidad con el lector viejo (versión + tamaños). */
internal fun parseEngineManifest(text: String): Pair<Int, Map<String, Long>> {
    val seal = parseEngineSeal(text)
    return seal.version to seal.sizes
}

/** sha256 en hexadecimal de un bloque de bytes (minúsculas, 64 caracteres). */
internal fun sha256Of(bytes: ByteArray): String = sha256Hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))

/** sha256 en hexadecimal de un archivo; `null` si no se pudo leer. */
internal fun sha256Of(file: File): String? = runCatching {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    sha256Hex(digest.digest())
}.getOrNull()

private fun sha256Hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xFF
        append(HEX[value ushr 4]).append(HEX[value and 0x0F])
    }
}

private const val HEX = "0123456789abcdef"

/**
 * ¿Hay que volver a copiar este archivo del motor desde el APK?
 *
 * Sí cuando falta, quedó vacío, no hay sello, el sello es de OTRA versión de la app (el
 * motor pudo cambiar), el tamaño no coincide con el que se copió (copia truncada) o el
 * sha256 no coincide con el del archivo que se selló (copia corrompida del mismo tamaño).
 */
internal fun engineFileNeedsExtraction(
    name: String,
    exists: Boolean,
    length: Long,
    manifestVersion: Int,
    appVersionCode: Int,
    sizes: Map<String, Long>,
    expectedSha: String? = null,
    actualSha: String? = null,
): Boolean {
    if (!exists || length <= 0L) return true
    if (manifestVersion != appVersionCode) return true
    val expected = sizes[name] ?: return true
    if (expected != length) return true
    return expectedSha != null && actualSha != null && expectedSha != actualSha
}

/**
 * ¿El archivo que ya estaba era una copia DAÑADA (y no solo de otra versión)? Se usa para
 * reportar la auto-reparación sin llenar la telemetría en cada actualización de la app.
 */
internal fun engineFileWasCorrupt(
    exists: Boolean,
    length: Long,
    manifestVersion: Int,
    appVersionCode: Int,
    sizes: Map<String, Long>,
    name: String,
    expectedSha: String? = null,
    actualSha: String? = null,
): Boolean {
    if (!exists || length <= 0L) return false
    if (manifestVersion != appVersionCode) return false
    val expected = sizes[name] ?: return false
    if (expected != length) return true
    return expectedSha != null && actualSha != null && expectedSha != actualSha
}

/** Generación de la extracción leída del archivo (basura → 0). */
internal fun engineRevisionFrom(text: String): Int =
    text.trim().toIntOrNull()?.coerceIn(0, ENGINE_REVISION_MAX) ?: 0

/** Nombre de la carpeta de la generación (`x4k-r0`, `x4k-r1`…): cambia la ruta de los DEX. */
internal fun engineDirName(revision: Int): String = "x4k-r${revision.coerceIn(0, ENGINE_REVISION_MAX)}"

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

    /** Carpeta de los DEX extraídos (dentro de `filesDir`). */
    private const val DIR_DEX = "x4k"

    /** Carpeta de las librerías nativas extraídas. */
    private const val DIR_LIBS = "x4k-libs"

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

    /**
     * La última carga falló con un error de enlace/verificación (`LinkageError`): el DEX
     * extraído está dañado o es de otra versión. Habilita la auto-reparación (borrar y
     * volver a extraer) una sola vez por arranque.
     */
    @Volatile
    private var linkageFailure = false

    /**
     * Cuántas veces se puede borrar y re-extraer el motor en ESTE proceso. Si tras la
     * reparación el bytecode sigue sin verificar, el problema no es una copia corta: se
     * deja de copiar 34 MB en cada intento (el equipo no reproduciría, pero no se castiga
     * el almacenamiento).
     */
    @Volatile
    private var healBudget = 1

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
                // ¿El motor YA viene dentro del APK (`classes2/3/4.dex`, estilo Crystal)? Android
                // lo dejó verificado al instalar: no hay nada que copiar ni verificar en runtime
                // (era el origen del bug de ART en una ZTE). Las libs nativas ya viajan en
                // `lib/<abi>` (build X4K), así que `System.loadLibrary` del motor las encuentra
                // sin precargar nada: antes se colaba la copia del motor legado y su JNI_OnLoad
                // mataba la reproducción en un Chromecast.
                val bundledAllowed = runCatching {
                    com.chronosbvrx.visionplus.data.config.RemoteConfig
                        .flag(app, "engine_bundled", true)
                }.getOrDefault(true)
                val bundled = bundledAllowed && runCatching {
                    Class.forName("yc.h", false, X4KTitan::class.java.classLoader)
                }.isSuccess
                if (bundled) {
                    adoptLoader(X4KTitan::class.java.classLoader)
                    runCatching { File(app.filesDir, ENGINE_VERIFY_FLAG).delete() }
                    Log.i(
                        TAG,
                        "SDK X4K desde el APK (sin extraer): env=${envClass != null} " +
                            "program=${programClass != null} view=${viewClass != null}",
                    )
                    com.chronosbvrx.visionplus.data.diag.Diagnostics.engine("cargado")
                    return true
                }
                val revision = readRevision(app)
                // Carpeta legada (`files/x4k`): el motor vive ahora en generaciones `x4k-rN`.
                // Se libera en la primera apertura (17 MB en una tele con poco espacio).
                runCatching { File(app.filesDir, DIR_DEX).takeIf { it.exists() }?.deleteRecursively() }
                val workDir = File(app.filesDir, engineDirName(revision)).apply { mkdirs() }
                val manifestFile = File(workDir, ENGINE_MANIFEST_NAME)
                val seal = parseEngineSeal(
                    runCatching { manifestFile.readText() }.getOrDefault("")
                )
                val appVersion = BuildConfig.VERSION_CODE
                // Un motor sellado por otra versión de la app pudo cambiar: se re-extrae.
                val staleEngine = seal.version != appVersion
                // Solo se hashea el contenido si hay una marca de fallo previo (o si el sello
                // no trae huella); en régimen sano manda el tamaño y no se leen 17 MB.
                val verifyContent = File(app.filesDir, ENGINE_VERIFY_FLAG).exists()
                val sizes = LinkedHashMap<String, Long>()
                val hashes = LinkedHashMap<String, String>()
                val paths = ArrayList<String>()
                var copyMismatch = false
                DEX_ASSETS.forEachIndexed { index, asset ->
                    val file = File(workDir, "sdk-$index.dex")
                    val expectedSha = seal.hashes[file.name]
                    val actualSha = if (verifyContent && file.exists() && expectedSha != null) {
                        sha256Of(file)
                    } else {
                        null
                    }
                    val wasCorrupt = engineFileWasCorrupt(
                        file.exists(), file.length(), seal.version, appVersion, seal.sizes, file.name,
                        expectedSha, actualSha,
                    )
                    val needs = engineFileNeedsExtraction(
                        file.name, file.exists(), file.length(), seal.version, appVersion, seal.sizes,
                        expectedSha, actualSha,
                    )
                    if (needs) {
                        if (expectedSha != null && actualSha != null && expectedSha != actualSha) {
                            copyMismatch = true
                        }
                        // El sha se calcula MIENTRAS se copia: sellar no cuesta una lectura extra.
                        val copiedSha = copyAssetAtomically(app, asset, file)
                            ?: throw java.io.IOException("no se pudo extraer $asset")
                        if (wasCorrupt) {
                            val why = if (expectedSha != null && actualSha != null && expectedSha != actualSha) {
                                "contenido distinto"
                            } else {
                                "tamaño distinto"
                            }
                            Log.w(TAG, "DEX dañado vuelto a extraer: ${file.name} ($why, ${file.length()} B)")
                            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                "ENGINE_DEX_MISMATCH", "${file.name} $why (${file.length()} B)",
                            )
                        }
                        file.setReadOnly()
                        sizes[file.name] = file.length()
                        hashes[file.name] = copiedSha
                    } else {
                        file.setReadOnly()
                        sizes[file.name] = file.length()
                        hashes[file.name] = actualSha ?: expectedSha ?: ""
                    }
                    paths += file.absolutePath
                }
                // El sello se escribe SOLO cuando los tres DEX quedaron copiados y medidos: si
                // el sistema mata la app a mitad, no queda un manifiesto que dé por bueno un
                // archivo corto y la siguiente apertura vuelve a copiar todo.
                runCatching { manifestFile.writeText(engineManifest(appVersion, sizes, hashes)) }
                val outDir = File(app.codeCacheDir, engineDirName(revision)).apply { mkdirs() }
                val libDir = extractNativeLibs(app, force = staleEngine)
                // Kill-switch remoto `engine_child_first`: si algún equipo se portara raro,
                // `{"engine_child_first": false}` devuelve el cargador parent-first sin OTA.
                val childFirst = engineChildFirst(app)
                val cl = runCatching {
                    if (childFirst) {
                        EngineClassLoader(
                            paths.joinToString(File.pathSeparator),
                            outDir.absolutePath,
                            libDir.absolutePath,
                            javaClass.classLoader,
                        )
                    } else {
                        DexClassLoader(
                            paths.joinToString(File.pathSeparator),
                            outDir.absolutePath,
                            libDir.absolutePath,
                            javaClass.classLoader,
                        )
                    }
                }.getOrElse {
                    Log.w(TAG, "DexClassLoader rechazado (${it.message}); cargando en memoria")
                    memoryLoader(app) ?: throw it
                }
                adoptLoader(cl)
                // Verificó bien: se apaga la marca y los próximos arranques no leen 17 MB.
                runCatching { File(app.filesDir, ENGINE_VERIFY_FLAG).delete() }
                // Autocomprobación del espacio de nombres: si una clase del motor la sirve
                // nuestro APK, se reporta AQUÍ (antes del crash del reproductor).
                val leaks = leakedEngineNames(cl)
                if (leaks.isNotEmpty()) {
                    Log.e(TAG, "motor ensombrecido por el APK: ${leaks.joinToString()}")
                    com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                        "ENGINE_NAMESPACE_LEAK",
                        "childFirst=$childFirst motor=${leaks.joinToString()}",
                    )
                }
                Log.i(
                    TAG,
                    "SDK X4K cargado: gen=${engineDirName(revision)} env=${envClass != null} " +
                        "program=${programClass != null} media=${mediaClass != null} " +
                        "entry=${entryClass != null} view=${viewClass != null} " +
                        "childFirst=$childFirst copiaDistinta=$copyMismatch fugas=${leaks.size}",
                )
                com.chronosbvrx.visionplus.data.diag.Diagnostics.engine("cargado")
                true
            } catch (e: LinkageError) {
                // El DEX existe pero una clase no verifica. Si la copia es idéntica al APK, el
                // problema NO es la extracción (el verificador de ART del equipo rechaza esos
                // bytes): se distingue en la telemetría para no reparar en bucle algo sano.
                Log.e(TAG, "DEX del motor inválido: ${e.javaClass.simpleName}: ${e.message}")
                linkageFailure = true
                com.chronosbvrx.visionplus.data.diag.Diagnostics.engine("error")
                reportVerificationFailure(context, e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "no se pudo cargar el SDK X4K: ${e.message}")
                com.chronosbvrx.visionplus.data.diag.Diagnostics.engine("error")
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "ENGINE_LOAD_FAIL", "${e.javaClass.simpleName}: ${e.message}",
                )
                false
            }
        }
    }

    /**
     * Carga las MISMAS clases con los DEX en memoria: sin archivos, sin vdex y sin los
     * artefactos que ART deja en `code_cache`. Es la última palanca cuando el verificador del
     * equipo rechaza un DEX que en el APK está intacto (`ENGINE_VERIFY_FAIL`).
     */
    private fun memoryLoader(app: Context): ClassLoader? = runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        val buffers = DEX_ASSETS.map { asset ->
            val bytes = app.assets.open(asset).use { input -> input.readBytes() }
            java.nio.ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                position(0)
            }
        }
        val libDir = extractNativeLibs(app, force = false)
        dalvik.system.InMemoryDexClassLoader(
            buffers.toTypedArray(),
            libDir.absolutePath,
            javaClass.classLoader,
        )
    }.getOrNull()

    /** Carga las clases que el reproductor toca seguro y adopta el cargador. */
    private fun adoptLoader(cl: ClassLoader) {
        envClass = cl.loadClass("com.titan.ranger.bean.Env")
        programClass = runCatching { cl.loadClass("com.titan.ranger.bean.Program") }.getOrNull()
        mediaClass = runCatching { cl.loadClass("com.titan.ranger.bean.Media") }.getOrNull()
        entryClass = runCatching { cl.loadClass("com.titan.ranger.bean.Entry") }.getOrNull()
        // La vista (`ed.e`) y su compañera (`yc.e`) son las que tumbaban la reproducción en la
        // tele de campo. `ed.e` es obligatoria (`viewClass!!`); `yc.e` se comprueba porque es la
        // que ART rechazaba a media reproducción.
        viewClass = probeEngineClass(cl, "ed.e", required = true)
        // La fachada es la clase que el reproductor más usa: cargarla aquí convierte un DEX
        // truncado en un fallo controlado (y reparable) ANTES de arrancar el motor.
        cl.loadClass("yc.h")
        probeEngineClass(cl, "yc.e", required = false)
        loader = cl
    }

    /**
     * `loadClass` que NO se traga un `LinkageError`: un bytecode que no verifica debe abortar
     * la carga (y disparar la reparación), no reventar luego en el reproductor.
     */
    private fun probeEngineClass(cl: ClassLoader, name: String, required: Boolean): Class<*>? = try {
        cl.loadClass(name)
    } catch (e: LinkageError) {
        throw e
    } catch (e: ClassNotFoundException) {
        if (required) throw e else null
    }

    /** Distingue en la telemetría "la copia está dañada" de "ART rechaza bytes idénticos al APK". */
    private fun reportVerificationFailure(context: Context, e: LinkageError) {
        runCatching {
            val app = context.applicationContext
            val revision = readRevision(app)
            // La próxima carga verifica el contenido (por si la copia quedó dañada).
            runCatching { File(app.filesDir, ENGINE_VERIFY_FLAG).writeText("1") }
            val workDir = File(app.filesDir, engineDirName(revision))
            val seal = parseEngineSeal(
                runCatching { File(workDir, ENGINE_MANIFEST_NAME).readText() }.getOrDefault("")
            )
            val mismatched = DEX_ASSETS.mapIndexed { index, _ -> File(workDir, "sdk-$index.dex") }
                .filter { it.exists() }
                .mapNotNull { file ->
                    val expected = seal.hashes[file.name] ?: return@mapNotNull null
                    val actual = sha256Of(file) ?: return@mapNotNull null
                    if (expected == actual) null else file.name
                }
            val code = if (mismatched.isEmpty()) "ENGINE_VERIFY_FAIL" else "ENGINE_DEX_MISMATCH"
            val detail = buildString {
                append(e.javaClass.simpleName).append(": ").append(e.message)
                append(" | gen=").append(engineDirName(revision))
                val copy = if (mismatched.isEmpty()) "idéntica-al-apk" else "distinta:${mismatched.joinToString()}"
                append(" copia=").append(copy)
            }
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(code, detail)
        }
    }

    private fun readRevision(app: Context): Int = engineRevisionFrom(
        runCatching { File(app.filesDir, ENGINE_REVISION_FILE).readText() }.getOrDefault("")
    )

    private fun writeRevision(app: Context, revision: Int) {
        runCatching { File(app.filesDir, ENGINE_REVISION_FILE).writeText(revision.toString()) }
    }

    /**
     * Copia un asset a [target] sin dejar nunca un archivo a medias: se escribe en
     * `name.part<pid>`, se fuerza a disco y solo entonces se renombra (atómico en el mismo
     * sistema de archivos). Una copia interrumpida deja el `.part`, no el archivo bueno.
     *
     * Devuelve el **sha256** de lo copiado (se calcula en la misma pasada, sin leer otra vez)
     * o `null` si la copia falló.
     */
    private fun copyAssetAtomically(app: Context, asset: String, target: File): String? {
        val dir = target.parentFile ?: return null
        dir.mkdirs()
        // El sufijo con el pid evita que dos procesos del mismo UID escriban el mismo `.part`.
        val tmp = File(dir, target.name + ".part" + android.os.Process.myPid())
        runCatching { tmp.delete() }
        return try {
            var copied = 0L
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            app.assets.open(asset).use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                    }
                    output.flush()
                    (output as? java.io.FileOutputStream)?.fd?.sync()
                }
            }
            if (copied <= 0L) {
                runCatching { tmp.delete() }
                null
            } else {
                runCatching { target.delete() }
                if (tmp.renameTo(target)) {
                    sha256Of(digest.digest())
                } else {
                    runCatching { tmp.delete() }
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo extraer $asset: ${e.message}")
            runCatching { tmp.delete() }
            null
        }
    }

    /**
     * ABI cuyas libs se extraen: la primera de [android.os.Build.SUPPORTED_ABIS] que exista en
     * assets. En un emulador x86_64 con traducción ARM la lista es ["x86_64", "arm64-v8a"] y no
     * hay carpeta x86_64 (el SDK es ARM): sin el filtro la extracción no copiaba nada y el motor
     * moría con UnsatisfiedLinkError. En teléfonos no cambia nada (arm64-v8a/armeabi-v7a existen).
     */
    private fun pickNativeAbi(app: Context): String {
        val candidates = android.os.Build.SUPPORTED_ABIS.toList() + listOf("arm64-v8a", "armeabi-v7a")
        return candidates.distinct().firstOrNull { candidate ->
            runCatching { !app.assets.list("x4k/lib/$candidate").isNullOrEmpty() }
                .getOrDefault(false)
        } ?: "arm64-v8a"
    }

    /** Copies the X4K .so set from assets into a private directory the loader can search. */
    private fun extractNativeLibs(app: Context, force: Boolean): File {
        val abi = pickNativeAbi(app)
        val libDir = File(app.filesDir, DIR_LIBS)
        // Versión nueva del APK: las libs viejas podrían no ser las del motor actual.
        if (force) runCatching { libDir.deleteRecursively() }
        libDir.mkdirs()
        LIB_NAMES.forEach { name ->
            val out = File(libDir, name)
            if (!out.exists() || out.length() == 0L) {
                if (copyAssetAtomically(app, "x4k/lib/$abi/$name", out) == null) {
                    Log.w(TAG, "lib $name no disponible")
                }
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

    /** Cuánto tardó el último arranque real del motor (0 si ya estaba caliente). */
    @Volatile
    var lastBootMs: Long = 0L
        private set

    /** Boots the engine: `yc.h.m.a().r(workPath, callbacks...)`. */
    fun boot(context: Context): Boolean {
        if (booted) {
            lastBootMs = 0L
            return true
        }
        synchronized(this) {
            if (booted) {
                lastBootMs = 0L
                return true
            }
            val started = android.os.SystemClock.uptimeMillis()
            try {
                if (bootOnce(context)) return true
                if (!linkageFailure) return false
                // El DEX extraído no verifica en ESTE equipo. Se repara en dos pasos, de lo más
                // barato a lo más radical:
                //  1. re-extraer en una GENERACIÓN nueva (ruta de DEX nueva ⇒ ART no reutiliza su
                //     vdex/oat) y limpiar los artefactos de ART del equipo;
                //  2. cargar los DEX en memoria (sin archivos ni artefactos: la última palanca).
                linkageFailure = false
                if (healBudget <= 0) return false
                healBudget -= 1
                healExtracted(context)
                if (bootOnce(context)) return true
                if (!linkageFailure) return false
                linkageFailure = false
                Log.w(TAG, "motor: reintento final con los DEX en memoria")
                val memory = memoryLoader(context.applicationContext) ?: return false
                runCatching { adoptLoader(memory) }.getOrElse {
                    com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                        "ENGINE_VERIFY_FAIL", "memoria: ${it.javaClass.simpleName}: ${it.message}",
                    )
                    return false
                }
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "ENGINE_MEMORY_OK", "motor cargado con los DEX en memoria",
                )
                return bootOnce(context)
            } finally {
                lastBootMs = android.os.SystemClock.uptimeMillis() - started
            }
        }
    }

    /** Kill-switch remoto `engine_child_first` (por defecto encendido). */
    private fun engineChildFirst(app: Context): Boolean = runCatching {
        com.chronosbvrx.visionplus.data.config.RemoteConfig.flag(app, "engine_child_first", true)
    }.getOrDefault(true)

    private fun bootOnce(context: Context): Boolean {
        if (!load(context)) return false
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
            val procName = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.app.Application.getProcessName()
                } else ""
            }.getOrDefault("")
            // La carpeta de trabajo es el estado persistente del motor: PlayerRecovery la
            // rota a una generación nueva tras fallos silenciosos repetidos (caja atascada)
            // para que el arranque limpio no herede el estado envenenado.
            val mainProcess = !procName.endsWith(":x4k")
            val dirName = PlayerRecovery.dirNameFor(mainProcess, PlayerRecovery.engineGen(context))
            val workPath = File(context.applicationContext.filesDir, dirName).apply { mkdirs() }.absolutePath
            instance.javaClass.methods.first { it.name == "r" && it.parameterCount == 3 }
                .invoke(instance, workPath, cb1, cb2)
            booted = true
            Log.i(TAG, "motor X4K arrancado en $workPath")
            true
        } catch (e: LinkageError) {
            // No es un error de datos: el bytecode del motor no verifica en este equipo.
            linkageFailure = true
            Log.e(TAG, "clases del motor inválidas: ${e.javaClass.simpleName}: ${e.message}")
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                "ENGINE_CLASS_INVALID", "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "boot X4K falló: ${e.cause ?: e}")
            false
        }
    }

    /**
     * Borra el motor extraído (DEX, sello y libs) y los artefactos que ART dejó de él, y
     * estrena una GENERACIÓN nueva (`x4k-rN`): con otra ruta de DEX, ART no puede reutilizar
     * el vdex/oat que ya tenía (una tele de campo rechazaba `yc.h`/`yc.e` en bucle aunque el
     * DEX fuera idéntico al del APK). No toca datos del usuario ni la carpeta de trabajo.
     */
    private fun healExtracted(context: Context) {
        loader = null
        booted = false
        envSent = false
        lastSentEntries = ""
        lastSentParams = ""
        facade = null
        envClass = null
        programClass = null
        mediaClass = null
        entryClass = null
        viewClass = null
        val app = context.applicationContext
        // A partir de aquí se verifica el contenido en cada arranque hasta que una carga
        // vuelva a verificar bien.
        runCatching { File(app.filesDir, ENGINE_VERIFY_FLAG).writeText("1") }
        // Todas las generaciones (y la carpeta legada `x4k`): el motor se estrena de cero.
        // OJO: solo directorios — `x4k-rev` es el archivo de la generación y no se borra.
        app.filesDir?.listFiles()?.forEach { dir ->
            if (dir.isDirectory && (dir.name == DIR_DEX || dir.name.startsWith("x4k-r"))) {
                runCatching { dir.deleteRecursively() }
            }
        }
        runCatching { File(app.filesDir, DIR_LIBS).deleteRecursively() }
        // vdex/oat se indexan por ruta + contenido: si quedaron dañados, reutilizarlos vuelve
        // a rechazar las mismas clases. `codeCacheDir` es caché: se puede tirar completa.
        runCatching { app.codeCacheDir?.listFiles()?.forEach { it.deleteRecursively() } }
        val next = if (readRevision(app) >= ENGINE_REVISION_MAX) 0 else readRevision(app) + 1
        writeRevision(app, next)
        Log.w(TAG, "motor re-extraído: generación ${engineDirName(next)}, artefactos ART limpios")
        com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
            "ENGINE_DEX_REPAIRED",
            "motor borrado y re-extraído tras clases inválidas (gen=${engineDirName(next)})",
        )
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

    /**
     * Arranca un programa SIN vista, por la misma llamada que `ed.e.A()` hace por dentro
     * (`yc.h.x(0, code, program, buss)`). Es lo que el reproductor ejecuta al abrir; aquí
     * sirve al Modo Offline para que el motor resuelva la `play_url` sin renderizar.
     *
     * Solo instance 0 (la que `boot` inicializa; la 1 nunca se prepara y su uso directo
     * provocaba el SIGSEGV de `star_file_cache`). No Surface, no ventana, no audio.
     * Llamar solo con el reproductor inactivo (una vista activa por proceso).
     */
    fun playHeadless(code: String, buss: String, title: String, medias: List<MediaSpec>): Boolean {
        if (code.isBlank() || medias.isEmpty()) return false
        val pClass = programClass ?: return false
        val f = facade ?: return false
        return try {
            val mediaObjs = buildMedias(medias)
            val program = pClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, List::class.java, String::class.java,
                java.lang.Long.TYPE,
            ).newInstance(code, buss, title, "-1", "-1", "user", mediaObjs, medias.first().name, 0L)
            f.javaClass.methods.first {
                it.name == "x" && it.parameterCount == 4 &&
                    it.parameterTypes[0] == java.lang.Integer.TYPE &&
                    it.parameterTypes[1] == String::class.java
            }.invoke(f, 0, code, program, buss)
            Log.i(TAG, "playHeadless $buss $code (${medias.size} medias)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "playHeadless falló: ${e.cause ?: e}")
            false
        }
    }

    /** Libera el programa headless (espejo de `ed.e.B()` → `yc.h.H(0, code)`). */
    fun stopHeadless(code: String) {
        if (code.isBlank()) return
        val f = facade ?: return
        runCatching {
            f.javaClass.methods.first {
                it.name == "H" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == java.lang.Integer.TYPE &&
                    it.parameterTypes[1] == String::class.java
            }.invoke(f, 0, code)
        }
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

    /**
     * Lectura de SOLO-LECTURA del estado del motor para un programa: devuelve
     * `(playUrl, links)` si Ranger ya publicó URLs http para ese `code`.
     *
     * v2.5.52: es la única consulta que el Modo Offline usa. Jamás prepara, configura
     * instancias ni toca cachés: no puede provocar el SIGSEGV de `star_file_cache`
     * que causaba la vía anterior (`NativeJni.o/g` directo en instance=1, eliminada).
     */
    fun queryStatusUrls(code: String): Pair<String, List<String>> {
        // Vía primaria: el bean de estado uc.c publicado por el motor tras x()
        // (d() = play_url http, verificado en dispositivo real).
        runCatching {
            val beanUrl = beanPlayUrl()
            if (beanUrl.startsWith("http")) return beanUrl to emptyList()
        }
        return try {
            val cl = loader ?: return "" to emptyList()
            val nativeJniClass = runCatching { cl.loadClass("com.titan.ranger.NativeJni") }.getOrNull()
                ?: return "" to emptyList()
            val nativeJni = runCatching {
                nativeJniClass.methods.first { it.name == "c" && it.parameterCount == 0 }.invoke(null)
            }.getOrNull() ?: return "" to emptyList()
            val getStatus = nativeJni.javaClass.methods.firstOrNull {
                it.name == "a" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
            } ?: return "" to emptyList()
            val raw = runCatching {
                getStatus.invoke(nativeJni, "GetStatus", "{\"instance\":0,\"name\":\"$code\"}") as? String
            }.getOrNull().orEmpty()
            parseStatusUrls(raw)
        } catch (e: Exception) {
            "" to emptyList()
        }
    }

    /**
     * Lee la `play_url` del bean de estado `uc.c` (`p(0)` → `d()`): es donde el motor
     * publica la dirección http local tras `x()` (verificado en dispositivo: el player
     * la expone ahí mientras reproduce). Solo lectura, jamás modifica nada.
     */
    internal fun beanPlayUrl(): String {
        return try {
            val f = facade ?: return ""
            val status = f.javaClass.methods.firstOrNull {
                it.name == "p" && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
            }?.invoke(f, 0) ?: return ""
            (status.javaClass.methods.firstOrNull { it.name == "d" && it.parameterCount == 0 }
                ?.invoke(status) as? String).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    /** Parsea `(play_url, links)` de un `GetStatus` (lógica pura, testeable). */    internal fun parseStatusUrls(rawRes: String): Pair<String, List<String>> {
        if (rawRes.isBlank()) return "" to emptyList()
        return runCatching {
            val outer = org.json.JSONObject(rawRes)
            val resStr = outer.optString("res")
            if (resStr.isBlank() || !resStr.startsWith("{")) return "" to emptyList()
            val st = org.json.JSONObject(resStr)
            val pUrl = st.optString("play_url")
            val arr = st.optJSONArray("links")
            val links = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val l = arr.optString(i)
                    if (l.isNotBlank()) links.add(l)
                }
            }
            pUrl to links
        }.getOrDefault("" to emptyList())
    }
}
