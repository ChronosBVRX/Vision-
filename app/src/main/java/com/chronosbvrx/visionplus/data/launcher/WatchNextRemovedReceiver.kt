package com.chronosbvrx.visionplus.data.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * El usuario descartó una tarjeta de Vision+ desde el launcher («No me interesa»): el sistema
 * avisa con el id del programa y la app debe quitarlo del canal y **no volver a publicarlo**
 * mientras el historial no avance otra vez.
 */
class WatchNextRemovedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED) return
        val programId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
        if (programId <= 0L) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { LauncherWatchNext.onProgramDisabled(app, programId) }
            } finally {
                pending.finish()
            }
        }
    }
}
