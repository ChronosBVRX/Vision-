package com.chronosbvrx.visionplus.data.sources

import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LivePlaylist
import com.chronosbvrx.visionplus.data.normalizeDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The catalog of public-domain films of the Internet Archive, as one more source. It never
 * needs a session or a key: every row is an archive.org search something like "cine negro",
 * and every film's playback is resolved at the moment from the item's own metadata.
 * The rows are fixed to the ones the screens already know, so Home keeps its idiom.
 */
class ArchiveSource : Source {

    override val id = "ia_catalog"
    override val title = "El Internet Archive"
    override val builtIn = true

    /** One Home row: its title and its archive.org query (public domains, in Spanish screens). */
    private data class RowDef(val id: String, val label: String, val query: String, val sort: String)

    private val rowDefs = listOf(
        RowDef("peliculas", "Películas", "collection:(feature_films) AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
        RowDef("cine-negro", "Cine negro", "subject:(\"film noir\") AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
        RowDef("terror-suspenso", "Terror y suspenso", "subject:(\"horror films\") AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
        RowDef("animacion", "Animación clásica", "collection:(classics_animation) AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
        RowDef("keaton", "Buster Keaton", "title:(\"buster keaton\") AND mediatype:(movies) AND format:(MPEG4)", "date desc"),
        RowDef("chaplin", "Charlie Chaplin", "title:(\"charlie chaplin\") AND mediatype:(movies) AND format:(MPEG4)", "date desc"),
    )

    companion object {
        @Volatile
        private var cachedRows: List<CatalogRow>? = null
    }

    override suspend fun livePlaylist(): LivePlaylist? = null

    override suspend fun catalogRows(): List<CatalogRow> = withContext(Dispatchers.IO) {
        cachedRows?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val fetched = withTimeoutOrNull(2500L) {
            coroutineScope {
                rowDefs.map { def ->
                    async {
                        val json = runCatching { search(def.query, def.sort) }.getOrNull() ?: return@async null
                        val films = filmsOf(json, def.label)
                        if (films.isEmpty()) null else CatalogRow(id = def.id, title = def.label, films = films)
                    }
                }.awaitAll().filterNotNull()
            }
        }.orEmpty()
        if (fetched.isNotEmpty()) {
            cachedRows = fetched
        }
        fetched
    }

    override fun knows(ref: String): Boolean = ref.startsWith("ia:")

    override suspend fun playable(ref: String): Playable? = withContext(Dispatchers.IO) {
        if (!knows(ref)) return@withContext null
        // ref = "ia:identifier|title": the identifier is all the resolution needs.
        val identifier = ref.removePrefix("ia:").substringBefore('|')
        val mp4 = runCatching { firstMp4Of(identifier) }.getOrNull() ?: return@withContext null
        Playable(url = mp4, label = identifier)
    }

    // ───────────────────────────────── api ─────────────────────────────────

    /** One advancedsearch call; the Archive answers JSON with docs[] (identifier, title, year). */
    private fun search(query: String, sort: String): String {
        val q = URLEncoder.encode("$query", "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$q" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&sort%5B%5D=" +
            URLEncoder.encode(sort, "UTF-8") + "&rows=24&page=1&output=json"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 4_000
        }
        try {
            if (connection.responseCode !in 200..299) error("Archive HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** The search's JSON as films of the row: poster via the img service, playback by ref later. */
    private fun filmsOf(json: String, label: String): List<Film> = runCatching {
        val docs = Json.parseToJsonElement(json).jsonObject.run {
            (get("response")?.jsonObject?.get("docs")?.jsonArray) ?: emptyList()
        }
        docs.mapNotNull { element ->
            val doc = element.jsonObject
            val identifier = doc["identifier"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val raw = doc["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val title = normalizeDisplayName(raw.substringBefore('(').trim().ifBlank { identifier })
            Film(
                id = "ia-$identifier",
                title = title,
                year = 0,
                durationMin = 0,
                synopsis = "",
                posterUrl = "https://archive.org/services/img/$identifier",
                videoUrl = "ia:$identifier|$title",
                sourceUrl = "internet-archive",
            )
        }
    }.getOrDefault(emptyList())

    /** The item's metadata, first mp4 in its file list (the player's one clicked playback). */
    private fun firstMp4Of(identifier: String): String? {
        val connection = (URL("https://archive.org/metadata/$identifier").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val files = Json.parseToJsonElement(text).jsonObject["files"]?.jsonArray ?: return null
            val candidates = files.mapNotNull { it.jsonObject }.filter { (it["name"]?.jsonPrimitive?.content ?: "").endsWith(".mp4") }
            val chosen = candidates.firstOrNull { (it["size"]?.jsonPrimitive?.longOrNull ?: 0L) > 0 } ?: return null
            val name = chosen["name"]?.jsonPrimitive?.content ?: return null
            return "https://archive.org/download/$identifier/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}"
        } finally {
            connection.disconnect()
        }
    }
}
