package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Un extremo ADB descubierto por mDNS (la tele y su puerto). */
internal data class AdbEndpoint(val host: String, val port: Int)

/**
 * El ADB del teléfono: la app se presenta como una PC más.
 *
 * Reutiliza la llave de [AdbKeys] (una por teléfono) para que el emparejamiento se haga UNA
 * vez; después basta con descubrir el puerto y conectar. El `api` se fija bajo a propósito:
 * el demonio de la tele puede ser viejo y solo se usan servicios `shell:` clásicos.
 */
internal object TvAdb : AbsAdbConnectionManager() {

    private var key: PrivateKey? = null
    private var cert: Certificate? = null

    fun init(context: Context) {
        if (key != null) return
        val (privateKey, certificate) = AdbKeys.load(context)
        key = privateKey
        cert = certificate
        setApi(28)
        setTimeout(12, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = key ?: error("llave ADB sin cargar")
    override fun getCertificate(): Certificate = cert ?: error("certificado ADB sin cargar")

    override fun getDeviceName(): String = AdbKeys.deviceName()

    /**
     * Ejecuta un comando en la tele y devuelve su salida. El stream se cierra siempre: una
     * sesión colgada dejaría el canal ADB ocupado para el siguiente intento.
     */
    fun shell(command: String): String {
        val stream = openStream("shell:$command")
        return try {
            val input = stream.openInputStream()
            try {
                input.readBytes().toString(Charsets.UTF_8)
            } finally {
                runCatching { input.close() }
            }
        } finally {
            runCatching { stream.close() }
        }
    }

    fun disconnectQuietly() {
        runCatching { disconnect() }
    }
}

/**
 * Descubre los servicios que publica la depuración inalámbrica de la tele.
 *
 * `libadb` trae un descubridor propio, pero solo acepta servicios del MISMO equipo (compara
 * la IP con las interfaces locales): aquí la tele es otro equipo, así que se resuelve con
 * `NsdManager` y se le pasa host/puerto a [TvAdb].
 */
internal object AdbDiscovery {

    const val SERVICE_PAIRING = "adb-tls-pairing"
    const val SERVICE_CONNECT = "adb-tls-connect"

    fun find(context: Context, serviceType: String, timeoutMs: Long): AdbEndpoint? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        var found: AdbEndpoint? = null
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                latch.countDown()
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress ?: return
                            if (found != null) return
                            found = AdbEndpoint(host, resolved.port)
                            latch.countDown()
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
        }
        return try {
            nsd.discoverServices("_$serviceType._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
