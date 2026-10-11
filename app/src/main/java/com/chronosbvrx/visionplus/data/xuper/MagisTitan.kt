package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.Log

/**
 * Boots the Titan engine once per process and hands it the current Magis session: the SLB's
 * play params (the engine's `params`), the CDN entries, and the account's device identity. From
 * there on the engine resolves its own sessions, signs its requests and plays.
 */
object MagisTitan {
    private const val TAG = "MagisTitan"

    @Volatile
    private var ready = false

    /** Engine up + session + entries; safe to call from any thread. */
    fun ensureSession(context: Context): Boolean {
        if (ready) return true
        synchronized(this) {
            if (ready) return true
            return try {
                val app = context.applicationContext
                if (!TitanSdk.engineBoot(app)) return false
                val client = MagisClient(app)
                val slb = client.getSlbInfoRecovering()
                val params = slb.optString("play_params")
                TitanSdk.sendSession(client.config.userId, client.config.sn, params)
                TitanSdk.sendEntries(client.entriesJson(slb))
                ready = true
                Log.i(TAG, "sesión entregada al motor (params ${params.length} bytes)")
                true
            } catch (e: Exception) {
                Log.w(TAG, "ensureSession falló: ${e.message}")
                false
            }
        }
    }
}
