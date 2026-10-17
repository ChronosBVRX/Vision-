package com.chronosbvrx.visionplus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.chronosbvrx.visionplus.data.xuper.X4KWarmupReceiver

/** Ventana del «dos veces Atrás»: el segundo toque dentro de este tiempo cierra la app. */
internal const val BACK_EXIT_WINDOW_MS = 2_500L

/**
 * Política pura del cierre con confirmación (probada): el primer Atrás de la pantalla
 * principal arma la salida y muestra el aviso; el segundo, dentro de la ventana, confirma.
 * Pasada la ventana, el siguiente toque vuelve a armar (jamás cierra sin un aviso fresco).
 */
internal class BackExitGuard(private val windowMs: Long = BACK_EXIT_WINDOW_MS) {
    private var armedUntilMs = Long.MIN_VALUE

    /** true cuando este toque ya confirma la salida. */
    fun press(nowMs: Long): Boolean {
        if (nowMs <= armedUntilMs) {
            armedUntilMs = Long.MIN_VALUE
            return true
        }
        armedUntilMs = nowMs + windowMs
        return false
    }

    /** El aviso sigue vigente (si pasó la ventana, ya no). */
    fun hintVisible(nowMs: Long): Boolean = nowMs <= armedUntilMs
}

private fun x4kAlive(context: Context): Boolean = runCatching {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
    am?.runningAppProcesses?.any { it.processName.endsWith(":x4k") } == true
}.getOrDefault(false)

/**
 * Salida real de la app desde la pantalla principal (segundo Atrás):
 *
 *  1. Si el reproductor residente sigue vivo (el proceso `:x4k` guarda su actividad con
 *     tarea propia), se le pide cerrarse: sin eso el sistema traería ESA tarea al frente
 *     al soltar la principal — el «cae en el reproductor de nuevo» del reporte, del que
 *     ya no se salía con Atrás.
 *  2. Recién entonces se suelta la tarea principal (`finishAndRemoveTask`): la casa queda
 *     al frente, como cualquier app que se cierra.
 *
 * Si `:x4k` no está vivo la salida es instantánea; si está vivo, se le da un instante
 * (tope 700 ms) para soltar su tarea antes de cerrar (el proceso muere solo al cerrar su
 * actividad, liberando el motor).
 */
internal fun exitAppCleanly(activity: Activity) {
    val app = activity.applicationContext
    if (!x4kAlive(app)) {
        activity.finishAndRemoveTask()
        return
    }
    runCatching {
        app.sendBroadcast(
            Intent(app, X4KWarmupReceiver::class.java).apply {
                action = X4KWarmupReceiver.ACTION_FINISH_FOR_EXIT
            },
        )
    }
    val handler = Handler(Looper.getMainLooper())
    val deadline = System.currentTimeMillis() + 700L
    val check = object : Runnable {
        override fun run() {
            if (!x4kAlive(app) || System.currentTimeMillis() >= deadline) {
                runCatching { activity.finishAndRemoveTask() }
            } else {
                handler.postDelayed(this, 60L)
            }
        }
    }
    handler.postDelayed(check, 100L)
}
