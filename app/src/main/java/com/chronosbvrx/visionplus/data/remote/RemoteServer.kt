package com.chronosbvrx.visionplus.data.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * El servidor del control remoto: **la tele escucha**.
 *
 * - **UDP** ([RemoteDiscovery.PORT]): contesta la sonda del teléfono con su baliza (nombre,
 *   modelo y puerto de control). Se pide un `MulticastLock` porque los drivers Wi-Fi filtran la
 *   difusión cuando nadie la reclama.
 * - **TCP** ([RemoteDiscovery.CONTROL_PORT]): un JSON por línea; emparejamiento por PIN la
 *   primera vez y token guardado después.
 *
 * Las teclas van a [RemoteBus] (nunca se inyecta una tecla que no esté en la lista blanca) y las
 * órdenes `open` se convierten en el deep link que la app ya sabe abrir.
 */
class RemoteServer(private val context: Context) {

    private val app = context.applicationContext
    private val tag = "VisionRemote"

    private var tcp: ServerSocket? = null
    private var udp: DatagramSocket? = null
    @Volatile private var lock: WifiManager.MulticastLock? = null
    private val running = AtomicBoolean(false)

    private val clients = CopyOnWriteArrayList<Client>()

    /** PIN de esta sesión: se muestra en la tele solo cuando un teléfono pregunta. */
    @Volatile private var pin: String = RemotePairing.newPin()

    private val deviceName: String by lazy {
        val model = Build.MODEL?.trim().orEmpty()
        if (model.isNotBlank()) model else "Vision+ TV"
    }
    private val deviceModel: String by lazy {
        val maker = Build.MANUFACTURER?.trim().orEmpty()
        val rel = Build.VERSION.RELEASE.orEmpty()
        listOf(maker, "Android $rel".trim()).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Identidad estable de esta tele: una vez creada, el vínculo del teléfono no depende de la IP. */
    private val tvId: String by lazy { tvIdOf(app) }

    private var nowReceiver: BroadcastReceiver? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        pin = RemotePairing.newPin()
        // Los dispositivos recordados quedan visibles para Ajustes (aunque nadie esté conectado).
        RemoteBus.pairedDevices.value = rememberedDevices(app)
        // Los drivers Wi-Fi filtran la difusión si nadie la reclama: sin esto el teléfono
        // tiene que saber la IP a mano.
        runCatching {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            lock = wifi?.createMulticastLock("visionplus-remote")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        runCatching { startUdp() }.onFailure { Log.w(tag, "UDP de descubrimiento no arrancó: $it") }
        runCatching { startTcp() }.onFailure { Log.w(tag, "servidor de control no arrancó: $it") }
        watchNow()
        Log.i(tag, "control remoto activo ($deviceName, puerto ${RemoteDiscovery.CONTROL_PORT})")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { udp?.close() }
        runCatching { tcp?.close() }
        clients.forEach { it.close() }
        clients.clear()
        runCatching { lock?.release() }
        nowReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        nowReceiver = null
        RemoteBus.clients.value = 0
        RemoteBus.pairingPin.value = null
    }

    // --- Descubrimiento (UDP) ---------------------------------------------------------------

    private fun startUdp() {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(RemoteDiscovery.PORT))
        }
        udp = socket
        thread(name = "remote-udp", isDaemon = true) {
            val buffer = ByteArray(512)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                val ok = runCatching { socket.receive(packet) }.isSuccess
                if (!ok) break
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!RemoteDiscovery.isProbe(text)) continue
                val reply = RemoteDiscovery.beaconLine(deviceName, deviceModel, RemoteDiscovery.CONTROL_PORT, tvId)
                    .toByteArray(Charsets.UTF_8)
                runCatching { socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port)) }
            }
        }
    }

    // --- Control (TCP) ----------------------------------------------------------------------

    private fun startTcp() {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(RemoteDiscovery.CONTROL_PORT))
        }
        tcp = server
        thread(name = "remote-tcp", isDaemon = true) {
            while (running.get()) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                val client = Client(socket)
                clients += client
                client.start()
            }
        }
    }

    /** Le manda a todos los teléfonos autorizados lo que la tele está mostrando. */
    private fun watchNow() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(RemoteBus.EXTRA_NOW)?.let { RemoteBus.publishScreen(it) }
            }
        }
        nowReceiver = receiver
        runCatching {
            // API 33+: obligatorio declarar el receptor como no exportado (no es un
            // broadcast del sistema y solo lo manda nuestra propia app).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, IntentFilter(RemoteBus.ACTION_REMOTE_NOW), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, IntentFilter(RemoteBus.ACTION_REMOTE_NOW))
            }
        }
        thread(name = "remote-now", isDaemon = true) {
            var last = ""
            while (running.get()) {
                val now = RemoteBus.screen.value
                if (now != last) {
                    last = now
                    clients.forEach { it.send(RemoteMsg(t = RemoteProto.NOW, screen = now)) }
                }
                runCatching { Thread.sleep(400) }
            }
        }
    }

    // --- Un teléfono ------------------------------------------------------------------------

    private inner class Client(private val socket: Socket) {
        private val out: BufferedWriter =
            BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        @Volatile private var authorized = false
        @Volatile private var name = ""
        @Volatile private var attempts = 0
        private var reader: BufferedReader? = null

        fun start() {
            thread(name = "remote-client", isDaemon = true) {
                try {
                    val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    reader = input
                    while (running.get() && !socket.isClosed) {
                        val line = input.readLine() ?: break
                        val msg = RemoteProto.decode(line) ?: continue
                        handle(msg)
                    }
                } catch (_: Throwable) {
                    // El teléfono se fue: se limpia solo.
                } finally {
                    close()
                }
            }
        }

        fun send(msg: RemoteMsg) = runCatching {
            synchronized(out) {
                out.write(RemoteProto.encode(msg))
                out.write("\n")
                out.flush()
            }
        }

        fun close() {
            clients.remove(this)
            RemoteBus.clients.value = clients.count { it.authorized }
            runCatching { socket.close() }
        }

        private fun handle(msg: RemoteMsg) {
            when (msg.t) {
                RemoteProto.HELLO -> {
                    name = msg.name.orEmpty().take(48)
                    val tokens = tokens()
                    if (RemotePairing.isValidToken(tokens, msg.token)) {
                        authorized = true
                        // El recordado sube al frente: la lista queda ordenada por reciente.
                        val tok = msg.token.orEmpty()
                        if (tokens.firstOrNull() != tok) saveTokens(RemotePairing.addToken(tokens, tok))
                        RemoteBus.clients.value = clients.count { it.authorized }
                        send(RemoteMsg(t = RemoteProto.HELLO, name = deviceName, model = deviceModel, paired = true, id = tvId))
                        send(RemoteMsg(t = RemoteProto.NOW, screen = RemoteBus.screen.value))
                    } else {
                        // Primera vez: la tele enseña el PIN (solo ahora, porque alguien lo pidió).
                        RemoteBus.pairingPin.value = pin
                        send(RemoteMsg(t = RemoteProto.NEED_PIN, name = deviceName, model = deviceModel, id = tvId))
                    }
                }

                RemoteProto.PIN -> {
                    if (RemotePairing.pinMatches(pin, msg.pin)) {
                        val token = RemotePairing.newToken(Random.Default)
                        // El nombre viaja con el token: la tele recuerda QUIÉN es cada vinculado.
                        saveDeviceName(token, name)
                        saveTokens(RemotePairing.addToken(tokens(), token))
                        authorized = true
                        pin = RemotePairing.newPin()
                        RemoteBus.pairingPin.value = null
                        RemoteBus.pairedWith.value = name
                        RemoteBus.clients.value = clients.count { it.authorized }
                        send(RemoteMsg(t = RemoteProto.OK, token = token, name = deviceName, id = tvId))
                        send(RemoteMsg(t = RemoteProto.NOW, screen = RemoteBus.screen.value))
                    } else {
                        attempts++
                        RemoteBus.pairingPin.value = pin
                        send(RemoteMsg(t = RemoteProto.DENIED, reason = RemoteProto.DENIED_PIN))
                        if (attempts >= MAX_PIN_ATTEMPTS) close()
                    }
                }

                RemoteProto.KEY -> {
                    if (!authorized) return
                    val code = msg.code ?: return
                    if (RemoteKeys.isAllowed(code)) {
                        RemoteBus.dispatchKey(app, code, msg.action ?: RemoteProto.ACTION_TAP)
                    }
                }

                RemoteProto.OPEN -> {
                    if (!authorized) return
                    launchOpen(msg)
                }

                RemoteProto.PING -> send(RemoteMsg(t = RemoteProto.PONG))
                RemoteProto.BYE -> close()
                else -> send(RemoteMsg(t = RemoteProto.ERR, msg = "desconocido: ${msg.t.take(24)}"))
            }
        }
    }

    private fun launchOpen(msg: RemoteMsg) {
        val uri = remoteOpenUriString(
            ref = msg.ref.orEmpty(),
            title = msg.title.orEmpty(),
            poster = msg.poster.orEmpty(),
            seriesId = msg.series.orEmpty(),
            seasonIndex = msg.seasonIndex ?: 0,
            seasonIds = msg.seasonIds.orEmpty(),
            seasonNames = msg.seasonNames.orEmpty(),
        ) ?: return
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setPackage(app.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // --- Tokens recordados ------------------------------------------------------------------

    private fun prefs() = app.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)

    private fun tokens(): List<String> =
        prefs().getString(KEY_TOKENS, "").orEmpty().split('|').filter { it.isNotBlank() }

    private fun saveTokens(list: List<String>) {
        val p = prefs()
        val knownNames = p.all.keys.filter { it.startsWith(DEVICE_PREFIX) }
        val edit = p.edit().putString(KEY_TOKENS, list.joinToString("|"))
        // Un token que sale de la lista (tope de recordados) se lleva su nombre.
        knownNames.forEach { key ->
            if (key.removePrefix(DEVICE_PREFIX) !in list) edit.remove(key)
        }
        edit.apply()
        RemoteBus.pairedDevices.value = rememberedDevices(app)
    }

    /** El nombre con el que se recuerda a este dispositivo (la tele lo muestra en Ajustes). */
    private fun saveDeviceName(token: String, deviceName: String) {
        if (token.isBlank()) return
        prefs().edit().putString(DEVICE_PREFIX + token, deviceName.trim().take(48).ifBlank { "Teléfono" }).apply()
    }

    companion object {
        private const val KEY_TOKENS = "tv_tokens"
        private const val KEY_TV_ID = "tv_id"
        private const val DEVICE_PREFIX = "dev_"
        private const val MAX_PIN_ATTEMPTS = 5

        private fun pairingPrefs(context: Context) =
            context.applicationContext.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)

        /**
         * Identidad estable de esta tele: se crea una vez y no cambia más. El teléfono guarda
         * su token contra este id (no contra la IP), así el vínculo sobrevive a los reinicios
         * y a los cambios de dirección.
         */
        internal fun tvIdOf(context: Context): String {
            val p = pairingPrefs(context)
            val existing = p.getString(KEY_TV_ID, null).orEmpty()
            if (existing.isNotBlank()) return existing
            val fresh = RemotePairing.newToken(Random.Default)
            p.edit().putString(KEY_TV_ID, fresh).apply()
            return fresh
        }

        /** Los dispositivos recordados (sus nombres, el más reciente primero). */
        fun rememberedDevices(context: Context): List<String> {
            val p = pairingPrefs(context)
            return p.getString(KEY_TOKENS, "").orEmpty().split('|')
                .filter { it.isNotBlank() }
                .map { token -> p.getString(DEVICE_PREFIX + token, null).orEmpty().ifBlank { "Teléfono" } }
        }

        /**
         * Olvida TODOS los vínculos: la próxima vez cada teléfono tendrá que emparejar de
         * nuevo con el PIN. Devuelve cuántos dispositivos se olvidaron.
         */
        fun forgetPairings(context: Context): Int {
            val p = pairingPrefs(context)
            val tokens = p.getString(KEY_TOKENS, "").orEmpty().split('|').filter { it.isNotBlank() }
            val edit = p.edit().remove(KEY_TOKENS)
            tokens.forEach { edit.remove(DEVICE_PREFIX + it) }
            edit.apply()
            RemoteBus.pairedDevices.value = emptyList()
            return tokens.size
        }
    }
}
