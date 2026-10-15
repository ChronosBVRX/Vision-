package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import java.io.File

/**
 * Auto-reparado del motor ante la "caja atascada": el motor arranca, acepta el `play` y
 * jamás pinta un cuadro (el error de "No se pudo iniciar la reproducción" a los 20 s),
 * una y otra vez, hasta para títulos que el portal sirve perfecto. Es un estado
 * persistente del motor (su carpeta de trabajo sobrevive a reinicios y reinstalaciones).
 *
 * Aquí se cuenta la racha de fallos silenciosos y, al superar [THRESHOLD], se rota la
 * carpeta de trabajo del motor a una generación nueva; el siguiente intento arranca con
 * estado limpio. Los datos viven en archivos (no en SharedPreferences: cruzan procesos
 * `:x4k` ↔ principal, que no comparten su copia en memoria).
 */
object PlayerRecovery {

    /** Fallos silenciosos consecutivos que disparan la reparación. */
    internal const val THRESHOLD = 2

    private const val FILE_STREAK = "player_fail_streak"
    private const val FILE_GEN = "engine_gen"
    private const val FILE_GEN_SEEN = "engine_gen_seen"

    /** Nombre de la carpeta de trabajo del motor (pura, testeable). Gen 1 = histórico. */
    internal fun dirNameFor(mainProcess: Boolean, gen: Int): String {
        val base = if (mainProcess) "x4k-luna-main" else "x4k-luna"
        return if (gen <= 1) base else "$base-gen$gen"
    }

    internal fun shouldHeal(streak: Int): Boolean = streak >= THRESHOLD

    /** Generación actual del motor (1 = histórico; >1 = reparado). */
    fun engineGen(context: Context): Int =
        readInt(File(context.filesDir, FILE_GEN)) ?: 1

    /** Suma un fallo silencioso del motor y devuelve la racha resultante. */
    fun noteFailure(context: Context): Int {
        val f = File(context.filesDir, FILE_STREAK)
        val next = (readInt(f) ?: 0) + 1
        runCatching { f.writeText(next.toString()) }
        return next
    }

    /** Primer cuadro pintado: la racha vuelve a cero. */
    fun noteSuccess(context: Context) {
        runCatching { File(context.filesDir, FILE_STREAK).delete() }
    }

    /** Rota el motor a una generación nueva y limpia la racha. */
    fun bumpEngineGen(context: Context): Int {
        val gen = engineGen(context) + 1
        runCatching { File(context.filesDir, FILE_GEN).writeText(gen.toString()) }
        noteSuccess(context)
        return gen
    }

    /**
     * Devuelve la generación nueva una sola vez tras una rotación (el proceso principal
     * la consume para renovar sesión/SLB antes del próximo arranque). null si no cambió.
     */
    fun consumeGenChange(context: Context): Int? {
        val gen = engineGen(context)
        val seenFile = File(context.filesDir, FILE_GEN_SEEN)
        val seen = readInt(seenFile) ?: 1
        if (gen <= seen) return null
        runCatching { seenFile.writeText(gen.toString()) }
        return gen
    }

    private fun readInt(f: File): Int? =
        runCatching { f.readText().trim().toInt() }.getOrNull()
}
