package com.chronosbvrx.visionplus.data.launcher

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.chronosbvrx.visionplus.DeviceKind
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Publica «Seguir viendo» en el canal Watch Next del launcher de TV (en Google TV se ve como
 * «Continuar viendo» / «Play Next») y retira lo que ya no está en el historial.
 *
 * Cada tarjeta lleva el póster (local vía [LauncherArt], con la URL remota de respaldo) y un
 * `intent_uri` (`visionplus://watch/<clave>`) que el launcher dispara al seleccionarla; la app
 * lo recibe en MainActivity y reanuda la película o el capítulo exacto.
 *
 * Solo escribe en dispositivos de TV: en un teléfono el canal no existe. Todas las llamadas
 * al provider van envueltas: si el dispositivo no trae TV provider, esto queda en silencio.
 */
object LauncherWatchNext {
    private const val TAG = "LauncherWatchNext"

    /** Freno entre sincronizaciones (la app la llama al volver a cada pantalla). */
    private const val SYNC_MIN_INTERVAL_MS = 15_000L

    private const val DISMISSED_FILE = "launcher_watch_next_dismissed.txt"

    /** La línea que acompaña a la tarjeta; la cola la pinta el launcher. */
    private const val DESCRIPTION = "Vision+ · Continuar viendo"

    @Volatile
    private var lastSyncAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Dispara una sincronización sin bloquear a quien llama (UI, reproductor, receiver). */
    fun syncAsync(context: Context, delayMs: Long = 0L, force: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            runCatching { sync(app, force) }.onFailure { Log.w(TAG, "sync: ${it.message}") }
        }
    }

    suspend fun sync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        syncInternal(app, force = force, deviceIsTv = DeviceKind.isTelevision(app))
    }

    /** Igual que [sync], con el chequeo de dispositivo explícito (pruebas). */
    internal suspend fun syncInternal(context: Context, force: Boolean, deviceIsTv: Boolean) {
        if (!deviceIsTv) return
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncAt < SYNC_MIN_INTERVAL_MS) return
        lastSyncAt = now
        mutex.withLock {
            // Del archivo, no de la copia en memoria: el minuto lo escribe el proceso `:x4k`.
            val desired = filterDismissed(
                watchNextSpecs(freshHistoryEntries(app)),
                readDismissed(dismissedFile(app)),
            )
            val plan = reconcileWatchNext(desired, queryExisting(app))
            if (plan.inserts.isEmpty() && plan.updates.isEmpty() && plan.deletes.isEmpty()) return@withLock
            val resolver = app.contentResolver
            plan.deletes.forEach { rowId ->
                runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(rowId), null, null) }
                    .onFailure { Log.w(TAG, "delete $rowId: ${it.message}") }
            }
            plan.updates.forEach { (rowId, spec) ->
                runCatching {
                    resolver.update(TvContractCompat.buildWatchNextProgramUri(rowId), valuesFor(app, spec), null, null)
                }.onFailure { Log.w(TAG, "update $rowId: ${it.message}") }
            }
            plan.inserts.forEach { spec ->
                runCatching { resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, valuesFor(app, spec)) }
                    .onFailure { Log.w(TAG, "insert: ${it.message}") }
            }
            Log.i(
                TAG,
                "canal Watch Next: ${plan.inserts.size} nuevas, ${plan.updates.size} al día, ${plan.deletes.size} fuera",
            )
        }
    }

    /** El usuario descartó la tarjeta («No me interesa»): fuera del canal y anotada. */
    suspend fun onProgramDisabled(context: Context, programId: Long) {
        val app = context.applicationContext
        val rowUri = TvContractCompat.buildWatchNextProgramUri(programId)
        var key: String? = null
        runCatching {
            app.contentResolver.query(rowUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext()) key = WatchNextProgram.fromCursor(cursor).internalProviderId
            }
        }
        key?.takeIf { it.isNotBlank() }?.let { appendDismissed(dismissedFile(app), it, System.currentTimeMillis()) }
        runCatching { app.contentResolver.delete(rowUri, null, null) }
    }

    private fun queryExisting(context: Context): List<ExistingWatchNext> {
        val out = ArrayList<ExistingWatchNext>()
        runCatching {
            context.contentResolver
                .query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val program = WatchNextProgram.fromCursor(cursor)
                        val key = program.internalProviderId ?: continue
                        out += ExistingWatchNext(
                            rowId = program.id,
                            key = key,
                            positionMs = program.lastPlaybackPositionMillis.toLong(),
                            durationMs = program.durationMillis.toLong(),
                            updatedAt = program.lastEngagementTimeUtcMillis,
                        )
                    }
                }
        }.onFailure { Log.w(TAG, "query: ${it.message}") }
        return out
    }

    private suspend fun valuesFor(context: Context, spec: WatchNextSpec): ContentValues =
        buildValues(context, spec, artUriFor(context, spec))

    /** Los valores de la tarjeta en el canal (visible para las pruebas). */
    internal fun buildValues(context: Context, spec: WatchNextSpec, artUri: Uri?): ContentValues {
        val builder = WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(spec.updatedAt)
            .setType(
                if (spec.isEpisode) {
                    TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
                } else {
                    TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
                },
            )
            .setInternalProviderId(spec.key)
            .setContentId(spec.key)
            .setTitle(spec.title)
            .setDescription(DESCRIPTION)
            .setLastPlaybackPositionMillis(millisToInt(spec.positionMs))
            .setDurationMillis(millisToInt(spec.durationMs))
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            .setIntentUri(intentUriFor(context, spec))
        artUri?.let { builder.setPosterArtUri(it) }
        if (spec.isEpisode) {
            builder.setEpisodeTitle(spec.episodeTitle.ifBlank { "Episodio ${spec.episodeNumber}" })
            if (spec.episodeNumber > 0) builder.setEpisodeNumber(spec.episodeNumber)
            if (spec.seasonNumber > 0) builder.setSeasonNumber(spec.seasonNumber)
            if (spec.seriesId.isNotBlank()) builder.setSeriesId(spec.seriesId)
        }
        return builder.build().toContentValues()
    }

    /** El `intent_uri` de la tarjeta: al seleccionarla, el launcher abre este deep link. */
    private fun intentUriFor(context: Context, spec: WatchNextSpec): Uri {
        val data = LauncherLinks.uriFor(
            WatchLink(
                key = spec.key,
                title = spec.displayTitle,
                posterUrl = spec.posterUrl,
                code = spec.code,
                videoUrl = spec.videoUrl,
            ),
        )
        val intent = Intent(Intent.ACTION_VIEW, data)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }

    /** Póster local si se pudo cachear; si no, la URL remota (el esquema la admite). */
    private suspend fun artUriFor(context: Context, spec: WatchNextSpec): Uri? {
        val remote = spec.posterUrl.trim()
        if (remote.isEmpty()) return null
        LauncherArt.ensure(context, remote)?.let { return it }
        return runCatching { Uri.parse(remote) }.getOrNull()
            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
    }

    private fun millisToInt(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    internal fun dismissedFile(context: Context): File =
        File(context.applicationContext.filesDir, DISMISSED_FILE)
}

/** Pares clave→marca de tiempo de lo descartado en el launcher. */
internal fun readDismissed(file: File): Map<String, Long> {
    if (!file.exists()) return emptyMap()
    return runCatching {
        file.readLines().mapNotNull { parseDismissedLine(it) }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun parseDismissedLine(line: String): Pair<String, Long>? {
    val idx = line.lastIndexOf('\t')
    if (idx <= 0) return null
    val key = line.substring(0, idx).trim()
    val at = line.substring(idx + 1).trim().toLongOrNull() ?: return null
    return key.takeIf { it.isNotBlank() }?.let { it to at }
}

/** Anota un descarte (una línea por clave; el archivo se poda solo). */
internal fun appendDismissed(file: File, key: String, atMs: Long, maxLines: Int = 100) {
    runCatching {
        val clean = key.replace('\t', ' ').replace('\n', ' ')
        file.appendText("$clean\t$atMs\n")
        val lines = file.readLines()
        if (lines.size > maxLines) {
            file.writeText(lines.takeLast(maxLines).joinToString(separator = "\n", postfix = "\n"))
        }
    }
}
