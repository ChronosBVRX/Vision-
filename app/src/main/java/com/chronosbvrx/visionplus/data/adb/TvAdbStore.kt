package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Una tele ya emparejada, con lo necesario para reaplicar o deshacer el Inicio sin volver a
 * pedir el código. El `id` es estable (Android ID del televisor), así que cambiar de IP no la
 * convierte en otra tele.
 */
internal data class TvRecord(
    val id: String,
    val label: String,
    val host: String,
    val paired: Boolean = false,
    val applied: Boolean = false,
    val originalHome: String = "",
    val disabled: List<String> = emptyList(),
    val updatedAt: Long = 0L,
) {
    /** Nombre corto para la interfaz. */
    val displayName: String get() = label.ifBlank { host.ifBlank { "Tele" } }
}

/**
 * Las teles del teléfono: lista en JSON dentro de las preferencias de la app.
 *
 * Se guardan varias a propósito (una casa tiene más de una tele y cada una tiene su propio
 * Inicio original y sus apps retiradas); el parseo y las operaciones son puras para poder
 * probarlas sin Android.
 */
internal object TvAdbStore {

    private const val PREFS = "vision_tv_adb"
    private const val KEY = "tvs"

    fun load(context: Context): List<TvRecord> =
        parse(prefs(context).getString(KEY, null))

    fun save(context: Context, tvs: List<TvRecord>) {
        prefs(context).edit().putString(KEY, serialize(tvs)).apply()
    }

    fun upsert(context: Context, record: TvRecord): List<TvRecord> {
        val out = upsert(load(context), record)
        save(context, out)
        return out
    }

    fun remove(context: Context, id: String): List<TvRecord> {
        val out = remove(load(context), id)
        save(context, out)
        return out
    }

    fun find(context: Context, id: String): TvRecord? = load(context).firstOrNull { it.id == id }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- puro (con tests) ---

    fun parse(json: String?): List<TvRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").trim()
                if (id.isBlank()) return@mapNotNull null
                TvRecord(
                    id = id,
                    label = obj.optString("label").trim(),
                    host = obj.optString("host").trim(),
                    paired = obj.optBoolean("paired", false),
                    applied = obj.optBoolean("applied", false),
                    originalHome = obj.optString("originalHome").trim(),
                    disabled = obj.optJSONArray("disabled")?.let { items ->
                        (0 until items.length()).mapNotNull { items.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                    updatedAt = obj.optLong("updatedAt", 0L),
                )
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun serialize(tvs: List<TvRecord>): String {
        val array = JSONArray()
        tvs.forEach { tv ->
            array.put(
                JSONObject()
                    .put("id", tv.id)
                    .put("label", tv.label)
                    .put("host", tv.host)
                    .put("paired", tv.paired)
                    .put("applied", tv.applied)
                    .put("originalHome", tv.originalHome)
                    .put("disabled", JSONArray(tv.disabled))
                    .put("updatedAt", tv.updatedAt),
            )
        }
        return array.toString()
    }

    /** Reemplaza por `id` conservando el orden; si es nueva, va al frente. */
    fun upsert(tvs: List<TvRecord>, record: TvRecord): List<TvRecord> {
        val index = tvs.indexOfFirst { it.id == record.id }
        return if (index >= 0) {
            tvs.toMutableList().also { it[index] = record }
        } else {
            listOf(record) + tvs
        }
    }

    fun remove(tvs: List<TvRecord>, id: String): List<TvRecord> = tvs.filterNot { it.id == id }

    /**
     * El identificador estable del televisor: Android ID y, si el firmware no lo da, el serial.
     * Devuelve vacío si no hay ninguno (se cae al host como id).
     */
    fun identityFrom(androidId: String?, serial: String?): String {
        val clean = { value: String? -> value?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "unknown" } }
        return clean(androidId) ?: clean(serial) ?: ""
    }
}
