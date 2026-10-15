package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one source's engine, packed inside Vision+: the app itself talks to Xuper's backend with the
 * captured session, and serves standard HLS on localhost. Nothing to configure — first launch does
 * it all — and the proxy's own refresh loop keeps the playlist window alive while the session lasts.
 */
object XuperEngine {
    private const val TAG = "XuperEngine"

    /** The captured opaque playlist path it boots with; the proxy renews its cookies on its own. */
    private const val DEFAULT_PLAYLIST_PATH = "/brpvotelpbjrt"

    @Volatile
    private var proxy: M3uProxyServer? = null
    private var lastFailure: Throwable? = null
    private var lastFailureAt = 0L

    fun isRunning(): Boolean = proxy?.isRunning() == true

    /** Stops serving (the pinned app's own lifecycle: Activity destroyed or new session). */
    fun stop() {
        proxy?.stop()
        proxy = null
    }

    /**
     * Starts serving (idempotent) and answers with the localhost playlist address. If the session
     * is rejected, it rotates it by itself (auto-login with the account it carries) and tries once
     * more; the failure explains what the person can only fix by installing a newer build. When a
     * try already happened seconds ago, its answer repeats instead of hammering the backend.
     */
    suspend fun ensureReady(context: Context): Result<String> = withContext(Dispatchers.Main) {
        // Desarmado permanente: ni cliente ni red; el origen legado ya no puede rotar contra
        // el portal compartido (sus rotaciones rompían la reproducción XTV de la misma cuenta).
        if (isDisabled(context)) {
            return@withContext Result.failure(SourceError("el origen legado está desarmado"))
        }
        if (isRunning()) return@withContext Result.success(proxy!!.getProxyUrl())
        lastFailure?.let { cached ->
            if (System.currentTimeMillis() - lastFailureAt < RETRY_BACKOFF_MS) {
                return@withContext Result.failure(cached)
            }
        }
        // El cliente se construye aquí dentro: si el almacén cifrado falla (sin
        // AndroidKeyStore en tests JVM, keystore dañado en un aparato), eso es un
        // Result.failure con backoff, no una excepción sin atrapar que tumbe el scope.
        val client = try {
            XuperApiClient(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "sin cliente XTV (${e.message}); se desarma el origen legado")
            lastFailure = e
            lastFailureAt = System.currentTimeMillis()
            // Un almacén que ni abre (keyset roto tras un update) no se cura reintentando:
            // se desarma para no fallar en bucle ni tocar el keystore una y otra vez.
            disable(context)
            return@withContext Result.failure(e)
        }
        try {
            val first = serveAndVerify(context, client)
            if (first.isSuccess) return@withContext first
            // The playlist may simply be dead (a 404 from the backend, an expired one-time
            // path): that is not an exception, it is the moment to rotate and try again.
            Log.w(TAG, "la lista no respondió; rotando sesión y reintentando")
            runCatching { rotateSession(client, context) }
            val second = serveAndVerify(context, client)
            if (second.isSuccess) return@withContext second
            return@withContext first
        } catch (first: Exception) {
            Log.w(TAG, "arranque falló (${first.message}); rotando sesión y reintentando")
            runCatching {
                rotateSession(client, context)
                serveAndVerify(context, client)
            }.recover {
                Result.failure(first)
            }.getOrThrow()
        }.also { result ->
            result.exceptionOrNull()?.let {
                lastFailure = it
                lastFailureAt = System.currentTimeMillis()
            } ?: run {
                lastFailure = null
            }
        }
    }

    /** Starts the proxy and checks that its playlist answers; otherwise it stops for a retry. */
    private suspend fun serveAndVerify(context: Context, client: XuperApiClient): Result<String> {
        if (isRunning()) return Result.success(proxy!!.getProxyUrl())
        startProxy(context, client)
        val url = proxy!!.getProxyUrl()
        return if (playlistServes(url)) {
            Result.success(url)
        } else {
            // The session or its path are dead: stop, so the retry starts a fresh proxy.
            Log.w(TAG, "la lista no responde a través del proxy; deteniendo para reintentar")
            stop()
            Result.failure(SourceError("la lista del origen no respondió (sesión o ruta vieja)"))
        }
    }

    private suspend fun playlistServes(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = false
            }
            try {
                val body = connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readLine().orEmpty() }
                connection.responseCode in 200..299 && body?.startsWith("#EXTM3U") == true
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun startProxy(context: Context, client: XuperApiClient) {
        if (isRunning()) return
        val config = client.config
        if (!client.isSessionReady()) throw SourceError("la sesión integrada no tiene cookies (d/s/t)")
        val playlistPath = config.playlistPath.ifBlank { DEFAULT_PLAYLIST_PATH }
        if (config.playlistPath.isBlank()) client.config = config.copy(playlistPath = playlistPath)
        val host = config.apiHost.ifBlank { "23.94.64.155:30822" }
        val scheme = if (config.useHttps) "https" else "http"
        val server = M3uProxyServer(context.applicationContext).apply {
            onCookiesRefreshed = { d, s, t ->
                client.config = client.config.copy(cookieD = d, cookieS = s, cookieT = t)
            }
        }
        server.start("$scheme://$host$playlistPath", segmentUrl(config, host, scheme), config.cookieD, config.cookieS, config.cookieT)
        proxy = server
        Log.i(TAG, "Proxy servido en ${server.getProxyUrl()} (lista $playlistPath)")
    }

    private fun segmentUrl(config: XuperConfig, host: String, scheme: String): String =
        if (config.segmentPath.isNotBlank()) "$scheme://$host${config.segmentPath}" else ""

    /** The session stopped working: log in with the carried account and mint a fresh playlist path. */
    private suspend fun rotateSession(client: XuperApiClient, context: Context) = withContext(Dispatchers.IO) {
        val login = client.loginV8()
        Log.i(TAG, "auto-login: ${login.getOrNull() ?: login.exceptionOrNull()?.message}")
        if (login.isFailure) runCatching { client.probePortalBootstrap() }
        // A renew happens only against the portal's current feedback: a "version discontinued"
        // answer comes when the app version it knows is different, so try the next ones, quietly.
        val portal = client.probeGetLiveData().getOrNull()
        if (portal?.apiSuccess == true || mintFreshPlaylist(client)) return@withContext
        for (version in VERSION_VARIANTS) {
            client.config = client.config.copy(apkVersion = version)
            if (client.probeGetLiveData().getOrNull()?.apiSuccess == true && mintFreshPlaylist(client)) return@withContext
        }
        Log.w(TAG, "nada de la rotación produjo una lista nueva")
        // La sesión quedó inservible y rotar no la revive: desarmar el origen de forma
        // permanente evita que siga pidiendo bootstrap/play al portal compartido (rompía la
        // reproducción XTV del mismo portal en la misma cuenta).
        disable(context)
    }

    /**
     * Desarma el origen legado de forma PERMANENTE. Su configuración capturada renace del
     * código (borrar el almacén no basta — y el almacén es COMPARTIDO con el gate y Magis),
     * y con una sesión rancia sus rotaciones martillan el portal compartido y tiran la
     * reproducción XTV. El auto-reparado del reproductor también lo llama.
     */
    fun disable(context: Context) {
        stop()
        lastFailure = null
        lastFailureAt = 0L
        runCatching {
            context.applicationContext.getSharedPreferences(FLAG_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(FLAG_DISABLED, true).commit()
        }
        Log.w(TAG, "origen legado desarmado (no volverá a rotar)")
    }

    internal fun isDisabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(FLAG_PREFS, Context.MODE_PRIVATE)
            .getBoolean(FLAG_DISABLED, false)

    /** Plain prefs (sin keystore): marca el desarme permanente del origen legado. */
    private const val FLAG_PREFS = "xuper_engine"
    private const val FLAG_DISABLED = "disabled"

    /** Live data again? With channel codes, `startPlayLive` grants a fresh one-time playlist path. */
    private suspend fun mintFreshPlaylist(client: XuperApiClient): Boolean = withContext(Dispatchers.IO) {
        val channels = client.getLiveData().getOrNull().orEmpty()
        val code = channels.firstOrNull { it.channelCode.isNotBlank() }?.channelCode
            ?: return@withContext false
        val play = client.startPlayLive(code).getOrNull() ?: return@withContext false
        val path = playlistPathOf(play) ?: return@withContext false
        client.config = client.config.copy(playlistPath = path)
        Log.i(TAG, "ruta nueva conseguida por startPlayLive: $path")
        true
    }

    /** Versions the portal may accept after "versión descontinuada"; tried quietly, last one wins. */
    private val VERSION_VARIANTS = listOf("43406", "43415", "43505", "43605")

    private class SourceError(message: String?) : Exception(message)

    /** How long a failed attempt repeats its answer before the next try talks to the backend. */
    private const val RETRY_BACKOFF_MS = 60_000L

    /** The channel the current proxy serves; null when it serves the whole playlist. */
    @Volatile
    private var servingChannel: String? = null

    /**
     * One channel's playback: asks the portal for that channel's one-time playlist, re-serves
     * the proxy around it and answers with the localhost url. Re-asking the same channel
     * reuses the running proxy instead of restarting it.
     */
    suspend fun ensureChannel(context: Context, channelCode: String): Result<String> =
        withContext(Dispatchers.Main) {
            if (isRunning() && servingChannel == channelCode) {
                return@withContext Result.success(proxy!!.getProxyUrl())
            }
            val client = XuperApiClient(context.applicationContext)
            ensureSession(client, context)
            runCatching {
                val play = withContext(Dispatchers.IO) { client.startPlayLive(channelCode).getOrThrow() }
                val path = playlistPathOf(play)
                    ?: throw SourceError("el portal no entregó una lista para el canal $channelCode")
                client.config = client.config.copy(playlistPath = path)
                stop()
                startProxy(context, client)
                servingChannel = channelCode
                val url = proxy!!.getProxyUrl()
                if (!playlistServes(url)) {
                    stop()
                    throw SourceError("la lista del canal no respondió")
                }
                url
            }
        }

    /** "/ruta" de la respuesta de startPlayLive, o la ruta+query de un url absoluto. */
    private fun playlistPathOf(play: String): String? {
        val path = when {
            play.startsWith("/") -> play
            play.startsWith("http") -> java.net.URI(play).let { "${it.path ?: ""}${if (it.query != null) "?${it.query}" else ""}" }
            else -> return null
        }
        return path.takeIf { it.isNotBlank() && it != "/" }
    }

    /** The session must be alive before any per-channel request: rotate on a dead one. */
    private suspend fun ensureSession(client: XuperApiClient, context: Context) {
        if (client.isSessionReady()) return
        runCatching { rotateSession(client, context) }
        if (!client.isSessionReady()) throw SourceError("la sesión integrada no tiene cookies (d/s/t)")
    }
}
