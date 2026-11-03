package com.chronosbvrx.visionplus.data.netshare

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Datos comunes del anuncio de «qué está reproduciendo Vision+» en la red local.
 *
 * El servicio es de SOLO LECTURA: anuncia el endpoint y sirve el JSON. No expone ninguna
 * orden de control (para eso está el control remoto emparejado, que sí pide PIN).
 */
object NowPlayingShare {
    /** Tipo mDNS/Bonjour que deben buscar los demás equipos. */
    const val SERVICE_TYPE = "_visionplus._tcp"

    /** Ruta del recurso HTTP. */
    const val PATH = "/now-playing"

    /** Puerto preferido; si está ocupado, el servidor usa uno libre y lo anuncia igual. */
    const val DEFAULT_PORT = 8765

    /** TXT que viaja en el anuncio mDNS. */
    const val TXT_APP = "Vision+"
    const val TXT_VERSION = "1"

    private const val PREFS = "vision_now_playing"
    private const val KEY_TOKEN = "token"

    /**
     * Nombre legible del equipo para el anuncio. Se usa el nombre que el usuario le puso al
     * Android (`Settings.Global.DEVICE_NAME`) y, si no existe, el modelo. Sin datos inventados.
     */
    fun deviceName(context: Context): String {
        val configured = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else {
                null
            }
        }.getOrNull()
        val model = Build.MODEL?.trim().orEmpty()
        val raw = configured?.trim()?.takeIf { it.isNotBlank() }
            ?: model.takeIf { it.isNotBlank() }
            ?: "Android"
        return raw.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(48)
    }

    /** Nombre del servicio mDNS: «Vision+ - <nombre del dispositivo>». */
    fun serviceName(context: Context): String = "Vision+ - ${deviceName(context)}"

    /**
     * Token opcional para limitar el consumo a quien lo conozca. Vacío (por defecto) = sin
     * token, para que `curl http://<IP>:8765/now-playing` funcione en la LAN.
     */
    fun token(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "").orEmpty().trim()

    /** Fija o limpia el token (lo puede usar Ajustes o un script de operación). */
    fun setToken(context: Context, token: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token.trim()).apply()
    }
}
