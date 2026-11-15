package com.chronosbvrx.visionplus.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auditoría proactiva de señales (inspirada en KronosTV): hace un HEAD rápido a cada
 * señal candidata y, si la preferida falla pero otra responde, promueve la que responde
 * como preferida en [SignalPreference]. Se ejecuta en segundo plano y nunca bloquea la UI.
 *
 * Pensado para invocarse con la lista de señales de un evento deportivo (las mismas que
 * alimentan el zapping), idealmente cuando el usuario no está reproduciendo.
 */
object SignalAudit {

    private const val TIMEOUT_MS = 2_500

    /** Prueba una URL con HEAD; true si responde 2xx/3xx. */
    fun probe(url: String): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    /**
     * Audita las señales de un evento: si la primera (preferida actual) no responde pero
     * alguna otra sí, promueve la primera que responda como preferida.
     */
    fun auditEvent(context: Context, eventKey: String, urls: List<String>, scope: CoroutineScope) {
        if (eventKey.isBlank() || urls.size < 2) return
        scope.launch(Dispatchers.IO) {
            val preferred = SignalPreference.preferredUrl(context, eventKey)
            val ordered = if (preferred != null && urls.contains(preferred)) {
                listOf(preferred) + urls.filter { it != preferred }
            } else {
                urls
            }
            // Si la primera ya responde, no hay nada que hacer.
            if (probe(ordered.first())) return@launch
            for (u in ordered.drop(1)) {
                if (probe(u)) {
                    SignalPreference.setPreferred(context, eventKey, u)
                    return@launch
                }
            }
        }
    }
}