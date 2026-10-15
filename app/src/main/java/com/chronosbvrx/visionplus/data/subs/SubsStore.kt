package com.chronosbvrx.visionplus.data.subs

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.xuper.X4KApi.APP_ID
import com.chronosbvrx.visionplus.data.xuper.X4KApi.APK_VER
import com.chronosbvrx.visionplus.data.xuper.X4KApi.HOST_BACKUP
import com.chronosbvrx.visionplus.data.xuper.X4KApi.HOST_MAIN
import com.chronosbvrx.visionplus.data.xuper.X4KApi.SPKG_VER
import com.chronosbvrx.visionplus.data.xuper.XuperCrypto
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Red y disco de los subtítulos del proveedor (el motor no los resuelve: los pide, descarga
 * y pinta la app, igual que el APK oficial).
 *
 * - Lista por programa: `POST /api/subs/terminal/metadata` con `{"programId":…}` cifrado con
 *   la llave propia del servicio (`NxZZ…`) sobre el mismo sobre de la casa; la respuesta trae
 *   `data` cifrado que se descifra con la misma llave. Verificado en vivo contra `masnew`.
 * - SRT: se guardan bajo `filesDir/subs/<program>/<idioma>.srt` (los archivos cruzan
 *   procesos: el reproductor vive en `:x4k` y las SharedPreferences no).
 * - Elección por título: `selection.json` (archivo, no prefs, por lo mismo).
 */
object SubsStore {
    private const val TAG = "SubsStore"

    /** Llave del servicio de subtítulos (APK: `DesUtil.SUBTITLE_KEY`). */
    internal const val SUBS_KEY = "NxZZ7EYgaJiJSBHjnq7sDxYvYRm32tPQ"

    private const val PATH = "/api/subs/terminal/metadata"
    private const val DIR = "subs"
    private const val INDEX_FILE = "index.json"
    private const val SELECTION_FILE = "selection.json"
    private const val LIST_TTL_MS = 24L * 60 * 60 * 1000

    private val lock = Any()
    private val memoryList = ConcurrentHashMap<String, List<SubEntry>>()

    // ------------------------------------------------------------------ lista

    /** Pistas del programa: caché de 24 h; sin red cae a la última copia (aunque esté vieja). */
    fun loadList(context: Context, contentId: String, force: Boolean = false): List<SubEntry> {
        val cid = fileNameSafe(contentId)
        if (cid.isEmpty()) return emptyList()
        if (!force) {
            memoryList[cid]?.let { return it }
            cachedList(context, cid, LIST_TTL_MS)?.let {
                memoryList[cid] = it
                return it
            }
        }
        val fresh = runCatching { fetchList(cid) }.getOrNull()
        if (fresh != null) {
            memoryList[cid] = fresh
            saveIndex(context, cid, fresh)
            return fresh
        }
        return cachedList(context, cid, Long.MAX_VALUE)?.also { memoryList[cid] = it }.orEmpty()
    }

    private fun fetchList(cid: String): List<SubEntry> {
        val body = JSONObject().put("programId", cid).toString()
        val wire = XuperCrypto.encryptBody(body, SUBS_KEY)
        val text = post(wire)
        val obj = JSONObject(text)
        val rc = obj.optString("returnCode")
        if (rc != "0") throw IOException("subs rc=$rc ${obj.optString("errorMessage")}".trim())
        val data = obj.optString("data")
        if (data.isBlank()) return emptyList()
        val decrypted = XuperCrypto.decryptBody(data, SUBS_KEY) ?: throw IOException("subs sin descifrar")
        return parseSubsList(decrypted)
    }

    /** POST al servicio; mismo juego de cabeceras que el APK, con host de respaldo. */
    private fun post(wire: String): String {
        var last: Exception? = null
        for (host in listOf(HOST_MAIN, HOST_BACKUP)) {
            try {
                val conn = (URL("https://$host$PATH").openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json;charset=utf-8")
                    setRequestProperty("apk", APP_ID)
                    setRequestProperty("apkVer", APK_VER)
                    setRequestProperty("spkgVer", SPKG_VER)
                    setRequestProperty("User-Agent", "okhttp/4.12.0")
                }
                conn.outputStream.use { it.write(wire.toByteArray(Charsets.US_ASCII)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code")
                return text
            } catch (e: Exception) {
                Log.w(TAG, "subs @$host falló: ${e.message}")
                last = e
            }
        }
        throw last ?: IOException("sin hosts disponibles")
    }

    private fun indexFile(context: Context) = File(File(context.filesDir, DIR), INDEX_FILE)

    private fun cachedList(context: Context, cid: String, ttlMs: Long): List<SubEntry>? {
        val root = runCatching {
            val f = indexFile(context)
            if (!f.exists()) return null
            JSONObject(f.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null
        val entry = root.optJSONObject(cid) ?: return null
        val at = entry.optLong("at", 0L)
        if (ttlMs != Long.MAX_VALUE && System.currentTimeMillis() - at > ttlMs) return null
        val raw = entry.optString("list").trim()
        val parsed = parseSubsList(raw)
        // `[]` del servicio = «este título no tiene» (negativo legítimo). Cualquier otra
        // cosa que no parsee (formato viejo/corrupto) se descarta: mejor reintentar que
        // mostrar «Sin subtítulos disponibles» por un caché ilegible.
        if (parsed.isEmpty() && raw != "[]") return null
        return parsed
    }

    private fun saveIndex(context: Context, cid: String, list: List<SubEntry>) {
        synchronized(lock) {
            runCatching {
                val f = indexFile(context)
                f.parentFile?.mkdirs()
                val root = if (f.exists()) {
                    runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
                } else {
                    JSONObject()
                }
                // Poda: sin esto el índice crecería sin fin con cada título visto.
                if (root.length() > 120) {
                    val cut = root.keys().asSequence().sortedBy { root.optJSONObject(it)?.optLong("at", 0L) ?: 0L }
                        .drop(40).toList()
                    cut.forEach { root.remove(it) }
                }
                root.put(
                    cid,
                    JSONObject()
                        .put("at", System.currentTimeMillis())
                        .put("list", entriesToJson(list)),
                )
                f.writeText(root.toString(), Charsets.UTF_8)
            }
        }
    }

    // ------------------------------------------------------------------ SRT

    /** Archivo local del SRT (descarga si hace falta y valida md5 cuando el servicio lo trae). */
    fun ensureSrtFile(context: Context, contentId: String, entry: SubEntry): File? {
        val cid = fileNameSafe(contentId)
        if (cid.isEmpty() || entry.url.isBlank()) return null
        val file = srtFile(context, cid, entry)
        if (file.exists() && file.length() > 0L && md5Matches(file, entry.md5)) return file
        val downloaded = runCatching { downloadTo(file, entry.url) }.getOrNull() ?: return null
        return if (md5Matches(downloaded, entry.md5)) downloaded else null
    }

    /** Cues del SRT local; vacío si no existe o está roto. */
    fun readCues(file: File): List<SrtCue> = runCatching {
        parseSrt(decodeSrtBytes(file.readBytes()))
    }.getOrDefault(emptyList())

    fun srtFile(context: Context, cidSafe: String, entry: SubEntry): File =
        File(File(File(context.filesDir, DIR), cidSafe), srtFileName(entry))

    /** Nombre local del archivo: `<idioma>_<índice>.<ext>` (el índice distingue idiomas repetidos). */
    internal fun srtFileName(entry: SubEntry): String {
        val lang = fileNameSafe(entry.langKey.ifBlank { "sub" }).ifBlank { "sub" }
        val ext = fileNameSafe(entry.fileType.ifBlank { "srt" }).ifBlank { "srt" }.lowercase()
        return "${lang}_${entry.index}.$ext"
    }

    private fun downloadTo(file: File, url: String): File {
        file.parentFile?.mkdirs()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
        } finally {
            conn.disconnect()
        }
        if (file.length() <= 0L) {
            file.delete()
            throw IOException("SRT vacío")
        }
        return file
    }

    private fun md5Matches(file: File, expected: String): Boolean {
        if (expected.isBlank()) return true
        return runCatching {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val md5 = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            md5.equals(expected, ignoreCase = true)
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ elección

    /** Marca de «apagados a propósito» (no heredar la elección de la serie). */
    private const val OFF = "__off__"

    /** Idioma elegido para el programa (o para su serie, de respaldo); "" = desactivados. */
    fun selection(context: Context, contentId: String, seriesId: String = ""): String {
        val root = readSelection(context) ?: return ""
        val cid = fileNameSafe(contentId)
        val sid = fileNameSafe(seriesId)
        val direct = root.optString(cid)
        if (direct.isNotEmpty()) return if (direct == OFF) "" else direct
        if (sid.isNotEmpty()) {
            val fromSeries = root.optString(sid)
            return if (fromSeries == OFF) "" else fromSeries
        }
        return ""
    }

    /** Guarda la elección del programa (y de la serie, para que el siguiente capítulo herede). */
    fun saveSelection(context: Context, contentId: String, seriesId: String, language: String) {
        val cid = fileNameSafe(contentId)
        if (cid.isEmpty()) return
        val value = if (language.isBlank()) OFF else language
        synchronized(lock) {
            runCatching {
                val f = File(File(context.filesDir, DIR), SELECTION_FILE)
                f.parentFile?.mkdirs()
                val root = readSelection(context) ?: JSONObject()
                root.put(cid, value)
                val sid = fileNameSafe(seriesId)
                if (sid.isNotEmpty()) root.put(sid, value)
                f.writeText(root.toString(), Charsets.UTF_8)
            }
        }
    }

    private fun readSelection(context: Context): JSONObject? = runCatching {
        val f = File(File(context.filesDir, DIR), SELECTION_FILE)
        JSONObject(f.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: return null)
    }.getOrNull()

    // ------------------------------------------------------------------ helpers

    /** Mismo sobre que el servicio; `internal` para que el test fije el round-trip escritor/lector. */
    internal fun entriesToJson(list: List<SubEntry>): String {
        val arr = org.json.JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("language", e.language)
                    .put(
                        "file",
                        org.json.JSONArray().put(
                            JSONObject()
                                .put("url", e.url)
                                .put("fileType", e.fileType)
                                .put("md5", e.md5),
                        ),
                    ),
            )
        }
        return arr.toString()
    }

    /** Distancia (ms) al siguiente cambio de texto, acotada para que un seek se note rápido. */
    internal fun tickDelayMs(cues: List<SrtCue>, posMs: Long): Long {
        val next = nextCuePlayhead(cues, posMs)
        if (next == Long.MAX_VALUE) return 1000L
        return (next - posMs).coerceIn(80L, 1000L)
    }
}
