package com.chronosbvrx.visionplus.data.netshare

import com.chronosbvrx.visionplus.data.remote.NowPlaying
import org.json.JSONObject

/**
 * El JSON que consume cualquier equipo de la LAN. Es una traducción DIRECTA del estado que ya
 * publica el reproductor ([NowPlaying]): no se inventa el canal ni el programa.
 *
 * `position` y `duration` viajan en SEGUNDOS (los enteros que entiende cualquier consumidor);
 * `updated` es el epoch en milisegundos del último cambio de estado.
 */
internal object NowPlayingJson {

    fun build(
        now: NowPlaying,
        device: String,
        baseUrl: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val active = now.active
        val live = now.live
        val state = when {
            !active -> "idle"
            now.playing -> "playing"
            else -> "paused"
        }
        // Para el vivo, el modelo nativo trae el canal en `title`, el logo real en
        // `channelLogo`, el programa de la EPG en `program` y su sinopsis en `description`.
        // Si la EPG no tiene datos, se dejan vacíos (no se inventa un programa). En VOD,
        // `channel` no aplica y el programa es el título del contenido.
        val channel = if (live) now.channel.ifBlank { now.title } else ""
        val program = if (live) now.program else now.program.ifBlank { now.title }
        val description = if (live) now.description else now.description.ifBlank { now.subtitle }
        val position = if (live || !active) 0L else (now.positionMs / 1000L).coerceAtLeast(0L)
        val duration = if (live || !active) 0L else (now.durationMs / 1000L).coerceAtLeast(0L)
        val updated = now.updatedAt.takeIf { it > 0L } ?: nowMs
        val artwork = artworkUrl(if (live) now.channelLogo else now.poster, baseUrl)
        val artworkType = when {
            !active -> ""
            live -> "logo"
            else -> "poster"
        }

        return JSONObject().apply {
            put("app", NowPlayingShare.TXT_APP)
            put("device", device)
            put("state", state)
            put("channel", channel)
            put("program", program)
            put("description", description)
            put("position", position)
            put("duration", duration)
            put("updated", updated)
            put("artwork", artwork)
            put("artwork_type", artworkType)
            // Contexto extra para consumidores que quieran distinguir vivo de VOD.
            put("kind", now.kind)
            put("live", live)
            put("title", now.title)
            put("subtitle", now.subtitle)
        }.toString()
    }

    /**
     * URL ABSOLUTA del arte. Las remotas se devuelven tal cual; las locales se registran y se
     * sirven por el mismo servidor (`http://<IP>:8765/artwork/<clave>`), nunca en base64.
     */
    private fun artworkUrl(raw: String, baseUrl: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("data:", ignoreCase = true)) return ""
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            return clean
        }
        if (clean.startsWith("//")) return "https:$clean"
        if (baseUrl.isBlank()) return clean
        val key = ArtworkStore.register(clean)
        return baseUrl.trimEnd('/') + "/artwork/" + key
    }
}
