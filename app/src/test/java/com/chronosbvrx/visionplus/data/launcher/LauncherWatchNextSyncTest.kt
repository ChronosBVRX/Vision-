package com.chronosbvrx.visionplus.data.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tvprovider.media.tv.TvContractCompat
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** Un TV provider de mentira (mismo authority que el real) para probar el canal completo. */
class FakeTvProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val id = uri.lastPathSegment?.toLongOrNull()
        val columns = projection ?: COLUMNS.toTypedArray()
        val cursor = MatrixCursor(columns)
        synchronized(rows) {
            rows.filter { id == null || it.getAsLong(BaseColumns._ID) == id }
                .forEach { row -> cursor.addRow(columns.map { row.get(it) }) }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val row = ContentValues(values ?: ContentValues())
        synchronized(rows) {
            val id = ++lastId
            row.put(BaseColumns._ID, id)
            rows += row
            return Uri.withAppendedPath(uri, id.toString())
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        synchronized(rows) {
            val row = rows.firstOrNull { it.getAsLong(BaseColumns._ID) == id } ?: return 0
            values?.let { row.putAll(it) }
            return 1
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        synchronized(rows) {
            val before = rows.size
            rows.removeAll { it.getAsLong(BaseColumns._ID) == id }
            return before - rows.size
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir"

    companion object {
        val rows = mutableListOf<ContentValues>()
        private var lastId = 0L

        val COLUMNS = listOf(
            BaseColumns._ID,
            TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID,
            TvContractCompat.PreviewProgramColumns.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContractCompat.PreviewProgramColumns.COLUMN_DURATION_MILLIS,
            TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI,
            TvContractCompat.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContractCompat.Programs.COLUMN_TITLE,
        )

        fun reset() {
            synchronized(rows) {
                rows.clear()
                lastId = 0
            }
        }

        fun byKey(key: String): ContentValues? = rows.firstOrNull {
            it.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID) == key
        }
    }
}

/**
 * El ciclo completo del canal Watch Next contra un proveedor falso: publicar el historial,
 * avanzar el minuto sin duplicar, retirar lo que ya no está y respetar el «No me interesa».
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LauncherWatchNextSyncTest {

    private lateinit var context: Context

    private val watchNextType: String get() = TvContractCompat.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE
    private val lastEngagement: String get() = TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
    private val lastPosition: String get() = TvContractCompat.PreviewProgramColumns.COLUMN_LAST_PLAYBACK_POSITION_MILLIS
    private val internalId: String get() = TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID
    private val intentUri: String get() = TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI
    private val titleColumn: String get() = TvContractCompat.Programs.COLUMN_TITLE

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PlaybackHistory.clear(context)
        FakeTvProvider.reset()
        LauncherWatchNext.dismissedFile(context).delete()
        Robolectric.setupContentProvider(FakeTvProvider::class.java, TvContractCompat.AUTHORITY)
    }

    private fun entry(
        key: String,
        title: String,
        positionMs: Long = 120_000L,
        updatedAt: Long,
        series: Boolean = false,
    ) = ResumeEntry(
        key = key, title = title, posterUrl = "", xtvCode = key,
        seriesId = if (series) "s-$key" else "",
        episodeIds = if (series) listOf("${key}1", "${key}2") else emptyList(),
        episodeIndex = if (series) 1 else 0,
        seasonIds = if (series) listOf("t1", "t2") else emptyList(),
        seasonNames = if (series) listOf("T1", "T2") else emptyList(),
        positionMs = positionMs, durationMs = 600_000, updatedAt = updatedAt,
    )

    private fun sync() = runBlocking {
        LauncherWatchNext.syncInternal(context, force = true, deviceIsTv = true)
    }

    private fun save(entry: ResumeEntry) {
        // `immediate` deja el commit en disco antes de sincronizar (como el onDestroy del player).
        PlaybackHistory.save(context, entry, immediate = true)
    }

    @Test
    fun publishesTheHistoryIntoTheChannel() {
        save(entry("a", "Peli A", updatedAt = BASE))
        save(entry("b", "Serie B · T1 · C2", updatedAt = BASE - 1_000, series = true))
        sync()
        assertEquals(2, FakeTvProvider.rows.size)
        val a = FakeTvProvider.byKey("a")!!
        assertEquals("Peli A", a.getAsString(titleColumn))
        assertEquals(BASE, a.getAsLong(lastEngagement))
        assertEquals(120_000, a.getAsInteger(lastPosition))
        assertEquals(
            TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
            a.getAsInteger(watchNextType),
        )
        assertTrue(a.getAsString(intentUri)!!.startsWith("intent:"))
        val b = FakeTvProvider.byKey("b")!!
        assertEquals("Serie B", b.getAsString(titleColumn))
    }

    @Test
    fun aNewerMinuteUpdatesTheSameCardWithoutDuplicating() {
        save(entry("a", "Peli A", updatedAt = BASE))
        sync()
        val rowId = FakeTvProvider.rows.single().getAsLong(BaseColumns._ID)
        save(entry("a", "Peli A", positionMs = 240_000, updatedAt = BASE + 5_000))
        sync()
        assertEquals(1, FakeTvProvider.rows.size)
        val a = FakeTvProvider.byKey("a")!!
        assertEquals(rowId, a.getAsLong(BaseColumns._ID))
        assertEquals(240_000, a.getAsInteger(lastPosition))
    }

    @Test
    fun finishedContentLeavesTheChannel() {
        save(entry("a", "Peli A", updatedAt = BASE))
        save(entry("b", "Peli B", updatedAt = BASE - 1))
        sync()
        assertEquals(2, FakeTvProvider.rows.size)
        PlaybackHistory.delete(context, "b")
        sync()
        assertEquals(1, FakeTvProvider.rows.size)
        assertNotNull(FakeTvProvider.byKey("a"))
        assertNull(FakeTvProvider.byKey("b"))
    }

    @Test
    fun aDismissedCardNeverComesBack() {
        save(entry("a", "Peli A", updatedAt = BASE))
        sync()
        val rowId = FakeTvProvider.rows.single().getAsLong(BaseColumns._ID)
        runBlocking { LauncherWatchNext.onProgramDisabled(context, rowId) }
        assertTrue(FakeTvProvider.rows.isEmpty())
        sync()
        assertTrue(FakeTvProvider.rows.isEmpty())
    }

    @Test
    fun aPhoneDoesNotTouchTheChannel() = runBlocking {
        save(entry("a", "Peli A", updatedAt = BASE))
        LauncherWatchNext.syncInternal(context, force = true, deviceIsTv = false)
        assertTrue(FakeTvProvider.rows.isEmpty())
    }

    private companion object {
        /** Reciente: el historial poda lo que pasa de 90 días. */
        val BASE = System.currentTimeMillis() - 60_000L
    }
}
