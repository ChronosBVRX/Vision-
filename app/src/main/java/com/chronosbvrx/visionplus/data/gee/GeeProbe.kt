package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.sports.GeeSportsFallback
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * Comprueba de verdad si una señal responde AHORA.
 *
 * Una sola petición corta al manifiesto (HLS/DASH) con las cabeceras que el enlace trae tras `|`:
 * es lo mismo que hace el vigilante del servidor, pero en el equipo y al momento. Basta con que el
 * servidor conteste 2xx y entregue el primer trozo — no se descarga el video.
 *
 * Va con OkHttp (la misma pila que usa la casa): envía `Origin`/`Referer` sin recortes y sigue
 * redirecciones, así que el veredicto se parece al de la reproducción real.
 */
internal object GeeProbe {

    private const val TIMEOUT_MS = 3_500L
    private const val THREADS = 4
    private const val SAMPLE_BYTES = 512L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    private val pool = Executors.newFixedThreadPool(THREADS) { r ->
        Thread(r, "gee-ping").apply { isDaemon = true }
    }

    /** ¿Este enlace responde ahora? Nunca lanza: lo que truene sale caído. */
    fun pingOne(rawLink: String): Boolean {
        val parsed = GeeSportsFallback.parseStreamUrl(rawLink) ?: return false
        val request = runCatching {
            val builder = Request.Builder()
                .url(parsed.url)
                .get()
                .header("User-Agent", parsed.userAgent ?: "Mozilla/5.0")
                .header("Accept", "*/*")
            parsed.referrer?.let { builder.header("Referer", it) }
            parsed.origin?.let { builder.header("Origin", it) }
            builder.build()
        }.getOrNull() ?: return false
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                // Un 2xx sin cuerpo no es una señal: los manifiestos traen texto.
                val buffer = Buffer()
                response.body?.source()?.read(buffer, SAMPLE_BYTES)
                buffer.size > 0L
            }
        }.getOrDefault(false)
    }

    /**
     * Comprueba un lote de señales en paralelo (fuera del hilo principal) y devuelve el resultado
     * de cada una con su hora. Nunca lanza: una señal que truena simplemente sale caída.
     */
    suspend fun pingAll(rawLinks: List<String>, nowMs: Long = System.currentTimeMillis()): Map<String, GeeHealth.Check> =
        withContext(Dispatchers.IO) {
            val links = rawLinks.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (links.isEmpty()) return@withContext emptyMap()
            val tasks = links.map { link -> Callable { link to pingOne(link) } }
            val out = LinkedHashMap<String, GeeHealth.Check>(links.size)
            val budget = TIMEOUT_MS * 2 * THREADS + 1_000L
            for (future in pool.invokeAll(tasks, budget, TimeUnit.MILLISECONDS)) {
                val result = runCatching { future.get() }.getOrNull() ?: continue
                out[result.first] = GeeHealth.Check(alive = result.second, atMs = nowMs)
            }
            out
        }
}
