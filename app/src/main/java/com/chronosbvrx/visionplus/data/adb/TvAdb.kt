package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.runBlocking

/** Un extremo ADB descubierto por mDNS (la tele y su puerto). */
internal data class AdbEndpoint(val host: String, val port: Int)

/**
 * El ADB del teléfono: la app se presenta como una PC más.
 *
 * Reutiliza la llave de [AdbKeys] (una por teléfono) para que el emparejamiento se haga UNA
 * vez; después basta con descubrir el puerto y conectar. El `api` se fija bajo a propósito:
 * el demonio de la tele puede ser viejo y solo se usan servicios `shell:` clásicos.
 */
internal object TvAdb {

    @Volatile
    private var client: Kadb? = null

    @Volatile
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val (privateKey, certificate) = AdbKeys.load(context)
            // Kadb espera la llave y el certificado en PEM. Se reutiliza LA MISMA llave que ya
            // autorizó la tele con LibADB, así no vuelve a pedir permiso al cambiar de cliente.
            KadbCert.set(
                pem(certificate.encoded, "CERTIFICATE"),
                pem(privateKey.encoded, "PRIVATE KEY"),
            )
            ready = true
        }
    }

    private fun pem(der: ByteArray, label: String): ByteArray =
        ("-----BEGIN $label-----\n" +
            android.util.Base64.encodeToString(der, android.util.Base64.NO_WRAP) +
            "\n-----END $label-----\n").toByteArray(Charsets.US_ASCII)

    /**
     * Conecta y comprueba el shell con un `echo`. Kadb habla shell clásico en teles viejas y
     * shell v2 en las nuevas, y mantiene los streams sin el fallo de LibADB que cerraba la
     * conexión después de dos comandos en TCL Android 9.
     */
    fun connect(host: String, port: Int, timeoutMs: Long = 180_000): Boolean {
        disconnectQuietly()
        val socketTimeout = timeoutMs.toInt().coerceAtLeast(180_000)
        val connection = Kadb.create(host, port, 12_000, socketTimeout)
        val ok = runCatching {
            open(connection, "echo visionplus-ok").contains("visionplus-ok", ignoreCase = true)
        }.getOrDefault(false)
        if (!ok) {
            runCatching { connection.close() }
            return false
        }
        client = connection
        return true
    }

    /** Kadb ya espera el aviso del sistema; se conserva el nombre para el flujo existente. */
    fun connectAllowingAuthorization(host: String, port: Int, timeoutMs: Long = 180_000): Boolean =
        connect(host, port, timeoutMs)

    fun isConnected(): Boolean = client?.connectionCheck() == true

    fun shell(command: String): String {
        val connection = client ?: throw java.io.IOException("No conectado a ADB.")
        return open(connection, command)
    }

    private fun open(connection: Kadb, command: String): String {
        val stream = connection.open("shell:$command")
        return try {
            stream.source.readUtf8()
        } finally {
            runCatching { stream.close() }
        }
    }

    fun pair(host: String, port: Int, code: String): Boolean = runBlocking {
        runCatching {
            Kadb.pair(host, port, code, AdbKeys.deviceName())
            true
        }.getOrDefault(false)
    }

    fun installApk(bytes: ByteArray): Boolean =
        installApkOutput(bytes)?.contains("Success", ignoreCase = true) == true

    fun installApkOutput(bytes: ByteArray): String? {
        val temp = runCatching { File.createTempFile("vision-install", ".apk") }.getOrNull() ?: return null
        return try {
            temp.writeBytes(bytes)
            installApkFileOutput(temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    fun installApkFile(file: File): Boolean =
        installApkFileOutput(file)?.contains("Success", ignoreCase = true) == true

    /**
     * `pm install -r -S` por `exec:` cierra la conexión en algunas teles viejas. Aquí se sube
     * el APK por sync y se instala con un comando normal del shell; es el camino de TidyTV.
     */
    fun installApkFileOutput(file: File): String? {
        if (!file.isFile || file.length() <= 0) return null
        val connection = client ?: return null
        val remote = "/data/local/tmp/vision-install-${System.currentTimeMillis()}.apk"
        return try {
            connection.push(file, remote, "644".toInt(8), System.currentTimeMillis())
            open(connection, "pm install -r $remote")
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { open(connection, "rm -f $remote") }
        }
    }

    fun disconnectQuietly() {
        val current = client
        client = null
        runCatching { current?.close() }
    }

    fun disconnect() = disconnectQuietly()
}

/**
 * Descubre los servicios que publica la depuración inalámbrica de la tele.
 *
 * Kadb trae un descubridor propio, pero solo acepta servicios del MISMO equipo (compara
 * la IP con las interfaces locales): aquí la tele es otro equipo, así que se resuelve con
 * `NsdManager` y se le pasa host/puerto a [TvAdb].
 */
internal object AdbDiscovery {

    const val SERVICE_PAIRING = "adb-tls-pairing"
    const val SERVICE_CONNECT = "adb-tls-connect"

    /**
     * Android solo permite un `resolveService` a la vez. Si otro servicio se está resolviendo,
     * reintenta una vez a los 250 ms en vez de perder la tele.
     */
    private fun resolveWithRetry(
        nsd: NsdManager,
        info: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
    ) {
        val listener = object : NsdManager.ResolveListener {
            private var retried = false
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && !retried) {
                    retried = true
                    Handler(Looper.getMainLooper()).postDelayed(
                        { runCatching { nsd.resolveService(serviceInfo, this) } },
                        250,
                    )
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
        }
        runCatching { nsd.resolveService(info, listener) }
    }

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

/** Un servicio mDNS ya resuelto, con su nombre amigable si lo publica (Google Cast). */
internal data class AdbService(
    val host: String,
    val port: Int,
    val name: String = "",
    val model: String = "",
)

/**
 * Todos los extremos visibles de un servicio, con el nombre que publica (para las tarjetas de
 * «Mi tele»). Igual que [findAll], pero conserva `fn`/`md` de `_googlecast._tcp`.
 */
internal fun findAllServices(
    context: Context,
    serviceType: String,
    timeoutMs: Long,
    max: Int = 8,
): List<AdbService> {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
    val latch = CountDownLatch(1)
    val found = mutableListOf<AdbService>()
    val listener = object : NsdManager.DiscoveryListener {
        private var retriedStart = false
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (!retriedStart) {
                retriedStart = true
                Handler(Looper.getMainLooper()).postDelayed(
                    { runCatching { nsd.discoverServices("_$serviceType._tcp", NsdManager.PROTOCOL_DNS_SD, this) } },
                    300,
                )
            } else {
                latch.countDown()
            }
        }
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onServiceFound(info: NsdServiceInfo) {
            resolveWithRetry(nsd, info) { resolved ->
                val host = resolved.host?.hostAddress ?: return@resolveWithRetry
                val name = resolved.attributes["fn"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
                    .ifBlank { resolved.serviceName.orEmpty().trim() }
                val model = resolved.attributes["md"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
                val service = AdbService(host, resolved.port, name, model)
                synchronized(found) {
                    val index = found.indexOfFirst { it.host == service.host }
                    if (index >= 0) {
                        val previous = found[index]
                        found[index] = previous.copy(
                            name = previous.name.ifBlank { service.name },
                            model = previous.model.ifBlank { service.model },
                        )
                    } else {
                        found += service
                    }
                    if (found.size >= max) latch.countDown()
                }
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
                resolveWithRetry(nsd, info) { resolved ->
                    val host = resolved.host?.hostAddress ?: return@resolveWithRetry
                    val endpoint = AdbEndpoint(host, resolved.port)
                    synchronized(found) {
                        if (found.none { it.host == endpoint.host && it.port == endpoint.port }) {
                            found += endpoint
                        }
                        if (found.size >= max) latch.countDown()
                    }
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
