package com.chronosbvrx.visionplus.data

import android.content.Context
import android.content.SharedPreferences
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import org.json.JSONArray
import org.json.JSONObject

/** Un canal visto hace poco: lo mínimo para volver a abrirlo sin esperar la parrilla. */
data class RecentLiveChannel(
    val code: String,
    val name: String,
    val logo: String = "",
    val watchedAt: Long = 0L,
) {
    fun toChannel(): LiveChannel = LiveChannel(
        name = name,
        url = "xtvlive:$code",
        logo = logo.takeIf { it.isNotBlank() },
        group = "Recientes",
    )
}

/**
 * Memoria corta de TV en vivo, por perfil: último canal y recientes. El launcher ya guarda
 * su propio último canal para la baldosa «TV en vivo», pero la app no lo recordaba al volver
 * a abrirse; con esto el usuario puede retomar lo que estaba viendo.
 */
object LiveHistory {
    private const val PREFS = "vision_live_history"
    private const val KEY = "recent"
    private const val MAX = 12

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            ProfileStore.prefsName(context, PREFS),
            Context.MODE_PRIVATE,
        )

    fun remember(context: Context, code: String, name: String, logo: String = "") {
        val cleanCode = code.trim()
        if (cleanCode.isBlank() || name.isBlank()) return
        val current = recent(context, MAX)
        val next = listOf(
            RecentLiveChannel(
                code = cleanCode,
                name = name.trim(),
                logo = logo.trim(),
                watchedAt = System.currentTimeMillis(),
            ),
        ) + current.filterNot { it.code == cleanCode }
        val array = JSONArray()
        next.take(MAX).forEach { item ->
            array.put(
                JSONObject()
                    .put("c", item.code)
                    .put("n", item.name)
                    .put("l", item.logo)
                    .put("at", item.watchedAt),
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    fun recent(context: Context, limit: Int = MAX): List<RecentLiveChannel> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val code = o.optString("c").trim()
                val name = o.optString("n").trim()
                if (code.isBlank() || name.isBlank()) null else RecentLiveChannel(
                    code = code,
                    name = name,
                    logo = o.optString("l"),
                    watchedAt = o.optLong("at"),
                )
            }.take(limit.coerceAtLeast(0))
        }.getOrDefault(emptyList())
    }

    fun last(context: Context): RecentLiveChannel? = recent(context, 1).firstOrNull()
}
