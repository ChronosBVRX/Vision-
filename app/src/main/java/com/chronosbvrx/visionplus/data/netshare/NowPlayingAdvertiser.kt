package com.chronosbvrx.visionplus.data.netshare

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Anuncia el endpoint en la red local con mDNS/Bonjour:
 * `_visionplus._tcp` → «Vision+ - <dispositivo>» en el puerto real del servidor.
 *
 * El anuncio se rehace cuando cambia la red (Wi-Fi nueva, IP nueva). El [NsdManager] de Android
 * puede tardar en soltar un registro viejo: [restart] espera un momento y reintenta; si el
 * nombre ya está tomado, añade un sufijo para que el servicio siga siendo descubrible.
 */
internal class NowPlayingAdvertiser(
    context: Context,
    private val serverPort: Int,
    private val baseName: String,
) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val handler = Handler(Looper.getMainLooper())
    private var listener: NsdManager.RegistrationListener? = null
    private var attempt = 0
    private var restartToken = 0

    @Synchronized
    fun start() {
        val manager = nsd ?: return
        unregister()
        val name = if (attempt == 0) baseName else "$baseName (${attempt + 1})"
        val info = NsdServiceInfo().apply {
            serviceName = name.take(63)
            serviceType = NowPlayingShare.SERVICE_TYPE
            setPort(serverPort)
            setAttribute("app", NowPlayingShare.TXT_APP)
            setAttribute("version", NowPlayingShare.TXT_VERSION)
            setAttribute("path", NowPlayingShare.PATH)
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "mDNS listo: ${serviceInfo.serviceName} ${NowPlayingShare.SERVICE_TYPE}:$serverPort")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS falló (${serviceInfo.serviceName}, code=$errorCode); reintento")
                if (attempt < 2) {
                    attempt++
                    handler.postDelayed({ start() }, 1_500L)
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "mDNS retirado: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS no se pudo retirar (code=$errorCode)")
            }
        }
        listener = registration
        runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        }.onFailure {
            Log.w(TAG, "mDNS registerService: ${it.javaClass.simpleName}: ${it.message}")
            listener = null
        }
    }

    /**
     * Re-registro tras un cambio de red. Se espera un poco para que Android suelte el registro
     * anterior y para no rehacerlo en cada callback de conectividad.
     */
    fun restart() {
        val token = ++restartToken
        handler.postDelayed({
            if (token == restartToken) {
                attempt = 0
                start()
            }
        }, 900L)
    }

    @Synchronized
    fun stop() {
        restartToken++
        handler.removeCallbacksAndMessages(null)
        unregister()
    }

    @Synchronized
    private fun unregister() {
        val current = listener ?: return
        listener = null
        runCatching { nsd?.unregisterService(current) }
    }

    private companion object {
        const val TAG = "VisionNowPlaying"
    }
}
