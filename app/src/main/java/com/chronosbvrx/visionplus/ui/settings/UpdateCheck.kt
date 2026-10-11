package com.chronosbvrx.visionplus.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chronosbvrx.visionplus.data.AppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val CHECK_TITLE = "Buscar actualizaciones"
internal const val UP_TO_DATE = "Ya tienes la versión más reciente"
internal const val CHECK_FAILED = "No pude consultar las actualizaciones"

/** "Buscar actualizaciones": asks the one source's repository for its newest release. */
@Stable
class AppUpdateChecker internal constructor(private val context: Context, private val scope: CoroutineScope) {
    var checking by mutableStateOf(false)
        private set

    /** La release disponible cuando hay una nueva: las pantallas abren su diálogo integrado. */
    var available by mutableStateOf<com.chronosbvrx.visionplus.data.Release?>(null)

    fun run() {
        if (checking) return
        checking = true
        scope.launch {
            val release = AppUpdate.check(context, force = true)
            checking = false
            // Limpia disponible si no hay actualización real (evita bucle con debug APK)
            if (release != null && !AppUpdate.pending(context)) {
                available = null
            } else {
                available = release
            }
            val toast = when {
                release == null -> CHECK_FAILED
                !AppUpdate.pending(context) -> UP_TO_DATE
                else -> "Hay la ${release.version} disponible"
            }
            Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun rememberUpdateCheck(): AppUpdateChecker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { AppUpdateChecker(context, scope) }
}

/**
 * The in-app updater: it downloads the new release with live progress and hands it to the system
 * installer on top of Vision+ — nobody has to leave the app or open a browser.
 */
@Stable
class InAppUpdate internal constructor(private val context: Context, private val scope: CoroutineScope) {
    var release by mutableStateOf<com.chronosbvrx.visionplus.data.Release?>(null)
        private set
    var progress by mutableStateOf(-1)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val running: Boolean get() = progress >= 0

    fun start(target: com.chronosbvrx.visionplus.data.Release) {
        if (running) return
        release = target
        error = null
        progress = 0
        scope.launch {
            val file = AppUpdate.download(context, target) { percent -> progress = percent }
            if (file == null) {
                error = "No se pudo descargar la actualización"
                progress = -1
                return@launch
            }
            progress = 101
            if (!AppUpdate.installDownloaded(context, file)) {
                error = "No se pudo abrir el instalador"
                progress = -1
            }
        }
    }
}

@Composable
fun rememberInAppUpdate(): InAppUpdate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { InAppUpdate(context, scope) }
}
