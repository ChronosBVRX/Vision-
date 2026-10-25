package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
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

    /** El intent con el que Android resuelve quién es el Inicio del equipo. */
    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * Quién abre el Inicio AHORA MISMO (paquete), resuelto como lo hace el sistema.
     * Es la única fuente fiable: el rol de `RoleManager` puede estar concedido a una app que no
     * es la que abre la tele (visto en Google TV).
     */
    fun currentHomePackage(): String? = runCatching {
        pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /** El nombre visible del Inicio actual («Google TV», «TCL», «Vision+ Home»…). */
    fun currentHomeLabel(): String? {
        val pkg = currentHomePackage() ?: return null
        if (pkg == app.packageName) return "Vision+ Home"
        return runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    /** True si Vision+ Home es el launcher que abre la tele. */
    fun isVisionHomeDefault(): Boolean = isOwnHome(currentHomePackage(), app.packageName)

    /**
     * La puerta MANUAL: la primera pantalla del firmware que SÍ exista para elegir Inicio.
     *
     * Importante: `startActivity` «funciona» con intents que ningún componente atiende en estas
     * teles (abren y cierran en el acto, o caen en un stub vacío), así que cada ruta se comprueba
     * antes con `resolveActivity`. Si el firmware no trae selector, se abren los datos del
     * launcher que manda: ahí vive «Borrar predeterminados».
     */
    fun openHomeSettings(): HomeRoleAction {
        if (openIfHandled(Intent(Settings.ACTION_HOME_SETTINGS))) return HomeRoleAction.OPEN_SETTINGS
        if (openIfHandled(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
            return HomeRoleAction.OPEN_SETTINGS
        }
        if (openCurrentLauncherDetails()) return HomeRoleAction.OPEN_CURRENT_LAUNCHER
        open(Intent(Settings.ACTION_SETTINGS))
        return HomeRoleAction.OPEN_CURRENT_LAUNCHER
    }

    /** Los datos de la app que hoy es el Inicio: «Borrar predeterminados» está ahí. */
    fun openCurrentLauncherDetails(): Boolean {
        val pkg = currentHomePackage() ?: return false
        if (pkg == app.packageName) return false
        return open(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)),
        )
    }

    /**
     * La acción INTUITIVA: pide el Inicio al sistema por la vía que ese equipo entienda.
     * Devuelve la puerta que realmente se abrió para que la interfaz explique el paso siguiente.
     */
    fun requestHomeRole(): HomeRoleAction {
        val action = planHomeChange()
        return when (action) {
            HomeRoleAction.REQUEST_ROLE -> {
                val intent = HomeRoleApi29.requestIntent(app)
                if (intent != null && open(intent)) HomeRoleAction.REQUEST_ROLE else openHomeSettings()
            }
            // Ya es el Inicio: esta puerta deja cambiar al otro sin buscar en Ajustes.
            HomeRoleAction.ALREADY_DEFAULT -> openHomeSettings()
            HomeRoleAction.OPEN_SETTINGS -> openHomeSettings()
            HomeRoleAction.OPEN_CURRENT_LAUNCHER -> {
                if (openCurrentLauncherDetails()) {
                    HomeRoleAction.OPEN_CURRENT_LAUNCHER
                } else {
                    open(Intent(Settings.ACTION_SETTINGS))
                    HomeRoleAction.OPEN_CURRENT_LAUNCHER
                }
            }
        }
    }

    /** Decide la puerta del Inicio SIN abrir nada (la interfaz la usa para guiar antes de salir). */
    fun planHomeChange(): HomeRoleAction {
        val sdk = Build.VERSION.SDK_INT
        return homeRoleAction(
            sdk = sdk,
            roleAvailable = sdk >= 29 && HomeRoleApi29.isAvailable(app),
            roleHeld = sdk >= 29 && HomeRoleApi29.isHeld(app),
            isDefault = isVisionHomeDefault(),
            handlesHomeSettings = handles(Intent(Settings.ACTION_HOME_SETTINGS)),
            handlesDefaultApps = handles(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        )
    }

    /** True si algún componente del equipo atiende ese intent (y no es un stub vacío). */
    private fun handles(intent: Intent): Boolean = runCatching {
        val info = pm.resolveActivity(intent, 0) ?: return@runCatching false
        // Google TV deja pantallas «EmptyStubActivity» que no hacen nada: no cuentan.
        !info.activityInfo.name.endsWith("EmptyStubActivity")
    }.getOrDefault(false)

    private fun openIfHandled(intent: Intent): Boolean =
        if (handles(intent)) open(intent) else false

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
