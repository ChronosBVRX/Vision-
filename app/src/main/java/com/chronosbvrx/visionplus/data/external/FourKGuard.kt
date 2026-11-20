package com.chronosbvrx.visionplus.data.external

import android.content.Context

/**
 * Blindaje del 4K, sin tocar la navegación de la app.
 *
 * Si el 4K de una ficha falla, se oculta ese 4K durante unos minutos para no caer
 * en un ciclo de fallo → reintento → fallo. El reproductor sigue vivo y vuelve al
 * 1080p; no se relanza ninguna actividad ni se pisa el reproductor en vivo.
 */
internal object FourKGuard {
    private const val PREFS = "x4k_guard"
    private const val KEY_BLOCK_PREFIX = "block_"

    /** El 4K de una ficha se oculta este tiempo después de fallar. */
    private const val BLOCK_TTL_MS = 5 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun block(context: Context, titleId: String) {
        if (titleId.isBlank()) return
        // commit: la marca debe quedar en disco antes de tocar el decodificador 4K.
        // Si el proceso muere durante el intento, la proxima reproduccion ya entra en 1080p.
        prefs(context).edit()
            .putLong(KEY_BLOCK_PREFIX + titleId, System.currentTimeMillis())
            .commit()
    }

    fun isBlocked(context: Context, titleId: String): Boolean {
        if (titleId.isBlank()) return false
        val p = prefs(context)
        val at = p.getLong(KEY_BLOCK_PREFIX + titleId, 0L)
        if (at <= 0L) return false
        if (System.currentTimeMillis() - at > BLOCK_TTL_MS) {
            p.edit().remove(KEY_BLOCK_PREFIX + titleId).apply()
            return false
        }
        return true
    }

    fun clearBlock(context: Context, titleId: String) {
        if (titleId.isBlank()) return
        prefs(context).edit().remove(KEY_BLOCK_PREFIX + titleId).apply()
    }
}
