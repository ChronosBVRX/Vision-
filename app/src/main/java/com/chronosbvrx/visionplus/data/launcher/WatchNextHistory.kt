package com.chronosbvrx.visionplus.data.launcher

import android.content.Context
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry

/**
 * El historial leído del archivo de preferencias, no de la copia en memoria del proceso.
 *
 * Las SharedPreferences no se sincronizan entre procesos: el reproductor vive en `:x4k` y
 * escribe ahí el minuto, así que la copia cacheada del proceso principal puede quedarse
 * vieja. El canal Watch Next espeja el historial, y espejar una copia vieja borraría tarjetas
 * recién publicadas; por eso el publicador lee del disco a través de [PlaybackHistory].
 *
 * (El mismo criterio lo usan ahora la fila «Seguir viendo» del inicio y el deep link.)
 */

/** Las entradas del historial dentro del XML de preferencias; null cuando no se pudo leer. */
internal fun entriesFromPrefsXml(xml: String?): List<ResumeEntry>? =
    PlaybackHistory.entriesFromXml(xml)

/** El historial fresco: del archivo cuando se puede, de la copia en memoria como respaldo. */
internal fun freshHistoryEntries(context: Context): List<ResumeEntry> =
    PlaybackHistory.listFresh(context)
