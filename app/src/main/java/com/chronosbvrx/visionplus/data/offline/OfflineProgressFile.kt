package com.chronosbvrx.visionplus.data.offline

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Puente de progreso por ARCHIVO entre `:x4k` y el principal (respaldo de los
 * broadcasts): ambos procesos ven el mismo `vaultDir` en almacenamiento compartido.
 * En algunos equipos (POCO/HyperOS) los broadcasts llegan tarde o nunca; el archivo
 * siempre está. La reproducción no usa esto.
 */
object OfflineProgressFile {

    private const val SUFFIX = ".progress"
    private const val MIN_WRITE_INTERVAL_MS = 1_000L
    private val lastWriteMs = ConcurrentHashMap<String, Long>()

    private fun sidecar(vaultDir: File, id: String): File {
        val safe = id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(vaultDir, "$safe$SUFFIX")
    }

    /** Escribe bytes/total (acotado a 1 escritura/s por id). */
    fun write(vaultDir: File, id: String, bytes: Long, total: Long) {
        val now = System.currentTimeMillis()
        if ((now - (lastWriteMs[id] ?: 0L)) < MIN_WRITE_INTERVAL_MS) return
        lastWriteMs[id] = now
        runCatching {
            vaultDir.mkdirs()
            sidecar(vaultDir, id).writeText("$bytes\n$total")
        }
    }

    /** Lee el último progreso reportado, o null si no hay. */
    fun read(vaultDir: File, id: String): Pair<Long, Long>? {
        return runCatching {
            val f = sidecar(vaultDir, id)
            if (!f.exists()) return null
            val lines = f.readLines()
            val bytes = lines.getOrNull(0)?.trim()?.toLongOrNull() ?: return null
            val total = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
            bytes to total
        }.getOrNull()
    }

    fun clear(vaultDir: File, id: String) {
        lastWriteMs.remove(id)
        runCatching { sidecar(vaultDir, id).delete() }
    }
}
