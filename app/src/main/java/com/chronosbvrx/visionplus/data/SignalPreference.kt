package com.chronosbvrx.visionplus.data

import android.content.Context

/**
 * Memoria adaptativa de señales (Adaptive Signal Memory), inspirada en KronosTV:
 * recuerda, por canal/evento, la URL de la señal que reprodujo correctamente y la
 * promueve a la primera posición la próxima vez. Así el arranque usa la señal
 * conocida como buena y se reducen los failover automáticos.
 *
 * Persistencia en SharedPreferences (clave = nombre del canal/evento, valor = URL).
 * Sin caducidad: la preferencia se sobreescribe cuando otra señal gana.
 */
object SignalPreference {
    private const val PREFS = "vision_signal_pref"
    private const val PREFIX = "pref_"

    private fun key(channelKey: String): String = PREFIX + channelKey.hashCode().toString()

    fun preferredUrl(context: Context, channelKey: String): String? {
        if (channelKey.isBlank()) return null
        return runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(channelKey), null)
        }.getOrNull()
    }

    fun setPreferred(context: Context, channelKey: String, url: String) {
        if (channelKey.isBlank() || url.isBlank()) return
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key(channelKey), url)
                .apply()
        }
    }

    fun clear(context: Context, channelKey: String) {
        if (channelKey.isBlank()) return
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(key(channelKey))
                .apply()
        }
    }
}