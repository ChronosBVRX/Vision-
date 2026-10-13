package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ticker del proceso principal: cada 2 s refleja en [OfflineRegistry] el progreso
 * que el servicio `:x4k` deja en archivos sidecar. Respaldo del puente por
 * broadcasts (en A32 basta con broadcasts; en POCO/HyperOS solo llega el archivo).
 * Solo lee/escribe archivos propios en el vault; la reproducción no pasa por aquí.
 */
object OfflineProgressSync {
    private const val TAG = "OfflineProgressSync"
    private const val INTERVAL_MS = 2_000L

    private var job: Job? = null

    @Synchronized
    fun start(app: Context) {
        if (job?.isActive == true) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job = scope.launch {
            while (isActive) {
                runCatching { syncOnce(app) }
                    .onFailure { Log.w(TAG, "sync omitido: ${it.message}") }
                delay(INTERVAL_MS)
            }
        }
    }

    /**
     * Una pasada de sincronización. Retorna cuántos registros avanzaron.
     * Solo toca items DOWNLOADING cuyo sidecar traiga bytes mayores.
     */
    fun syncOnce(app: Context): Int {
        OfflineRegistry.init(app)
        var synced = 0
        val items = OfflineRegistry.getAll(app).filter { it.state == DownloadState.DOWNLOADING }
        for (item in items) {
            val vaultDir: File = runCatching { File(item.vaultFilePath).parentFile }.getOrNull() ?: continue
            val (bytes, total) = OfflineProgressFile.read(vaultDir, item.id) ?: continue
            val current = OfflineRegistry.get(item.id, app) ?: continue
            if (current.state != DownloadState.DOWNLOADING) continue
            if (bytes > current.bytesDownloaded) {
                OfflineRegistry.updateProgress(item.id, bytes, if (total > 0L) total else current.totalBytes)
                synced++
            }
        }
        return synced
    }
}
