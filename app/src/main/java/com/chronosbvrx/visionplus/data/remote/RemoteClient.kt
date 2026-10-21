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
                        found[beacon.id.ifBlank { host }] = RemoteTv(
                            host = host,
                            port = beacon.port,
                            name = beacon.name,
                            model = beacon.model,
                            id = beacon.id,
                        )
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
        // La respuesta trae la identidad estable de la tele: se aprende al instante y el token
        // guardado por dirección se muda a esa identidad (el vínculo no depende de la IP).
        val activeTv = learnTvId(context, state.value.tv ?: tv, msg.id)
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
                msg.token?.let { saveToken(context, activeTv, it) }
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

    /** Le entrega el código de sincronización a la tele (ella lo adopta y empieza a sincronizar). */
    fun sendSyncCode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) return
        send(RemoteMsg(t = RemoteProto.SYNC, syncCode = clean))
    }

    /**
     * Pide abrir contenido en la tele. Cuando es un capítulo de serie viaja también su contexto
     * ([seriesId] = la temporada activa y sus vecinas) para que la tele reproduzca ESE capítulo
     * y pueda seguir con el siguiente, en vez de mandar la serie a medias.
     *
     * [positionMs] es el minuto exacto que el teléfono está viendo (0 = desde el principio): la
     * tele reanuda ahí aunque su propio historial diga otra cosa. La orden explícita manda.
     */
    fun sendOpen(
        ref: String,
        title: String,
        poster: String = "",
        seriesId: String = "",
        seasonIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        positionMs: Long = 0,
    ) {
        send(
            RemoteMsg(
                t = RemoteProto.OPEN,
                ref = ref,
                title = title,
                poster = poster,
                series = seriesId.trim().ifBlank { null },
                seasonIndex = seasonIndex.takeIf { it > 0 },
                seasonIds = seasonIds.takeIf { it.isNotEmpty() },
                seasonNames = seasonNames.takeIf { it.isNotEmpty() },
                pos = positionMs.takeIf { it > 0 },
            ),
        )
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

    /**
     * El token de una tele. Se recuerda contra su identidad estable (sobrevive a los cambios
     * de IP); un vínculo viejo guardado por dirección se muda solo la primera vez.
     */
    fun tokenFor(context: Context, tv: RemoteTv): String {
        val p = prefs(context)
        val stored = p.getString(keyFor(tv), null)
        if (!stored.isNullOrBlank()) return stored
        if (tv.id.isBlank()) return ""
        // Migración: el mismo equipo ya vinculado puede traer su token bajo "host:puerto".
        val legacy = p.getString(legacyKeyFor(tv), null)
        if (!legacy.isNullOrBlank()) {
            p.edit().putString(keyFor(tv), legacy).apply()
        }
        return legacy.orEmpty()
    }

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
            id = p.getString(KEY_LAST_ID, "").orEmpty(),
        )
    }

    /** Las teles conocidas (aunque hoy no estén en la red), la más reciente primero. */
    fun knownTvs(context: Context): List<RemoteTv> =
        parseKnownTvs(prefs(context).getString(KEY_KNOWN_TVS, null))

    /** Las teles ya vinculadas: funcionan aunque el descubrimiento no las vea. */
    fun pairedTvs(context: Context): List<RemoteTv> = knownTvs(context).filter { isPaired(context, it) }

    /**
     * Todas las teles que el teléfono recuerda y con las que ya está vinculado: la última
     * usada primero, sin repetir. Sobre esto se conecta y se pinta «Tus teles vinculadas».
     */
    fun rememberedTvs(context: Context): List<RemoteTv> {
        val out = ArrayList<RemoteTv>(MAX_KNOWN_TVS)
        lastTv(context)?.takeIf { isPaired(context, it) }?.let { last ->
            if (out.none { it.key == last.key }) out += last
        }
        pairedTvs(context).forEach { tv ->
            if (out.none { it.key == tv.key }) out += tv
        }
        return out
    }

    fun rememberTv(context: Context, tv: RemoteTv) {
        val p = prefs(context)
        p.edit()
            .putString(KEY_LAST_HOST, tv.host)
            .putInt(KEY_LAST_PORT, tv.port)
            .putString(KEY_LAST_NAME, tv.name)
            .putString(KEY_LAST_MODEL, tv.model)
            .putString(KEY_LAST_ID, tv.id)
            .putString(KEY_KNOWN_TVS, upsertKnownTv(p.getString(KEY_KNOWN_TVS, null), tv))
            .apply()
    }

    private fun saveToken(context: Context, tv: RemoteTv, token: String) {
        prefs(context).edit().putString(keyFor(tv), token).apply()
        rememberTv(context, tv)
    }

    /**
     * Aprende la identidad estable de la tele (baliza o respuesta) y muda el token guardado
     * por dirección: el vínculo nunca se pierde por un cambio de IP. Devuelve la tele al día.
     */
    private fun learnTvId(context: Context, tv: RemoteTv, id: String?): RemoteTv {
        val clean = id?.trim().orEmpty().take(64)
        if (clean.isEmpty() || clean == tv.id) return tv
        val updated = tv.copy(id = clean)
        val p = prefs(context)
        if (p.getString(keyFor(updated), null).isNullOrBlank()) {
            p.getString(legacyKeyFor(tv), null)?.takeIf { it.isNotBlank() }?.let {
                p.edit().putString(keyFor(updated), it).remove(legacyKeyFor(tv)).apply()
            }
        }
        rememberTv(context, updated)
        if (state.value.tv == null || state.value.tv?.key == tv.key) {
            state.value = state.value.copy(tv = updated)
        }
        return updated
    }

    private fun keyFor(tv: RemoteTv): String = "tok_${tv.key}"

    /** La clave vieja (por dirección), solo para mudar vínculos de versiones anteriores. */
    private fun legacyKeyFor(tv: RemoteTv): String = "tok_${tv.host}:${tv.port}"

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
    private const val KEY_LAST_ID = "last_id"
    private const val KEY_KNOWN_TVS = "known_tvs"
}

/** Cómo rotula la pantalla a una tele: ya vinculada (entra sola, sin PIN) o por conectar. */
internal fun tvRowLabel(paired: Boolean): String = if (paired) "Vinculada" else "Conectar"

/** Cuántas teles recuerda el teléfono. */
private const val MAX_KNOWN_TVS = 5

/**
 * La lista de teles conocidas (JSON) con [tv] al frente: sin repetir (manda la identidad
 * estable) y sin crecer sin fin. Puro: lo que se recuerda queda cubierto por pruebas.
 *
 * Una tele sin identidad que ya conocemos mejor (la misma dirección con id) no la rediscribe:
 * la entrada con identidad vale más y evita el par duplicado de la migración.
 */
internal fun upsertKnownTv(raw: String?, tv: RemoteTv, max: Int = MAX_KNOWN_TVS): String {
    val existing = parseKnownTvs(raw)
    val sameBox = { other: RemoteTv -> other.host == tv.host && other.port == tv.port }
    if (tv.id.isBlank()) {
        if (existing.any { it.id.isNotBlank() && sameBox(it) }) return raw.orEmpty()
        val list = existing.filter { it.key != tv.key && !(it.id.isBlank() && sameBox(it)) }
        return knownTvsJson((listOf(tv) + list).take(max.coerceAtLeast(1)))
    }
    val list = existing.filter { it.key != tv.key && !sameBox(it) }
    return knownTvsJson((listOf(tv) + list).take(max.coerceAtLeast(1)))
}

private fun knownTvsJson(tvs: List<RemoteTv>): String {
    val arr = org.json.JSONArray()
    tvs.forEach { t ->
        arr.put(
            org.json.JSONObject().apply {
                put("id", t.id)
                put("host", t.host)
                put("port", t.port)
                put("name", t.name)
                put("model", t.model)
            },
        )
    }
    return arr.toString()
}

/** Lee la lista de teles conocidas; una entrada rara se descarta sola. */
internal fun parseKnownTvs(raw: String?): List<RemoteTv> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    val arr = runCatching { org.json.JSONArray(text) }.getOrNull() ?: return emptyList()
    val out = ArrayList<RemoteTv>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val host = o.optString("host").trim()
        if (host.isEmpty()) continue
        val port = o.optInt("port", RemoteDiscovery.CONTROL_PORT)
        if (port !in 1..65535) continue
        out += RemoteTv(
            host = host,
            port = port,
            name = o.optString("name").orEmpty(),
            model = o.optString("model").orEmpty(),
            id = o.optString("id").orEmpty(),
        )
    }
    return out
}
