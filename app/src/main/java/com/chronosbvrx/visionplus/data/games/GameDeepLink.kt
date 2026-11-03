package com.chronosbvrx.visionplus.data.games

import android.net.Uri

/** `visionplus://play/join?code=ABC-234` → código normalizado (o null). */
object GameDeepLink {
    internal fun joinCodeFrom(host: String?, segments: List<String>, codeParam: String?): String? {
        if (!host.equals("play", ignoreCase = true)) return null
        if (segments.firstOrNull()?.lowercase() != "join") return null
        return GameCodes.normalize(codeParam)
    }

    fun joinCode(uri: Uri?): String? {
        if (uri == null) return null
        return joinCodeFrom(uri.host, uri.pathSegments, uri.getQueryParameter("code"))
    }

    /**
     * Texto leído por el escáner QR: acepta el enlace completo, una URL con `code=` o el
     * código pelado. Es puro para probarlo sin cámara.
     */
    fun codeFromScan(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        GameCodes.normalize(text)?.let { return it }
        val marker = "code="
        val start = text.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val value = text.substring(start + marker.length)
            .substringBefore('&')
            .substringBefore('#')
            .trim()
        return GameCodes.normalize(value)
    }
}
