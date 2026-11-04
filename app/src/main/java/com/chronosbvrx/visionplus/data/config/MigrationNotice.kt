package com.chronosbvrx.visionplus.data.config

import android.content.Context
import kotlin.math.ceil

/**
 * Aviso de descontinuación del canal anterior.
 *
 * El servidor manda la fecha límite (`migration_deadline_ms`), el mensaje
 * (`migration_message`) y la versión que hay que instalar (`migration_version_code`).
 * La app muestra el conteo y, mientras la versión instalada esté por debajo de la mínima,
 * el diálogo de actualización no se puede cerrar.
 */
object MigrationNotice {
    const val K_DEADLINE = "migration_deadline_ms"
    const val K_MESSAGE = "migration_message"
    const val K_VERSION = "migration_version_code"
    const val DAY_MS = 86_400_000L

    private const val DEFAULT_MESSAGE =
        "Actualiza ahora para seguir usando Vision+. Las versiones anteriores dejarán de recibir actualizaciones."

    fun deadlineMs(context: Context): Long = RemoteConfig.longFlag(context, K_DEADLINE, 0L)

    fun message(context: Context): String = RemoteConfig.stringFlag(context, K_MESSAGE, DEFAULT_MESSAGE)

    fun updateVersionCode(context: Context): Int = RemoteConfig.intFlag(context, K_VERSION, 0)

    fun remainingDays(context: Context, nowMs: Long = System.currentTimeMillis()): Int? =
        remainingDaysFor(deadlineMs(context), nowMs)

    fun headline(context: Context, nowMs: Long = System.currentTimeMillis()): String? =
        headlineFor(deadlineMs(context), nowMs)

    internal fun remainingDaysFor(deadlineMs: Long, nowMs: Long): Int? {
        if (deadlineMs <= 0L) return null
        val diff = deadlineMs - nowMs
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / DAY_MS).toInt()
    }

    internal fun headlineFor(deadlineMs: Long, nowMs: Long): String? {
        val days = remainingDaysFor(deadlineMs, nowMs) ?: return null
        return when (days) {
            0 -> "Esta aplicación quedará descontinuada hoy"
            1 -> "Esta aplicación quedará descontinuada en 1 día"
            else -> "Esta aplicación quedará descontinuada en $days días"
        }
    }
}
