package com.chronosbvrx.visionplus.data.external

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Caché local del módulo 4K. Vive en `filesDir` (no en prefs: son listas y el
 * reproductor corre en otro proceso). Escritura atómica y saneo al leer: una
 * entrada vieja o un archivo corrupto nunca rompen la ficha.
 *
 * TTL: 6 h para una respuesta con fuentes; 45 min para una respuesta vacía. Una
 * fuente además muere con su propio `expiresAt` (el `e=` del CDN).
 */
internal object External4KStore {
    private const val FILE = "external_4k.json"
    private const val FRESH_MS = 6 * 60 * 60 * 1000L
    private const val NEGATIVE_MS = 45 * 60 * 1000L
    private const val MAX_TITLES = 600
    /**
     * Sube cuando cambian los proveedores. Las entradas viejas se ignoran: si no,
     * una caché con solo el 4K LAN de Kronos taparía a HdHub durante 6 h tras la
     * actualización.
     */
    private const val SCHEMA = 4

    fun fresh(context: Context, titleId: String): List<FourKSource> {
        val now = System.currentTimeMillis()
        val entry = read(context).optJSONObject("titles")?.optJSONObject(titleId) ?: return emptyList()
        if (entry.optInt("v", 0) != SCHEMA) return emptyList()
        return entry.optJSONArray("sources")?.let { parseSources(it, now) }.orEmpty()
    }

    fun checkedRecently(context: Context, titleId: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = read(context).optJSONObject("titles")?.optJSONObject(titleId) ?: return false
        if (entry.optInt("v", 0) != SCHEMA) return false
        val at = entry.optLong("at", 0L)
        val negativeAt = entry.optLong("negativeAt", 0L)
        return when {
            negativeAt > 0L && now - negativeAt < NEGATIVE_MS -> true
            at > 0L && now - at < FRESH_MS -> true
            else -> false
        }
    }

    fun imdb(context: Context, titleId: String): String =
        read(context).optJSONObject("imdb")?.optString(titleId).orEmpty()

    fun putImdb(context: Context, titleId: String, imdb: String) {
        if (!imdb.startsWith("tt")) return
        update(context) { root ->
            root.getOrPutObject("imdb").put(titleId, imdb)
        }
    }

    /** Borra un IMDb viejo cuando el portal reasigno el codigo a otra ficha. */
    fun clearImdb(context: Context, titleId: String) {
        update(context) { root ->
            root.optJSONObject("imdb")?.remove(titleId)
        }
    }

    fun put(context: Context, titleId: String, sources: List<FourKSource>) {
        val now = System.currentTimeMillis()
        update(context) { root ->
            val titles = root.getOrPutObject("titles")
            val entry = titles.optJSONObject(titleId) ?: JSONObject()
            entry.put("v", SCHEMA)
            entry.put("at", now)
            entry.put("negativeAt", if (sources.isEmpty()) now else 0L)
            entry.put("sources", JSONArray().apply {
                sources.forEach { s ->
                    put(JSONObject().apply {
                        put("url", s.url)
                        put("provider", s.provider)
                        put("language", s.language)
                        put("filename", s.filename)
                        put("lan", s.lan)
                        put("sizeBytes", s.sizeBytes)
                        put("checkedAt", s.checkedAt)
                        put("expiresAt", s.expiresAt)
                        put("height", s.height)
                    })
                }
            })
            titles.put(titleId, entry)
            prune(titles)
        }
    }

    /** Guarda la ficha mínima para que el worker pueda refrescarla sin el catálogo. */
    fun putFilm(context: Context, film: com.chronosbvrx.visionplus.data.Film) {
        update(context) { root ->
            val queue = root.getOrPutObject("queue")
            queue.put(film.id, JSONObject().apply {
                put("title", film.title)
                put("originalTitle", film.originalTitle)
                put("year", film.year)
                put("tmdbId", film.tmdbId)
                put("kind", film.kind)
                put("at", System.currentTimeMillis())
            })
            if (queue.length() > 100) {
                queue.keys().asSequence()
                    .sortedBy { queue.optJSONObject(it)?.optLong("at", 0L) ?: 0L }
                    .take(queue.length() - 100)
                    .toList()
                    .forEach { queue.remove(it) }
            }
        }
    }

    /** Fichas pendientes de revisar, de la más reciente a la más vieja. */
    fun filmsForRefresh(context: Context, limit: Int = 20): List<com.chronosbvrx.visionplus.data.Film> {
        val queue = read(context).optJSONObject("queue") ?: return emptyList()
        return queue.keys().asSequence()
            .sortedByDescending { queue.optJSONObject(it)?.optLong("at", 0L) ?: 0L }
            .take(limit)
            .mapNotNull { id ->
                val o = queue.optJSONObject(id) ?: return@mapNotNull null
                com.chronosbvrx.visionplus.data.Film(
                    id = id,
                    title = o.optString("title"),
                    year = o.optInt("year"),
                    durationMin = 0,
                    synopsis = "",
                    posterUrl = "",
                    videoUrl = id,
                    sourceUrl = "xtv",
                    originalTitle = o.optString("originalTitle"),
                    tmdbId = o.optInt("tmdbId"),
                    kind = o.optString("kind", "movie"),
                )
            }
            .toList()
    }

    private fun parseSources(arr: JSONArray, now: Long): List<FourKSource> {
        val out = ArrayList<FourKSource>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (!url.startsWith("http")) continue
            val expires = o.optLong("expiresAt", 0L)
            if (expires in 1 until now) continue
            out += FourKSource(
                url = url,
                provider = o.optString("provider").ifBlank { "4K externo" },
                language = o.optString("language"),
                filename = o.optString("filename"),
                lan = o.optBoolean("lan"),
                sizeBytes = o.optLong("sizeBytes"),
                checkedAt = o.optLong("checkedAt"),
                expiresAt = expires,
                height = o.optInt("height", 0),
            )
        }
        return out
    }

    private fun prune(titles: JSONObject) {
        if (titles.length() <= MAX_TITLES) return
        titles.keys().asSequence()
            .sortedBy { titles.optJSONObject(it)?.optLong("at", 0L) ?: 0L }
            .take(titles.length() - MAX_TITLES)
            .toList()
            .forEach { titles.remove(it) }
    }

    private fun update(context: Context, block: (JSONObject) -> Unit) {
        synchronized(this) {
            val root = read(context)
            block(root)
            write(context, root)
        }
    }

    private fun read(context: Context): JSONObject = synchronized(this) {
        runCatching {
            val file = File(context.filesDir, FILE)
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrDefault(JSONObject())
    }

    private fun write(context: Context, root: JSONObject) {
        runCatching {
            val file = File(context.filesDir, FILE)
            // Nombre unico por proceso: el reproductor vive en :x4k y no debe pisar
            // el temporal del proceso principal mientras resuelve en paralelo.
            val tmp = File(context.filesDir, "$FILE.${android.os.Process.myPid()}.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun JSONObject.getOrPutObject(key: String): JSONObject =
        optJSONObject(key) ?: JSONObject().also { put(key, it) }
}
