package com.chronosbvrx.visionplus.data.remote

/**
 * Descubrimiento en la misma red: el teléfono lanza una **sonda** por difusión UDP y la tele
 * contesta a quien preguntó con una **baliza** (nombre, modelo y puerto de control).
 *
 * Se sostiene en la misma codificación que el resto del control remoto (un JSON por datagrama),
 * así no hay un segundo formato que mantener. Aquí vive lo puro: las líneas y su lectura.
 */
object RemoteDiscovery {
    /** Puerto UDP de la sonda/baliza. */
    const val PORT = 48632

    /** Puerto TCP de control. */
    const val CONTROL_PORT = 48633

    /** La línea que difunde el teléfono. */
    fun probeLine(): String = RemoteProto.encode(RemoteMsg(t = RemoteProto.PROBE))

    /** La línea con la que la tele se da a conocer. */
    fun beaconLine(name: String, model: String, port: Int = CONTROL_PORT): String =
        RemoteProto.encode(
            RemoteMsg(
                t = RemoteProto.HELLO,
                name = name.take(48).ifBlank { "Vision+ TV" },
                model = model.take(48),
                port = port,
            ),
        )

    /** ¿El datagrama es una sonda? */
    fun isProbe(text: String?): Boolean = RemoteProto.decode(text)?.t == RemoteProto.PROBE

    /** La baliza de una tele, si el datagrama es una. */
    fun parseBeacon(text: String?): Beacon? {
        val msg = RemoteProto.decode(text) ?: return null
        if (msg.t != RemoteProto.HELLO) return null
        val port = msg.port ?: return null
        if (port !in 1..65535) return null
        return Beacon(name = msg.name.orEmpty(), model = msg.model.orEmpty(), port = port)
    }

    /** Lo que una tele dice de sí misma. */
    data class Beacon(val name: String, val model: String, val port: Int)
}

/** Una tele encontrada (o escrita a mano): dónde y cómo llegarle. */
data class RemoteTv(
    val host: String,
    val port: Int = RemoteDiscovery.CONTROL_PORT,
    val name: String = "",
    val model: String = "",
) {
    /** Clave estable para recordar el token de ESTA tele. */
    val key: String get() = "$host:$port"
}
