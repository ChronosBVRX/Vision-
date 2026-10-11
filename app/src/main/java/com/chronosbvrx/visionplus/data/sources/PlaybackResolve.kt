package com.chronosbvrx.visionplus.data.sources

/**
 * The quiet bridge between screens that remember refs and the gateway: the catalog registers
 * its gateway here, and any playback url that is actually a ref inside itself gets routed
 * through it. Screens need nothing of the catalog besides this one hook.
 */
object PlaybackResolve {

    @Volatile
    private var gateway: SourcesGateway? = null

    internal fun host(gw: SourcesGateway) { gateway = gw }

    /** `esquema:uid|datos` → url real de la fuente; un url ya-http pasa sin tocarlo. */
    suspend fun urlFor(stream: String): String {
        val gw = gateway ?: return stream
        if (!stream.contains('|')) return stream
        return gw.playable(stream)?.url ?: stream
    }

    /** ¿El texto es un ref resolvible (con `|`) y el gateway lo conoce? */
    suspend fun knows(stream: String): Boolean {
        val gw = gateway ?: return false
        if (!stream.contains('|')) return false
        return gw.knows(stream)
    }
}
