package com.chronosbvrx.visionplus.ui.games

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes

/** Short, local narration clips for the Juegos section. */
internal fun playPartyAudio(
    context: Context,
    @RawRes resource: Int,
    volume: Float = 0.82f,
): MediaPlayer? {
    val player = MediaPlayer()
    return try {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        context.resources.openRawResourceFd(resource).use { fd ->
            player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        player.setVolume(volume, volume)
        player.prepare()
        player.start()
        player
    } catch (_: Exception) {
        runCatching { player.release() }
        null
    }
}

internal fun stopPartyAudio(player: MediaPlayer?) {
    if (player == null) return
    runCatching { player.stop() }
    runCatching { player.release() }
}
