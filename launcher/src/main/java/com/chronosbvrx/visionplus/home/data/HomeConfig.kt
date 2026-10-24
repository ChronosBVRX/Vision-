package com.chronosbvrx.visionplus.home.data

import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.LiveItem
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Densidad del Inicio: cambia pósteres, filas y cuánto se ve de una vez. */
enum class LauncherDensity { COMPACT, COMFORT, CINEMA }

/** Animaciones: Auto respeta la RAM de la tele; el usuario puede forzar. */
enum class LauncherAnimations { AUTO, LIGHT, FULL }

/** Orden del mosaico de canales. */
enum class ChannelSort { AUTO, FAVORITES, ALPHA }

/** Preferencias visuales del launcher. */
data class DisplayConfig(
    val density: LauncherDensity = LauncherDensity.COMFORT,
    val animations: LauncherAnimations = LauncherAnimations.AUTO,
    val clock24h: Boolean = true,
    val showClock: Boolean = true,
    val showEpg: Boolean = true,
    val itemsPerRow: Int = 12,
) {
    fun normalized(): DisplayConfig = copy(itemsPerRow = itemsPerRow.coerceIn(MIN_ITEMS, MAX_ITEMS))

    companion object {
        const val MIN_ITEMS = 8
        const val MAX_ITEMS = 18
        val ITEM_OPTIONS = listOf(8, 12, 18)
    }
}

/**
 * La personalización del Inicio por perfil. Vive SOLO en el launcher: el feed de Vision+
 * sigue siendo la fuente de datos y esta capa únicamente filtra, ordena y muestra.
 */
data class HomeConfig(
    val version: Int = VERSION,
    val rowOrder: List<String> = DEFAULT_ROW_ORDER,
    val hiddenRows: Set<String> = emptySet(),
    val channelFavorites: List<String> = emptyList(),
    val hiddenChannels: Set<String> = emptySet(),
    val channelSort: ChannelSort = ChannelSort.AUTO,
    val appFavorites: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val display: DisplayConfig = DisplayConfig(),
) {
    fun toggleChannelFavorite(code: String): HomeConfig {
        if (code.isBlank()) return this
        val favorites = if (code in channelFavorites) {
            channelFavorites - code
        } else {
            channelFavorites + code
        }
        // Favorito y oculto son excluyentes: fijar un canal lo vuelve a mostrar.
        return copy(channelFavorites = favorites, hiddenChannels = hiddenChannels - code)
    }

    fun toggleChannelHidden(code: String): HomeConfig {
        if (code.isBlank()) return this
        val hidden = if (code in hiddenChannels) hiddenChannels - code else hiddenChannels + code
        return copy(
            hiddenChannels = hidden,
            channelFavorites = if (code in hidden) channelFavorites - code else channelFavorites,
        )
    }

    fun moveChannelFavorite(code: String, delta: Int): HomeConfig {
        val index = channelFavorites.indexOf(code)
        if (index < 0) return this
        val target = (index + delta).coerceIn(0, channelFavorites.lastIndex)
        if (target == index) return this
        val next = channelFavorites.toMutableList().apply { add(target, removeAt(index)) }
        return copy(channelFavorites = next)
    }

    fun promoteChannelFavorite(code: String): HomeConfig =
        if (code.isBlank()) this else copy(
            channelFavorites = listOf(code) + channelFavorites.filterNot { it == code },
            hiddenChannels = hiddenChannels - code,
        )

    fun setChannelSort(sort: ChannelSort): HomeConfig = copy(channelSort = sort)

    fun toggleAppFavorite(packageName: String): HomeConfig {
        if (packageName.isBlank()) return this
        val favorites = if (packageName in appFavorites) {
            appFavorites - packageName
        } else {
            appFavorites + packageName
        }
        return copy(appFavorites = favorites, hiddenApps = hiddenApps - packageName)
    }

    fun toggleAppHidden(packageName: String): HomeConfig {
        if (packageName.isBlank()) return this
        val hidden = if (packageName in hiddenApps) hiddenApps - packageName else hiddenApps + packageName
        return copy(
            hiddenApps = hidden,
            appFavorites = if (packageName in hidden) appFavorites - packageName else appFavorites,
        )
    }

    fun moveAppFavorite(packageName: String, delta: Int): HomeConfig {
        val index = appFavorites.indexOf(packageName)
        if (index < 0) return this
        val target = (index + delta).coerceIn(0, appFavorites.lastIndex)
        if (target == index) return this
        val next = appFavorites.toMutableList().apply { add(target, removeAt(index)) }
        return copy(appFavorites = next)
    }

    fun promoteAppFavorite(packageName: String): HomeConfig =
        if (packageName.isBlank()) this else copy(
            appFavorites = listOf(packageName) + appFavorites.filterNot { it == packageName },
            hiddenApps = hiddenApps - packageName,
        )

    fun toggleRow(id: String): HomeConfig {
        if (id.isBlank()) return this
        val hidden = if (id in hiddenRows) hiddenRows - id else hiddenRows + id
        return copy(hiddenRows = hidden)
    }

    fun moveRow(id: String, delta: Int): HomeConfig {
        val index = rowOrder.indexOf(id)
        if (index < 0) return this
        val target = (index + delta).coerceIn(0, rowOrder.lastIndex)
        if (target == index) return this
        val next = rowOrder.toMutableList().apply { add(target, removeAt(index)) }
        return copy(rowOrder = next)
    }

    fun withDisplay(display: DisplayConfig): HomeConfig = copy(display = display.normalized())

    fun resetChannels(): HomeConfig = copy(
        channelFavorites = emptyList(),
        hiddenChannels = emptySet(),
        channelSort = ChannelSort.AUTO,
    )

    fun resetApps(): HomeConfig = copy(appFavorites = emptyList(), hiddenApps = emptySet())

    fun resetRows(): HomeConfig = copy(rowOrder = DEFAULT_ROW_ORDER, hiddenRows = emptySet())

    fun resetAll(): HomeConfig = HomeConfig()

    /** ¿El usuario tocó algo o es la vista original? Sirve para migrar sin pisar datos. */
    fun hasCustomization(): Boolean =
        channelFavorites.isNotEmpty() ||
            hiddenChannels.isNotEmpty() ||
            hiddenRows.isNotEmpty() ||
            appFavorites.isNotEmpty() ||
            hiddenApps.isNotEmpty() ||
            rowOrder != DEFAULT_ROW_ORDER ||
            display != DisplayConfig()

    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("rowOrder", JSONArray(rowOrder))
        put("hiddenRows", JSONArray(hiddenRows.toList()))
        put("channelFavorites", JSONArray(channelFavorites))
        put("hiddenChannels", JSONArray(hiddenChannels.toList()))
        put("channelSort", channelSort.name)
        put("appFavorites", JSONArray(appFavorites))
        put("hiddenApps", JSONArray(hiddenApps.toList()))
        put("display", JSONObject().apply {
            put("density", display.density.name)
            put("animations", display.animations.name)
            put("clock24h", display.clock24h)
            put("showClock", display.showClock)
            put("showEpg", display.showEpg)
            put("itemsPerRow", display.itemsPerRow)
        })
    }.toString()

    companion object {
        const val VERSION = 1

        /** Filas sintéticas + filas del feed, en el orden que tenía el Inicio. */
        val DEFAULT_ROW_ORDER = listOf(
            "continue",
            "channels",
            "live",
            "apps",
            "top10",
            "premieres",
            "home-kind-movie",
            "home-kind-series",
            "home-kind-anime",
            "home-kind-kids",
        )

        val SYNTHETIC_ROWS = linkedMapOf(
            "continue" to "Seguir viendo",
            "channels" to "Mis canales",
            "live" to "TV en vivo",
            "apps" to "Apps",
        )

        fun default(legacyAppFavorites: Collection<String> = emptyList()): HomeConfig =
            HomeConfig(appFavorites = legacyAppFavorites.distinct())

        /** JSON tolerante: cualquier basura cae a los valores por defecto sin romper el Inicio. */
        fun fromJson(raw: String?, legacyAppFavorites: Collection<String> = emptyList()): HomeConfig {
            if (raw.isNullOrBlank()) return default(legacyAppFavorites)
            return runCatching {
                val root = JSONObject(raw)
                val order = root.optJSONArray("rowOrder").strings().ifEmpty { DEFAULT_ROW_ORDER }
                val favorites = root.optJSONArray("appFavorites")
                    ?.strings()
                    ?: legacyAppFavorites.toList()
                HomeConfig(
                    rowOrder = order.distinct(),
                    hiddenRows = root.optJSONArray("hiddenRows").strings().toSet(),
                    channelFavorites = root.optJSONArray("channelFavorites").strings().distinct(),
                    hiddenChannels = root.optJSONArray("hiddenChannels").strings().toSet(),
                    channelSort = enumOf(root.optString("channelSort"), ChannelSort.AUTO),
                    appFavorites = favorites.distinct(),
                    hiddenApps = root.optJSONArray("hiddenApps").strings().toSet(),
                    display = displayFrom(root.optJSONObject("display")),
                ).normalized()
            }.getOrDefault(default(legacyAppFavorites))
        }

        private fun displayFrom(obj: JSONObject?): DisplayConfig {
            if (obj == null) return DisplayConfig()
            return DisplayConfig(
                density = enumOf(obj.optString("density"), LauncherDensity.COMFORT),
                animations = enumOf(obj.optString("animations"), LauncherAnimations.AUTO),
                clock24h = obj.optBoolean("clock24h", true),
                showClock = obj.optBoolean("showClock", true),
                showEpg = obj.optBoolean("showEpg", true),
                itemsPerRow = obj.optInt("itemsPerRow", 12),
            ).normalized()
        }

        private inline fun <reified T : Enum<T>> enumOf(value: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

        private fun HomeConfig.normalized(): HomeConfig = copy(
            rowOrder = if (rowOrder.isEmpty()) DEFAULT_ROW_ORDER else rowOrder,
            display = display.normalized(),
        )

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            val out = ArrayList<String>(length())
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotBlank()) out += value
            }
            return out
        }
    }
}

/** Elige entre la configuración primaria y la vieja guardada con el nombre del perfil. */
internal fun chooseConfig(primary: HomeConfig, alias: HomeConfig?): HomeConfig {
    if (alias == null) return primary
    val aliasWins = alias.hasCustomization() && (
        !primary.hasCustomization() ||
            (alias.channelFavorites.isNotEmpty() && primary.channelFavorites.isEmpty()) ||
            (alias.hiddenChannels.isNotEmpty() && primary.hiddenChannels.isEmpty()) ||
            (alias.hiddenRows.isNotEmpty() && primary.hiddenRows.isEmpty()) ||
            (alias.hiddenApps.isNotEmpty() && primary.hiddenApps.isEmpty())
        )
    return if (aliasWins) alias else primary
}

/** El orden final de filas: primero lo configurado, luego lo nuevo que traiga el feed. */
internal fun homeRowOrder(config: HomeConfig, feedRows: List<FeedRow>): List<String> {
    val feedIds = feedRows.map { it.id }
    return (config.rowOrder + feedIds + HomeConfig.SYNTHETIC_ROWS.keys).distinct()
}

/** Todos los ids/labels que el gestor de filas puede mostrar. */
internal fun homeRowEntries(config: HomeConfig, feedRows: List<FeedRow>): List<RowEntry> {
    val labels = LinkedHashMap<String, String>()
    HomeConfig.SYNTHETIC_ROWS.forEach { (id, label) -> labels[id] = label }
    feedRows.forEach { row -> labels[row.id] = row.label }
    val ordered = (config.rowOrder + labels.keys).distinct()
    return ordered.mapNotNull { id -> labels[id]?.let { RowEntry(id, it) } }
}

internal data class RowEntry(val id: String, val label: String)

/** Canales visibles: ocultos fuera; el orden depende de la preferencia. */
internal fun visibleChannels(items: List<LiveItem>, config: HomeConfig): List<LiveItem> {
    val visible = items.filterNot { it.code in config.hiddenChannels }
    return when (config.channelSort) {
        ChannelSort.AUTO -> visible
        ChannelSort.ALPHA -> visible.sortedBy { it.name.lowercase(Locale.ROOT) }
        ChannelSort.FAVORITES -> {
            val rank = config.channelFavorites.withIndex().associate { it.value to it.index }
            visible.sortedBy { rank[it.code] ?: Int.MAX_VALUE }
        }
    }
}

/** «Mis canales»: el orden manual de favoritos, sin los que se ocultaron. */
internal fun favoriteChannels(items: List<LiveItem>, config: HomeConfig): List<LiveItem> {
    if (config.channelFavorites.isEmpty()) return emptyList()
    val byCode = items.associateBy { it.code }
    return config.channelFavorites
        .asSequence()
        .filterNot { it in config.hiddenChannels }
        .mapNotNull { byCode[it] }
        .toList()
}

/** Apps visibles: favoritas en su orden manual, luego recientes/alfabéticas. */
internal fun visibleApps(
    apps: List<AppEntry>,
    config: HomeConfig,
    recent: List<String> = emptyList(),
): List<AppEntry> {
    val visible = apps.filterNot { it.packageName in config.hiddenApps }
    val favoriteRank = config.appFavorites.withIndex().associate { it.value to it.index }
    val recentRank = recent.withIndex().associate { it.value to it.index }
    val (favorites, rest) = visible.partition { it.packageName in favoriteRank }
    val orderedFavorites = favorites.sortedBy { favoriteRank[it.packageName] ?: Int.MAX_VALUE }
    val orderedRest = rest.sortedWith(
        compareBy<AppEntry> { recentRank[it.packageName] ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )
    return orderedFavorites + orderedRest
}
