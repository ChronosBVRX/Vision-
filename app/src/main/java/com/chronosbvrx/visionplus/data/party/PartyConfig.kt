package com.chronosbvrx.visionplus.data.party

import android.content.Context
import android.net.Uri
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.data.config.RemoteConfig

/**
 * Servidor de Vision+ Party.
 *
 * La plataforma web (Open Party Lab + juegos) vive fuera del APK: la tele abre
 * el host en un WebView y los teléfonos entran por QR. El APK no carga los
 * assets de los juegos ni los audios de la narradora, así que se mantiene
 * ligero.
 *
 * `party_host` se puede cambiar desde /v1/config sin publicar OTA.
 */
object PartyConfig {
    const val DEFAULT_HOST = "https://party.visionplusv2.duckdns.org/"

    fun host(context: Context): String =
        RemoteConfig.stringFlag(context, "party_host", DEFAULT_HOST)
            .trim()
            .ifBlank { DEFAULT_HOST }
            .trimEnd('/')

    fun hostUrl(context: Context, gameId: String? = null, lowPower: Boolean = false): String {
        val base = host(context)
        val query = buildList {
            add("platform=android")
            if (!gameId.isNullOrBlank()) add("game=${Uri.encode(gameId)}")
            if (lowPower) add("quality=low")
            // Una URL distinta por OTA evita que el WebView reutilice el
            // documento anterior y garantiza que el lobby nuevo entre limpio.
            add("v=${BuildConfig.VERSION_CODE}")
            // Host-only deploys do not bump the APK version. A fresh token
            // prevents an old cached document from hiding newly added games.
            add("cb=${System.currentTimeMillis()}")
        }
        // Entra directo al host: la landing queda para el navegador. La sala
        // nace ya dedicada al juego elegido y no muestra el catálogo.
        return "$base/host?${query.joinToString("&")}"
    }

    /** Mando del celular dentro de la app, sin chrome de navegador. */
    fun controllerUrl(context: Context, roomCode: String): String {
        val code = roomCode.trim().uppercase().take(4)
        // Query (not hash) changes force a real document reload. `fresh=1`
        // discards the old room before the controller client boots; `cb`
        // prevents WebView from reusing the previous controller document.
        return "${host(context)}/controller/?fresh=1&cb=${System.currentTimeMillis()}#join?room=${Uri.encode(code)}"
    }
}
