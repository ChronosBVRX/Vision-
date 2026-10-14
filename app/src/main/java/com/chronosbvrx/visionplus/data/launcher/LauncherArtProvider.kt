package com.chronosbvrx.visionplus.data.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Sirve los pósters cacheados del launcher ([LauncherArt]) como `content://` de solo lectura.
 *
 * El launcher es otro proceso (y en Google TV, otra app de sistema): para poder pintar la
 * tarjeta necesita abrir el URI, por eso el provider es `exported` y expone **únicamente**
 * archivos con nombre válido dentro de su carpeta — nada del resto del sandbox.
 */
class LauncherArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = fileFor(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        })
        return cursor
    }

    override fun getType(uri: Uri): String? = when (fileFor(uri)?.extension?.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "image/*"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw FileNotFoundException("Solo lectura: $uri")
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("LauncherArtProvider es de solo lectura")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("LauncherArtProvider es de solo lectura")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("LauncherArtProvider es de solo lectura")

    /** Solo nombres `sha1.ext` dentro de la carpeta de launcher: nada de rutas coladas. */
    private fun fileFor(uri: Uri): File? {
        val context = context ?: return null
        if (uri.authority != LauncherArt.authority(context)) return null
        val name = uri.lastPathSegment ?: return null
        if (!ART_NAME.matches(name)) return null
        val file = File(LauncherArt.dir(context), name)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    private companion object {
        val ART_NAME = Regex("^[0-9a-f]{40}\\.(jpg|png|webp)$")
    }
}
