package com.chronosbvrx.visionplus.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chronosbvrx.visionplus.data.xuper.X4KWarmupReceiver

/** Ventana del «dos veces Atrás»: el segundo toque dentro de este tiempo cierra la app. */
internal const val BACK_EXIT_WINDOW_MS = 2_500L

/** Clase real de la actividad del reproductor residente (tarea aparte, proceso :x4k). */
internal const val PLAYER_ACTIVITY_CLASS = "com.chronosbvrx.visionplus.ui.player.X4KPlayerAty"

/**
 * ¿Esta tarea es la del reproductor residente? (por base o por tope). Puro y
 * probado: cerrar el reproductor al salir no puede depender de adivinar por el
 * proceso — un proceso vivo puede no tener tarea y una tarea puede sobrevivir a
 * la muerte del proceso.
 */
internal fun isPlayerTask(baseClass: String?, topClass: String?): Boolean =
    baseClass == PLAYER_ACTIVITY_CLASS || topClass == PLAYER_ACTIVITY_CLASS

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
 * Intenta cerrar la tarea del reproductor residente desde el proceso principal
 * (`AppTask`). Best-effort: en algunos equipos la tarea queda fuera de `getAppTasks`
 * por `excludeFromRecents`; ahí siguen el broadcast y la red de seguridad del
 * propio reproductor (se cierra solo si vuelve al frente sin reproducción).
 */
private fun finishPlayerTasks(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    var closed = false
    val tasks = runCatching { am.appTasks }.getOrNull().orEmpty()
    for (task in tasks) {
        val info = runCatching { task.taskInfo }.getOrNull() ?: continue
        if (!isPlayerTask(info.baseActivity?.className, info.topActivity?.className)) continue
        runCatching { task.finishAndRemoveTask() }
            .onSuccess { closed = true }
            .onFailure { Log.w("ExitFlow", "no se pudo cerrar la tarea del reproductor: ${it.message}") }
    }
    return closed
}

/**
 * Salida real de la app desde la pantalla principal (segundo Atrás):
 *
 *  1. Se intenta cerrar la tarea del reproductor residente DESDE AQUÍ (`AppTask`): no se
 *     depende de que un proceso `:x4k` congelado (los ANR de la tele lo mostraban) alcance
 *     a procesar un broadcast. Sin esa tarea, el sistema no puede traerla al frente al
 *     soltar la principal — el «cae en el reproductor de nuevo» del reporte, del que ya
 *     no se salía con Atrás.
 *  2. Si el sistema no deja ver la tarea, se le pide cerrarse por broadcast y se le da un
 *     instante (tope 700 ms); el reproductor, además, se cierra solo si vuelve al frente
 *     sin reproducción.
 *  3. Recién entonces se suelta la tarea principal (`finishAndRemoveTask`): la casa queda
 *     al frente, como cualquier app que se cierra.
 */
internal fun exitAppCleanly(activity: Activity) {
    val app = activity.applicationContext
    if (finishPlayerTasks(app)) {
        Log.i("ExitFlow", "tarea del reproductor cerrada antes de salir")
        activity.finishAndRemoveTask()
        return
    }
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
