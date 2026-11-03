package com.chronosbvrx.visionplus.data.netshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.remote.RemoteBus

/**
 * Mantiene vivo el anuncio mDNS y el HTTP de «qué está reproduciendo» mientras Vision+ está
 * abierta (incluso en segundo plano, por ejemplo mientras el reproductor `:x4k` está al frente).
 *
 * Es un servicio en primer plano de tipo `dataSync`: no toca el reproductor ni el DRM, solo lee
 * [RemoteBus.playback] (que el reproductor ya publica) y lo sirve por la LAN.
 */
class NowPlayingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var server: NowPlayingHttpServer? = null
    private var advertiser: NowPlayingAdvertiser? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground()
        startShare()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // `START_STICKY`: si Android reinicia el proceso, el anuncio vuelve solo.
        startInForeground()
        startShare()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopShare()
        unregisterNetworkCallback()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startShare() {
        if (server != null) return
        val device = NowPlayingShare.deviceName(this)
        val http = NowPlayingHttpServer(
            stateProvider = { RemoteBus.playback.value },
            device = device,
            token = NowPlayingShare.token(this),
            context = this,
        )
        val port = http.start()
        if (port <= 0) {
            Log.w(TAG, "no se pudo abrir el HTTP; reintento en 5 s")
            handler.postDelayed({ startShare() }, 5_000L)
            return
        }
        server = http
        advertiser = NowPlayingAdvertiser(this, port, NowPlayingShare.serviceName(this)).also {
            it.start()
        }
        Log.i(TAG, "compartiendo en la red: puerto $port (${NowPlayingShare.SERVICE_TYPE})")
    }

    private fun stopShare() {
        runCatching { advertiser?.stop() }
        advertiser = null
        runCatching { server?.stop() }
        server = null
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                advertiser?.restart()
            }

            override fun onLost(network: Network) {
                advertiser?.restart()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                // La IP puede cambiar sin que la red se caiga (DHCP nuevo, Wi-Fi distinto).
                advertiser?.restart()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    advertiser?.restart()
                }
            }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { Log.w(TAG, "network callback: ${it.message}") }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun startInForeground() {
        if (!startedForeground) {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startedForeground = true
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vision+ · Compartiendo en la red")
            .setContentText("Descubrible como _visionplus._tcp · GET /now-playing")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Compartir lo que se reproduce",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "VisionNowPlaying"
        const val CHANNEL_ID = "vision_now_playing"
        const val NOTIFICATION_ID = 0x5EED
    }
}
