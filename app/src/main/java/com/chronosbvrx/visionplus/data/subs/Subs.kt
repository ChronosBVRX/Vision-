package com.chronosbvrx.visionplus.data.subs

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.json.JSONArray

/**
 * Subtítulos del proveedor (réplica del APK de X4K).
 *
 * El motor nativo **no** sabe ni elegir ni pintar subtítulos (en el SDK la llave `"subtitle"`
 * de la fachada es un no-op: solo `"audio"` está cableado). El APK oficial resuelve el tema
 * en la app: pide la lista al servicio del portal (`/api/subs/terminal/metadata`, sobre el
 * mismo sobre cifrado de la casa con la llave propia `NxZZ…`), descarga los SRT y los pinta
 * en un `TextView` sobre el video, sincronizados con la posición. Esto es exactamente eso,
 * con la parte pura aquí y la red/archivos en [SubsStore].
 */

/** Una pista del servicio: idioma + archivo (`srt` normalmente) + md5 opcional. */
data class SubEntry(
    val language: String,
    val url: String,
    val fileType: String,
    val index: Int,
    val md5: String = "",
) {
    val langKey: String get() = language.trim().lowercase()
}

/** Una línea de tiempo del SRT. */
data class SrtCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Lista descifrada del servicio — `[{"language":"es","file":[{"url":"…","fileType":"srt"}]}]` —
 * a pistas planas. Tolerante: `null`, vacío o entradas rotas dan lista vacía (los títulos sin
 * subtítulos responden `data: null`).
 */
internal fun parseSubsList(json: String?): List<SubEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        val out = ArrayList<SubEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lang = item.optString("language").trim()
            val files = item.optJSONArray("file") ?: continue
            for (j in 0 until files.length()) {
                val f = files.optJSONObject(j) ?: continue
                val url = f.optString("url").trim()
                if (url.isEmpty()) continue
                out += SubEntry(
                    language = lang,
                    url = url,
                    fileType = f.optString("fileType").trim().lowercase(),
                    index = out.size,
                    md5 = f.optString("md5").trim(),
                )
            }
        }
        out
    }.getOrDefault(emptyList())
}

/**
 * Una opción por idioma (el servicio puede repetir idioma con varias calidades/fuentes):
 * se conserva la primera pista `srt` de cada idioma; si no hay srt, la primera a secas.
 */
internal fun distinctLanguages(entries: List<SubEntry>): List<SubEntry> {
    val byLang = LinkedHashMap<String, SubEntry>()
    for (e in entries) {
        if (e.language.isBlank()) continue
        val prev = byLang[e.langKey]
        if (prev == null || (prev.fileType != "srt" && e.fileType == "srt")) byLang[e.langKey] = e
    }
    return byLang.values.toList()
}

/** Nombre del idioma para la UI; cadena vacía si el código no se conoce. */
internal fun languageName(code: String): String = when (code.trim().lowercase()) {
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
    "nld", "dut", "nl" -> "Neerlandés"
    "pol", "pl" -> "Polaco"
    "tur", "tr" -> "Turco"
    "swe", "sv" -> "Sueco"
    "dan", "da" -> "Danés"
    "nor", "no" -> "Noruego"
    "fin", "fi" -> "Finés"
    "ell", "gre", "el" -> "Griego"
    "heb", "he" -> "Hebreo"
    "tha", "th" -> "Tailandés"
    "vie", "vi" -> "Vietnamita"
    "ind", "id" -> "Indonesio"
    "ukr", "uk" -> "Ucraniano"
    "ron", "rum", "ro" -> "Rumano"
    "hun", "hu" -> "Húngaro"
    "ces", "cze", "cs" -> "Checo"
    else -> ""
}

/** Clave segura para nombres de archivo (contentId, idioma). */
internal fun fileNameSafe(raw: String): String =
    raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

private val TIMELINE_RX = Regex(
    """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
)
private val KARAOKE_RX = Regex("""\{\\[^}]*\}""")
private val BR_RX = Regex("""(?i)<br\s*/?>""")

private fun cueTime(h: String, m: String, s: String, ms: String): Long? = runCatching {
    val millis = when (ms.length) {
        1 -> ms.toLong() * 100
        2 -> ms.toLong() * 10
        else -> ms.toLong()
    }
    (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + millis
}.getOrNull()

/**
 * Parser SRT puro: tolera CRLF/CR, BOM, bloques sin índice, separador `.` en milisegundos,
 * etiquetas de posición (`{\an8}`) y `<br>` del proveedor (se vuelven saltos reales).
 * Los bloques sin timeline válido se descartan.
 */
internal fun parseSrt(text: String): List<SrtCue> {
    if (text.isBlank()) return emptyList()
    val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    val cues = ArrayList<SrtCue>()
    for (block in clean.split(Regex("\n{2,}"))) {
        val lines = block.trim('\n').lines()
        if (lines.isEmpty()) continue
        val tl = lines.indexOfFirst { TIMELINE_RX.containsMatchIn(it) }
        if (tl < 0) continue
        val match = TIMELINE_RX.find(lines[tl]) ?: continue
        val g = match.groupValues
        val start = cueTime(g[1], g[2], g[3], g[4]) ?: continue
        val end = cueTime(g[5], g[6], g[7], g[8]) ?: continue
        if (end <= start) continue
        val body = lines.drop(tl + 1).joinToString("\n").trim()
        val normalized = KARAOKE_RX.replace(BR_RX.replace(body, "\n"), "").trim()
        if (normalized.isEmpty()) continue
        cues += SrtCue(start, end, normalized)
    }
    return cues.sortedBy { it.startMs }
}

/** Cue activo para [posMs] (búsqueda binaria), o null. */
internal fun cueAt(cues: List<SrtCue>, posMs: Long): SrtCue? {
    if (cues.isEmpty() || posMs < 0L) return null
    var lo = 0
    var hi = cues.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (cues[mid].startMs <= posMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (found < 0) return null
    val cue = cues[found]
    return if (posMs <= cue.endMs) cue else null
}

/**
 * Próximo instante en el que el texto debe cambiar (fin del cue activo, o inicio del
 * siguiente). `Long.MAX_VALUE` cuando ya no hay nada más.
 */
internal fun nextCuePlayhead(cues: List<SrtCue>, posMs: Long): Long {
    if (cues.isEmpty()) return Long.MAX_VALUE
    val active = cueAt(cues, posMs)
    if (active != null) return active.endMs + 1
    var lo = 0
    var hi = cues.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (cues[mid].startMs > posMs) {
            found = mid
            hi = mid - 1
        } else {
            lo = mid + 1
        }
    }
    return if (found < 0) Long.MAX_VALUE else cues[found].startMs
}

/**
 * Bytes de un SRT a texto: BOM UTF-8/UTF-16, UTF-8 estricto y, si no es UTF-8 válido,
 * Windows-1252 (los archivos viejos de la región llegan en Latin-1).
 */
internal fun decodeSrtBytes(bytes: ByteArray): String {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }
}
