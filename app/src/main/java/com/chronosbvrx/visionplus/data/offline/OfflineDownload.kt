package com.chronosbvrx.visionplus.data.offline

import com.chronosbvrx.visionplus.data.Film
import org.json.JSONObject

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR;

    val isTerminal: Boolean get() = this == COMPLETED || this == ERROR
}

/** Mínimo de bytes reales para considerar válido un `.vpack` (anti-stubs). */
internal const val MIN_VALID_VAULT_BYTES = 16 * 1024L

/**
 * Representa una película o episodio descargado (o en proceso de descarga) en Vision+.
 */
data class OfflineDownload(
    val id: String,
    val contentId: String,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String = "",
    val videoUrl: String = "",
    val durationMs: Long = 0L,
    val quality: String = "1080p",
    val state: DownloadState = DownloadState.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val vaultFilePath: String = "",
    val storageLabel: String = "",
    val isRemovable: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isSeries: Boolean = false,
    val seriesId: String = "",
    val seasonIndex: Int = 0,
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val formattedSize: String
        get() = OfflineStoragePolicy.formatBytes(if (bytesDownloaded > 0) bytesDownloaded else totalBytes)

    val playUri: String
        get() = "vault:$id"

    /**
     * Convierte a objeto Film para reproducir sin fisuras en los reproductores existentes de Vision+.
     */
    fun toFilm(): Film {
        return Film(
            id = "vault:$id",
            title = if (subtitle.isNotBlank()) "$title · $subtitle" else title,
            year = 2026,
            durationMin = if (durationMs > 0) (durationMs / 60_000L).toInt() else 0,
            synopsis = "Descargado en $storageLabel · Disponible sin conexión",
            posterUrl = posterUrl,
            videoUrl = "vault:$id",
            sourceUrl = "vault",
            score = 10.0,
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("contentId", contentId)
            put("title", title)
            put("subtitle", subtitle)
            put("posterUrl", posterUrl)
            put("videoUrl", videoUrl)
            put("durationMs", durationMs)
            put("quality", quality)
            put("state", state.name)
            put("bytesDownloaded", bytesDownloaded)
            put("totalBytes", totalBytes)
            put("vaultFilePath", vaultFilePath)
            put("storageLabel", storageLabel)
            put("isRemovable", isRemovable)
            put("createdAtMs", createdAtMs)
            put("isSeries", isSeries)
            put("seriesId", seriesId)
            put("seasonIndex", seasonIndex)
            put("episodeIndex", episodeIndex)
            put("episodeName", episodeName)
            put("errorMessage", errorMessage.orEmpty())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): OfflineDownload {
            return OfflineDownload(
                id = json.optString("id"),
                contentId = json.optString("contentId"),
                title = json.optString("title"),
                subtitle = json.optString("subtitle"),
                posterUrl = json.optString("posterUrl"),
                videoUrl = json.optString("videoUrl"),
                durationMs = json.optLong("durationMs", 0L),
                quality = json.optString("quality", "1080p"),
                state = runCatching { DownloadState.valueOf(json.optString("state", "QUEUED")) }.getOrDefault(DownloadState.QUEUED),
                bytesDownloaded = json.optLong("bytesDownloaded", 0L),
                totalBytes = json.optLong("totalBytes", 0L),
                vaultFilePath = json.optString("vaultFilePath"),
                storageLabel = json.optString("storageLabel"),
                isRemovable = json.optBoolean("isRemovable", false),
                createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
                isSeries = json.optBoolean("isSeries", false),
                seriesId = json.optString("seriesId"),
                seasonIndex = json.optInt("seasonIndex", 0),
                episodeIndex = json.optInt("episodeIndex", 0),
                episodeName = json.optString("episodeName"),
                errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() },
            )
        }
    }
}
