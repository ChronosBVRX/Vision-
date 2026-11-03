package com.chronosbvrx.visionplus.data.games

import java.util.Locale

/** Códigos de sala: 6 caracteres sin I, L, O, 0 ni 1 (se dictan sin errores). */
object GameCodes {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun normalize(raw: String?): String? {
        val text = (raw ?: return null)
            .uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
        if (text.length != LENGTH) return null
        if (text.any { it !in ALPHABET }) return null
        return text
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null

    /** Formato para leer en voz alta: ABC-234. */
    fun format(raw: String?): String {
        val code = normalize(raw) ?: return ""
        return "${code.take(3)}-${code.drop(3)}"
    }
}
