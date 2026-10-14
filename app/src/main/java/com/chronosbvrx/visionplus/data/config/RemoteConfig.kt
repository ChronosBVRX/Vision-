package com.chronosbvrx.visionplus.data.config

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Configuración remota (Fase 2): flags con los que el servidor puede mitigar un problema
 * sin publicar una OTA (kill-switches).
 *
 * - Se lee al arrancar y cada 6 h; si no hay red, valen los flags cacheados y, si no hay
 *   ninguno, los defaults seguros (todo activo).
 * - Flags honrados hoy: `downloads_enabled` (apaga las descargas) y
 *   `diagnostics_forced_off` (apaga el diagnóstico aunque el usuario lo tenga activo).
 */
object RemoteConfig {

    private const val TAG = "RemoteConfig"
    private const val PREFS = "remote_config"
    private const val K_FLAGS = "flags_json"
    private const val K_AT = "flags_at"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    @Volatile private var flags: JSONObject = JSONObject()

    fun init(context: Context) {
        load(context)
        applyKillSwitches(context)
        thread(name = "remote-config", isDaemon = true) {
            runCatching { refresh(context) }
            while (true) {
                Thread.sleep(TTL_MS)
                runCatching { refresh(context) }
            }
        }
    }

    /** Lectura síncrona de un flag (default seguro si el servidor no lo define). */
    fun flag(context: Context, name: String, default: Boolean): Boolean {
        if (flags.length() == 0) load(context)
        return flagFrom(flags, name, default)
    }

    internal fun flagFrom(flags: JSONObject, name: String, default: Boolean): Boolean =
        when (val value = flags.opt(name)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            is Number -> value.toInt() != 0
            else -> default
        }

    /** Del JSON de `/v1/config` saca el objeto `flags` (pura, testeable). */
    internal fun parseFlags(text: String?): JSONObject =
        runCatching { JSONObject(text ?: "{}").optJSONObject("flags") ?: JSONObject() }
            .getOrDefault(JSONObject())

    /** Descarga y cachea los flags; bloqueante (se llama desde un hilo propio). */
    fun refresh(context: Context): Boolean {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/config").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return false
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val fresh = parseFlags(text)
            flags = fresh
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_FLAGS, fresh.toString())
                .putLong(K_AT, System.currentTimeMillis())
                .apply()
            applyKillSwitches(context)
            Log.i(TAG, "config remota: ${fresh.length()} flags")
            true
        } catch (e: Exception) {
            Log.w(TAG, "config remota inalcanzable: ${e.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        flags = runCatching { JSONObject(p.getString(K_FLAGS, "{}") ?: "{}") }.getOrDefault(JSONObject())
    }

    /** Kill-switches que la app honra aquí mismo. */
    private fun applyKillSwitches(context: Context) {
        val forcedOff = flag(context, "diagnostics_forced_off", false)
        val userWants = VisionSettings.isDiagnosticsEnabled(context)
        com.chronosbvrx.visionplus.data.diag.Diagnostics.setEnabled(userWants && !forcedOff)
    }
}
