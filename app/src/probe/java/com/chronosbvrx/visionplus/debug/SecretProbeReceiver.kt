package com.chronosbvrx.visionplus.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronosbvrx.visionplus.data.xuper.XuperApiClient
import com.arkiv.player.data.credentials.NativeCredentialResolver

/**
 * Probe panel for lab builds. Two broadcast actions:
 * - `PROBE_SECRETS` — prints the credentials library's resolved keys (debug safety note: never
 *   call this in the non-debuggable probe variant; the library refuses to load there).
 * - `PROBE_PORTAL` — walks the whole portal chain (login → bootstrap hosts → channels) and
 *   prints every step, so a dead tier shows exactly where it died.
 *  Trigger with `adb shell am broadcast -a <action> -n <pkg>/.debug.SecretProbeReceiver`.
 */
class SecretProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PORTAL -> {
                val app = context.applicationContext
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { probePortal(XuperApiClient(app)) }
                            .onFailure { Log.e(TAG, "portal probe falló: ${it.javaClass.simpleName}: ${it.message}") }
                    }
                }
            }
            ACTION_SECRETS -> runCatching {
                Log.i(TAG, "tmdb=${NativeCredentialResolver.resolveTmdbKey()}")
                Log.i(TAG, "opensubtitles=${NativeCredentialResolver.resolveOpenSubtitlesKey()}")
                Log.i(TAG, "subdl=${NativeCredentialResolver.resolveSubdlKey()}")
                Log.i(TAG, "selfTest=${NativeCredentialResolver.sealSelfTest()}")
            }.onFailure { Log.e(TAG, "probe falló: ${it.javaClass.simpleName}: ${it.message}") }
            ACTION_SCAN_VERSIONS -> {
                // Escáner del gate de versiones: prueba un rango de apkVersion contra el
                // portal y anota en logcat cada versión que NO conteste portal200001.
                // Args: --es from 43000 --es to 43600 --es step 2
                val from = intent.getStringExtra("from")?.toIntOrNull() ?: 43400
                val to = intent.getStringExtra("to")?.toIntOrNull() ?: 43600
                val step = (intent.getStringExtra("step")?.toIntOrNull() ?: 2).coerceAtLeast(1)
                val ctxApp = context.applicationContext
                Thread {
                    try {
                        val client = XuperApiClient(ctxApp)
                        var alive = 0
                        var version = from
                        while (version <= to) {
                            client.config = client.config.copy(apkVersion = version.toString())
                            val slb = runCatching { client.getSlbInfo().getOrElse { "ERR" } }.getOrDefault("ERR")
                            val ok = !slb.contains("portal200001") && !slb.startsWith("ERR")
                            if (ok) {
                                alive++
                                Log.i(TAG, "scan: V=$version ALIVE → ${slb.take(140)}")
                            }
                            if (version % 50 == 0) Log.i(TAG, "scan: progreso V=$version (vivos=$alive)")
                            version += step
                        }
                        Log.i(TAG, "scan: fin from=$from to=$to step=$step vivos=$alive")
                    } catch (t: Throwable) {
                        Log.e(TAG, "scan falló: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }.start()
            }
            ACTION_PROBE_KOOCAN -> {
                val app = context.applicationContext
                Thread {
                    try {
                        com.chronosbvrx.visionplus.data.xuper.KoocanProbe.run(app) { line -> Log.i(TAG, "koocan: $line") }
                    } catch (t: Throwable) {
                        Log.e(TAG, "koocan falló: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }.start()
            }
            ACTION_SET_SESSION -> runCatching {
                // Canal de rescate de la sesión portal: un broadcast con el JSON completo del
                // config lo guarda cifrado; el próximo arranque del engine parte de ahí.
                // El contenido nunca se loguea.
                val payload: String = intent.getStringExtra("config").orEmpty()
                if (payload.isBlank()) { Log.e(TAG, "set_session: sin config"); return }
                com.chronosbvrx.visionplus.data.xuper.SecretKeystore.get(context)
                    .edit().putString("config", payload).apply()
                Log.i(TAG, "set_session: config guardado (${payload.length} chars) — reinicia el app")
            }.onFailure { Log.e(TAG, "set_session falló: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun probePortal(client: XuperApiClient) {
        try { Log.i(TAG, "login: " + client.loginV8().getOrElse { "ERR ${it.message}" }) }
        catch (t: Throwable) { Log.e(TAG, "login EXC", t) }
        try {
            val boot = client.probePortalBootstrap().getOrElse { "ERR ${it.message}" }
            boot.lines().forEach { Log.i(TAG, "boot: $it") }
        } catch (t: Throwable) { Log.e(TAG, "boot EXC", t) }
        Log.i(TAG, "portalHost elegido: '${client.config.portalHost}'")
        try {
            Log.i(TAG, "live: " + client.probeGetLiveData().fold(
                { fetch -> "ok apiSuccess=${fetch.apiSuccess} hint=${fetch.probeHint} channels=${fetch.channels.size}" },
                { "ERR ${it.message}" },
            ))
        } catch (t: Throwable) { Log.e(TAG, "live EXC", t) }
        try { Log.i(TAG, "slb: " + client.getSlbInfo().getOrElse { "ERR ${it.message}" }) }
        catch (t: Throwable) { Log.e(TAG, "slb EXC", t) }
    }

    private companion object {
        const val TAG = "SecretProbe"
        const val ACTION_SECRETS = "com.chronosbvrx.visionplus.PROBE_SECRETS"
        const val ACTION_PORTAL = "com.chronosbvrx.visionplus.PROBE_PORTAL"
        const val ACTION_SET_SESSION = "com.chronosbvrx.visionplus.SET_SESSION"
        const val ACTION_SCAN_VERSIONS = "com.chronosbvrx.visionplus.SCAN_VERSIONS"
        const val ACTION_PROBE_KOOCAN = "com.chronosbvrx.visionplus.PROBE_KOOCAN"
    }
}
