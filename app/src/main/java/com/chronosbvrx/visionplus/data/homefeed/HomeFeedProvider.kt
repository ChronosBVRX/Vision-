package com.chronosbvrx.visionplus.data.homefeed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.chronosbvrx.visionplus.home.contract.FeedMeta
import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.HomeContract
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.HomeFeedCodec
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El canal por el que Vision+ Home lee el Inicio y su buscador.
 *
 * Solo lectura y solo firma: `readPermission` es de nivel `signature`, así que ninguna app
 * ajena puede leer el historial ni el perfil del equipo. Devuelve una sola fila con el JSON;
 * el trabajo pesado lo hace [HomeFeedStore] fuera del hilo de la UI.
 *
 * Dos rutas:
 * - `content://…/feed`  → el Inicio completo (caché en disco, instantáneo).
 * - `content://…/search?q=…` → resultados del MISMO buscador del servidor que usa la app.
 */
class HomeFeedProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = context
        if (uri.pathSegments.firstOrNull() == "search") {
            val query = uri.getQueryParameter("q").orEmpty()
            val items = if (context == null || query.isBlank()) {
                emptyList()
            } else {
                runBlocking {
                    withTimeoutOrNull(5_000L) { HomeFeedStore.searchItems(context, query) }
                }.orEmpty()
            }
            val feed = HomeFeed(
                meta = FeedMeta(
                    version = HomeContract.VERSION,
                    generatedAt = System.currentTimeMillis(),
                ),
                rows = listOf(FeedRow(id = "search", label = "Resultados", items = items)),
            )
            return oneRow(HomeFeedCodec.encode(feed))
        }

        val json = if (context == null) {
            emptyFeedJson()
        } else {
            // El launcher es quien pide el feed: aprovechar para refrescar la guía y la agenda
            // en segundo plano (con freno propio), sin bloquear esta respuesta.
            runCatching { HomeFeedStore.refreshAsync(context) }
            runCatching { HomeFeedStore.readOrBuild(context) }.getOrElse { emptyFeedJson() }
        }
        return oneRow(json)
    }

    private fun oneRow(json: String): Cursor =
        MatrixCursor(arrayOf(HomeContract.COLUMN)).apply { addRow(arrayOf<Any>(json)) }

    private fun emptyFeedJson(): String =
        """{"meta":{"version":${HomeContract.VERSION}},"hero":[],"continue":[],"live":[],"rows":[],"sports":[]}"""

    override fun getType(uri: Uri): String =
        if (uri.pathSegments.firstOrNull() == "search") {
            "application/vnd.visionplus.search+json"
        } else {
            "application/vnd.visionplus.homefeed+json"
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("El feed es de solo lectura")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("El feed es de solo lectura")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("El feed es de solo lectura")
}
