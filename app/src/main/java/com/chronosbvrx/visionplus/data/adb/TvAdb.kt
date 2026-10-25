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
     *
     * OJO: LibADB lanza `IOException("Stream closed.")` cuando el demonio cierra el stream sin
     * datos pendientes (en vez de devolver -1). Sin atraparlo se perdía TODA la respuesta de los
     * comandos rápidos — por eso el flujo creía que Vision+ Home no estaba instalado y no hacía
     * nada en la tele.
     */
    fun shell(command: String): String {
        val stream = openStream("shell:$command")
        return try {
            val output = StringBuilder()
            val input = stream.openInputStream()
            val buffer = ByteArray(8 * 1024)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.append(String(buffer, 0, read, Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                if (!isClosedMessage(t)) throw t
            } finally {
                runCatching { input.close() }
            }
            output.toString()
        } finally {
            runCatching { stream.close() }
        }
    }

    /** El cierre normal del stream según LibADB (no es un error del comando). */
    fun isClosedMessage(t: Throwable): Boolean =
        t is java.io.IOException && t.message.orEmpty().contains("closed", ignoreCase = true)

    fun disconnectQuietly() {
        runCatching { disconnect() }
    }

    /**
     * Instala un APK en la tele por ADB (`pm install -S`). Se usa para dejar Vision+ Home en el
     * televisor cuando el flujo lo necesita y no estaba: así el usuario no tiene que ir a la tele.
     */
    fun installApk(bytes: ByteArray): Boolean {
        val stream = openStream("exec:pm install -S ${bytes.size}")
        return try {
            val output = stream.openOutputStream()
            // No se cierra antes de leer: `pm install -S` termina al recibir el tamaño exacto y
            // responde por el mismo stream; cerrar aquí podría cortar la respuesta.
            output.write(bytes)
            output.flush()
            val text = StringBuilder()
            val input = stream.openInputStream()
            val buffer = ByteArray(8 * 1024)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    text.append(String(buffer, 0, read, Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                if (!isClosedMessage(t)) throw t
            } finally {
                runCatching { input.close() }
            }
            runCatching { output.close() }
            text.contains("Success", ignoreCase = true)
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { stream.close() }
        }
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

    /**
     * Todos los extremos visibles de un servicio (hasta [max]). Se usa para identificar de qué
     * tele es cada puerto cuando el teléfono conoce más de una: hay que conectar y preguntarle
     * su Android ID.
     */
    fun findAll(context: Context, serviceType: String, timeoutMs: Long, max: Int = 4): List<AdbEndpoint> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val latch = CountDownLatch(1)
        val found = mutableListOf<AdbEndpoint>()
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
                            val endpoint = AdbEndpoint(host, resolved.port)
                            synchronized(found) {
                                if (found.none { it.host == endpoint.host && it.port == endpoint.port }) {
                                    found += endpoint
                                }
                                if (found.size >= max) latch.countDown()
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
        }
        return try {
            nsd.discoverServices("_$serviceType._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            synchronized(found) { found.toList() }
        } catch (t: Throwable) {
            emptyList()
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
