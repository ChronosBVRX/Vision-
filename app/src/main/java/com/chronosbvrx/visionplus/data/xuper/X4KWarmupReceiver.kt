package com.chronosbvrx.visionplus.data.xuper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronosbvrx.visionplus.ui.player.X4KPlayerAty

/**
 * Receptor ligero en el proceso `:x4k` que precalienta el motor nativo Titan/Ranger
 * (`X4KTitan.load`, `boot`, `sendEntriesCdnList`, `sendEnv`) y la caché de canales en
 * memoria RAM en segundo plano antes de que el usuario haga clic en un canal o título.
 *
 * Lo usan el splash de la app (acción [ACTION_PREWARM]) y el camino diferido de siempre
 * (`X4KApi.prewarmEngine`). El launcher, en cambio, se vincula a [EngineWarmService].
 */
class X4KWarmupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Salida de la app pedida por el principal: el reproductor residente (su tarea aparte)
        // se cierra para que el sistema no lo traiga al frente al soltar la actividad principal.
        if (intent?.action == ACTION_FINISH_FOR_EXIT) {
            X4KPlayerAty.finishResidentForExit()
            return
        }
        // Reinicio ordenado del proceso pedido por el principal (descargas): el motor
        // headless exige un proceso en el que la vista del reproductor no haya reproducido.
        // Sin reproducción activa, el proceso entero se reinicia; el principal espera a que
        // muera y recién entonces encarga la descarga (que nacerá en un proceso limpio).
        if (intent?.action == ACTION_DIE_FOR_DOWNLOADS) {
            if (!X4KPlayerAty.isPlayerActive) {
                Log.i("X4KWarmupReceiver", "proceso :x4k reiniciado para descargas (residencia)")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            return
        }
        if (X4KPlayerAty.isPlayerActive) return
        val appCtx = context.applicationContext
        val entriesJson = intent?.getStringExtra("entriesJson")
        val playParams = intent?.getStringExtra("playParams").orEmpty()
        val source = if (intent?.action == ACTION_PREWARM) "splash" else "deferred"
        val pending = goAsync()
        Thread {
            try {
                EngineWarmup.warmBlocking(
                    context = appCtx,
                    entriesOverride = entriesJson,
                    paramsOverride = playParams,
                    source = source,
                    playerActive = { X4KPlayerAty.isPlayerActive },
                )
            } catch (e: Exception) {
                Log.w("X4KWarmupReceiver", "Precalentamiento :x4k omitido: ${e.message}")
            } finally {
                runCatching { pending.finish() }
            }
        }.start()
    }

    companion object {
        /** El splash pide el warmup explícitamente (misma app, receptor no exportado). */
        const val ACTION_PREWARM = "com.chronosbvrx.visionplus.action.X4K_PREWARM"

        /** Orden del principal: el proceso `:x4k` se reinicia antes de una descarga. */
        const val ACTION_DIE_FOR_DOWNLOADS = "com.chronosbvrx.visionplus.action.X4K_DIE_FOR_DOWNLOADS"

        /** Orden del principal: el reproductor residente se cierra (salida de la app). */
        const val ACTION_FINISH_FOR_EXIT = "com.chronosbvrx.visionplus.action.X4K_FINISH_FOR_EXIT"
    }
}
