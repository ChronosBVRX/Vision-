package com.chronosbvrx.visionplus.data.netshare

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Arte LOCAL que el servidor puede servir por `/artwork/<clave>`.
 *
 * El JSON nunca lleva base64: si el póster o el logo es un archivo/URI local, se registra aquí
 * y se devuelve una URL `http://<IP>:8765/artwork/<clave>`. La clave es un hash del recurso;
 * el servidor solo abre lo que está registrado (no hay rutas arbitrarias).
 */
internal object ArtworkStore {
    private val items = ConcurrentHashMap<String, String>()

    fun register(raw: String): String {
        val key = sha256(raw).take(32)
        items[key] = raw
        return key
    }

    fun get(key: String): String? = items[key]

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
