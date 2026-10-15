package com.chronosbvrx.visionplus.data.gate

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.xuper.DeviceSlot
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Canje y reclamo del slot propio contra el pool del servidor (réplica Hydra).
 *
 * El operador emite códigos de un solo uso (uno por SN aprovisionado); el usuario
 * escribe el suyo UNA vez y el equipo queda con identidad propia para siempre:
 * - `redeem(code)`: quema el código y fija el slot (idempotente por equipo).
 * - `recoverIfNeeded()`: tras borrar datos/reinstalar, reclama el slot ya asignado
 *   a este `deviceKey` sin gastar otro código (silencioso: si no hay, no pasa nada).
 */
object SlotClient {

    fun statusText(context: Context): String {
        val slot = com.chronosbvrx.visionplus.data.xuper.SlotStore.load(context)
        return if (slot != null) "Propia (${slot.sn.take(8)}…)" else "Compartida"
    }

    suspend fun redeem(context: Context, code: String): Result<DeviceSlot> = withContext(Dispatchers.IO) {
        val clean = DeviceSlot.normalizeCode(code)
        if (clean.isEmpty()) return@withContext Result.failure(IllegalArgumentException("código inválido"))
        val deviceKey = GateClient.deviceKey(context)
        post("slots/redeem", JSONObject().apply {
            put("code", clean)
            put("device_key", deviceKey)
        }).mapCatching { applySlot(context, it) }
    }

    /** Reclamo silencioso al arrancar: solo aplica si el servidor ya conoce al equipo
     *  con una identidad PROPIA. Un reclamo del SN compartido se ignora: no aísla y
     *  bloquearía el alta derivada. */
    suspend fun recoverIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (com.chronosbvrx.visionplus.data.xuper.SlotStore.load(context)?.isShared() == false) {
            return@withContext false
        }
        val deviceKey = try {
            GateClient.deviceKey(context)
        } catch (_: Exception) {
            return@withContext false
        }
        val got = post("slots/claim", JSONObject().put("device_key", deviceKey)).getOrNull()
            ?: return@withContext false
        if (got.isShared()) {
            android.util.Log.i("SlotClient", "reclamo compartido; se ignora")
            return@withContext false
        }
        return@withContext try {
            applySlot(context, got)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     Automático total (cero códigos, cero pool para el caso común): slot local →
     reclamo por `deviceKey` (reinstalación: no consume nada) → auto-alta en el
     portal (gratis e ilimitada: el equipo acuña SU sesión de visitante) → pool del
     operador (respaldo para slots especiales). Silencioso: jamás interrumpe el arranque.

     Un slot local del SN compartido se jubila: equivale a no tener identidad propia
     (el portal admite una sola sesión por SN: patadas aaa100083) y su "ya hay slot"
     bloqueaba para siempre el alta derivada.
     */
    suspend fun ensureSlot(context: Context): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.i("SlotClient", "ensureSlot: inicio")
        try {
            val local = com.chronosbvrx.visionplus.data.xuper.SlotStore.load(context)
            if (local != null && !local.isShared()) {
                android.util.Log.i("SlotClient", "ensureSlot: ya hay slot local")
                return@withContext false
            }
            if (local?.isShared() == true) {
                android.util.Log.i("SlotClient", "ensureSlot: slot compartido; migrando al alta propia")
                com.chronosbvrx.visionplus.data.xuper.SlotStore.clear(context)
            }
        } catch (e: Exception) {
            android.util.Log.w("SlotClient", "ensureSlot: load falló: ${e.message}")
        }
        if (recoverIfNeeded(context)) return@withContext true
        // Alta propia primero (gratis): el pool solo se toca si el portal la rechaza.
        try {
            com.chronosbvrx.visionplus.data.xuper.X4KApi.mintOwnSlot(context)
            android.util.Log.i("SlotClient", "auto-alta en el portal: ok")
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.w("SlotClient", "auto-alta falló: ${e.message}")
        }
        val deviceKey = try {
            GateClient.deviceKey(context)
        } catch (_: Exception) {
            return@withContext false
        }
        val got = post("slots/auto", JSONObject().put("device_key", deviceKey)).getOrNull()
            ?: return@withContext false
        if (got.isShared()) {
            android.util.Log.i("SlotClient", "pool con SN compartido; se ignora")
            return@withContext false
        }
        return@withContext try {
            applySlot(context, got)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applySlot(context: Context, slot: DeviceSlot): DeviceSlot {
        X4KApi.applySlot(context, slot)
        return slot
    }

    private fun post(path: String, body: JSONObject): Result<DeviceSlot> = runCatching {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 404) error("Sin slot: código inválido o equipo sin asignar")
            if (code == 409) error("Ese código ya se usó en otro equipo")
            if (code == 403) error("Slot bloqueado")
            if (code !in 200..299) error("Servidor $code")
            DeviceSlot.parseResponse(text) ?: error("Respuesta ilegible del servidor")
        } finally {
            connection.disconnect()
        }
    }
}
