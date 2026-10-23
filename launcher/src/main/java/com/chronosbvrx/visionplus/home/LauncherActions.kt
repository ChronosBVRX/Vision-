package com.chronosbvrx.visionplus.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.chronosbvrx.visionplus.home.contract.HomeContract

/** Las salidas del launcher: todo se resuelve con intents, nada de lógica de streaming aquí. */
object LauncherActions {

    /** Dispara un deep link de Vision+ (`visionplus://…`). */
    fun openDeepLink(context: Context, link: String): Boolean {
        if (link.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setPackage(HomeContract.APP_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
