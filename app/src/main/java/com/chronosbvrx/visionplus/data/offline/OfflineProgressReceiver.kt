package com.chronosbvrx.visionplus.data.offline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Puente proceso principal: escucha al [X4KDownloadService] (`:x4k`) y refleja
 * progreso/fin/error en [OfflineRegistry] para la UI. Solo se registra en el
 * proceso principal (ver VisionApp). No toca reproducción.
 */
class OfflineProgressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val id = intent?.getStringExtra(OfflineProgressBus.EXTRA_ID).orEmpty()
        if (id.isBlank()) return
        when (intent?.action) {
            OfflineProgressBus.ACTION_PROGRESS -> {
                val bytes = intent.getLongExtra(OfflineProgressBus.EXTRA_BYTES, 0L)
                val total = intent.getLongExtra(OfflineProgressBus.EXTRA_TOTAL, 0L)
                OfflineRegistry.updateProgress(id, bytes, total)
            }
            OfflineProgressBus.ACTION_DONE -> {
                val bytes = intent.getLongExtra(OfflineProgressBus.EXTRA_BYTES, 0L)
                OfflineRegistry.updateProgress(id, bytes, bytes)
                OfflineRegistry.updateState(id, DownloadState.COMPLETED)
            }
            OfflineProgressBus.ACTION_ERROR -> {
                val msg = intent.getStringExtra(OfflineProgressBus.EXTRA_ERROR).orEmpty()
                OfflineRegistry.updateState(id, DownloadState.ERROR, msg.ifBlank { "Error de red al descargar" })
            }
        }
    }

    companion object {
        fun register(context: Context): OfflineProgressReceiver {
            val receiver = OfflineProgressReceiver()
            val filter = IntentFilter().apply {
                addAction(OfflineProgressBus.ACTION_PROGRESS)
                addAction(OfflineProgressBus.ACTION_DONE)
                addAction(OfflineProgressBus.ACTION_ERROR)
            }
            val app = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
