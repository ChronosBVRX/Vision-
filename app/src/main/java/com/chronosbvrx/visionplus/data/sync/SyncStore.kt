package com.chronosbvrx.visionplus.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Lo que la pantalla de Ajustes pinta: código, última vez y estado. */
data class SyncUiState(
    val code: String = "",
    val lastSyncAt: Long = 0L,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * La continuidad entre equipos del lado de la app.
 *
 * El servidor solo guarda elementos con su hora (last-write-wins) y devuelve lo nuevo desde
 * `since`; aquí se arma el snapshot local, se envían los cambios, se fusiona la respuesta con
 * [mergeSync] y se reescribe el historial/biblioteca del equipo. Sin cuentas: un código de 8
 * caracteres compartido una vez. Nunca bloquea la interfaz y nunca lanza: si el VPS no
 * responde, la app sigue funcionando y el siguiente intento reconcilia.
 */
object SyncStore {

    private const val TAG = "SyncStore"
    private const val PREFS = "sync_state"
    private const val K_CODE = "code"
    private const val K_SEQ = "last_seq"
    private const val K_AT = "last_sync_at"
    private const val K_TIMES = "times"
    private const val K_REMOVED = "removed"
    private const val K_DEVICE = "device_id"

    /** Menos de esto entre intentos automáticos no vale la pena: el botón «ahora» sí fuerza. */
    private const val AUTO_EVERY_MS = 30_000L

    val state = mutableStateOf(SyncUiState())

    @Volatile private var lastAttemptAt = 0L
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun code(context: Context): String = prefs(context).getString(K_CODE, "").orEmpty()

    /** Carga el estado guardado en el objeto observable (Ajustes la llama al abrirse). */
    fun load(context: Context): SyncUiState {
        val p = prefs(context)
        val savedCode = p.getString(K_CODE, "").orEmpty()
        val savedAt = p.getLong(K_AT, 0L)
        val current = state.value
        if (current.code != savedCode || current.lastSyncAt != savedAt) {
            state.value = current.copy(code = savedCode, lastSyncAt = savedAt)
        }
        return state.value
    }

    /** Código del equipo: se crea la primera vez que hace falta (Ajustes o la tele). */
    fun ensureCode(context: Context): String {
        val existing = code(context)
        if (existing.isNotBlank()) return existing
        val fresh = generateSyncCode()
        prefs(context).edit().putString(K_CODE, fresh).apply()
        state.value = state.value.copy(code = fresh)
        return fresh
    }

    /**
     * Vincula este equipo a un código existente. Se reinicia el estado de sincronización para
     * que el snapshot local completo viaje al grupo nuevo (no se pierde nada de este equipo).
     */
    fun setCode(context: Context, raw: String): Boolean {
        val clean = normalizeSyncCode(raw)
        if (clean.isEmpty()) return false
        prefs(context).edit()
            .putString(K_CODE, clean)
            .putLong(K_SEQ, 0L)
            .remove(K_TIMES)
            .remove(K_REMOVED)
            .apply()
        state.value = SyncUiState(code = clean)
        return true
    }

    /** Deja de sincronizar en este equipo (el código y los datos locales se conservan). */
    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
        state.value = SyncUiState()
    }

    /**
     * Lanza una sincronización en segundo plano. `force` la salta el freno de 30 s (botón
     * «Sincronizar ahora»). No hace nada sin código: la app se comporta como siempre.
     */
    fun syncAsync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (code(app).isBlank()) return
        // En pruebas (Robolectric) no se toca la red real del VPS: el flujo de UI/cable se
        // prueba sin depender de internet. En el APK la clase no existe y esto vale false.
        if (runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (!force && now - lastAttemptAt < AUTO_EVERY_MS) return
            lastAttemptAt = now
        }
        if (!running.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { sync(app) }
            } finally {
                running.set(false)
            }
        }
    }

    /** Una pasada completa push + pull. Devuelve true si el servidor contestó. */
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!RemoteConfig.flag(app, "sync_enabled", true)) return@withContext false
        val code = code(app)
        if (code.isBlank()) return@withContext false
        state.value = state.value.copy(code = code, busy = true, error = null)
        val now = System.currentTimeMillis()
        runCatching {
            val snapshot = readSnapshot(app)
            val current = snapshotKeys(snapshot)
            val (times, removed) = detectRemovals(readMap(app, K_TIMES), current, readMap(app, K_REMOVED), now)
            // La hora del tombstone se guarda ANTES de la red: un reintento no la refresca (un
            // «re-agregar» en el otro equipo con una hora más nueva debe poder ganar).
            saveMap(app, K_TIMES, times)
            saveMap(app, K_REMOVED, pruneRemoved(removed, now))

            val body = JSONObject()
                .put("since", prefs(app).getLong(K_SEQ, 0L))
                .put("device", deviceId(app))
                .put("items", buildItems(snapshot, times, removed, now))
            val response = post(app, code, body) ?: throw IllegalStateException("Sin conexión con el servidor")
            val remote = parseRemote(response.optJSONArray("items"))
            val merged = mergeSync(snapshot, remote, times, removed, now)
            applySnapshot(app, merged.snapshot)
            saveMap(app, K_TIMES, merged.times)
            saveMap(app, K_REMOVED, pruneRemoved(merged.removed, now))
            val seq = maxOf(prefs(app).getLong(K_SEQ, 0L), response.optLong("seq", 0L))
            prefs(app).edit()
                .putLong(K_SEQ, seq)
                .putLong(K_AT, now)
                .putString(K_CODE, code)
                .apply()
            state.value = SyncUiState(code = code, lastSyncAt = now, busy = false, error = null)
            true
        }.getOrElse { error ->
            Log.w(TAG, "sincronización falló: ${error.message}")
            state.value = state.value.copy(busy = false, error = error.message ?: "No se pudo sincronizar")
            false
        }
    }

    // --- snapshot local ---------------------------------------------------------------------

    private fun readSnapshot(context: Context): SyncSnapshot = SyncSnapshot(
        resume = PlaybackHistory.listFresh(context)
            .filter { it.key.isNotBlank() && it.positionMs > 0 }
            .distinctBy { it.key },
        favorites = UserLibrary.favorites(context).distinctBy { it.id },
        watchLater = UserLibrary.watchLater(context).distinctBy { it.id },
        watched = UserLibrary.watchedEpisodesFresh(context),
    )

    private fun buildItems(
        snapshot: SyncSnapshot,
        times: Map<String, Long>,
        removed: Map<String, Long>,
        nowMs: Long,
    ): JSONArray {
        val out = ArrayList<JSONObject>(snapshot.resume.size + snapshot.favorites.size + snapshot.watchLater.size + removed.size)
        // Primero los borrados: si el tope de 200 recorta, que se entere el servidor.
        for ((stamp, at) in removed) {
            val kind = stamp.substringBefore('|')
            val key = stamp.substringAfter('|', "")
            if (kind !in setOf(KIND_RESUME, KIND_FAV, KIND_LATER) || key.isBlank()) continue
            out += item(kind, key, at, deleted = true, data = null)
        }
        snapshot.resume.forEach { entry ->
            val subject = stampKey(KIND_RESUME, entry.key)
            val at = maxOf(entry.updatedAt, times[subject] ?: 0L).takeIf { it > 0L } ?: nowMs
            out += item(KIND_RESUME, entry.key, at, deleted = false, data = PlaybackHistory.entryToJson(entry))
        }
        snapshot.favorites.forEach { film ->
            val subject = stampKey(KIND_FAV, film.id)
            out += item(KIND_FAV, film.id, times[subject] ?: nowMs, deleted = false, data = UserLibrary.filmToJson(film))
        }
        snapshot.watchLater.forEach { film ->
            val subject = stampKey(KIND_LATER, film.id)
            out += item(KIND_LATER, film.id, times[subject] ?: nowMs, deleted = false, data = UserLibrary.filmToJson(film))
        }
        snapshot.watched.groupBy { it.substringBefore('|') }.forEach { (series, keys) ->
            val episodes = keys.mapNotNull { it.substringAfter('|', "").toIntOrNull() }
            if (series.isBlank() || episodes.isEmpty()) return@forEach
            val subject = stampKey(KIND_WATCHED, series)
            val data = JSONObject().put("eps", JSONArray(episodes))
            out += item(KIND_WATCHED, series, times[subject] ?: nowMs, deleted = false, data = data)
        }
        return JSONArray(out.take(200))
    }

    private fun item(kind: String, key: String, at: Long, deleted: Boolean, data: JSONObject?): JSONObject =
        JSONObject()
            .put("k", kind)
            .put("key", key)
            .put("at", at)
            .put("d", deleted)
            .put("data", data ?: JSONObject.NULL)

    private fun parseRemote(array: JSONArray?): List<RemoteSyncItem> {
        if (array == null) return emptyList()
        val out = ArrayList<RemoteSyncItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = o.optString("k")
            val key = o.optString("key")
            if (kind !in SYNC_KINDS || key.isBlank()) continue
            val data = o.optJSONObject("data")
            val watched = if (kind == KIND_WATCHED && data != null) {
                val arr = data.optJSONArray("eps")
                if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { index ->
                    arr.optInt(index).takeIf { it > 0 }
                }
            } else {
                emptyList()
            }
            out += RemoteSyncItem(
                kind = kind,
                key = key,
                at = o.optLong("at"),
                deleted = o.optBoolean("d"),
                resume = if (kind == KIND_RESUME && data != null) PlaybackHistory.entryFromJson(data) else null,
                film = if ((kind == KIND_FAV || kind == KIND_LATER) && data != null) UserLibrary.filmFromJson(data) else null,
                watched = watched,
            )
        }
        return out
    }

    private fun applySnapshot(context: Context, snapshot: SyncSnapshot) {
        PlaybackHistory.replaceAll(context, snapshot.resume)
        UserLibrary.replaceFavorites(context, snapshot.favorites)
        UserLibrary.replaceWatchLater(context, snapshot.watchLater)
        UserLibrary.replaceWatched(context, snapshot.watched)
    }

    // --- red ---------------------------------------------------------------------------------

    private fun post(context: Context, code: String, body: JSONObject): JSONObject? = runCatching {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 2_500
            readTimeout = 3_500
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("X-Sync-Code", code)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    // --- estado persistido -------------------------------------------------------------------

    private fun readMap(context: Context, key: String): Map<String, Long> = runCatching {
        val o = JSONObject(prefs(context).getString(key, "{}") ?: "{}")
        val out = HashMap<String, Long>(o.length())
        o.keys().forEach { name -> out[name] = o.optLong(name) }
        out
    }.getOrDefault(emptyMap())

    private fun saveMap(context: Context, key: String, values: Map<String, Long>) {
        val o = JSONObject()
        values.forEach { (name, at) -> o.put(name, at) }
        prefs(context).edit().putString(key, o.toString()).apply()
    }

    private fun deviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(K_DEVICE, "").orEmpty()
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString().replace("-", "").take(24)
        p.edit().putString(K_DEVICE, fresh).apply()
        return fresh
    }
}
