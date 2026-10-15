package com.chronosbvrx.visionplus.data.xuper

import org.json.JSONObject

/**
 * Identidad propia del portal (slot del pool): la réplica del modelo Hydra.
 *
 * El tenant no incorpora dispositivos nuevos, así que cada instalación canjea UN
 * código por SU slot (sn + user_id). Un slot = una sesión = nadie patea a nadie.
 * Sin slot, la app sigue con la identidad compartida de siempre (legado).
 */
data class DeviceSlot(val sn: String, val userId: String = "") {

    fun isValid(): Boolean = normalizeSn(sn).isNotEmpty()

    companion object {
        fun normalizeSn(raw: String?): String {
            val sn = raw?.trim()?.take(64).orEmpty()
            return if (sn.isNotEmpty() && sn.all { it.code >= 32 }) sn else ""
        }

        fun normalizeCode(raw: String?): String {
            val code = raw?.uppercase()?.filterNot { it.isWhitespace() }.orEmpty()
            return if (code.length in 6..12 && code.all { it.isLetterOrDigit() }) code else ""
        }

        fun normalizeDeviceKey(raw: String?): String {
            val key = raw?.trim()?.lowercase()?.take(64).orEmpty()
            val clean = key.filter { it.isLetterOrDigit() || it == '-' }
            return clean.takeIf { it.isNotEmpty() }.orEmpty()
        }

        /** Respuesta de /v1/slots/redeem y /v1/slots/claim: {"sn":…, "user_id":…}. */
        fun parseResponse(json: String): DeviceSlot? = try {
            val obj = JSONObject(json)
            val slot = DeviceSlot(
                sn = normalizeSn(obj.optString("sn")),
                userId = obj.optString("user_id").trim().take(32),
            )
            slot.takeIf { it.isValid() }
        } catch (_: Exception) {
            null
        }
    }
}
