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
    private const val PAUSE_SUFFIX = ".paused"
    private const val DONE_SUFFIX = ".done"
    private const val ERROR_SUFFIX = ".error"
    private const val MIN_WRITE_INTERVAL_MS = 1_000L
    private val lastWriteMs = ConcurrentHashMap<String, Long>()

    private fun safeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun sidecar(vaultDir: File, id: String): File = File(vaultDir, "${safeId(id)}$SUFFIX")

    private fun pauseSidecar(vaultDir: File, id: String): File = File(vaultDir, "${safeId(id)}$PAUSE_SUFFIX")

    private fun doneSidecar(vaultDir: File, id: String): File = File(vaultDir, "${safeId(id)}$DONE_SUFFIX")

    private fun errorSidecar(vaultDir: File, id: String): File = File(vaultDir, "${safeId(id)}$ERROR_SUFFIX")

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

    /**
     * Ack de pausa: lo escribe el servicio `:x4k` recién cuando soltó el motor nativo
     * (`stopHeadless`). El principal lo espera antes de arrancar el reproductor para
     * que la descarga y la reproducción jamás se crucen en el motor.
     */
    fun writePauseFlag(vaultDir: File, id: String) {
        runCatching {
            vaultDir.mkdirs()
            pauseSidecar(vaultDir, id).writeText("1")
        }
    }

    fun hasPauseFlag(vaultDir: File, id: String): Boolean =
        runCatching { pauseSidecar(vaultDir, id).exists() }.getOrDefault(false)

    fun clearPauseFlag(vaultDir: File, id: String) {
        runCatching { pauseSidecar(vaultDir, id).delete() }
    }

    // -------------------------------------------------------------------------
    // Resultado FINAL de la corrida por ARCHIVO (respaldo del bus): en equipos donde
    // los broadcasts no llegan al proceso principal (lección POCO/HyperOS, visto
    // también en el emulador), el servicio deja `.done` o `.error` junto al vault y
    // el ticker del principal ([OfflineProgressSync]) lo consume en segundos.
    // -------------------------------------------------------------------------

    /** Marca de descarga COMPLETADA (lo escribe el servicio `:x4k` al cerrar el vault). */
    fun writeDone(vaultDir: File, id: String, bytes: Long) {
        runCatching {
            vaultDir.mkdirs()
            doneSidecar(vaultDir, id).writeText(bytes.toString())
        }
    }

    /** Bytes finales si la corrida terminó bien, o null. */
    fun readDone(vaultDir: File, id: String): Long? = runCatching {
        val f = doneSidecar(vaultDir, id)
        if (!f.exists()) return null
        f.readText().trim().toLongOrNull() ?: 0L
    }.getOrNull()

    /** Marca de ERROR terminal de la corrida (mensaje incluido). */
    fun writeError(vaultDir: File, id: String, message: String) {
        runCatching {
            vaultDir.mkdirs()
            errorSidecar(vaultDir, id).writeText(message)
        }
    }

    /** Mensaje de error terminal si la corrida falló, o null. */
    fun readError(vaultDir: File, id: String): String? = runCatching {
        val f = errorSidecar(vaultDir, id)
        if (!f.exists()) return null
        f.readText().ifBlank { "Error de red al descargar" }
    }.getOrNull()

    /** Retira las marcas de resultado (antes de un nuevo intento, para no heredar nada). */
    fun clearOutcome(vaultDir: File, id: String) {
        runCatching { doneSidecar(vaultDir, id).delete() }
        runCatching { errorSidecar(vaultDir, id).delete() }
    }
}
