package com.chronosbvrx.visionplus.home.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.chronosbvrx.visionplus.home.contract.HomeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Una app instalada, ya lista para pintar y para gestionar.
 *
 * `canUninstall` no es un capricho: Android no deja quitar las apps de sistema que nunca se
 * actualizaron, y el menú tiene que decir la verdad antes de abrir el desinstalador.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null,
    val isSystem: Boolean = false,
    val canUninstall: Boolean = false,
    val versionName: String = "",
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

    /** ¿El sistema dejaría quitarla? Las de fábrica sin actualizar no se pueden desinstalar. */
    fun canUninstall(packageName: String): Boolean = runCatching {
        val flags = pm.getApplicationInfo(packageName, 0).flags
        uninstallable(
            isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
            updatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
        )
    }.getOrDefault(false)

    /**
     * Abre el desinstalador del sistema. Se lanza el intent DIRECTAMENTE (la visibilidad de
     * paquetes de Android 11+ puede ocultar al instalador en una consulta, pero no impide
     * abrirlo). En teles sin esa pantalla cae a la ficha de la app, donde vive igual.
     */
    fun requestUninstall(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == app.packageName) return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (open(intent)) return true
        return openAppInfo(packageName)
    }

    /** La ficha del sistema: permisos, almacenamiento, «Forzar detención» y desinstalar. */
    fun openAppInfo(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        if (open(details)) return true
        if (open(Intent(Settings.ACTION_APPLICATION_SETTINGS))) return true
        return open(Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Cierra una app que quedó abierta en segundo plano (el launcher está al frente, así que la
     * otra app por definición lo está). Es la utilidad clásica de un launcher de tele.
     */
    fun stopApp(packageName: String): Boolean = runCatching {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
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

    fun openDisplaySettings(): Boolean = open(Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun openSoundSettings(): Boolean = open(Intent(Settings.ACTION_SOUND_SETTINGS))

    fun openDateTimeSettings(): Boolean = open(Intent(Settings.ACTION_DATE_SETTINGS))

    fun openAboutSettings(): Boolean = open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    /** Almacenamiento: la pantalla exacta si el firmware la tiene; si no, «Acerca de». */
    fun openStorageSettings(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        ) {
            return true
        }
        if (open(Intent(Settings.ACTION_MEMORY_CARD_SETTINGS))) return true
        return open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
    }

    /** La lista de apps del sistema: sirve para desinstalar lo que este launcher no lista. */
    fun openAppListSettings(): Boolean = open(Intent(Settings.ACTION_APPLICATION_SETTINGS))

    /** Idioma y región del equipo (el launcher no traduce el sistema por su cuenta). */
    fun openLanguageSettings(): Boolean = open(Intent(Settings.ACTION_LOCALE_SETTINGS))

    /** Accesibilidad: subtítulos del sistema, lector de pantalla, pulsación prolongada. */
    fun openAccessibilitySettings(): Boolean = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Protector de pantalla (Daydream) del televisor. */
    fun openScreensaverSettings(): Boolean = open(Intent(Settings.ACTION_DREAM_SETTINGS))

    /** Apps predeterminadas: navegador, asistente, Inicio… */
    fun openDefaultAppsSettings(): Boolean = open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

    /** «Wi-Fi» o «Ethernet»: lo que de verdad está conectado ahora, sin adivinar. */
    fun networkLabel(): String = runCatching {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching "Sin red"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Conectada"
        }
    }.getOrDefault("Sin red")

    /** «TCL BeyondTV · Android 9» para la cabecera de Sistema. */
    fun deviceLabel(): String = buildString {
        val maker = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        if (maker.isNotBlank() && !model.startsWith(maker, ignoreCase = true)) {
            append(maker).append(' ')
        }
        append(model.ifBlank { "Televisor" })
        append(" · Android ").append(Build.VERSION.RELEASE)
    }

    /** Espacio libre del equipo, para que el launcher diga algo útil en Sistema. */
    fun storageLabel(): String = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L
        val total = stat.totalBytes / gb
        val free = stat.availableBytes
        // «0 GB libres» no dice nada: por debajo de un giga se habla en MB.
        val freeText = if (free < gb) "${free / mb} MB" else "${free / gb} GB"
        "$freeText libres de $total GB"
    }.getOrDefault("")

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
                val appInfo = info.activityInfo.applicationInfo
                val flags = appInfo?.flags ?: 0
                val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
                val updatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                out += AppEntry(
                    packageName = pkg,
                    label = label,
                    icon = iconOf(info),
                    isSystem = system,
                    canUninstall = uninstallable(
                        isSystem = system,
                        updatedSystem = updatedSystem,
                    ),
                    versionName = runCatching { pm.getPackageInfo(pkg, 0).versionName }
                        .getOrNull()
                        .orEmpty(),
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

/**
 * La regla real de Android para el botón «Desinstalar»: una app de fábrica que nunca se
 * actualizó no se puede quitar; una de fábrica actualizada solo pierde la actualización.
 * No se usa `FLAG_INSTALLED`: en varias versiones no viene en la consulta del launcher y hacía
 * desaparecer «Desinstalar» en apps instaladas por el usuario (verificado en Android 16).
 */
internal fun uninstallable(
    isSystem: Boolean,
    updatedSystem: Boolean,
): Boolean = !isSystem || updatedSystem

/**
 * Busca apps instaladas por nombre (sin acentos ni mayúsculas) o por paquete. Es lo que hace
 * que el buscador del launcher también sirva para abrir «Netflix» escribiendo «net».
 */
internal fun filterApps(
    apps: List<AppEntry>,
    query: String,
    limit: Int = 6,
): List<AppEntry> {
    val folded = foldSearch(query)
    val compact = folded.filter { it.isLetterOrDigit() }
    if (folded.isBlank()) return emptyList()
    return apps.asSequence()
        .map { app ->
            val label = foldSearch(app.label)
            val labelCompact = label.filter { it.isLetterOrDigit() }
            val hitsLabel = label.contains(folded) || labelCompact.contains(compact)
            val hitsPackage = app.packageName.contains(folded.replace(" ", "."))
            app to (hitsLabel || hitsPackage)
        }
        .filter { it.second }
        .sortedWith(
            compareByDescending<Pair<AppEntry, Boolean>> {
                foldSearch(it.first.label).startsWith(folded)
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label },
        )
        .map { it.first }
        .take(limit.coerceAtLeast(1))
        .toList()
}

/** Minúsculas, sin acentos y sin espacios repetidos: la misma forma para consulta y nombre. */
internal fun foldSearch(text: String): String {
    val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{M}+"), "")
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
}
