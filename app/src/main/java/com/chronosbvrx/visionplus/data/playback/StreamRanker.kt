package com.chronosbvrx.visionplus.data.playback

/**
 * Puntúa las pistas VOD con lo que el portal declara (`q`, `a`, `e`, `f`).
 *
 * La resolución manda: un 4K siempre va antes que un 1080p. Dentro de la MISMA
 * resolución el orden lo decide la pista que más le conviene al usuario de Vision+:
 *
 *  - Audio explícitamente latino (`dual-lat`, `esp.latino`, `[latino]`, `419`, 🇲🇽)
 *    suma 500; "latino"/"lat" a secas, 350; "castellano"/🇪🇸 resta 200.
 *  - AV1 resta 400: muchas cajas y Fire TV no lo decodifican o lo hacen con tirones.
 *  - H.264/WEB-DL/WEBRip suma 180; HEVC/H.265, 100; MP4, 80.
 *
 * Es una función pura (sin Android ni red) para poder probarla y para que el mismo
 * criterio lo usen el catálogo, el reproductor y las descargas offline.
 */
internal object StreamRanker {

    /** Lo que el portal declara de una pista. Los campos desconocidos van vacíos. */
    data class Facts(
        val quality: String = "",
        val audio: String = "",
        val encodeFormat: String = "",
        val videoFormat: String = "",
    )

    /** Altura declarada de una calidad; 0 cuando el portal no la declara. */
    fun tier(raw: String): Int {
        val q = raw.trim().lowercase()
        return when {
            q.contains("2160") || q.contains("4k") || q.contains("uhd") -> 2160
            q.contains("1080") || q.contains("fhd") || q.contains("full hd") -> 1080
            q.contains("720") || q.contains("hd") -> 720
            q.contains("480") || q.contains("sd") -> 480
            q.contains("360") -> 360
            else -> 0
        }
    }

    /** Puntaje de desempate dentro de una misma resolución. */
    fun score(facts: Facts): Int {
        var score = audioScore(facts.audio)
        val codec = "${facts.encodeFormat} ${facts.videoFormat}".lowercase()
        if (codec.contains("av1")) {
            score -= 400
        } else {
            if (hasAny(codec, "h264", "h.264", "avc", "x264", "web-dl", "webdl", "webrip")) {
                score += 180
            }
            if (hasAny(codec, "h265", "h.265", "hevc", "x265")) {
                score += 100
            }
            if (codec.contains("mp4")) {
                score += 80
            }
        }
        return score
    }

    /**
     * Ordena por resolución (de mayor a menor) y, dentro de cada resolución, por
     * [score]. Los empates conservan el orden del portal (`sortedWith` es estable).
     */
    fun <T> rank(items: List<T>, factsOf: (T) -> Facts): List<T> {
        if (items.size < 2) return items
        return items
            .map { it to factsOf(it) }
            .sortedWith(
                compareByDescending<Pair<T, Facts>> { tier(it.second.quality) }
                    .thenByDescending { score(it.second) },
            )
            .map { it.first }
    }

    private fun audioScore(raw: String): Int {
        val text = raw.lowercase()
        if (text.isBlank()) return 0
        val tokens = text.split(Regex("[^a-z0-9áéíóúñü]+")).filter { it.isNotBlank() }.toSet()
        val foreign = tokens.any { it in FOREIGN_LANGUAGES }
        val explicitLatino = hasAny(
            text,
            "dual-lat",
            "dual lat",
            "esp.latino",
            "esp latino",
            "español latino",
            "espanol latino",
            "[latino]",
        ) ||
            tokens.any { it in EXPLICIT_LATINO_TOKENS } ||
            text.contains("🇲🇽")
        val latino = explicitLatino ||
            tokens.any { it in LATINO_TOKENS } ||
            text.contains("latinoamerica") ||
            text.contains("latinoamérica")
        val castellano = tokens.any { it in CASTELLANO_TOKENS } || text.contains("🇪🇸")
        return when {
            explicitLatino && !foreign -> 500
            latino && !foreign -> 350
            latino && foreign -> 150
            castellano -> -200
            else -> 0
        }
    }

    private fun hasAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    private val EXPLICIT_LATINO_TOKENS = setOf(
        "dual-lat", "es-mx", "esmx", "spa-mx", "spamx", "419", "mx",
    )

    private val LATINO_TOKENS = setOf(
        "latino", "latina", "lat",
    )

    private val CASTELLANO_TOKENS = setOf(
        "castellano", "castellana", "espana", "españa",
    )

    /** Pistas que el público de Vision+ no quiere que ganen por accidente. */
    private val FOREIGN_LANGUAGES = setOf(
        "hindi", "hin", "ukr", "ukrainian", "ucraniano",
        "rus", "russian", "ruso", "multi7", "multi4",
    )
}
