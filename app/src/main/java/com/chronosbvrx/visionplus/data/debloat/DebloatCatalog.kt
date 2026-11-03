package com.chronosbvrx.visionplus.data.debloat

import android.content.Context

/**
 * Listas de limpieza por modelo, portadas de TidyTV / CleanYourTV (MIT).
 *
 * La idea es la misma que en TidyTV: no se inventa qué apagar. Se elige una lista que se probó
 * en un televisor real (marca, modelo, código de placa y versión de Android) y solo se tocan
 * paquetes que esa lista nombra. Todo es reversible: nunca se desinstala, se usa
 * `pm disable-user`, y antes de tocar nada se guarda una copia del estado del televisor.
 *
 * Atribución: TidyTV (github.com/jolan94/tidytv), Copyright (c) 2026 Winkle Studios y
 * contribuidores, MIT. Listas de CleanYourTV (github.com/Gathin23/cleanyourtv), MIT.
 */

/** Un nombre de paquete que se va a interpolar en un comando del televisor. */
internal object PackageName {
    private val PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun isValid(name: String): Boolean = PATTERN.matches(name)

    fun require(name: String): String {
        require(isValid(name)) { "not a package name: ${name.take(80)}" }
        return name
    }
}

/** Una línea activa de una lista de limpieza. */
internal data class DebloatEntry(
    val pkg: String,
    /** Título del bloque de comentarios donde vive, p. ej. «Voice, casting and assistants». */
    val section: String,
    /** Comentario al final de la misma línea, si lo hay. */
    val note: String,
    /** `true` para líneas comentadas (`# com.tcl.airplay2  # AirPlay`): se ofrecen, pero apagadas. */
    val optional: Boolean = false,
)

internal data class DebloatList(
    /** Carpeta de origen, p. ej. `tcl-g05-google-tv`. */
    val id: String,
    /** Primera línea sin la `#`: nombre, modelo y versión. */
    val title: String,
    /** Comentarios anteriores al primer paquete. */
    val header: List<String>,
    val entries: List<DebloatEntry>,
    val optional: List<DebloatEntry>,
    /** `Stock launcher: ...` del encabezado. */
    val stockLauncher: String?,
    /** Paquetes que la lista pide conservar. Nunca se apagan, aunque aparezcan. */
    val keep: Set<String>,
    val isFireOs: Boolean,
    /** Firmware que rechaza `pm disable-user` y usa `pm hide` (Redmi Android 5.1). */
    val needsHide: Boolean,
) {
    val packages: List<String> get() = entries.map { it.pkg }
}

internal sealed interface ParseResult {
    data class Ok(val list: DebloatList) : ParseResult
    /** La lista entera se rechaza si una entrada no es un paquete limpio. */
    data class Rejected(val id: String, val reasons: List<String>) : ParseResult
}

/**
 * Parser de las listas de CleanYourTV. Son archivos escritos por personas: si una línea no es
 * exactamente un nombre de paquete, se rechaza la lista completa en vez de adivinar.
 */
internal object ListParser {
    private val TOKEN = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    private val STOCK = Regex(
        "Stock launcher:\\s*([A-Za-z_][A-Za-z0-9_.]*[A-Za-z0-9_])",
        RegexOption.IGNORE_CASE,
    )
    private val KEEP_WORDS = Regex(
        "\\b(keep|kept|must stay|stays? enabled|leave .* enabled|left enabled|do not disable|don't disable|needed)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(id: String, raw: String): ParseResult {
        val text = raw.removePrefix("\uFEFF")
        val lines = text.split("\n").map { it.removeSuffix("\r") }
        val reasons = mutableListOf<String>()
        if (lines.firstOrNull()?.trimStart()?.startsWith("#") != true) {
            reasons += "first line should be a comment naming the TV"
        }

        val header = mutableListOf<String>()
        val entries = mutableListOf<DebloatEntry>()
        val optional = mutableListOf<DebloatEntry>()
        var seenPackage = false
        var section = ""
        var blockStart = true

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> blockStart = true
                trimmed.startsWith("#") -> {
                    val body = trimmed.removePrefix("#").trim()
                    if (!seenPackage) header += body
                    val opt = optionalEntry(body)
                    if (seenPackage && opt != null) {
                        optional += opt.copy(section = section)
                    } else if (blockStart && body.isNotEmpty()) {
                        section = body
                        blockStart = false
                    }
                }
                else -> {
                    val code = line.substringBefore("#").trim()
                    val note = if (line.contains("#")) line.substringAfter("#").trim() else ""
                    val pkg = code.split(Regex("\\s+")).first()
                    if (code.contains(Regex("\\s")) || !PackageName.isValid(pkg)) {
                        reasons += "line ${index + 1}: not a package name: ${line.take(80)}"
                    } else {
                        seenPackage = true
                        blockStart = false
                        entries += DebloatEntry(pkg, section, note)
                    }
                }
            }
        }

        if (entries.isEmpty()) reasons += "no packages"
        val dupes = entries.groupBy { it.pkg }.filterValues { it.size > 1 }.keys
        if (dupes.isNotEmpty()) reasons += "listed twice: ${dupes.sorted()}"
        if (reasons.isNotEmpty()) return ParseResult.Rejected(id, reasons)

        val headerText = header.joinToString("\n")
        val listed = entries.map { it.pkg }.toSet()
        return ParseResult.Ok(
            DebloatList(
                id = id,
                title = header.firstOrNull().orEmpty(),
                header = header,
                entries = entries,
                optional = optional.filter { it.pkg !in listed }.distinctBy { it.pkg },
                stockLauncher = STOCK.find(headerText)?.groupValues?.get(1),
                keep = keepPackages(header),
                isFireOs = headerText.contains("Fire OS", ignoreCase = true) || id.contains("fire-tv"),
                needsHide = headerText.contains("pm hide"),
            ),
        )
    }

    private fun optionalEntry(body: String): DebloatEntry? {
        val first = body.split(Regex("\\s+"), limit = 2)
        val pkg = first[0]
        if (!PackageName.isValid(pkg)) return null
        val rest = first.getOrNull(1)?.trim().orEmpty()
        if (rest.isNotEmpty() && !rest.startsWith("#")) return null
        return DebloatEntry(pkg, "", rest.removePrefix("#").trim(), optional = true)
    }

    /**
     * El encabezado dice cosas como «Keep com.tcl.tvinput, com.tcl.settings, ...» y sigue en
     * varias líneas. Se une todo y se analiza frase por frase; de más protege, nunca de menos.
     */
    internal fun keepPackages(header: List<String>): Set<String> {
        val sentences = header.joinToString(" ").split(Regex("(?<=[.;:])\\s+(?=[A-Z(])"))
        return sentences.filter { KEEP_WORDS.containsMatchIn(it) }
            .flatMap { s -> TOKEN.findAll(s).map { it.value.trimEnd('.') } }
            .filter { PackageName.isValid(it) && it.count { c -> c == '.' } >= 2 }
            .toSet()
    }
}

/** Lo que el televisor dice de sí mismo (getprop). */
internal data class TvInfo(
    val model: String,
    val device: String,
    val product: String,
    val brand: String,
    val manufacturer: String,
    val sdk: Int,
    val release: String,
    val serial: String,
) {
    val displayName: String
        get() = listOf(brand.replaceFirstChar { it.uppercase() }, model)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")

    /** Fire TV: se puede limpiar, pero Amazon no deja cambiar el Inicio. */
    val isFireOs: Boolean
        get() = brand.equals("Amazon", ignoreCase = true) ||
            manufacturer.equals("Amazon", ignoreCase = true) ||
            model.startsWith("AFT", ignoreCase = true) ||
            device.startsWith("aft", ignoreCase = true)
}

internal data class ListMatch(val list: DebloatList, val score: Int, val overlap: Int = 0) {
    /** Proporción de apps de la lista que están en esta tele: cuanto más cerca el firmware, más alto. */
    val coverage: Double get() = overlap.toDouble() / maxOf(1, list.entries.size)

    /** La lista se probó en este modelo (código o número de modelo en el título). */
    val exact: Boolean get() = score >= ModelMatcher.EXACT
}

/**
 * Elige la lista de la tele. La primera línea de cada lista nombra la tele, sus códigos y la
 * versión de Android. Misma marca y versión parecida se ofrece como «modelo parecido, no
 * probado»; entre esas, primero la que cubre más apps instaladas.
 */
internal object ModelMatcher {
    const val EXACT = 90
    private const val SIMILAR = 20

    /** Modelos genéricos que reportan muchas marcas y que nunca bastan para un match. */
    private val GENERIC_MODELS = setOf(
        "tv", "smarttv", "smarttvpro", "androidtv", "googletv", "ledtv", "uhdtv", "hdtv", "4ktv", "atv", "tvbox", "box",
    )

    /** Listas que no se pueden aplicar con este flujo (el firmware rechaza pm disable-user). */
    val NOT_FOR_VISION = setOf("tivo-stream-4k")

    fun match(
        tv: TvInfo,
        lists: List<DebloatList>,
        limit: Int = 3,
        installed: Set<String> = emptySet(),
    ): List<ListMatch> = lists
        .filter { it.id !in NOT_FOR_VISION }
        .map { list -> ListMatch(list, score(tv, list), overlap = list.entries.count { it.pkg in installed }) }
        .filter { it.score >= SIMILAR }
        .sortedWith(
            compareByDescending<ListMatch> { it.score }
                .thenByDescending { it.overlap }
                .thenByDescending { it.coverage }
                .thenBy { it.list.id },
        )
        .take(limit)

    internal fun score(tv: TvInfo, list: DebloatList): Int {
        val titleTokens = tokens(list.title) + tokens(list.id.replace('-', ' '))
        val compactTitle = compact(list.title + " " + list.id)
        var score = 0

        val codenames = listOf(tv.device, tv.product).map { it.lowercase() }.filter { it.length >= 3 }
        if (codenames.any { it in titleTokens }) score = maxOf(score, 100)

        val brands = listOf(tv.brand, tv.manufacturer).map { it.lowercase() }.filter { it.length >= 2 }
        val brandHit = brands.any { brand -> brand in titleTokens || list.id.startsWith("$brand-") }

        // Un modelo cuenta cuando es específico: no genérico, y número de modelo o misma marca.
        val model = compact(tv.model)
        val specific = model.length >= 4 && model !in GENERIC_MODELS && (brandHit || model.any { it.isDigit() })
        if (specific && (model in titleTokens || compactTitle.contains(model))) score = maxOf(score, 95)
        if (brandHit) {
            var similar = 40
            val major = tv.release.substringBefore('.')
            if (major.isNotEmpty() && Regex(
                    "\\b(android|google) tv $major\\b|\\bandroid $major\\b",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(list.title + " " + list.header.getOrNull(1).orEmpty())
            ) {
                similar += 20
            }
            score = maxOf(score, similar)
        }
        return score
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length >= 3 }.toSet()

    private fun compact(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}

/**
 * Catálogo empaquetado en `assets/debloat`. Se lee una vez y se cachea; son ~2.3 MB de texto.
 */
internal object DebloatCatalog {
    @Volatile
    private var cached: List<DebloatList>? = null

    @Volatile
    private var rejected: List<ParseResult.Rejected> = emptyList()

    fun load(context: Context): List<DebloatList> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val parsed = mutableListOf<DebloatList>()
            val bad = mutableListOf<ParseResult.Rejected>()
            val assets = context.assets.list("debloat").orEmpty()
                .filter { it.endsWith(".txt", ignoreCase = true) && !it.startsWith("LICENSE", ignoreCase = true) }
                .sorted()
            for (name in assets) {
                val id = name.removeSuffix(".txt")
                val text = runCatching {
                    context.assets.open("debloat/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull() ?: continue
                when (val result = ListParser.parse(id, text)) {
                    is ParseResult.Ok -> parsed += result.list
                    is ParseResult.Rejected -> bad += result
                }
            }
            rejected = bad
            cached = parsed
            return parsed
        }
    }

    /** Listas rechazadas por el parser (para diagnóstico; no se ofrecen). */
    fun rejected(): List<ParseResult.Rejected> = rejected
}
