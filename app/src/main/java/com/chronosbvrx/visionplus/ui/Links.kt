package com.chronosbvrx.visionplus.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** The project's public home. */
const val PROJECT_URL = "https://github.com/ChronosBVRX/Vision-"

/** What a placeholder button says instead of doing the real work. */
const val COMING_SOON = "Función disponible próximamente en Vision+"

/** Shows [COMING_SOON]: the button exists so the screen keeps its shape while the feature lands. */
fun comingSoon(context: Context) {
    Toast.makeText(context, COMING_SOON, Toast.LENGTH_LONG).show()
}

/** Opens [url] in whatever app handles it; on a device with no browser it says where to go instead. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
