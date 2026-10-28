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

/**
 * Dónde abre el launcher al ENCENDER la tele (no al pulsar Home, que siempre vuelve al
 * Inicio para que el botón nunca sorprenda). [LAST] recuerda la última pestaña usada.
 */
enum class StartTab(val label: String) {
    HOME("Inicio"),
    LIVE("TV en vivo"),
    INPUTS("Entradas de la tele"),
    APPS("Apps"),
    LAST("La última que usaste"),
}

/**
 * Plantillas de Inicio: un launcher de verdad trae atajos de disposición, no solo opciones
 * sueltas. Se aplican al instante y se deshacen con «Restablecer personalización».
 */
enum class LauncherPreset(val label: String, val detail: String) {
    TV_FIRST("TV primero", "TV en vivo, Mis canales y las entradas de la tele al frente"),
    CINEMA("Cine", "Top 10, estrenos y el catálogo primero; la TV en vivo se aparta"),
    JUST_MINE("Solo lo mío", "Seguir viendo, Mis canales y tus apps; sin catálogo de relleno"),
    KIDS("Niños", "Infantil y anime primero, con las secciones de adultos fuera"),
}

/** Preferencias visuales del launcher. */
data class DisplayConfig(    val density: LauncherDensity = LauncherDensity.COMFORT,
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
    /** Pestaña con la que abre el launcher al encender la tele. */
    val startTab: StartTab = StartTab.HOME,
    /** Entradas de video del televisor que el usuario quitó del launcher (no del sistema). */
    val hiddenInputs: Set<String> = emptySet(),
    /** Orden de las pestañas de la barra (Inicio siempre va primero). */
    val tabOrder: List<String> = DEFAULT_TAB_ORDER,
    /** Pestañas que el usuario quitó de la barra. */
    val hiddenTabs: Set<String> = emptySet(),
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

    fun setStartTab(tab: StartTab): HomeConfig = copy(startTab = tab)

    fun toggleInputHidden(id: String): HomeConfig {
        if (id.isBlank()) return this
        val hidden = if (id in hiddenInputs) hiddenInputs - id else hiddenInputs + id
        return copy(hiddenInputs = hidden)
    }

    fun resetInputs(): HomeConfig = copy(hiddenInputs = emptySet())

    /** Mueve una pestaña; Inicio no se mueve ni se oculta (siempre abre el Inicio). */
    fun moveTab(id: String, delta: Int): HomeConfig {
        if (id == HOME_TAB_ID) return this
        val index = tabOrder.indexOf(id)
        if (index < 0) return this
        val target = (index + delta).coerceIn(1, tabOrder.lastIndex)
        if (target == index) return this
        val next = tabOrder.toMutableList().apply { add(target, removeAt(index)) }
        return copy(tabOrder = next)
    }

    fun toggleTab(id: String): HomeConfig {
        if (id.isBlank() || id == HOME_TAB_ID) return this
        val hidden = if (id in hiddenTabs) hiddenTabs - id else hiddenTabs + id
        return copy(hiddenTabs = hidden)
    }

    fun resetTabs(): HomeConfig = copy(tabOrder = DEFAULT_TAB_ORDER, hiddenTabs = emptySet())

    /**
     * Aplica una plantilla: ordena las filas y las pestañas, y oculta lo que sobra. Es
     * determinista (no depende de la configuración anterior) para que se sienta una plantilla
     * de verdad; cualquier ajuste posterior del usuario manda.
     */
    fun withPreset(preset: LauncherPreset): HomeConfig = when (preset) {
        LauncherPreset.TV_FIRST -> copy(
            rowOrder = rowsFirst("continue", "channels", "live", "inputs", "apps", "top10"),
            hiddenRows = emptySet(),
            tabOrder = tabsFirst("home", "live", "inputs", "events", "apps"),
            hiddenTabs = emptySet(),
        )
        LauncherPreset.CINEMA -> copy(
            rowOrder = rowsFirst(
                "continue", "top10", "premieres",
                "home-kind-movie", "home-kind-series", "home-kind-anime", "apps",
            ),
            hiddenRows = setOf("live", "channels", "inputs"),
            tabOrder = tabsFirst("home", "movies", "series", "apps"),
            hiddenTabs = emptySet(),
        )
        LauncherPreset.JUST_MINE -> copy(
            rowOrder = rowsFirst("continue", "channels", "apps"),
            hiddenRows = setOf(
                "live", "inputs", "top10", "premieres",
                "home-kind-movie", "home-kind-series", "home-kind-anime", "home-kind-kids",
            ),
            tabOrder = tabsFirst("home", "apps"),
            hiddenTabs = setOf("live", "events"),
        )
        LauncherPreset.KIDS -> copy(
            // El modo niños de Vision+ filtra el catálogo; aquí se deja el Inicio a la vista.
            rowOrder = rowsFirst("home-kind-kids", "home-kind-anime", "continue", "apps"),
            hiddenRows = setOf(
                "live", "channels", "inputs", "top10", "premieres",
                "home-kind-movie", "home-kind-series",
            ),
            tabOrder = tabsFirst("home", "apps"),
            hiddenTabs = setOf("live", "events", "movies", "series"),
        )
    }

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
            startTab != StartTab.HOME ||
            hiddenInputs.isNotEmpty() ||
            tabOrder != DEFAULT_TAB_ORDER ||
            hiddenTabs.isNotEmpty() ||
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
        put("startTab", startTab.name)
        put("hiddenInputs", JSONArray(hiddenInputs.toList()))
        put("tabOrder", JSONArray(tabOrder))
        put("hiddenTabs", JSONArray(hiddenTabs.toList()))
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
        /** Versión 3: entradas de la tele, pestaña inicial y barra de pestañas configurable. */
        const val VERSION = 3

        /** Inicio nunca se oculta ni se mueve: es la puerta de vuelta del launcher. */
        const val HOME_TAB_ID = "home"

        /** El orden de la barra que traía el launcher. */
        val DEFAULT_TAB_ORDER = listOf("home", "live", "inputs", "events", "movies", "series", "apps")

        /** Filas sintéticas + filas del feed, en el orden que tenía el Inicio. */
        val DEFAULT_ROW_ORDER = listOf(
            "continue",
            "channels",
            "live",
            "inputs",
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
            "inputs" to "Entradas de la tele",
            "apps" to "Apps",
        )

        fun default(legacyAppFavorites: Collection<String> = emptyList()): HomeConfig =
            HomeConfig(appFavorites = legacyAppFavorites.distinct())

        /** JSON tolerante: cualquier basura cae a los valores por defecto sin romper el Inicio. */
        fun fromJson(raw: String?, legacyAppFavorites: Collection<String> = emptyList()): HomeConfig {
            if (raw.isNullOrBlank()) return default(legacyAppFavorites)
            return runCatching {
                val root = JSONObject(raw)
                val version = root.optInt("version", 1)
                val order = migrateRowOrder(root.optJSONArray("rowOrder").strings(), version)
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
                    startTab = enumOf(root.optString("startTab"), StartTab.HOME),
                    hiddenInputs = root.optJSONArray("hiddenInputs").strings().toSet(),
                    tabOrder = root.optJSONArray("tabOrder").strings(),
                    hiddenTabs = root.optJSONArray("hiddenTabs").strings().toSet(),
                ).normalized()
            }.getOrDefault(default(legacyAppFavorites))
        }

        /** La plantilla empieza por estas filas y conserva las demás detrás. */
        private fun rowsFirst(vararg ids: String): List<String> =
            (ids.toList() + DEFAULT_ROW_ORDER).distinct()

        private fun tabsFirst(vararg ids: String): List<String> =
            (ids.toList() + DEFAULT_TAB_ORDER).distinct()

        /**
         * Una configuración guardada por la versión 1 no conoce la fila «Entradas». Se le
         * inserta EN SU SITIO (después de TV en vivo) en vez de dejarla al final: el Inicio
         * queda como el de una tele, sin reordenar lo que la persona ya había movido.
         */
        internal fun migrateRowOrder(order: List<String>, version: Int): List<String> {
            val base = order.ifEmpty { DEFAULT_ROW_ORDER }
            if (version >= VERSION || "inputs" in base) return base
            val next = base.toMutableList()
            val afterLive = next.indexOf("live")
            next.add(if (afterLive >= 0) afterLive + 1 else next.size, "inputs")
            return next
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
            // Las pestañas nuevas de una versión futura entran al final, nunca desaparecen.
            tabOrder = (tabOrder + DEFAULT_TAB_ORDER).distinct(),
            hiddenTabs = hiddenTabs - HOME_TAB_ID,
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
