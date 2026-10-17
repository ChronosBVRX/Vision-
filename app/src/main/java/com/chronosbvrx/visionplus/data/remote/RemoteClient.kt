package com.chronosbvrx.visionplus.data.remote

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** En qué va el control remoto (lo pinta la pantalla del teléfono). */
enum class RemoteStatus { IDLE, SEARCHING, CONNECTING, NEED_PIN, CONNECTED, ERROR }

/** Estado observable de la pantalla de control remoto. */
data class RemoteUiState(
    val status: RemoteStatus = RemoteStatus.IDLE,
    val tvs: List<RemoteTv> = emptyList(),
    val tv: RemoteTv? = null,
    val error: String? = null,
    /** Qué está viendo la tele. */
    val now: String = "",
    val paired: Boolean = false,
)

/**
 * El control remoto del lado del teléfono: encuentra las teles de la red, se conecta y manda las
 * órdenes. El token queda guardado por tele, así que la siguiente vez entra derecho.
 */
object RemoteClient {

    val state = mutableStateOf(RemoteUiState())

    @Volatile private var socket: Socket? = null
    @Volatile private var out: BufferedWriter? = null
    @Volatile private var searching = false

    /** Generación de conexión: un hilo viejo no puede pisar el estado del nuevo. */
    @Volatile private var gen = 0

    /**
     * Todas las escrituras del cable pasan por aquí: los botones de la pantalla corren en el hilo
     * principal y escribir en un socket ahí dispara `NetworkOnMainThreadException` (la orden se
     * perdía en silencio). Un solo hilo conserva el orden de las órdenes.
     */
    private val sender = Executors.newSingleThreadExecutor { r ->
        Thread(r, "remote-send").apply { isDaemon = true }
    }

    // --- Descubrimiento ---------------------------------------------------------------------

    /** Busca teles en la misma red (difusión UDP) y publica lo que encuentre. */
    fun search(timeoutMs: Long = 1_600L) {
        if (searching) return
        searching = true
        state.value = state.value.copy(status = RemoteStatus.SEARCHING, tvs = emptyList(), error = null)
        thread(name = "remote-search", isDaemon = true) {
            val found = LinkedHashMap<String, RemoteTv>()
            try {
                DatagramSocket().use { udp ->
                    udp.broadcast = true
                    udp.soTimeout = 250
                    val probe = RemoteDiscovery.probeLine().toByteArray(Charsets.UTF_8)
                    for (target in broadcastTargets()) {
                        runCatching {
                            udp.send(DatagramPacket(probe, probe.size, target, RemoteDiscovery.PORT))
                        }
                    }
                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buffer = ByteArray(512)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        if (runCatching { udp.receive(packet) }.isFailure) continue
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val beacon = RemoteDiscovery.parseBeacon(text) ?: continue
                        val host = packet.address.hostAddress ?: continue
                        found[host] = RemoteTv(host = host, port = beacon.port, name = beacon.name, model = beacon.model)
                    }
                }
            } catch (_: Throwable) {
                // Sin red o sin permiso de difusión: se reporta como «nada encontrado».
            } finally {
                searching = false
            }
            val list = found.values.toList()
            state.value = state.value.copy(
                status = if (list.isEmpty()) RemoteStatus.ERROR else RemoteStatus.IDLE,
                tvs = list,
                error = if (list.isEmpty()) "No encontré ninguna tele en esta red" else null,
            )
        }
    }

    /** Direcciones de difusión: la global más la de cada interfaz (algunas redes la necesitan). */
    private fun broadcastTargets(): List<InetAddress> {
        val targets = LinkedHashSet<InetAddress>()
        runCatching { targets += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                if (!nif.isUp) return@forEach
                nif.interfaceAddresses.forEach { addr ->
                    addr.broadcast?.let { targets += it }
                }
            }
        }
        return targets.toList()
    }

    // --- Conexión ---------------------------------------------------------------------------

    /** Se conecta a una tele; si ya está emparejada entra directo, si no pide el PIN. */
    fun connect(context: Context, tv: RemoteTv) {
        disconnect(keepList = true)
        rememberTv(context, tv)
        val myGen = ++gen
        state.value = state.value.copy(status = RemoteStatus.CONNECTING, tv = tv, error = null, now = "", paired = false)
        thread(name = "remote-connect", isDaemon = true) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(tv.host, tv.port), 4_000)
                if (gen != myGen) {
                    runCatching { s.close() }
                    return@thread
                }
                socket = s
                val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                out = writer
                send(RemoteMsg(t = RemoteProto.HELLO, name = deviceName(), token = tokenFor(context, tv)))
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (gen == myGen) {
                    val line = reader.readLine() ?: break
                    val msg = RemoteProto.decode(line) ?: continue
                    handle(context, tv, msg, myGen)
                }
                fail(myGen, "La tele cerró la conexión")
            } catch (t: Throwable) {
                fail(myGen, t.message ?: "No se pudo conectar")
            }
        }
    }

    private fun handle(context: Context, tv: RemoteTv, msg: RemoteMsg, myGen: Int) {
        if (gen != myGen) return
        when (msg.t) {
            RemoteProto.HELLO -> {
                if (msg.paired == true) {
                    state.value = state.value.copy(status = RemoteStatus.CONNECTED, paired = true, error = null)
                }
            }
            RemoteProto.NEED_PIN -> {
                state.value = state.value.copy(status = RemoteStatus.NEED_PIN, error = null)
            }
            RemoteProto.OK -> {
                msg.token?.let { saveToken(context, tv, it) }
                state.value = state.value.copy(status = RemoteStatus.CONNECTED, paired = true, error = null)
            }
            RemoteProto.DENIED -> {
                state.value = state.value.copy(status = RemoteStatus.NEED_PIN, error = "PIN incorrecto")
            }
            RemoteProto.NOW -> {
                state.value = state.value.copy(now = msg.screen.orEmpty())
            }
            RemoteProto.PONG -> Unit
        }
    }

    private fun fail(myGen: Int, message: String) {
        if (gen != myGen) return
        state.value = state.value.copy(status = RemoteStatus.ERROR, error = message)
        socket = null
        out = null
    }

    /** Manda el PIN que la tele está mostrando. */
    fun submitPin(pin: String) {
        send(RemoteMsg(t = RemoteProto.PIN, pin = pin))
        state.value = state.value.copy(error = null)
    }

    fun disconnect(keepList: Boolean = false) {
        gen++
        runCatching { socket?.close() }
        socket = null
        out = null
        state.value = state.value.copy(
            status = RemoteStatus.IDLE,
            tv = if (keepList) state.value.tv else null,
            now = "",
            paired = false,
        )
    }

    // --- Órdenes ----------------------------------------------------------------------------

    fun sendKey(code: Int, action: String = RemoteProto.ACTION_TAP) {
        if (!RemoteKeys.isAllowed(code)) return
        send(RemoteMsg(t = RemoteProto.KEY, code = code, action = action))
    }

    fun sendOpen(ref: String, title: String, poster: String = "") {
        send(RemoteMsg(t = RemoteProto.OPEN, ref = ref, title = title, poster = poster))
    }

    private fun send(msg: RemoteMsg) {
        val line = RemoteProto.encode(msg)
        sender.execute {
            val writer = out ?: return@execute
            runCatching {
                synchronized(writer) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            }
        }
    }

    // --- Tokens del teléfono ----------------------------------------------------------------

    fun tokenFor(context: Context, tv: RemoteTv): String =
        prefs(context).getString(keyFor(tv), null).orEmpty()

    fun isPaired(context: Context, tv: RemoteTv): Boolean = tokenFor(context, tv).isNotBlank()

    /** La última tele usada, si quedó guardada. */
    fun lastTv(context: Context): RemoteTv? {
        val p = prefs(context)
        val host = p.getString(KEY_LAST_HOST, null) ?: return null
        if (host.isBlank()) return null
        return RemoteTv(
            host = host,
            port = p.getInt(KEY_LAST_PORT, RemoteDiscovery.CONTROL_PORT),
            name = p.getString(KEY_LAST_NAME, "").orEmpty(),
            model = p.getString(KEY_LAST_MODEL, "").orEmpty(),
        )
    }

    fun rememberTv(context: Context, tv: RemoteTv) {
        prefs(context).edit()
            .putString(KEY_LAST_HOST, tv.host)
            .putInt(KEY_LAST_PORT, tv.port)
            .putString(KEY_LAST_NAME, tv.name)
            .putString(KEY_LAST_MODEL, tv.model)
            .apply()
    }

    private fun saveToken(context: Context, tv: RemoteTv, token: String) {
        prefs(context).edit().putString(keyFor(tv), token).apply()
        rememberTv(context, tv)
    }

    private fun keyFor(tv: RemoteTv): String = "tok_${tv.key}"

    /**
     * Solo para pruebas: engancha (o quita) un escritor falso del cable para observar en qué hilo
     * se escribe. El blindaje que cubre: escribir el socket en el hilo de la pantalla dispara
     * `NetworkOnMainThreadException` y la orden se perdía en silencio.
     */
    internal fun wireWriterForTest(writer: BufferedWriter?) {
        out = writer
    }

    private fun prefs(context: Context) = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)

    private fun deviceName(): String {
        val model = android.os.Build.MODEL?.trim().orEmpty()
        return model.ifBlank { "Teléfono" }
    }

    private const val KEY_LAST_HOST = "last_host"
    private const val KEY_LAST_PORT = "last_port"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_LAST_MODEL = "last_model"
}
