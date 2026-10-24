package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.chronosbvrx.visionplus.home.contract.HomeContract
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.HomeFeedCodec
import com.chronosbvrx.visionplus.home.contract.FeedItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El feed del Inicio, con la regla que hace que el launcher se sienta instantáneo:
 *
 * 1. Primero se pinta la **caché propia** (disco del launcher).
 * 2. Después se le pregunta a Vision+ por el provider, con tope de 1.5 s.
 * 3. Si Vision+ no está o no contesta, se conserva lo pintado.
 */
class FeedRepository(context: Context) {
    private val app = context.applicationContext
    private val cacheFile = File(app.filesDir, "home_feed.json")

    /** Lo último bueno que se pintó (aunque el proveedor esté arrancando en frío). */
    fun cached(): HomeFeed? = HomeFeedCodec.decode(
        runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull(),
    )

    /** Busca en el catálogo completo a través de Vision+ (mismo buscador del servidor). */
    suspend fun search(query: String): List<FeedItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val uri = Uri.parse(HomeContract.SEARCH_URI).buildUpon()
            .appendQueryParameter("q", clean)
            .build()
        val raw = withTimeoutOrNull(6_000L) {
            runCatching {
                app.contentResolver.query(
                    uri,
                    arrayOf(HomeContract.COLUMN),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
        HomeFeedCodec.decode(raw)?.rows?.firstOrNull()?.items.orEmpty()
    }

    /** Consulta el provider de Vision+. Null = no se pudo leer (sin permiso, sin app, tarde). */
    suspend fun refresh(): HomeFeed? = withContext(Dispatchers.IO) {
        val raw = withTimeoutOrNull(1_500L) {
            runCatching {
                app.contentResolver.query(
                    Uri.parse(HomeContract.FEED_URI),
                    arrayOf(HomeContract.COLUMN),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
        val feed = HomeFeedCodec.decode(raw) ?: return@withContext null
        runCatching { cacheFile.writeText(raw!!) }
        feed
    }

    /** Vision+ avisa cuando cambia el historial o el perfil: el launcher se refresca solo. */
    fun observe(onChange: () -> Unit): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange()
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                Uri.parse(HomeContract.FEED_URI),
                true,
                observer,
            )
        }
        return observer
    }

    fun stopObserving(observer: ContentObserver) {
        runCatching { app.contentResolver.unregisterContentObserver(observer) }
    }
}
