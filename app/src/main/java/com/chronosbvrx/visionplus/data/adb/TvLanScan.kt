package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.net.ConnectivityManager
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Barrido explícito de la red para las teles Android 8/9 que solo tienen ADB clásico en el
 * puerto 5555. No se ejecuta solo: la persona pulsa «Buscar teles en la red» y, si aparece más
 * de una, elige la suya. Es la misma técnica que usa TidyTV (probar los 253 hosts del /24 en
 * paralelo), sin depender de mDNS.
 */
internal object TvLanScan {

    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** La IPv4 de la Wi-Fi o Ethernet activa; null si el teléfono no está en una red privada. */
    fun phoneIpv4(context: Context): String? {
        return runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching null
            val network = cm.activeNetwork ?: return@runCatching null
            val properties = cm.getLinkProperties(network) ?: return@runCatching null
            properties.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    /** Los otros 253 hosts del /24; vacío si la dirección no es privada. */
    fun hostsOf(phoneIp: String): List<String> {
        val match = IPV4.matchEntire(phoneIp.trim()) ?: return emptyList()
        val octets = match.groupValues.drop(1).map { it.toInt() }
        if (octets.any { it > 255 }) return emptyList()
        if (!isPrivate(octets)) return emptyList()
        val base = "${octets[0]}.${octets[1]}.${octets[2]}."
        return (1..254).filter { it != octets[3] }.map { base + it }
    }

    fun isValidHost(value: String): Boolean {
        val match = IPV4.matchEntire(value.trim()) ?: return false
        return match.groupValues.drop(1).all { it.toIntOrNull() in 0..255 }
    }

    private fun isPrivate(o: List<Int>) =
        o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)

    /** Hosts con el puerto 5555 abierto, en orden de IP. */
    suspend fun scan(
        phoneIp: String,
        port: Int = 5555,
        timeoutMs: Int = 350,
        parallel: Int = 64,
    ): List<String> = coroutineScope {
        val gate = Semaphore(parallel)
        val found = java.util.Collections.synchronizedList(mutableListOf<String>())
        val jobs = hostsOf(phoneIp).map { host ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    if (isOpen(host, port, timeoutMs)) found += host
                }
            }
        }
        jobs.forEach { it.join() }
        synchronized(found) { found.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 } }
    }

    suspend fun isOpen(host: String, port: Int, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: ConnectException) {
            false
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }
}
