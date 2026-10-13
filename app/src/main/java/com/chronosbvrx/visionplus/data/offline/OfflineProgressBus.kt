package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.content.Intent

/**
 * Bus de progreso offline entre el proceso `:x4k` (descarga real) y el proceso
 * principal (UI). El servicio en `:x4k` emite; el principal escucha y actualiza
 * [OfflineRegistry]. La reproducción no usa este bus.
 */
object OfflineProgressBus {
    const val ACTION_PROGRESS = "com.chronosbvrx.visionplus.OFFLINE_PROGRESS"
    const val ACTION_DONE = "com.chronosbvrx.visionplus.OFFLINE_DONE"
    const val ACTION_ERROR = "com.chronosbvrx.visionplus.OFFLINE_ERROR"

    const val EXTRA_ID = "download_id"
    const val EXTRA_BYTES = "bytes_downloaded"
    const val EXTRA_TOTAL = "total_bytes"
    const val EXTRA_ERROR = "error_message"

    fun sendProgress(context: Context, id: String, bytes: Long, total: Long) {
        val i = Intent(ACTION_PROGRESS)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_BYTES, bytes)
            .putExtra(EXTRA_TOTAL, total)
        context.sendBroadcast(i)
    }

    fun sendDone(context: Context, id: String, bytes: Long) {
        val i = Intent(ACTION_DONE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_BYTES, bytes)
        context.sendBroadcast(i)
    }

    fun sendError(context: Context, id: String, message: String) {
        val i = Intent(ACTION_ERROR)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_ERROR, message)
        context.sendBroadcast(i)
    }
}
