package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Las reglas del «dejar Vision+ Home como Inicio» por ADB, separadas de la red para poder
 * probarlas: qué paquete abre el Inicio, cuál se puede retirar y qué contestó el sistema.
 */
internal object TvHomeRules {

    const val OUR_PKG = "com.chronosbvrx.visionplus.home"
    const val OUR_HOME = "$OUR_PKG/.LauncherActivity"

    /**
     * Nunca se desactiva nada de esto: sin System UI, Ajustes o el instalador la tele deja de
     * ser usable y no habría forma de volver desde el propio equipo.
     */
    private val PROTECTED = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.providers.settings",
    )

    /** `cmd package resolve-activity --brief` deja una línea con `pkg/.Actividad`. */
    fun parseResolveActivity(output: String): String? = output
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && it.contains("/") && !it.startsWith("priority") && !it.startsWith("Starting") }

    /** Quién está realmente en pantalla según `dumpsys activity activities`. */
    fun parseResumedPackage(dumpsys: String): String? =
        Regex("(?:topResumedActivity|mResumedActivity)\\s*[:=]\\s*ActivityRecord\\{[^ ]+ u\\d+ ([^/\\s]+)/")
            .find(dumpsys)
            ?.groupValues
            ?.getOrNull(1)

    fun canDisable(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && pkg != OUR_PKG && pkg !in PROTECTED

    fun packageOf(component: String?): String? = component?.substringBefore("/")?.takeIf { it.isNotBlank() }
}

/**
 * «Inicio en la TV» desde el teléfono: empareja ADB por Wi-Fi con la tele (una sola vez) y
 * aplica el mismo procedimiento que se hacía con una PC — fijar Vision+ Home y retirar el
 * Inicio del fabricante solo si sigue robándose la pantalla.
 */
internal object TvHomeSetup {

    enum class Phase { IDLE, SEARCHING, READY, WORKING, DONE, ERROR }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val host: String = "",
        val pairingPort: Int = 0,
        val paired: Boolean = false,
        val applied: Boolean = false,
        val disabled: List<String> = emptyList(),
        val originalHome: String = "",
    )

    private const val PREFS = "tv_adb_home"
    private const val K_PAIRED = "paired"
    private const val K_ORIGINAL = "original_home"
    private const val K_DISABLED = "disabled"

    /** Estado observable (mismo patrón que SyncStore/RemoteClient): la UI lo lee directo. */
    val state = mutableStateOf(UiState())

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        state.value = state.value.copy(
            paired = p.getBoolean(K_PAIRED, false),
            disabled = p.getStringSet(K_DISABLED, emptySet()).orEmpty().sorted(),
            originalHome = p.getString(K_ORIGINAL, "").orEmpty(),
            phase = Phase.IDLE,
            message = "",
        )
    }

    /**
     * Busca la tele. El servicio de emparejamiento solo existe mientras la tele tiene abierta
     * la pantalla de «Emparejar dispositivo con código»; si ya está emparejado basta el de
     * conexión, que vive mientras la depuración inalámbrica esté encendida.
     */
    suspend fun search(context: Context) = withContext(Dispatchers.IO) {
        state.value = state.value.copy(phase = Phase.SEARCHING, message = "Buscando la tele en tu red…")
        val pairing = AdbDiscovery.find(context, AdbDiscovery.SERVICE_PAIRING, 20_000)
        if (pairing != null) {
            state.value = state.value.copy(
                phase = Phase.READY,
                host = pairing.host,
                pairingPort = pairing.port,
                message = "Tele encontrada. Escribe el código que muestra la pantalla.",
            )
            return@withContext
        }
        val connect = AdbDiscovery.find(context, AdbDiscovery.SERVICE_CONNECT, 12_000)
        if (connect != null && prefs(context).getBoolean(K_PAIRED, false)) {
            state.value = state.value.copy(
                phase = Phase.READY,
                host = connect.host,
                pairingPort = 0,
                message = "Ya está emparejada: puedes aplicar el Inicio sin código.",
            )
            return@withContext
        }
        // Teles Android 9/10 y cajas con «ADB por red»: no tienen el emparejamiento moderno,
        // pero sí un ADB en el 5555. La tele pedirá autorizar la llave (un OK con el mando).
        val legacy = connectLegacyTcp(context)
        if (legacy != null) {
            prefs(context).edit().putBoolean(K_PAIRED, true).apply()
            state.value = state.value.copy(
                phase = Phase.READY,
                host = legacy,
                pairingPort = 0,
                paired = true,
                message = "Conectada por ADB de red. Pulsa Aplicar para dejar el Inicio.",
            )
            return@withContext
        }
        state.value = state.value.copy(
            phase = Phase.ERROR,
            message = "No encontré la tele. En la tele: Ajustes → Sistema → Desarrollador → " +
                "Depuración inalámbrica, y deja abierta «Emparejar dispositivo con código».\n\n" +
                "En teles Android 9/10 activa «ADB por red» y vincula antes el control remoto " +
                "para que el teléfono sepa su dirección.",
        )
    }

    /** Empareja con el código de 6 dígitos y aplica el Inicio en el mismo paso. */
    suspend fun pairAndApply(context: Context, code: String) = withContext(Dispatchers.IO) {
        val current = state.value
        if (current.host.isBlank() || current.pairingPort == 0) {
            state.value = current.copy(phase = Phase.ERROR, message = "Primero busca la tele.")
            return@withContext
        }
        try {
            state.value = current.copy(phase = Phase.WORKING, message = "Emparejando con la tele…")
            TvAdb.init(context)
            val paired = TvAdb.pair(current.host, current.pairingPort, code)
            if (!paired) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "La tele rechazó el código. Vuelve a abrir «Emparejar dispositivo con código» y repite.",
                )
                return@withContext
            }
            prefs(context).edit().putBoolean(K_PAIRED, true).apply()
            state.value = state.value.copy(paired = true, message = "Emparejado. Conectando…")
            connect(context)
            applyInternal(context)
        } catch (t: Throwable) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo emparejar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** Reaplica sin código (la llave ya está guardada). Útil si una actualización devolvió el Inicio. */
    suspend fun apply(context: Context) = withContext(Dispatchers.IO) {
        try {
            TvAdb.init(context)
            state.value = state.value.copy(phase = Phase.WORKING, message = "Conectando con la tele…")
            connect(context)
            applyInternal(context)
        } catch (t: Throwable) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo conectar: ${t.javaClass.simpleName}. Enciende la depuración inalámbrica y busca la tele otra vez.",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** Devuelve la tele como estaba: reactiva lo desactivado y vuelve a su Inicio original. */
    suspend fun restore(context: Context) = withContext(Dispatchers.IO) {
        try {
            TvAdb.init(context)
            state.value = state.value.copy(phase = Phase.WORKING, message = "Restaurando el Inicio de la tele…")
            connect(context)
            val p = prefs(context)
            val disabled = p.getStringSet(K_DISABLED, emptySet()).orEmpty().sorted()
            for (pkg in disabled) {
                TvAdb.shell("pm enable $pkg")
            }
            val original = p.getString(K_ORIGINAL, "").orEmpty()
            if (original.isNotBlank()) {
                TvAdb.shell("pm set-home-activity ${original.removePrefix("package:")}")
            }
            p.edit().clear().apply()
            state.value = state.value.copy(
                phase = Phase.DONE,
                applied = false,
                paired = false,
                disabled = emptyList(),
                originalHome = "",
                message = if (disabled.isEmpty()) {
                    "La tele vuelve a su Inicio de siempre."
                } else {
                    "Listo: reactivé ${disabled.size} app(s) y la tele vuelve a su Inicio de siempre."
                },
            )
            TvAdb.disconnectQuietly()
        } catch (t: Throwable) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo restaurar: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    fun resetMessage() {
        state.value = state.value.copy(phase = Phase.IDLE, message = "")
    }

    // --- interno ---

    /**
     * Teles viejas o cajas: ADB sobre TCP en el 5555 (sin emparejamiento TLS moderno). La
     * primera vez la tele muestra «¿Permitir depuración USB?»; se reintenta mientras la
     * persona lo acepta con el mando.
     */
    private suspend fun connectLegacyTcp(context: Context): String? {
        val hosts = runCatching {
            com.chronosbvrx.visionplus.data.remote.RemoteClient.rememberedTvs(context)
                .map { it.host }
                .distinct()
        }.getOrDefault(emptyList())
        if (hosts.isEmpty()) return null
        TvAdb.init(context)
        for (host in hosts.take(3)) {
            repeat(4) { attempt ->
                val ok = runCatching { TvAdb.connect(host, 5555) }.getOrDefault(false)
                if (ok) return host
                TvAdb.disconnectQuietly()
                state.value = state.value.copy(
                    message = "Probando $host… si la tele muestra un aviso de depuración, acéptalo con el mando.",
                )
                if (attempt < 3) delay(2_500)
            }
        }
        return null
    }

    private suspend fun connect(context: Context) {
        var endpoint: AdbEndpoint? = null
        repeat(4) {
            endpoint = AdbDiscovery.find(context, AdbDiscovery.SERVICE_CONNECT, 8_000)
            if (endpoint == null) delay(1_200)
        }
        val ep = endpoint
        if (ep == null) {
            // Tele sin emparejamiento moderno: su ADB vive en el 5555.
            if (connectLegacyTcp(context) != null) return
            error("la tele no publica el puerto de depuración")
        }
        if (!TvAdb.isConnected) {
            TvAdb.connect(ep.host, ep.port)
        }
    }

    private suspend fun applyInternal(context: Context) {
        state.value = state.value.copy(phase = Phase.WORKING, message = "Comprobando la tele…")
        // Candado: sin Vision+ Home instalado, retirar el Inicio dejaría la tele sin pantalla.
        val installed = runCatching {
            TvAdb.shell("pm list packages ${TvHomeRules.OUR_PKG}")
        }.getOrDefault("")
        if (!installed.contains(TvHomeRules.OUR_PKG)) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "La tele no tiene Vision+ Home instalado. Instálalo primero (Vision+ → Ajustes → " +
                    "Sistema → Vision+ Home) y vuelve a intentar.",
            )
            TvAdb.disconnectQuietly()
            return
        }
        state.value = state.value.copy(phase = Phase.WORKING, message = "Fijando Vision+ Home como Inicio…")
        val resolve =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        val before = runCatching { TvAdb.shell(resolve) }.getOrDefault("")
        val original = TvHomeRules.parseResolveActivity(before)
        val p = prefs(context)
        if (p.getString(K_ORIGINAL, null) == null && original != null && !original.startsWith(TvHomeRules.OUR_PKG)) {
            p.edit().putString(K_ORIGINAL, original).apply()
        }

        TvAdb.shell("pm set-home-activity ${TvHomeRules.OUR_HOME}")

        val disabled = p.getStringSet(K_DISABLED, emptySet()).orEmpty().toMutableSet()
        repeat(3) { attempt ->
            val effective = probeEffectiveHome()
            if (effective == TvHomeRules.OUR_PKG) {
                state.value = state.value.copy(
                    phase = Phase.DONE,
                    applied = true,
                    paired = true,
                    disabled = disabled.sorted(),
                    originalHome = p.getString(K_ORIGINAL, "").orEmpty(),
                    message = if (disabled.isEmpty()) {
                        "Listo: Vision+ Home es el Inicio de la tele."
                    } else {
                        "Listo: Vision+ Home es el Inicio. Retiré ${disabled.size} Inicio(s) que lo tapaban " +
                            "(puedes devolverlos con «Restaurar la tele»)."
                    },
                )
                TvAdb.disconnectQuietly()
                return
            }
            if (!TvHomeRules.canDisable(effective)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "La tele sigue abriendo «$effective», y esa app no se puede retirar sin dejarla " +
                        "inservible. No hay forma segura sin root.",
                )
                TvAdb.disconnectQuietly()
                return
            }
            state.value = state.value.copy(
                phase = Phase.WORKING,
                message = "La tele sigue abriendo «$effective»; lo retiro (intento ${attempt + 1} de 3)…",
            )
            TvAdb.shell("pm disable-user --user 0 $effective")
            disabled += effective!!
            p.edit().putStringSet(K_DISABLED, disabled).apply()
            delay(1_000)
        }
        state.value = state.value.copy(
            phase = Phase.ERROR,
            applied = probeEffectiveHome() == TvHomeRules.OUR_PKG,
            message = "Casi: revisa la pantalla de la tele. Algunas marcas vuelven a su Inicio al reiniciar; " +
                "puedes reaplicarlo sin código.",
        )
        TvAdb.disconnectQuietly()
    }

    /**
     * El paquete que abre la tele AHORA. `resolve-activity` puede contestar el que tiene más
     * prioridad y no el que usa el sistema, así que se pulsa Inicio y se lee quién quedó al
     * frente — es la misma comprobación que se hacía a mano con `dumpsys`.
     */
    private suspend fun probeEffectiveHome(): String? {
        runCatching { TvAdb.shell("input keyevent KEYCODE_WAKEUP") }
        runCatching { TvAdb.shell("input keyevent KEYCODE_HOME") }
        delay(1_500)
        val dumpsys = runCatching { TvAdb.shell("dumpsys activity activities") }.getOrDefault("")
        val resumed = TvHomeRules.parseResumedPackage(dumpsys)
        if (resumed != null) return resumed
        val resolve =
            runCatching {
                TvAdb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            }.getOrDefault("")
        return TvHomeRules.packageOf(TvHomeRules.parseResolveActivity(resolve))
    }
}
