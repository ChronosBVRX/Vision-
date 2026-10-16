package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.ui.EpisodePlay
import org.json.JSONObject

/**
 * Auto-descarga del «siguiente capítulo» al salir del reproductor: el proceso `:x4k` toma
 * la decisión (ajuste activo + WiFi + capítulo pendiente) y deja aquí la orden; el proceso
 * PRINCIPAL la ejecuta — así el encargo pasa por [OfflineDownloadManager], que devuelve el
 * proceso `:x4k` a limpio si su vista del motor ya reprodujo (un programa headless en un
 * proceso que ya usó la vista mezclaría historias del motor).
 */
object PendingAutoDownload {

    private const val TAG = "PendingAutoDownload"

    /** Lee y consume la orden pendiente (una sola vez; silencioso si no hay nada). */
    fun runIfAny(context: Context) {
        val app = context.applicationContext
        // Archivo (no SharedPreferences): el proceso `:x4k` lo escribió al salir y la copia
        // cacheada del principal podría no verlo.
        val file = java.io.File(app.filesDir, "x4k_pending_download.json")
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        runCatching { file.delete() }
        if (raw.isBlank()) return
        runCatching {
            val spec = JSONObject(raw)
            val code = spec.optString("code")
            if (code.isBlank()) return
            val film = Film(
                id = spec.optString("id").ifBlank { "xtv:$code" },
                title = spec.optString("title"),
                year = 0,
                durationMin = 0,
                synopsis = "",
                posterUrl = spec.optString("poster"),
                videoUrl = "xtv:$code",
                sourceUrl = "xtv",
            )
            val ep = EpisodePlay(
                code = code,
                titleSuffix = " · C${spec.optInt("epIndex") + 1}",
                seriesId = spec.optString("seriesId"),
                episodeIndex = spec.optInt("epIndex"),
                seasonIndex = spec.optInt("seasonIndex"),
                durationMs = spec.optLong("durationMs"),
            )
            OfflineDownloadManager.enqueueDownload(app, film, ep)
        }.onFailure { Log.w(TAG, "no se pudo encargar la descarga pendiente: ${it.message}") }
    }
}
