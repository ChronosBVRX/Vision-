package com.chronosbvrx.visionplus.data.debloat

/**
 * Paquetes que Vision+ no apaga nunca, diga lo que diga una lista. De más es mejor que de menos:
 * si un paquete está aquí simplemente se queda encendido.
 *
 * Portado de TidyTV / CleanYourTV (MIT). Se añadieron los paquetes de Vision+ y de su Inicio.
 */
internal object Protected {
    val EXACT: Set<String> = setOf(
        "android",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gsf.login",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.providers.settings",
        "com.android.providers.tv",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.android.externalstorage",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.bluetooth",
        "com.android.bluetoothmidiservice",
        "com.android.inputdevices",
        "com.android.webview",
        "com.google.android.webview",
        "com.android.location.fused",
        "com.google.android.ext.services",
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.wifi.resources",
        "com.android.provision",
        "com.android.keychain",
        "com.android.certinstaller",
        // Módulos del sistema: un Mi TV Stick original no volvió a arrancar tras apagarlos
        // (cleanyourtv #884).
        "com.android.ext.services",
        "com.android.ext.shared",
        "com.google.android.ext.shared",
        "com.android.modulemetadata",
        "com.google.android.modulemetadata",
        "com.android.phone",
        // Vision+ y su Inicio: son el objetivo del flujo, nunca parte de la limpieza.
        "com.chronosbvrx.visionplus",
        "com.chronosbvrx.visionplus.home",
    ) + Launchers.HOME_APPS.map { it.pkg } + Launchers.SETUPWRAITH

    /**
     * El asistente de voz y los ayudantes del mando. No están bloqueados (hay dueños que los
     * quieren fuera), pero una lista nunca los marca sola: usuarios perdieron el micrófono y
     * las teclas de Netflix/YouTube tras una limpieza.
     */
    val REMOTE_ESSENTIALS: Set<String> = setOf(
        "com.google.android.katniss",
        "com.google.android.tv.assistant",
        "com.google.android.tv.remote.service",
        "com.tcl.keyhelp",
        "com.tcl.micmanager",
    )

    /** Marcadores de entrada de TV, sintonizador, teclado y emparejamiento del mando. */
    private val PATTERNS = listOf(
        "tvinput", "tv.input", "tvcenter", "hdmi", "tuner", "tvsystem", "tv.framework", "tvserver",
        "inputmethod", ".ime", "leanback.ime", "keyboard",
        "bluetooth", "bt_rcu", "btrcu", "rcuservice", "autopair", "remotepair",
        "networkstack", "permissioncontroller", "packageinstaller",
        // Ajustes y servicios base del fabricante: apagarlos deja la tele sin forma de configurarse.
        "settings", "system.server", "providers.config",
        // Lápices USB y archivos: una tele cuyo dueño ya no podía abrir un USB.
        "documentsui", "android.mtp", "mtpservice", "usbstorage", "externalstorage",
        // Teclas del mando (YouTube, Ajustes, TV en vivo).
        "hotkey", "globalkey", "keyhandler", "keyservice", "keymanager", "keyreceiver", "remotekey",
    )

    /** Proveedores de Android, salvo los pocos de los que una tele puede prescindir. */
    private val SPARE_PROVIDERS = setOf("calendar", "contacts", "userdictionary", "blockednumber")

    private fun isCore(pkg: String) =
        pkg.startsWith("com.google.android.overlay.modules") ||
            (pkg.startsWith("com.android.providers.") &&
                pkg.removePrefix("com.android.providers.").substringBefore('.') !in SPARE_PROVIDERS)

    fun reason(pkg: String, live: Set<String> = emptySet(), listKeep: Set<String> = emptySet()): String? {
        val lower = pkg.lowercase()
        return when {
            pkg in EXACT || isCore(pkg) -> "needed by Android"
            pkg in live -> "in use by your TV right now"
            pkg in listKeep -> "the list for this TV says to keep it"
            PATTERNS.any { lower.contains(it) } -> "needed for TV inputs, remote or keyboard"
            else -> null
        }
    }

    fun isProtected(pkg: String, live: Set<String> = emptySet(), listKeep: Set<String> = emptySet()) =
        reason(pkg, live, listKeep) != null
}

/** Un Inicio conocido. Solo se usa para protegerlo: el cambio de Inicio lo hace Vision+ Home. */
internal enum class HomeApp(val pkg: String) {
    VISION("com.chronosbvrx.visionplus.home"),
    SIMPLE("com.ansub.tvhome"),
    STYLED("com.gathin.tvhome"),
    FIRETV("com.amazon.tv.launcher"),
    GOOGLE_TV("com.google.android.apps.tv.launcherx"),
    ANDROID_TV("com.google.android.tvlauncher"),
    LEANBACK("com.google.android.leanbacklauncher"),
    MI_TV("com.mitv.tvhome");

    companion object {
        fun of(pkg: String?): HomeApp? = entries.firstOrNull { it.pkg == pkg }
    }
}

internal object Launchers {
    val HOME_APPS: Set<HomeApp> = HomeApp.entries.toSet()

    const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
    const val CVTE_LAUNCHER = "com.cvte.tv.launcher"

    /** Inicios de fábrica que se pueden retirar SOLO después de que Vision+ Home sea el Inicio. */
    val KNOWN_STOCK: Set<String> = setOf(
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.mitv.tvhome",
        "com.amazon.tv.launcher",
        "com.android.tv.fallbackhome",
        "com.google.android.setupwizard",
    )

    /** Nunca forma parte de la limpieza: Inicios y la app de setup de Google TV. */
    fun isHomeApp(pkg: String): Boolean =
        pkg in KNOWN_STOCK ||
            pkg == SETUPWRAITH ||
            pkg == CVTE_LAUNCHER ||
            HomeApp.of(pkg) != null
}

/** Grupos que ve el dueño en la revisión. */
internal enum class CleanupGroup(val title: String, val defaultOn: Boolean) {
    ADS("Anuncios y rastreo", true),
    PRELOADED("Apps preinstaladas", true),
    VOICE("Asistente de voz", false),
    CASTING("Casting y espejo", false),
    UPDATES("Actualizaciones del sistema", false),
    STREAMING("Apps de streaming", false);

    companion object {
        /** Lo que se apaga con un toque: lo que solo molesta, nunca lo que el dueño puede usar. */
        val DEFAULTS: Set<CleanupGroup> = entries.filter { it.defaultOn }.toSet()
    }
}

/** Una app que se puede apagar, con su nombre legible y el grupo al que pertenece. */
internal enum class CleanupSource {
    /** Línea activa de la lista probada para este modelo. */
    MATCHED,
    /** Línea comentada de esa misma lista: se ofrece, pero no se marca sola. */
    OPTIONAL,
    /** App instalada que aparece en la lista de otro modelo. */
    OTHER,
}

internal data class CleanupCandidate(
    val pkg: String,
    val label: String,
    val note: String,
    val group: CleanupGroup,
    val optional: Boolean = false,
    val source: CleanupSource = CleanupSource.MATCHED,
)

/**
 * Clasifica las entradas de una lista en los grupos de la revisión. El nombre del paquete y el
 * título de la sección deciden; lo que no está claro cae en «Apps preinstaladas».
 */
internal object Grouping {
    private val STREAMING_PKGS = listOf(
        "netflix", "youtube", "amazonvideo", "primevideo", "disney", "hulu", "hotstar", "spotify",
        "twitch", "plex", "wbd.stream", "hbo", "peacock", "paramount", "tubi", "pluto", "crunchyroll",
        "dazn", "zee5", "sonyliv", "jio", "erosnow", "eros.now", "mubi", "apple.atve", "rakuten", "viki",
        "vudu", "fubo", "sling", "deezer", "tidal", "amazon.music", "google.android.videos", "tvmusic",
    )

    fun groupOf(entry: DebloatEntry): CleanupGroup {
        val p = entry.pkg.lowercase()
        val s = (entry.section + " " + entry.note).lowercase()
        return when {
            entry.pkg in Protected.REMOTE_ESSENTIALS -> CleanupGroup.VOICE
            p.containsAny("katniss", "alexa", "assistant", "voice", "speech", "vui", "micmanager") ||
                s.containsAny("voice", "assistant", "alexa", "microphone") -> CleanupGroup.VOICE
            p.containsAny("mediashell", "airplay", "miracast", "dlna", "milink", "screencast", "mirror", "chromecast", ".cast") ||
                s.containsAny("cast", "mirroring", "airplay", "miracast", "dlna") -> CleanupGroup.CASTING
            p.containsAny(".ota", "ota.", "update", "upgrade", "fota") ||
                s.containsAny("firmware update", "ota", "updater", "update service") -> CleanupGroup.UPDATES
            STREAMING_PKGS.any { p.contains(it) } || s.contains("streaming") -> CleanupGroup.STREAMING
            s.containsAny(
                "telemetry", "tracking", "analytics", "acr", " ad", "ads", "promo", "recommendation",
                "sponsored", "patchwall", "content row", "feedback", "report", "metrics", "samba",
                "alphonso", "demo", "shop", "store mode", "retail", "hotel", "data collection",
            ) || p.containsAny("analytics", "ads", "acr", "telemetry", "tracker", "report", "feedback", "demo") ->
                CleanupGroup.ADS
            else -> CleanupGroup.PRELOADED
        }
    }

    private val NOT_OFFERED = Regex(
        "protected|not on this|refused|cannot|can't|keep|needed|build-and-install|not installed",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Candidatos reales de una lista: solo los instalados, nunca los protegidos, los Inicios o
     * los que la propia lista manda conservar. Las líneas comentadas (`optional`) se ofrecen
     * aparte y no se marcan solas.
     */
    fun candidates(
        list: DebloatList,
        installed: Set<String>,
        disabled: Set<String> = emptySet(),
        liveProtected: Set<String> = emptySet(),
    ): List<CleanupCandidate> {
        val active = list.entries.map { it to false }
        val extras = list.optional
            .filterNot { NOT_OFFERED.containsMatchIn(it.note) }
            .map { it to true }
        return (active + extras)
            .filter { (entry, _) -> entry.pkg in installed && entry.pkg !in disabled }
            .filterNot { (entry, _) -> Launchers.isHomeApp(entry.pkg) }
            .filterNot { (entry, _) -> Protected.isProtected(entry.pkg, liveProtected, list.keep) }
            .map { (entry, optional) ->
                CleanupCandidate(
                    pkg = entry.pkg,
                    label = AppLabels.label(entry.pkg, entry.note),
                    note = entry.note,
                    group = groupOf(entry),
                    optional = optional,
                    source = if (optional) CleanupSource.OPTIONAL else CleanupSource.MATCHED,
                )
            }
            .distinctBy { it.pkg }
            .sortedWith(compareBy({ it.group.ordinal }, { it.label.lowercase() }))
    }

    private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }
}

/**
 * Nombres legibles para los paquetes más comunes. Si no hay nombre conocido se usa la nota de
 * la lista o las últimas palabras del paquete; nunca se enseña solo el nombre técnico.
 */
internal object AppLabels {
    private val EXACT: Map<String, String> = mapOf(
        "com.android.vending" to "Google Play Store",
        "com.google.android.gms" to "Servicios de Google Play",
        "com.google.android.gsf" to "Servicios de Google",
        "com.google.android.webview" to "Android System WebView",
        "com.google.android.tts" to "Voz de Google",
        "com.google.android.inputmethod.latin" to "Teclado Gboard",
        "com.google.android.tv.assistant" to "Asistente de Google (TV)",
        "com.google.android.tv.remote.service" to "Servicio del control remoto",
        "com.google.android.partnersetup" to "Configuración de socio de Google",
        "com.google.android.onetimeinitializer" to "Configuración inicial",
        "com.google.android.syncadapters.calendar" to "Sincronización de calendario",
        "com.google.android.apps.nbu.smartconnect.tv" to "Conexión con el teléfono (Google TV)",
        "com.google.android.packageinstaller" to "Instalador de apps",
        "com.google.android.permissioncontroller" to "Control de permisos",
        "com.google.android.ext.services" to "Servicios de Android",
        "com.google.android.sss.authbridge" to "Puente de inicio de sesión",
        "com.google.android.tv.frameworkpackagestubs" to "Stubs del framework",
        "android.autoinstalls.config.google.gtvpai" to "Autoinstalación de socio",
        "com.android.tv.settings" to "Ajustes de TV",
        "com.android.systemui" to "Interfaz del sistema",
        "com.android.bluetooth" to "Bluetooth",
        "com.android.printspooler" to "Servicio de impresión",
        "com.android.providers.tv" to "Canales de TV",
        "com.android.providers.media.module" to "Almacenamiento multimedia",
        "com.android.dreams.basic" to "Salvapantallas básico",
        "com.android.htmlviewer" to "Visor HTML",
        "com.android.captiveportallogin" to "Inicio de sesión Wi-Fi",
        "com.android.backupconfirm" to "Confirmación de copia",
        "com.android.sharedstoragebackup" to "Copia de almacenamiento",
        "com.android.wallpaperbackup" to "Copia de fondos",
        "com.android.localtransport" to "Transporte de copia local",
        "com.android.cts.ctsshim" to "Shim de pruebas (CTS)",
        "com.android.cts.priv.ctsshim" to "Shim de pruebas (CTS)",
        "com.android.hotspot2.osulogin" to "Hotspot 2.0",
        "com.android.pacprocessor" to "Proxy automático",
        "com.android.proxyhandler" to "Gestor de proxy",
        "com.android.statementservice" to "Verificador de enlaces",
        "com.android.vpndialogs" to "Diálogos de VPN",
        "com.android.companiondevicemanager" to "Gestor de dispositivos",
        "com.android.soundpicker" to "Selector de sonido",
        "com.android.se" to "Elemento seguro",
        "com.android.settings.intelligence" to "Búsqueda de Ajustes",
        "com.dolby.android.audio.service" to "Audio Dolby",
        "com.tcl.guard" to "TGuard (seguimiento de uso)",
        "com.tcl.ttvs" to "Analítica y actualizador de TCL",
        "com.tcl.usercenter" to "Cuenta TCL",
        "com.tcl.esticker" to "Sticker de tienda TCL",
        "com.tcl.messagebox" to "Mensajes y promociones TCL",
        "com.tcl.partnercustomizer" to "Filas de Inicio de TCL",
        "com.tcl.useragreement" to "Acuerdo de usuario TCL",
        "com.tcl.waterfall.overseas" to "Contenido promocional TCL",
        "com.tcl.hotelmenu" to "Modo hotel TCL",
        "com.tcl.dashboard" to "Panel de Inicio TCL",
        "com.tcl.android.webview" to "WebView de TCL",
        "com.tcl.t_solo" to "T-Solo (audio Bluetooth)",
        "com.tcl.ui_mediaCenter" to "Reproductor TCL",
        "com.tcl.smartalexa" to "Alexa de TCL",
        "com.tcl.copydatatotv" to "Copia de datos a la TV",
        "com.tcl.common.shortcutmenu" to "Menú de atajos TCL",
        "com.tcl.tv" to "Servicio de TV TCL",
        "com.tcl.settings" to "Ajustes TCL",
        "com.tcl.system.server" to "Servidor del sistema TCL",
        "com.tcl.tvinput" to "Entrada de TV TCL",
        "com.tcl.inputmethod.international" to "Teclado TCL",
        "com.tcl.autopair" to "Emparejamiento del mando TCL",
        "com.tcl.factory.view" to "Menú de fábrica TCL",
        "com.tcl.initsetup" to "Configuración inicial TCL",
        "com.tcl.keyhelp" to "Ayudante de teclas TCL",
        "com.tcl.micmanager" to "Gestor del micrófono TCL",
        "com.tcl.providers.config" to "Configuración TCL",
        "com.tcl.suspension" to "Botón flotante TCL",
        "com.tcl.tcl_bt_rcu_service" to "Mando Bluetooth TCL",
        "com.tcl.channelplus" to "Canales en vivo TCL",
        "com.netflix.ninja" to "Netflix",
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.hulu.livingroomplus" to "Hulu",
        "com.wbd.stream" to "Max",
        "in.startv.hotstar" to "JioHotstar",
        "com.spotify.tv.android" to "Spotify",
        "tv.twitch.android.app" to "Twitch",
        "tv.twitch.android.viewer" to "Twitch",
        "com.plexapp.android" to "Plex",
        "com.peacocktv.peacockandroid" to "Peacock",
        "com.cbs.ott" to "Paramount+",
        "com.tubitv" to "Tubi",
        "tv.pluto.android" to "Pluto TV",
        "com.crunchyroll.crunchyroid" to "Crunchyroll",
        "com.dazn" to "DAZN",
        "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.google.android.videos" to "Google TV",
        "com.google.android.play.games" to "Play Games",
        "com.google.android.katniss" to "Asistente de Google",
        "com.google.android.apps.mediashell" to "Chromecast integrado",
        "com.google.android.feedback" to "Comentarios de Google",
        "com.google.android.marvin.talkback" to "TalkBack",
        "com.google.android.apps.tv.dreamx" to "Modo ambiente",
        "com.google.android.tvrecommendations" to "Recomendaciones de Google",
        "com.google.android.backdrop" to "Salvapantallas",
        "com.amazon.alexa.tv" to "Alexa",
        "com.facebook.katana" to "Facebook",
        "com.facebook.appmanager" to "Gestor de apps de Facebook",
        "com.facebook.system" to "Servicios de Facebook",
        "com.samba.tv" to "Samba TV",
        "com.google.android.apps.tv.launcherx" to "Inicio de Google TV",
        "com.google.android.tvlauncher" to "Inicio de Android TV",
        "com.google.android.leanbacklauncher" to "Inicio de Android TV",
        "com.google.android.tungsten.setupwraith" to "Configuración de Google TV",
        "com.chronosbvrx.visionplus" to "Vision+",
        "com.chronosbvrx.visionplus.home" to "Vision+ Home",
        "com.ansub.tvhome" to "Inicio simple",
        "com.gathin.tvhome" to "Inicio CleanYourTV",
    )

    private val GENERIC = setOf("com", "org", "net", "tv", "android", "app", "google", "apps", "service", "services")

    fun label(pkg: String, note: String = ""): String {
        EXACT[pkg]?.let { return it }
        val fromNote = note.split(Regex("[,;(]| - | -- ")).first().trim()
        if (fromNote.length in 3..40 && !fromNote.contains("re-enable", ignoreCase = true)) {
            return fromNote.replaceFirstChar { it.uppercase() }
        }
        val parts = pkg.split('.').filterNot { it in GENERIC }
        val tail = parts.takeLast(2).joinToString(" ") { it.replace('_', ' ') }
        return tail.replaceFirstChar { it.uppercase() }.ifBlank { pkg }
    }
}
