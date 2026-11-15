package com.chronosbvrx.visionplus.data

import android.content.Context

/**
 * Marca canales (por clave) que han fallado repetidamente, con expiración (4 h),
 * para poder atenuarlos en las listas y ofrecer "Restaurar". Inspirado en el
 * `offlineMap` de KronosTV, pero sin ocultar: solo degradar la prioridad.
 */
object DeadChannelStore {
    private const val PREFS = "vision_dead_channels"
    private const val TTL_MS = 4L * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markDead(context: Context, key: String) {
        if (key.isBlank()) return
        runCatching { prefs(context).edit().putLong(key, System.currentTimeMillis()).apply() }
    }

    fun isDead(context: Context, key: String): Boolean {
        if (key.isBlank()) return false
        val ts = runCatching { prefs(context).getLong(key, 0L) }.getOrDefault(0L)
        if (ts == 0L) return false
        if (System.currentTimeMillis() - ts > TTL_MS) {
            runCatching { prefs(context).edit().remove(key).apply() }
            return false
        }
        return true
    }

    fun clear(context: Context, key: String) {
        if (key.isBlank()) return
        runCatching { prefs(context).edit().remove(key).apply() }
    }

    fun clearAll(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }
}