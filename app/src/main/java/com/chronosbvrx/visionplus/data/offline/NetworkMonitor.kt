package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Monitor reactivo de conectividad de red en tiempo real para Vision+:
 *
 * Detecta instantáneamente caídas o recuperaciones de conexión a internet (Wi-Fi, Ethernet, Datos móviles)
 * y expone [isOnline] como [StateFlow] para conmutar automáticamente hacia o desde la Interfaz Especial
 * de Modo Sin Conexión ("Vision+ Offline").
 *
 * Una red con transporte activo **no siempre tiene internet**: el caso clásico es el Wi-Fi del router
 * con el cable/ONT caído (o un portal cautivo). Android marca esas redes como «INTERNET» pero no las
 * puede *validar*. Antes eso se daba por bueno y la app intentaba cargar el catálogo para terminar en
 * un Inicio vacío; ahora, si el sistema no valida la red, se confirma con una sonda corta (TCP 443 a
 * resolutores públicos) y, si no hay salida, se abre el Modo Sin Conexión.
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile
    private var registered = false

    @Volatile
    private var testingOverride: Boolean? = null

    /** Contexto de aplicación para re-verificar la red al volver una sonda (nunca la Activity). */
    @Volatile
    private var appContext: Context? = null

    /** Sonda de salida real; un solo hilo y una sola sonda a la vez. */
    private val probeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vision-net-probe").apply { isDaemon = true }
    }
    @Volatile
    private var probeInFlight = false
    @Volatile
    private var lastProbeAtMs = 0L

    private const val PROBE_TIMEOUT_MS = 1_500
    private const val PROBE_COOLDOWN_MS = 15_000L

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh(context)
        if (registered) return
        synchronized(this) {
            if (registered) return
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (testingOverride == null) {
                        onNetworkState(cm)
                    }
                }

                override fun onLost(network: Network) {
                    if (testingOverride == null) {
                        onNetworkState(cm)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (testingOverride == null) {
                        onNetworkState(cm)
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
     * Comprueba el estado actual sin bloquear. Si la red dice tener internet pero el sistema no la
     * validó, lanza una sonda en segundo plano y conserva el veredicto anterior hasta que responda.
     */
    fun refresh(context: Context): Boolean {
        testingOverride?.let {
            _isOnline.value = it
            return it
        }
        val app = context.applicationContext
        appContext = app
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return onNetworkState(cm)
    }

    /**
     * Comprobación de arranque: si la red no está validada, espera la sonda (en IO, nunca en el hilo
     * principal) antes de decidir si se carga el catálogo o se abre directo el Modo Sin Conexión.
     */
    suspend fun verify(context: Context): Boolean = withContext(Dispatchers.IO) {
        testingOverride?.let {
            _isOnline.value = it
            return@withContext it
        }
        val app = context.applicationContext
        appContext = app
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val state = networkState(cm)
        if (!state.capable) {
            _isOnline.value = false
            return@withContext false
        }
        if (state.validated) {
            _isOnline.value = true
            return@withContext true
        }
        val reachable = probeReachable()
        lastProbeAtMs = System.currentTimeMillis()
        // La red pudo caerse/validarse mientras la sonda viajaba: manda el estado de AHORA.
        val current = networkState(cm)
        val online = when {
            !current.capable -> false
            current.validated -> true
            else -> reachable
        }
        _isOnline.value = online
        online
    }

    private fun onNetworkState(cm: ConnectivityManager?): Boolean {
        val state = networkState(cm)
        if (!state.capable) {
            _isOnline.value = false
            return false
        }
        if (state.validated) {
            _isOnline.value = true
            return true
        }
        // «Tiene transporte e INTERNET, pero nadie lo validó»: se confirma con una sonda.
        scheduleProbe(force = !_isOnline.value)
        return _isOnline.value
    }

    private fun scheduleProbe(force: Boolean) {
        val now = System.currentTimeMillis()
        if (probeInFlight) return
        if (!force && now - lastProbeAtMs < PROBE_COOLDOWN_MS) return
        probeInFlight = true
        lastProbeAtMs = now
        probeExecutor.execute {
            val reachable = probeReachable()
            probeInFlight = false
            if (testingOverride == null) {
                // La sonda no puede resucitar una red que ya se cayó ni contradecir al sistema
                // si la validó mientras viajaba: se re-lee el estado actual antes de aplicar.
                val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val current = networkState(cm)
                _isOnline.value = when {
                    !current.capable -> false
                    current.validated -> true
                    else -> reachable
                }
            }
        }
    }

    /** TCP 443 a resolutores públicos: hay salida real si al menos uno acepta la conexión. */
    private fun probeReachable(): Boolean {
        val targets = listOf("1.1.1.1", "8.8.8.8")
        for (host in targets) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, 443), PROBE_TIMEOUT_MS)
                    return true
                }
            } catch (_: Exception) {
                // Se prueba el siguiente resolutor.
            }
        }
        return false
    }

    private data class NetState(val capable: Boolean, val validated: Boolean)

    private fun networkState(cm: ConnectivityManager?): NetState {
        if (cm == null) return NetState(capable = true, validated = false)
        return runCatching {
            val activeNet = cm.activeNetwork ?: return@runCatching NetState(false, false)
            val caps = cm.getNetworkCapabilities(activeNet) ?: return@runCatching NetState(false, false)
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@runCatching NetState(false, false)
            }
            val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            if (!hasTransport) return@runCatching NetState(false, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // En redes cautivas o desconectadas sin internet real VALIDATED es false; con
                // TV boxes que no pueden validar (sin servicios de Google) la sonda decide.
                NetState(capable = true, validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            } else {
                NetState(capable = true, validated = true)
            }
        }.getOrElse {
            @Suppress("DEPRECATION")
            val connected = runCatching { cm.activeNetworkInfo?.isConnected == true }.getOrDefault(false)
            NetState(capable = connected, validated = connected)
        }
    }

    fun setOnlineForTesting(online: Boolean?) {
        testingOverride = online
        if (online != null) {
            _isOnline.value = online
        }
    }
}
