package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor reactivo de conectividad de red en tiempo real para Vision+:
 *
 * Detecta instantáneamente caídas o recuperaciones de conexión a internet (Wi-Fi, Ethernet, Datos móviles)
 * y expone [isOnline] como [StateFlow] para conmutar automáticamente hacia o desde la Interfaz Especial
 * de Modo Sin Conexión ("Vision+ Offline").
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile
    private var registered = false

    @Volatile
    private var testingOverride: Boolean? = null

    fun init(context: Context) {
        refresh(context)
        if (registered) return
        synchronized(this) {
            if (registered) return
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (testingOverride == null) {
                        _isOnline.value = checkActiveNetwork(cm)
                    }
                }

                override fun onLost(network: Network) {
                    if (testingOverride == null) {
                        _isOnline.value = checkActiveNetwork(cm)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (testingOverride == null) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        _isOnline.value = hasInternet && checkActiveNetwork(cm)
                    }
                }
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(callback)
                } else {
                    val req = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(req, callback)
                }
                registered = true
            }
        }
    }

    /**
     * Comprueba sincrónicamente el estado actual de la red y actualiza [isOnline].
     */
    fun refresh(context: Context): Boolean {
        testingOverride?.let {
            _isOnline.value = it
            return it
        }
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val online = if (cm != null) checkActiveNetwork(cm) else true
        _isOnline.value = online
        return online
    }

    private fun checkActiveNetwork(cm: ConnectivityManager): Boolean {
        return runCatching {
            val activeNet = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return@runCatching false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@runCatching false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // En redes cautivas o desconectadas sin internet real, VALIDATED puede ser false,
                // pero aceptamos INTERNET + transporte activo para evitar falsos negativos en TV Boxes.
                val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                hasTransport
            } else {
                true
            }
        }.getOrElse {
            @Suppress("DEPRECATION")
            runCatching { cm.activeNetworkInfo?.isConnected == true }.getOrDefault(false)
        }
    }

    fun setOnlineForTesting(online: Boolean?) {
        testingOverride = online
        if (online != null) {
            _isOnline.value = online
        }
    }
}
