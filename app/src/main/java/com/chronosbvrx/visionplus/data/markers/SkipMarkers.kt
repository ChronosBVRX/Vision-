package com.chronosbvrx.visionplus.data.markers

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Marcadores de salto por episodio: dónde acaba el intro y dónde empiezan los créditos.
 *
 * Dos capas, y la local manda: lo **aprendido** por el usuario (archivo JSON, plantilla
 * por fila de temporada — el intro se repite en toda la temporada) pisa a lo **global**,
 * que es la caché del VPS de las bases comunitarias TheIntroDB / IntroDB.app (`/v1/markers`).
 *
 * El reproductor nativo (`:x4k`) recibe el resultado como JSON en el intent: las
 * SharedPreferences no cruzan procesos y este dato se necesita al abrir el capítulo.
 */

/** `intro`: [introStartMs] puede faltar (solo se conoce el fin); `credits`: solo el inicio. */
data class EpisodeMarkers(
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val creditsStartMs: Long = -1L,
) {
    val hasIntro: Boolean get() = introEndMs > 0L
    val hasCredits: Boolean get() = creditsStartMs > 0L
    val isEmpty: Boolean get() = !hasIntro && !hasCredits
}

/** Ventana en la que se ofrece «Omitir intro» ([start] = 0 cuando solo se conoce el fin). */
internal fun skipIntroWindow(markers: EpisodeMarkers): LongRange? {
    if (!markers.hasIntro) return null
    val start = if (markers.introStartMs >= 0L) markers.introStartMs else 0L
    val end = markers.introEndMs - 2000L
    return if (end < start) null else start..end
}

internal fun inIntroWindow(positionMs: Long, markers: EpisodeMarkers): Boolean {
    val window = skipIntroWindow(markers) ?: return false
    return positionMs in window
}

/** Con marcador de créditos: al llegar al inicio de los créditos el capítulo ya terminó. */
internal fun shouldOfferCredits(positionMs: Long, markers: EpisodeMarkers): Boolean =
    markers.hasCredits && positionMs >= markers.creditsStartMs

/** JSON del servidor (`{"intro":{"s":…,"e":…},"credits":{"s":…}}`) → marcadores. */
internal fun parseMarkers(json: String?): EpisodeMarkers {
    if (json.isNullOrBlank()) return EpisodeMarkers()
    return runCatching {
        val root = JSONObject(json)
        var intro = -1L
        var introEnd = -1L
        var credits = -1L
        root.optJSONObject("intro")?.let { seg ->
            if (!seg.isNull("s")) intro = seg.optLong("s", -1L)
            introEnd = seg.optLong("e", -1L)
        }
        root.optJSONObject("credits")?.let { seg ->
            if (!seg.isNull("s")) credits = seg.optLong("s", -1L)
        }
        if (introEnd <= 0L) intro = -1L
        if (credits <= 0L) credits = -1L
        EpisodeMarkers(intro, introEnd, credits)
    }.getOrDefault(EpisodeMarkers())
}

/** Lo aprendido pisa a lo global por tipo (la corrección del usuario manda). */
internal fun mergeMarkers(global: EpisodeMarkers, local: EpisodeMarkers): EpisodeMarkers =
    EpisodeMarkers(
        introStartMs = if (local.hasIntro) local.introStartMs else global.introStartMs,
        introEndMs = if (local.hasIntro) local.introEndMs else global.introEndMs,
        creditsStartMs = if (local.hasCredits) local.creditsStartMs else global.creditsStartMs,
    )

/** JSON compacto para los extras del reproductor (omite lo que no exista). */
internal fun toPlayerJson(markers: EpisodeMarkers): String {
    val root = JSONObject()
    if (markers.hasIntro) {
        val intro = JSONObject()
        if (markers.introStartMs >= 0L) intro.put("s", markers.introStartMs)
        intro.put("e", markers.introEndMs)
        root.put("intro", intro)
    }
    if (markers.hasCredits) {
        root.put("credits", JSONObject().put("s", markers.creditsStartMs))
    }
    return root.toString()
}

/** Fila de temporada canónica para la clave local (`xtv:…` como la conoce el servidor). */
internal fun normalizeMarkersRef(ref: String): String {
    val clean = ref.trim()
    if (clean.isEmpty()) return ""
    return if (clean.startsWith("xtv:") || clean.startsWith("magis:") || clean.startsWith("magisvod:")) clean
    else "xtv:$clean"
}

object MarkersStore {
    private const val LOCAL_FILE = "skip_markers.json"
    private const val REMOTE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val LOCAL_MAX_REFS = 400

    private val memory = ConcurrentHashMap<String, EpisodeMarkers>()
    private val memoryAt = ConcurrentHashMap<String, Long>()

    /** Para el reproductor: global del servidor + lo aprendido (manda), en JSON de extras. */
    suspend fun fetchForPlayer(context: Context, ref: String, ep: Int): String {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty() || ep < 1 || ep > 400) return ""
        val remote = runCatching { remoteMarkers(context, key, ep) }.getOrDefault(EpisodeMarkers())
        val merged = mergeMarkers(remote, localMarkers(context, key))
        return if (merged.isEmpty) "" else toPlayerJson(merged)
    }

    private suspend fun remoteMarkers(context: Context, ref: String, ep: Int): EpisodeMarkers {
        val cacheKey = "$ref:$ep"
        val at = memoryAt[cacheKey] ?: 0L
        if (System.currentTimeMillis() - at < REMOTE_TTL_MS) {
            return memory[cacheKey] ?: EpisodeMarkers()
        }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val url = URL(
                "${RemoteIndexSource.BASE_URL}/v1/markers?ref=${URLEncoder.encode(ref, "UTF-8")}&ep=$ep"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_500
                readTimeout = 4_500
                setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val markers = parseMarkers(text)
                memory[cacheKey] = markers
                memoryAt[cacheKey] = System.currentTimeMillis()
                markers
            } finally {
                connection.disconnect()
            }
        }
    }

    // ------------------------------------------------------------ capa aprendida (local)
    // Archivo JSON, no SharedPreferences: las prefs no cruzan procesos y esto lo escribe el
    // reproductor (`:x4k`) mientras el proceso principal lo lee al preparar el capítulo.

    fun localMarkers(context: Context, ref: String): EpisodeMarkers {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty()) return EpisodeMarkers()
        val entry = localRoot(context).optJSONObject(key) ?: return EpisodeMarkers()
        return parseMarkers(entry.toString())
    }

    /** «Aquí acabó el intro» / «aquí empiezan los créditos»: plantilla de toda la temporada. */
    fun saveLocal(context: Context, ref: String, kind: String, startMs: Long, endMs: Long) {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty()) return
        val root = localRoot(context)
        val entry = root.optJSONObject(key) ?: JSONObject()
        when (kind) {
            "intro" -> entry.put("intro", JSONObject().apply {
                if (startMs >= 0L) put("s", startMs)
                put("e", endMs)
            })
            "credits" -> entry.put("credits", JSONObject().put("s", startMs))
        }
        root.put(key, entry)
        while (root.length() > LOCAL_MAX_REFS) {
            val first = root.keys().asSequence().firstOrNull() ?: break
            root.remove(first)
        }
        writeLocalRoot(context, root)
    }

    fun clearLocal(context: Context, ref: String, kind: String?) {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty()) return
        val root = localRoot(context)
        if (kind == null) {
            root.remove(key)
        } else {
            root.optJSONObject(key)?.let { entry ->
                entry.remove(kind)
                if (entry.length() == 0) root.remove(key) else root.put(key, entry)
            }
        }
        writeLocalRoot(context, root)
    }

    // ------------------------------------------------------------ aprendizaje al servidor

    /** Sube la marca para los demás equipos; si falla, la marca local ya quedó aplicada. */
    fun submit(context: Context, ref: String, ep: Int, kind: String, startMs: Long, endMs: Long) {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty()) return
        val body = JSONObject().apply {
            put("ref", key)
            put("ep", ep)
            put("kind", kind)
            if (startMs >= 0L) put("startMs", startMs)
            if (endMs >= 0L) put("endMs", endMs)
        }
        post(context, body)
    }

    fun submitClear(context: Context, ref: String, ep: Int, kind: String) {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty()) return
        post(context, JSONObject().apply {
            put("ref", key)
            put("ep", ep)
            put("kind", kind)
            put("clear", true)
        })
    }

    private fun post(context: Context, body: JSONObject) {
        val applicationContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = URL("${RemoteIndexSource.BASE_URL}/v1/markers")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3_500
                    readTimeout = 4_500
                    doOutput = true
                    setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    connection.responseCode // fuerza la petición
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun localRoot(context: Context): JSONObject {
        val file = java.io.File(context.filesDir, LOCAL_FILE)
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    /** Escritura atómica (temp + rename): el proceso del reproductor y el principal comparten archivo. */
    private fun writeLocalRoot(context: Context, root: JSONObject) {
        runCatching {
            val file = java.io.File(context.filesDir, LOCAL_FILE)
            val tmp = java.io.File(file.parentFile, "$LOCAL_FILE.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }
}
