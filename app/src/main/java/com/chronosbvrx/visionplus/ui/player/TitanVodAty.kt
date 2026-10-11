package com.chronosbvrx.visionplus.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.chronosbvrx.visionplus.data.Film

/**
 * Debug entry for the portal VOD player: opens it straight for one content id.
 * `adb shell am start -n com.chronosbvrx.visionplus/.ui.player.TitanVodAty --es content <id> --es title <name>`
 */
class TitanVodAty : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contentId = intent.getStringExtra("content").orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { contentId }
        if (contentId.isBlank()) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                TitanVodPlayerScreen(
                    film = Film(
                        id = contentId,
                        title = title,
                        year = 0,
                        durationMin = 0,
                        synopsis = "",
                        posterUrl = "",
                        videoUrl = "magisvod:$contentId",
                        sourceUrl = "visionplus.app",
                    ),
                    onBack = { finish() },
                )
            }
        }
    }
}
