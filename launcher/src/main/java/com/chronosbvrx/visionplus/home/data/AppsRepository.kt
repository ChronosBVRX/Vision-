package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.chronosbvrx.visionplus.home.contract.HomeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una app instalada, ya lista para pintar. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null,
    val isSystem: Boolean = false,
)

/**
 * Las apps de la tele: se piden al sistema (solo las que se pueden abrir), se les saca el
 * icono una vez y se lanzan con su intent de Leanback cuando existe.
 *
 * Sin `QUERY_ALL_PACKAGES`: el manifest declara los dos intents de lanzador y Android nos da
 * exactamente eso, nada más.
 */
class AppsRepository(context: Context) {
    private val app = context.applicationContext
    private val pm: PackageManager = app.packageManager

    @Volatile
    private var cached: List<AppEntry>? = null

    suspend fun load(force: Boolean = false): List<AppEntry> = withContext(Dispatchers.IO) {
        cached?.takeIf { !force }?.let { return@withContext it }
        val out = query()
        cached = out
        out
    }

    fun launch(packageName: String): Boolean {
        val intent = launchIntent(packageName) ?: return false
        return runCatching { app.startActivity(intent) }.isSuccess
    }

    fun launchIntent(packageName: String): Intent? {
        if (packageName.isBlank()) return null
        val intent = runCatching { pm.getLeanbackLaunchIntentForPackage(packageName) }.getOrNull()
            ?: runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()
            ?: return null
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        pm.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    fun visionPlusInstalled(): Boolean = isInstalled(HomeContract.APP_PACKAGE)

    fun launchVisionPlus(): Boolean = launch(HomeContract.APP_PACKAGE)

    /** Ajustes de Android: la puerta de salida que un launcher nunca debe esconder. */
    fun openSystemSettings(): Boolean = open(Intent(Settings.ACTION_SETTINGS))

    /** El selector de Inicio del sistema (aquí se elige Vision+ Home como pantalla de inicio). */
    fun openHomeSettings(): Boolean =
        open(Intent(Settings.ACTION_HOME_SETTINGS)) || open(Intent(Settings.ACTION_SETTINGS))

    fun openWifiSettings(): Boolean = open(Intent(Settings.ACTION_WIFI_SETTINGS))

    private fun open(intent: Intent): Boolean = runCatching {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    // --- Consulta ---

    private fun query(): List<AppEntry> {
        val saw = HashSet<String>(96)
        val out = ArrayList<AppEntry>(64)
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )
        for (intent in intents) {
            val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == app.packageName) continue
                if (!saw.add(pkg)) continue
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg
                val system = (info.activityInfo.applicationInfo?.flags ?: 0) and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                out += AppEntry(
                    packageName = pkg,
                    label = label,
                    icon = iconOf(info),
                    isSystem = system,
                )
            }
        }
        return out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun iconOf(info: android.content.pm.ResolveInfo): ImageBitmap? = runCatching {
        val drawable: Drawable = info.loadIcon(pm)
        drawable.toBitmap(width = 128, height = 128).asImageBitmap()
    }.getOrNull()
}
