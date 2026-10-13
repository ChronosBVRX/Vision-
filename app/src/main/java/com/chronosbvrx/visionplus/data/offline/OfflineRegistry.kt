package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Registro persistente de descargas offline en Vision+:
 *
 * Mantiene la lista de descargas activas y completadas en SharedPreferences (JSON),
 * sincronizada en memoria y expuesta como StateFlow reactivo para Jetpack Compose.
 */
object OfflineRegistry {

    private const val PREFS = "vision_offline_registry"
    private const val K_ITEMS = "downloads_list"

    private val lock = Any()
    private val memoryMap = LinkedHashMap<String, OfflineDownload>()
    private val _downloadsFlow = MutableStateFlow<List<OfflineDownload>>(emptyList())
    val downloadsFlow: StateFlow<List<OfflineDownload>> = _downloadsFlow.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val app = context.applicationContext
            appContext = app
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            loadFromPrefs(prefs)
            initialized = true
        }
    }

    private fun ensureInit(context: Context) {
        if (!initialized) init(context)
    }

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadFromPrefs(p: SharedPreferences) {
        val raw = p.getString(K_ITEMS, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            memoryMap.clear()
            var dirty = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val item = OfflineDownload.fromJson(obj)
                val reconciled = reconcileWithDisk(item)
                if (reconciled == null) {
                    dirty = true
                    continue
                }
                if (reconciled != item) dirty = true
                memoryMap[reconciled.id] = reconciled
            }
            if (dirty) {
                persistLocked()
            } else {
                _downloadsFlow.value = memoryMap.values.toList()
            }
        }
    }

    /**
     * Concilia un registro con el disco (lógica pura, testeable). Retorna null si debe
     * descartarse. Reglas:
     * - `.vpack` final válido (>= 16 KB) → COMPLETED adoptado aunque el broadcast de
     *   fin se haya perdido (proceso principal muerto al completar).
     * - Stubs simulados v2.5.49 (<= 512 B) → se purgan con archivo.
     * - COMPLETED fantasma (sin archivo) → se descarta (re-descargable).
     * - DOWNLOADING/QUEUED al arrancar → PAUSED (reanuda el usuario, no solo).
     */
    internal fun reconcileWithDisk(item: OfflineDownload): OfflineDownload? {
        val vaultOk = item.vaultFilePath.isNotBlank() &&
            runCatching { File(item.vaultFilePath) }.getOrNull()?.let { it.exists() && it.length() >= MIN_VALID_VAULT_BYTES } == true
        if (vaultOk) {
            val len = runCatching { File(item.vaultFilePath).length() }.getOrDefault(item.bytesDownloaded)
            return item.copy(
                state = DownloadState.COMPLETED,
                bytesDownloaded = len,
                totalBytes = len.coerceAtLeast(1L),
                errorMessage = null,
            )
        }
        val isSimulatedStub = item.state == DownloadState.COMPLETED &&
            item.bytesDownloaded in 1L..512L &&
            item.totalBytes in 1L..512L
        if (isSimulatedStub) {
            if (item.vaultFilePath.isNotBlank()) {
                runCatching { File(item.vaultFilePath).delete() }
            }
            return null
        }
        if (item.state == DownloadState.COMPLETED) return null
        if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED) {
            return item.copy(state = DownloadState.PAUSED)
        }
        return item
    }

    private fun persistLocked() {        val p = prefs() ?: return
        val arr = JSONArray()
        memoryMap.values.forEach { arr.put(it.toJson()) }
        p.edit().putString(K_ITEMS, arr.toString()).apply()
        _downloadsFlow.value = memoryMap.values.toList()
    }

    fun getAll(context: Context? = null): List<OfflineDownload> {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            return memoryMap.values.toList()
        }
    }

    fun get(id: String, context: Context? = null): OfflineDownload? {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            return memoryMap[id]
        }
    }

    fun getCompleted(context: Context? = null): List<OfflineDownload> {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            return memoryMap.values.filter { it.state == DownloadState.COMPLETED }
        }
    }

    fun put(download: OfflineDownload, context: Context? = null) {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            memoryMap[download.id] = download
            persistLocked()
        }
    }

    fun remove(id: String, context: Context? = null): OfflineDownload? {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            val removed = memoryMap.remove(id)
            if (removed != null) {
                // Borrar archivo .vpack del almacenamiento
                if (removed.vaultFilePath.isNotBlank()) {
                    runCatching { File(removed.vaultFilePath).delete() }
                    runCatching { File("${removed.vaultFilePath}.downloading").delete() }
                }
                persistLocked()
            }
            return removed
        }
    }

    fun updateProgress(id: String, bytesDownloaded: Long, totalBytes: Long) {
        synchronized(lock) {
            val current = memoryMap[id] ?: return
            val updated = current.copy(
                bytesDownloaded = bytesDownloaded,
                totalBytes = if (totalBytes > 0) totalBytes else current.totalBytes,
                state = DownloadState.DOWNLOADING,
            )
            memoryMap[id] = updated
            persistLocked()
        }
    }

    fun updateState(id: String, state: DownloadState, error: String? = null) {
        synchronized(lock) {
            val current = memoryMap[id] ?: return
            val updated = current.copy(
                state = state,
                errorMessage = error,
            )
            memoryMap[id] = updated
            persistLocked()
        }
    }

    fun isDownloaded(id: String, context: Context? = null): Boolean {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            val item = memoryMap[id] ?: return false
            return item.state == DownloadState.COMPLETED && File(item.vaultFilePath).exists()
        }
    }

    fun isDownloading(id: String, context: Context? = null): Boolean {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            val item = memoryMap[id] ?: return false
            return item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED
        }
    }

    fun findDownloadFor(filmId: String, episodeCode: String? = null, context: Context? = null): OfflineDownload? {
        context?.let { ensureInit(it) }
        synchronized(lock) {
            val targetId = if (!episodeCode.isNullOrBlank()) "$filmId:ep:$episodeCode" else filmId
            return memoryMap[targetId] ?: memoryMap.values.firstOrNull {
                it.contentId == filmId || (episodeCode != null && it.id.endsWith(episodeCode))
            }
        }
    }

    /**
     * Limpia registros huérfanos cuyos archivos hayan sido eliminados físicamente.
     */
    fun pruneMissingFiles(context: Context) {
        ensureInit(context)
        synchronized(lock) {
            val toRemove = mutableListOf<String>()
            memoryMap.forEach { (id, item) ->
                if (item.state == DownloadState.COMPLETED) {
                    val f = File(item.vaultFilePath)
                    if (!f.exists() || f.length() == 0L) {
                        toRemove.add(id)
                    }
                }
            }
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { memoryMap.remove(it) }
                persistLocked()
            }
        }
    }

    fun resetForTesting(context: Context? = null) {
        synchronized(lock) {
            memoryMap.clear()
            _downloadsFlow.value = emptyList()
            initialized = false
            context?.let {
                val app = it.applicationContext
                appContext = app
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().clear().commit()
                loadFromPrefs(prefs)
                initialized = true
            }
        }
    }
}
