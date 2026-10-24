package com.chronosbvrx.visionplus.data.xuper

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.chronosbvrx.visionplus.home.contract.EnginePrewarmContract
import com.chronosbvrx.visionplus.ui.player.X4KPlayerAty

/**
 * Servicio vinculado que vive en el proceso `:x4k` y calienta el motor cuando el
 * launcher (Vision+ Home) arranca con la tele.
 *
 * Es opcional: si el launcher no existe, la app se calienta desde su splash. El servicio
 * no reproduce, no acuña sesión y no abre la vista del reproductor; solo paga el arranque
 * que hoy se siente al tocar el primer canal.
 *
 * Seguridad: exportado únicamente con el permiso `signature` del contrato. El launcher y
 * Vision+ usan la misma llave histórica, así que ninguna app ajena puede arrancarlo.
 */
class EngineWarmService : Service() {

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        if (EngineWarmup.shouldWarm(this)) {
            Log.i(TAG, "launcher pidió el motor; calentando :x4k")
            warmAsync()
        } else {
            Log.i(TAG, "warmup del launcher omitido (RAM/política); la app usará su splash")
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // El camino normal es bindService. Si alguien hace startService, calienta y se suelta
        // cuando termina (no se queda vivo en segundo plano).
        if (EngineWarmup.shouldWarm(this)) {
            warmAsync(stopId = startId)
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun warmAsync(stopId: Int? = null) {
        Thread {
            try {
                EngineWarmup.warmBlocking(
                    context = this,
                    source = "launcher",
                    playerActive = { X4KPlayerAty.isPlayerActive },
                )
            } finally {
                stopId?.let { stopSelf(it) }
            }
        }.start()
    }

    override fun onDestroy() {
        Log.i(TAG, "servicio de warmup destruido")
        super.onDestroy()
    }

    /** Binder local: el launcher solo necesita saber que el servicio existe/está listo. */
    inner class LocalBinder : Binder() {
        fun version(): Int = EnginePrewarmContract.VERSION
        fun isReady(): Boolean = EngineWarmup.isWarmFresh(this@EngineWarmService)
    }

    private companion object {
        const val TAG = "EngineWarmService"
    }
}
