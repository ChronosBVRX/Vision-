package com.chronosbvrx.visionplus.data.remote

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import java.net.URLEncoder

/**
 * El puente en proceso entre el servidor del control remoto y lo que está en pantalla.
 *
 * La actividad que está al frente se registra ([attach]) y es la única que recibe las teclas
 * remotas: así la tele nunca inyecta una tecla en la pantalla equivocada. El reproductor vive en
 * otro proceso (`:x4k`), así que cuando él tiene el frente la tecla viaja por un broadcast
 * interno ([ACTION_REMOTE_KEY]) que solo él escucha.
 */
object RemoteBus {
    /** Lo que una pantalla al frente sabe hacer con una orden remota. */
    interface Sink {
        /** Una tecla de la lista blanca: la pantalla decide (volumen, o inyectarla al sistema). */
        fun onRemoteKey(code: Int, action: String)
    }

    /** La pantalla al frente; null cuando la app está en segundo plano. */
    @Volatile
    private var sink: Sink? = null

    /** Cuántos teléfonos escuchan (Ajustes/avisos lo muestran). */
    val clients = mutableStateOf(0)

    /**
     * Los dispositivos que esta tele recuerda (nombres, el más reciente primero): los
     * vinculados alguna vez entran solos, sin PIN. Ajustes lo muestra y deja olvidarlos.
     */
    val pairedDevices = mutableStateOf<List<String>>(emptyList())

    /** Lo que la tele está mostrando, para que el teléfono lo refleje. */
    val screen = mutableStateOf("")

    /**
     * PIN que la tele debe mostrar porque un teléfono quiere emparejarse. Null cuando nadie está
     * preguntando: el PIN jamás se pinta solo, solo cuando alguien lo pide desde la red local.
     */
    val pairingPin = mutableStateOf<String?>(null)

    /** El nombre de la tele conectada más reciente (para el aviso de «emparejado»). */
    val pairedWith = mutableStateOf<String?>(null)

    fun attach(s: Sink) {
        sink = s
    }

    fun detach(s: Sink) {
        if (sink === s) sink = null
    }

    /**
     * Entrega una tecla remota a quien está al frente. Si la app está en el menú se atiende aquí
     * mismo; si manda el reproductor (otro proceso), viaja por el broadcast interno.
     */
    fun dispatchKey(context: Context, code: Int, action: String) {
        if (!RemoteKeys.isAllowed(code)) return
        val local = sink
        if (local != null) {
            local.onRemoteKey(code, action)
            return
        }
        broadcastKey(context, code, action)
    }

    /**
     * Manda la tecla al reproductor (`:x4k`) por el broadcast interno, aunque el menú
     * local siga registrado: cuando el reproductor está al frente (transición de
     * actividad), la tecla no puede caer en la pantalla invisible del menú.
     */
    fun broadcastKey(context: Context, code: Int, action: String) {
        if (!RemoteKeys.isAllowed(code)) return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_REMOTE_KEY)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_KEY_CODE, code)
                    .putExtra(EXTRA_KEY_ACTION, action),
            )
        }
    }

    /** La pantalla al frente avisa qué está mostrando (menú). */
    fun publishScreen(text: String) {
        if (screen.value != text) screen.value = text
    }

    /** El reproductor avisa qué está mostrando (otro proceso → broadcast). */
    fun publishScreenRemote(context: Context, text: String) {
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_REMOTE_NOW).setPackage(context.packageName).putExtra(EXTRA_NOW, text),
            )
        }
    }

    const val ACTION_REMOTE_KEY = "com.chronosbvrx.visionplus.action.REMOTE_KEY"
    const val ACTION_REMOTE_NOW = "com.chronosbvrx.visionplus.action.REMOTE_NOW"
    const val EXTRA_KEY_CODE = "code"
    const val EXTRA_KEY_ACTION = "action"
    const val EXTRA_NOW = "now"
}

/**
 * Traduce una orden `open` del teléfono al deep link que la app ya sabe abrir. Puro (se puede
 * probar sin Android) para que la decisión de a dónde va cada referencia quede clara y cubierta.
 *
 * - `xtvlive:<código>` → canal en vivo.
 * - `xtv:<playCode>` → película/serie del portal (por su playCode). Cuando es un capítulo, el
 *   contexto de su serie viaja en los parámetros `s` (temporada activa), `sx` (índice de
 *   temporada) y `sl`/`sn` (las temporadas del grupo): la tele abre ESE capítulo y puede
 *   seguir con el siguiente.
 * - cualquier otra cosa (una URL suelta) → se abre tal cual.
 */
internal fun remoteOpenUriString(
    ref: String,
    title: String,
    poster: String,
    seriesId: String = "",
    seasonIndex: Int = 0,
    seasonIds: List<String> = emptyList(),
    seasonNames: List<String> = emptyList(),
): String? {
    val clean = ref.trim()
    if (clean.isEmpty()) return null
    val t = enc(title)
    val p = enc(poster)
    return when {
        clean.startsWith("xtvlive:") -> {
            val code = clean.removePrefix("xtvlive:").trim()
            if (code.isEmpty()) return null
            "visionplus://live/${enc(code)}?t=$t&p=$p"
        }
        clean.startsWith("xtv:") -> {
            val code = clean.removePrefix("xtv:").trim()
            if (code.isEmpty()) return null
            "visionplus://watch/${enc(clean)}?t=$t&p=$p&c=${enc(code)}" +
                seriesQuery(seriesId, seasonIndex, seasonIds, seasonNames)
        }
        clean.startsWith("http://") || clean.startsWith("https://") ->
            "visionplus://watch/${enc(clean)}?t=$t&p=$p&u=${enc(clean)}"
        else -> null
    }
}

/**
 * El contexto del capítulo como parámetros del enlace (vacío para una película). Solo viaja
 * cuando hay una serie: sin `s` no hay nada que reconstruir en la tele.
 */
private fun seriesQuery(
    seriesId: String,
    seasonIndex: Int,
    seasonIds: List<String>,
    seasonNames: List<String>,
): String {
    val cleanSeries = seriesId.trim()
    if (cleanSeries.isEmpty()) return ""
    val sb = StringBuilder("&s=").append(enc(cleanSeries))
    if (seasonIndex > 0) sb.append("&sx=").append(seasonIndex)
    val ids = seasonIds.map { it.trim() }.filter { it.isNotEmpty() }
    if (ids.isNotEmpty()) sb.append("&sl=").append(enc(ids.joinToString("|")))
    val names = seasonNames.map { it.trim() }.filter { it.isNotEmpty() }
    if (names.isNotEmpty()) sb.append("&sn=").append(enc(names.joinToString("|")))
    return sb.toString()
}

/** Percent-encoding de un parámetro de consulta (URLEncoder con `+` → `%20`). */
private fun enc(s: String): String =
    runCatching { URLEncoder.encode(s, "UTF-8").replace("+", "%20") }.getOrDefault("")
