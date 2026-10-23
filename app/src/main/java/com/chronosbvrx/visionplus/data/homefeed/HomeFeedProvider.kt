package com.chronosbvrx.visionplus.data.homefeed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.chronosbvrx.visionplus.home.contract.HomeContract

/**
 * El canal por el que Vision+ Home lee el Inicio.
 *
 * Solo lectura y solo firma: `readPermission` es de nivel `signature`, así que ninguna app
 * ajena puede leer el historial ni el perfil del equipo. Devuelve una sola fila con el JSON
 * del feed; el trabajo pesado lo hace [HomeFeedStore] fuera del hilo de la UI.
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
        val json = if (context == null) {
            """{"meta":{"version":${HomeContract.VERSION}},"hero":[],"continue":[],"live":[],"rows":[]}"""
        } else {
            runCatching { HomeFeedStore.readOrBuild(context) }
                .getOrElse { """{"meta":{"version":${HomeContract.VERSION}},"hero":[],"continue":[],"live":[],"rows":[]}""" }
        }
        return MatrixCursor(arrayOf(HomeContract.COLUMN)).apply { addRow(arrayOf<Any>(json)) }
    }

    override fun getType(uri: Uri): String = "application/vnd.visionplus.homefeed+json"

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
