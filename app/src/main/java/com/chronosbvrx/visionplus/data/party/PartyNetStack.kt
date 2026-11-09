package com.chronosbvrx.visionplus.data.party

import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Capa de red del WebView de Vision+ Party.
 *
 * Problema (medido en una TCL BeyondTV Android 9): la pila de red de Chromium
 * dentro del WebView se cuelga a la mitad del cuerpo en respuestas de más de
 * ~50 KB (los fetch() quedan pendientes para siempre y el bundle JS nunca
 * termina de cargarse: pantalla blanca/negra congelada). En cambio, OkHttp del
 * proceso de la app descarga los mismos archivos en milisegundos.
 *
 * Solución: `WebViewClient.shouldInterceptRequest` sirve los recursos del
 * host de Party (documento /host y assets) descargándolos con OkHttp, con una
 * caché en memoria para los archivos con nombre estable. Cualquier otra URL
 * (websockets socket.io, APIs, rangos de audio) pasa por Chromium.
 */
object PartyNetStack {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(val data: ByteArray, val mime: String, val encoding: String?)

    // Caché LRU simple (máx ~64 MB en entradas de assets).
    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        @Synchronized
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            var total = 0L
            for (e in values) total += e.data.size
            return total > 64L * 1024 * 1024
        }
    }

    private val lock = Any()

    private fun mimeFor(path: String): String = when {
        path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".wasm") -> "application/wasm"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".m4a") -> "audio/mp4"
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".ogg") -> "audio/ogg"
        path.endsWith(".wav") -> "audio/wav"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".ttf") -> "font/ttf"
        else -> "application/octet-stream"
    }

    private fun isText(mime: String): Boolean = mime.startsWith("text/") || mime == "application/json" || mime == "text/javascript" || mime == "image/svg+xml"

    /**
     * ¿Deberíamos interceptar esta URL? Solo el host de Party servido por el
     * documental y sus assets estáticos. Nunca websockets, APIs ni rangos.
     */
    fun shouldIntercept(hostBase: String, requestUrl: String, method: String, headers: Map<String, String>): Boolean {
        if (method.equals("GET", ignoreCase = true).not()) return false
        val base = hostBase.trimEnd('/')
        if (!requestUrl.startsWith(base)) return false
        val path = requestUrl.substring(base.length).removePrefix("/")
        val cleanPath = path.substringBefore('?')
        if (cleanPath.isEmpty() || cleanPath == "host" || cleanPath.startsWith("assets/") ||
            cleanPath.startsWith("party-audio/") || cleanPath.startsWith("party-covers/") ||
            cleanPath.startsWith("covers/") || cleanPath.startsWith("fonts/")
        ) {
            return true
        }
        return false
    }

    // Parche de medidas del documento /host antes de que arranque Phaser:
    // en WebViews viejos la cadena html/body/#app height:100% puede
    // resolverse a 0 y el Scale.RESIZE de Phaser deja el canvas en 0 de
    // altura (pantalla blanca con el shell encima). Forzamos alturas en px
    // en el inicio (script clásico, antes de los módulos diferidos) y en
    // resize.
    private fun patchDocument(bytes: ByteArray): ByteArray {
        val html = String(bytes, Charsets.UTF_8)
        val marker = "<body>"
        if (!html.contains(marker)) return bytes
        val patch = "<body>" +
            "<script>(function(){function f(){try{" +
            "var a=document.getElementById('app');" +
            "if(a){a.style.height=window.innerHeight+'px';a.style.maxHeight=window.innerHeight+'px';" +
            "a.style.minHeight='100%';}" +
            "document.body.style.minHeight=window.innerHeight+'px';" +
            "}catch(e){}}" +
            "window.addEventListener('resize',f);" +
            "if(document.readyState==='loading'){" +
            "document.addEventListener('DOMContentLoaded',f);" +
            "}else{f();}" +
            "requestAnimationFrame(f);})();</script>"
        return html.replace(marker, patch, ignoreCase = true).toByteArray(Charsets.UTF_8)
    }

    /**
     * Descarga con OkHttp (y caché de assets). Devuelve null si no se pudo
     * (el WebView decide el fallback/error).
     */
    @android.annotation.SuppressLint("NewApi")
    fun fetch(hostBase: String, requestUrl: String, rangeHeader: String? = null): WebResourceResponse? {
        val base = hostBase.trimEnd('/')
        val path = requestUrl.substring(base.length).removePrefix("/").substringBefore('?')
        val isDocument = path.isEmpty() || path == "host"
        val cacheKey = if (!isDocument) requestUrl.substringBefore('?') else null
        fun respond(bytes: ByteArray, mime: String, encoding: String?): WebResourceResponse {
            // Petición con Range (audio del host): se sirve el rango pedido
            // desde el archivo completo descargado por OkHttp. Servir rango
            // completo con 200 rompe algunos <audio>; mejor 206 con slice.
            if (rangeHeader != null) {
                val m = Regex("""bytes=(\d*)-(\d*)""").find(rangeHeader)
                val start = m?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val end = (m?.groupValues?.get(2)?.toLongOrNull() ?: (bytes.size - 1).toLong())
                    .coerceAtMost((bytes.size - 1).toLong())
                val slice = bytes.copyOfRange(start.toInt(), (end + 1).toInt())
                return WebResourceResponse(
                    mime,
                    null,
                    206, "Partial Content",
                    mapOf(
                        "Content-Range" to "bytes $start-$end/${bytes.size}",
                        "Accept-Ranges" to "bytes",
                        "Content-Length" to slice.size.toString(),
                    ),
                    ByteArrayInputStream(slice),
                )
            }
            return WebResourceResponse(mime, encoding, 200, "OK", emptyMap(), ByteArrayInputStream(bytes))
        }
        if (cacheKey != null) {
            synchronized(lock) {
                cache[cacheKey]?.let { hit ->
                    return respond(hit.data, hit.mime, hit.encoding)
                }
            }
        }
        val t0 = System.currentTimeMillis()
        return try {
            val req = Request.Builder()
                .url(requestUrl)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val bytes = body.byteStream().readBytes()
                var mime = body.contentType()?.type?.let { mimeFor(path) } ?: mimeFor(path)
                if (mime == "application/octet-stream") {
                    mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: mime
                }
                val encoding = if (isText(mime)) "utf-8" else null
                var payload = bytes
                if (isDocument) {
                    payload = patchDocument(payload)
                }
                if (cacheKey != null) {
                    synchronized(lock) { cache[cacheKey] = CacheEntry(payload, mime, encoding) }
                }
                android.util.Log.i(
                    "TVWEB",
                    "NETSTACK ${resp.code} $path ${bytes.size}b ${System.currentTimeMillis() - t0}ms",
                )
                respond(payload, mime, encoding)
            }
        } catch (e: Exception) {
            android.util.Log.w("TVWEB", "NETSTACK FAIL $path ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
