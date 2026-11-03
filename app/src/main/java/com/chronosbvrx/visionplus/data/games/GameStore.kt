package com.chronosbvrx.visionplus.data.games

import android.content.Context
import com.chronosbvrx.visionplus.data.profile.ProfileStore

/** Tokens por sala y preferencias del jugador. Nunca guarda la respuesta correcta. */
object GameStore {
    private const val PREFS = "vision_games"
    private const val K_NAME = "nickname"
    private const val K_RECENT = "recent"
    private const val K_NARRATOR = "narrator"
    private const val MAX_RECENT = 5

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nickname(context: Context): String {
        val saved = prefs(context).getString(K_NAME, "").orEmpty().trim()
        if (saved.isNotBlank()) return saved.take(14)
        val profile = runCatching { ProfileStore.active(context).name }.getOrDefault("")
        return profile.trim().take(14).ifBlank { "Jugador" }
    }

    fun saveNickname(context: Context, name: String) {
        prefs(context).edit().putString(K_NAME, name.trim().take(14)).commit()
    }

    fun playerToken(context: Context, code: String): String? {
        val key = GameCodes.normalize(code) ?: return null
        return prefs(context).getString("player_$key", null)?.takeIf { it.isNotBlank() }
    }

    fun hostToken(context: Context, code: String): String? {
        val key = GameCodes.normalize(code) ?: return null
        return prefs(context).getString("host_$key", null)?.takeIf { it.isNotBlank() }
    }

    fun savePlayer(context: Context, code: String, token: String) {
        val key = GameCodes.normalize(code) ?: return
        prefs(context).edit().putString("player_$key", token).commit()
    }

    fun saveHost(context: Context, code: String, token: String) {
        val key = GameCodes.normalize(code) ?: return
        prefs(context).edit().putString("host_$key", token).commit()
    }

    fun forget(context: Context, code: String) {
        val key = GameCodes.normalize(code) ?: return
        prefs(context).edit()
            .remove("player_$key")
            .remove("host_$key")
            .commit()
    }

    fun remember(context: Context, code: String) {
        val clean = GameCodes.normalize(code) ?: return
        val next = (listOf(clean) + recent(context)).distinct().take(MAX_RECENT)
        prefs(context).edit().putString(K_RECENT, next.joinToString(",")).commit()
    }

    fun recent(context: Context): List<String> = normalizeRecent(prefs(context).getString(K_RECENT, ""))

    internal fun normalizeRecent(raw: String?): List<String> =
        (raw ?: "").split(',')
            .mapNotNull { GameCodes.normalize(it) }
            .distinct()
            .take(MAX_RECENT)

    fun narratorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_NARRATOR, true)

    fun setNarratorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_NARRATOR, enabled).commit()
    }

    /** Por simetría con los demás almacenes; aquí no hay caché estática. */
    fun resetMemoryForTests() = Unit
}
