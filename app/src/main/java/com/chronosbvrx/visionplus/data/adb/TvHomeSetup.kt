package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.chronosbvrx.visionplus.home.contract.HomeContract
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        Regex("(?:topResumedActivity|mResumedActivity|ResumedActivity)\\s*[:=]\\s*ActivityRecord\\{[^ ]+ u\\d+ ([^/\\s]+)/")
            .find(dumpsys)
            ?.groupValues
            ?.getOrNull(1)

    fun canDisable(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && pkg != OUR_PKG && pkg !in PROTECTED

    fun packageOf(component: String?): String? = component?.substringBefore("/")?.takeIf { it.isNotBlank() }

    /** `versionName=1.3.1` de `dumpsys package`; vacío/null si la app no está. */
    fun parseVersionName(output: String): String? = Regex("versionName=([^\\s]+)")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

/**
 * «Inicio en la TV» desde el teléfono: empareja ADB por Wi-Fi (una vez por tele) y aplica el
 * mismo procedimiento que se hacía con una PC — fijar Vision+ Home y retirar el Inicio del
 * fabricante solo si sigue robándose la pantalla.
 *
 * Se lleva una LISTA de teles y cada paso deja un registro (`log`) visible en la interfaz: si
 * un televisor se resiste, se ve exactamente qué comando contestó qué.
 */
internal object TvHomeSetup {

    enum class Phase { IDLE, SEARCHING, READY, WORKING, DONE, ERROR }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val host: String = "",
        val pairingPort: Int = 0,
        val tvs: List<TvRecord> = emptyList(),
        /** Última dirección Android 8/9 intentada, para ofrecer «Reintentar conexión». */
        val legacyHost: String = "",
        val busyId: String = "",
        val log: List<String> = emptyList(),
    )

    /** Estado observable (mismo patrón que SyncStore/RemoteClient): la UI lo lee directo. */
    val state = mutableStateOf(UiState())

    fun load(context: Context) {
        state.value = state.value.copy(
            tvs = TvAdbStore.load(context),
            phase = Phase.IDLE,
            message = "",
            busyId = "",
            log = emptyList(),
        )
    }

    fun resetMessage() {
        state.value = state.value.copy(
            phase = Phase.IDLE,
            message = "",
            busyId = "",
            legacyHost = "",
            log = emptyList(),
        )
    }

    /** Añade una línea al registro visible (una por paso; se queda con las últimas 16). */
    private fun log(line: String) {
        val clean = line.replace('\n', ' ').trim().take(200)
        if (clean.isBlank()) return
        state.value = state.value.copy(log = (state.value.log + clean).takeLast(16))
    }

    /** Ejecuta un comando en la tele con tope de tiempo y deja el resultado en el registro. */
    private suspend fun sh(command: String, timeoutMs: Long = 15_000): String {
        log("$ $command")
        val out = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                runCatching { TvAdb.shell(command) }
                    .getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            }
        }
        if (out == null) {
            log("  (la tele no contestó en ${timeoutMs / 1000}s)")
            return ""
        }
        val first = out.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(180)
        log("  $first")
        return out
    }

    /**
     * Empareja con un tope REAL. `TvAdb.pair()` es una llamada bloqueante que, en algunos
     * televisores, no vuelve aunque el emparejamiento ya se hizo (se queda esperando el cierre
     * del diálogo del sistema). Se ejecuta en su propio hilo y, si no contesta, se abandona y se
     * prueba a conectar igual: si la tele ya emparejó, la conexión funciona.
     */
    private fun pairWithTimeout(host: String, port: Int, code: String, timeoutMs: Long = 45_000): Boolean {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<Boolean> {
                runCatching { TvAdb.pair(host, port, code) }.getOrDefault(false)
            }
            try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                log("  emparejamiento sin respuesta en ${timeoutMs / 1000}s")
                future.cancel(true)
                false
            }
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Busca una tele NUEVA en modo emparejamiento. El servicio solo existe mientras el
     * televisor tiene abierta la pantalla de «Emparejar dispositivo con código».
     */
    suspend fun search(context: Context) = withContext(Dispatchers.IO) {
        state.value = state.value.copy(phase = Phase.SEARCHING, message = "Buscando la tele en tu red…", log = emptyList())
        val pairing = AdbDiscovery.find(context, AdbDiscovery.SERVICE_PAIRING, 20_000)
        if (!isActive) return@withContext
        if (pairing == null) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No encontré ninguna tele esperando el código. En el televisor: Ajustes → Sistema → " +
                    "Desarrollador → Depuración inalámbrica → «Emparejar dispositivo con código».\n\n" +
                    "En teles Android 8/9 no hay código: enciende «ADB por red» y escribe su dirección abajo.",
            )
            return@withContext
        }
        log("emparejamiento en ${pairing.host}:${pairing.port}")
        state.value = state.value.copy(
            phase = Phase.READY,
            host = pairing.host,
            pairingPort = pairing.port,
            message = "Tele encontrada. Escribe el código que muestra la pantalla.",
        )
    }

    /**
     * Conecta con una tele Android 8/9 por su dirección (la que muestra el televisor en Ajustes →
     * Red) y la deja lista: instala Vision+ y Home y fija el Inicio. Esas teles no tienen el
     * emparejamiento con código de Android 11, solo «ADB por red» en el puerto 5555.
     *
     * Antes de conectar se comprueba el puerto: si está cerrado se dice tal cual (no tiene sentido
     * esperar 90 s); si está abierto se espera a que la persona acepte «¿Permitir depuración?».
     */
    suspend fun connectByAddress(context: Context, rawHost: String) = withContext(Dispatchers.IO) {
        val host = normalizeHost(rawHost)
        if (host == null) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "Escribe una dirección válida, por ejemplo 192.168.1.50.",
            )
            return@withContext
        }
        TvAppPackage.reset()
        TvLauncherPackage.reset()
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = "",
            legacyHost = host,
            message = "Comprobando $host:5555…",
            log = emptyList(),
        )
        try {
            TvAdb.init(context)
            if (TvAdb.isConnected) TvAdb.disconnectQuietly()
            log("comprobando $host:5555")
            if (!isPortOpen(host, 5555)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "El puerto 5555 de $host está cerrado. En la tele: Ajustes → Sistema → " +
                        "Información → 7 toques a «Versión» → Desarrollador → enciende «ADB por red». " +
                        "Revisa también que sea la IP correcta y que esté en la misma Wi-Fi.",
                )
                return@withContext
            }
            log("puerto abierto; esperando autorización")
            state.value = state.value.copy(
                message = "Conectando con $host… Si la tele pregunta «¿Permitir depuración?», acéptalo con el mando.",
            )
            var connected = false
            var unauthorized = false
            val start = System.currentTimeMillis()
            var attempt = 0
            while (!connected && System.currentTimeMillis() - start < 60_000) {
                attempt++
                val result = runCatching { TvAdb.connect(host, 5555) }
                connected = result.getOrDefault(false)
                if (!connected) {
                    val error = result.exceptionOrNull()
                    unauthorized = unauthorized || looksUnauthorized(error)
                }
                log("intento $attempt: ${if (connected) "conectado" else "esperando a la tele"}")
                if (!connected) delay(2_000)
            }
            if (!isActive) return@withContext
            if (!connected) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = if (unauthorized) {
                        "$host respondió, pero no autorizó al teléfono. Acepta «¿Permitir depuración?» en la tele " +
                            "(marca «Permitir siempre») y pulsa «Reintentar conexión»."
                    } else {
                        "El puerto 5555 de $host está abierto, pero la tele no completó la conexión. Acepta el " +
                            "aviso de depuración con el mando y pulsa «Reintentar conexión»."
                    },
                )
                TvAdb.disconnectQuietly()
                return@withContext
            }
            val (identity, model) = identify()
            log("tele: ${model.ifBlank { "(sin modelo)" }} · id=${identity.ifBlank { "(sin id)" }}")
            val record = TvRecord(
                id = identity.ifBlank { "host:$host" },
                label = model.ifBlank { "Tele ${host.substringAfterLast('.')}" },
                host = host,
                paired = true,
                updatedAt = System.currentTimeMillis(),
            )
            state.value = state.value.copy(
                tvs = TvAdbStore.upsert(context, record),
                message = "Conectado con ${record.displayName}. Instalando Vision+ y su Inicio…",
            )
            applyInternal(context, record, updateLauncher = true, updateApp = true)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo conectar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** «192.168.1.50», «192.168.1.50:5555» o una URL → IP limpia; null si no lo parece. */
    internal fun normalizeHost(raw: String): String? {
        val clean = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
        val octets = clean.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        return clean
    }

    /** ¿El puerto está abierto? Evita esperar 90 s cuando la tele ni siquiera tiene ADB por red. */
    internal fun isPortOpen(host: String, port: Int, timeoutMs: Int = 1_500): Boolean = runCatching {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            true
        }
    }.getOrDefault(false)

    /** LibADB no tipifica la negativa de la llave: se reconoce por el nombre o el mensaje. */
    private fun looksUnauthorized(t: Throwable?): Boolean {
        if (t == null) return false
        val text = (t.javaClass.name + " " + t.message.orEmpty()).lowercase()
        return "auth" in text || "unauthor" in text || "denied" in text
    }

    /** Empareja el televisor nuevo, lo guarda en la lista y le aplica el Inicio. */
    suspend fun pairAndApply(context: Context, code: String) = withContext(Dispatchers.IO) {
        val current = state.value
        if (current.host.isBlank() || current.pairingPort == 0) {
            state.value = current.copy(phase = Phase.ERROR, message = "Primero busca la tele.")
            return@withContext
        }
        try {
            TvAppPackage.reset()
            TvLauncherPackage.reset()
            state.value = current.copy(phase = Phase.WORKING, message = "Emparejando con la tele…")
            TvAdb.init(context)
            val paired = pairWithTimeout(current.host, current.pairingPort, code)
            log(if (paired) "emparejado con ${current.host}" else "la tele no confirmó; pruebo a conectar igual")
            state.value = state.value.copy(message = "Conectando con la tele…")
            val connected = connectTo(context, host = current.host, id = "")
            if (!connected) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = if (paired) {
                        "Emparejado, pero no pude conectarme. Revisa que la depuración inalámbrica siga encendida. " +
                            "Mira «Detalles» para ver hasta dónde llegó."
                    } else {
                        "No se pudo emparejar. Abre otra vez «Emparejar dispositivo con código» en la tele y repite. " +
                            "Mira «Detalles» para ver qué contestó."
                    },
                )
                return@withContext
            }
            val (identity, model) = identify()
            log("tele: ${model.ifBlank { "(sin modelo)" }} · id=${identity.ifBlank { "(sin id)" }}")
            val record = TvRecord(
                id = identity.ifBlank { "host:${current.host}" },
                label = model.ifBlank { "Tele ${current.host.substringAfterLast('.')}" },
                host = current.host,
                paired = true,
                updatedAt = System.currentTimeMillis(),
            )
            state.value = state.value.copy(tvs = TvAdbStore.upsert(context, record))
            applyInternal(context, record)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo emparejar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** Reaplica en una tele concreta, sin código. */
    suspend fun apply(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) {
            state.value = state.value.copy(phase = Phase.ERROR, message = "Esa tele ya no está en la lista.")
            return@withContext
        }
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = tvId,
            message = "Conectando con ${record.displayName}…",
            log = emptyList(),
        )
        TvAppPackage.reset()
        TvLauncherPackage.reset()
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    message = "No encontré «${record.displayName}». Enciende la depuración inalámbrica del televisor " +
                        "y vuelve a intentar.",
                )
                return@withContext
            }
            applyInternal(context, record, updateLauncher = false)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo conectar: ${t.javaClass.simpleName}. Enciende la depuración inalámbrica e intenta otra vez.",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /**
     * Deja la tele lista: instala o actualiza Vision+ (la app) y Vision+ Home con la última
     * versión del release y fija el launcher como Inicio. Es el botón «Instalar en TV».
     */
    suspend fun install(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) {
            state.value = state.value.copy(phase = Phase.ERROR, message = "Esa tele ya no está en la lista.")
            return@withContext
        }
        TvAppPackage.reset()
        TvLauncherPackage.reset()
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = tvId,
            message = "Preparando Vision+ y su Inicio para ${record.displayName}…",
            log = emptyList(),
        )
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    message = "No encontré «${record.displayName}». Enciende la depuración inalámbrica del televisor " +
                        "y vuelve a intentar.",
                )
                return@withContext
            }
            applyInternal(context, record, updateLauncher = true, updateApp = true)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo instalar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** Devuelve ESA tele como estaba: reactiva lo retirado y vuelve a su Inicio original. */
    suspend fun restore(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) {
            state.value = state.value.copy(phase = Phase.ERROR, message = "Esa tele ya no está en la lista.")
            return@withContext
        }
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = tvId,
            message = "Restaurando ${record.displayName}…",
            log = emptyList(),
        )
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    message = "No encontré «${record.displayName}». Enciende la depuración inalámbrica y vuelve a intentar.",
                )
                return@withContext
            }
            record.disabled.forEach { pkg -> sh("pm enable $pkg") }
            if (record.originalHome.isNotBlank()) {
                sh("pm set-home-activity ${record.originalHome.removePrefix("package:")}")
            }
            val clean = record.copy(
                applied = false,
                disabled = emptyList(),
                originalHome = "",
                updatedAt = System.currentTimeMillis(),
            )
            state.value = state.value.copy(
                phase = Phase.DONE,
                busyId = "",
                tvs = TvAdbStore.upsert(context, clean),
                message = if (record.disabled.isEmpty()) {
                    "${record.displayName} vuelve a su Inicio de siempre."
                } else {
                    "Listo: reactivé ${record.disabled.size} app(s) en ${record.displayName} y vuelve a su Inicio."
                },
            )
            TvAdb.disconnectQuietly()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo restaurar: ${t.message.orEmpty()}",
            )
            TvAdb.disconnectQuietly()
        }
    }

    /** Quita la tele de la lista (no toca el televisor). */
    fun forget(context: Context, tvId: String) {
        val record = TvAdbStore.find(context, tvId)
        state.value = state.value.copy(
            tvs = TvAdbStore.remove(context, tvId),
            phase = Phase.IDLE,
            busyId = "",
            message = "${record?.displayName ?: "La tele"} quitada de la lista.",
        )
    }

    // --- interno ---

    /** Android ID + modelo del televisor conectado (con tope: un firmware mudo no cuelga el flujo). */
    private suspend fun identify(): Pair<String, String> {
        suspend fun read(command: String): String = withTimeoutOrNull(8_000) {
            withContext(Dispatchers.IO) { runCatching { TvAdb.shell(command) }.getOrDefault("") }
        }.orEmpty()
        val androidId = read("settings get secure android_id")
        val serial = read("getprop ro.serialno")
        val model = read("getprop ro.product.model")
        return TvAdbStore.identityFrom(androidId, serial) to model.trim()
    }

    /**
     * Conecta con UNA tele: prueba primero el host conocido (el del emparejamiento), luego el
     * resto de puertos visibles y, al final, el ADB clásico del 5555 de las teles viejas.
     * Antes de tocar nada compara la identidad: si hay dos teles y ninguna coincide, no se
     * aplica nada en la equivocada.
     */
    private suspend fun connectTo(context: Context, host: String, id: String): Boolean {
        TvAdb.init(context)
        if (TvAdb.isConnected) TvAdb.disconnectQuietly()

        val endpoints = AdbDiscovery.findAll(context, AdbDiscovery.SERVICE_CONNECT, 10_000, 4)
            .sortedByDescending { it.host == host }
        log("puertos ADB visibles: ${endpoints.size}")
        for (endpoint in endpoints) {
            val ok = runCatching { TvAdb.connect(endpoint.host, endpoint.port) }.getOrDefault(false)
            log("  ${endpoint.host}:${endpoint.port} → ${if (ok) "conectado" else "sin conexión"}")
            if (!ok) continue
            val identity = identify().first
            if (id.isBlank() || identity.isBlank() || identity == id) return true
            log("  es otra tele (id=$identity); sigo buscando")
            TvAdb.disconnectQuietly()
        }

        if (host.isNotBlank()) {
            repeat(3) { attempt ->
                val ok = runCatching { TvAdb.connect(host, 5555) }.getOrDefault(false)
                log("  $host:5555 → ${if (ok) "conectado" else "sin conexión"}")
                if (ok) {
                    val identity = identify().first
                    if (id.isBlank() || identity.isBlank() || identity == id || id.startsWith("host:")) return true
                    TvAdb.disconnectQuietly()
                }
                if (attempt < 2) delay(1_500)
            }
        }
        return false
    }

    /** La versión de un paquete en la tele AHORA ("" si no está instalado). */
    private suspend fun packageVersionOnTv(pkg: String): String {
        val out = sh("dumpsys package $pkg | grep -m1 versionName", 12_000)
        return TvHomeRules.parseVersionName(out).orEmpty()
    }

    private suspend fun launcherVersionOnTv(): String = packageVersionOnTv(TvHomeRules.OUR_PKG)

    /**
     * Deja la última Vision+ en la tele. Si no está, la instala; con [update], la sube a la
     * versión publicada. Devuelve la versión que quedó (`""` si no hay ninguna y no se pudo
     * instalar: en ese caso el flujo NO toca el Inicio, para no dejar la tele sin app que abrir).
     */
    private suspend fun ensureVisionPlus(context: Context, update: Boolean): String {
        val pkg = HomeContract.APP_PACKAGE
        val onTv = packageVersionOnTv(pkg)
        log(if (onTv.isBlank()) "Vision+ no está en la tele" else "la tele tiene Vision+ $onTv")
        if (onTv.isNotBlank()) {
            if (!update) return onTv
            val latest = runCatching { TvAppPackage.latestVersion(context) }.getOrNull()
            if (latest.isNullOrBlank()) {
                log("no pude comprobar la última versión de Vision+; se queda la que tiene")
                return onTv
            }
            if (!TvLauncherPackage.isNewer(latest, onTv)) {
                log("Vision+ ya está al día ($onTv)")
                return onTv
            }
            log("hay una Vision+ más nueva: $latest")
        }
        val apk = TvAppPackage.resolve(context)
        if (apk == null) {
            log("no pude descargar Vision+")
            return onTv
        }
        if (onTv.isNotBlank() && !TvLauncherPackage.isNewer(apk.versionName, onTv)) {
            log("la tele ya tiene Vision+ $onTv")
            return onTv
        }
        state.value = state.value.copy(
            message = if (onTv.isBlank()) {
                "Instalando Vision+ ${apk.versionName} en la tele… (puede tardar unos minutos)"
            } else {
                "Actualizando Vision+ $onTv a ${apk.versionName}… (puede tardar unos minutos)"
            },
        )
        log("instalando Vision+ ${apk.versionName} (${apk.file.length() / 1_048_576} MB)…")
        val output = withTimeoutOrNull(15L * 60_000) {
            withContext(Dispatchers.IO) { runCatching { TvAdb.installApkFileOutput(apk.file) }.getOrNull() }
        }
        val ok = output.orEmpty().contains("Success", ignoreCase = true)
        if (ok) {
            log("Vision+ ${apk.versionName} instalado")
        } else {
            val reason = output.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140)
            log("no se pudo instalar Vision+${if (reason.isBlank()) "" else ": $reason"}")
        }
        val after = packageVersionOnTv(pkg)
        return when {
            after.isNotBlank() -> after
            ok -> apk.versionName
            else -> onTv
        }
    }

    /**
     * Deja Vision+ Home en la tele: si no está lo instala y, con [update], lo sube a la última
     * versión publicada. El APK sale del release oficial (con respaldo dentro de la app), así que
     * el televisor recibe la versión buena sin que nadie descargue nada a mano.
     *
     * Devuelve la versión que quedó en la tele, o `null` si no había nada y no se pudo instalar.
     */
    private suspend fun ensureLauncher(context: Context, installed: String, update: Boolean): String? {
        val apk = TvLauncherPackage.resolve(context)
        if (apk == null) {
            log("no pude obtener el APK de Vision+ Home")
            return installed.takeIf { it.isNotBlank() }
        }
        log(
            "APK listo: Vision+ Home ${apk.versionName} " +
                "(${if (apk.fromRelease) "del release oficial" else "incluido en la app"})",
        )
        val newer = TvLauncherPackage.isNewer(apk.versionName, installed)
        if (installed.isNotBlank() && !(update && newer)) {
            log("la tele ya tiene Vision+ Home $installed")
            return installed
        }
        state.value = state.value.copy(
            message = if (installed.isBlank()) {
                "Instalando Vision+ Home ${apk.versionName} en la tele…"
            } else {
                "Actualizando Vision+ Home $installed a ${apk.versionName}…"
            },
        )
        val bytes = withContext(Dispatchers.IO) { runCatching { apk.file.readBytes() }.getOrNull() }
        if (bytes == null || bytes.size < 100_000) {
            log("no pude leer el APK descargado")
            return installed.takeIf { it.isNotBlank() }
        }
        val output = withTimeoutOrNull(120_000) {
            withContext(Dispatchers.IO) { runCatching { TvAdb.installApkOutput(bytes) }.getOrNull() }
        }
        val ok = output.orEmpty().contains("Success", ignoreCase = true)
        if (ok) {
            log("Vision+ Home ${apk.versionName} instalado")
        } else {
            val reason = output.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140)
            log("no se pudo instalar${if (reason.isBlank()) "" else ": $reason"}")
        }
        val after = launcherVersionOnTv()
        return when {
            after.isNotBlank() -> after
            ok -> apk.versionName
            else -> installed.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun applyInternal(
        context: Context,
        record: TvRecord,
        updateLauncher: Boolean = false,
        updateApp: Boolean = false,
    ) {
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = record.id,
            message = "Comprobando ${record.displayName}…",
        )
        // Primero la app: el Inicio necesita qué abrir. Si no hay Vision+ y no se pudo instalar,
        // no se toca el Inicio del televisor.
        val appVersion = ensureVisionPlus(context, updateApp)
        if (appVersion.isBlank()) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "${record.displayName} no tiene Vision+ y no pude instalarlo. No toco su Inicio para " +
                    "no dejarla sin pantalla. Revisa «Detalles».",
            )
            TvAdb.disconnectQuietly()
            return
        }
        val onTv = launcherVersionOnTv()
        log(if (onTv.isBlank()) "Vision+ Home no está en la tele" else "la tele tiene Vision+ Home $onTv")
        val version = ensureLauncher(context, onTv, updateLauncher)
        if (version == null) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "${record.displayName} no tiene Vision+ Home y no pude instalarlo. Revisa «Detalles».",
            )
            TvAdb.disconnectQuietly()
            return
        }
        var current = record.copy(appVersion = appVersion, launcherVersion = version, paired = true)
        state.value = state.value.copy(tvs = TvAdbStore.upsert(context, current))

        state.value = state.value.copy(message = "Fijando Vision+ Home como Inicio en ${record.displayName}…")
        sh("input keyevent KEYCODE_WAKEUP", 8_000)
        val resolve =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        if (current.originalHome.isBlank()) {
            val before = sh(resolve)
            val original = TvHomeRules.parseResolveActivity(before)
            if (original != null && !original.startsWith(TvHomeRules.OUR_PKG)) {
                current = current.copy(originalHome = original)
            }
        }

        sh("pm set-home-activity ${TvHomeRules.OUR_HOME}")
        val disabled = current.disabled.toMutableSet()

        repeat(3) { attempt ->
            val effective = probeEffectiveHome()
            log("  abre: ${effective ?: "(no se pudo leer)"}")
            if (effective == TvHomeRules.OUR_PKG) {
                val done = current.copy(
                    applied = true,
                    paired = true,
                    disabled = disabled.toList(),
                    updatedAt = System.currentTimeMillis(),
                )
                state.value = state.value.copy(
                    phase = Phase.DONE,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, done),
                    message = if (disabled.isEmpty()) {
                        "Listo: ${done.displayName} abre Vision+ Home con Vision+ $appVersion."
                    } else {
                        "Listo: ${done.displayName} abre Vision+ Home con Vision+ $appVersion. Retiré " +
                            "${disabled.size} Inicio(s) que lo tapaban (puedes devolverlos con «Restaurar»)."
                    },
                )
                TvAdb.disconnectQuietly()
                return
            }
            if (!TvHomeRules.canDisable(effective)) {
                val kept = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, kept),
                    message = "${record.displayName} sigue abriendo «$effective», y esa app no se puede retirar sin " +
                        "dejar la tele inservible. No hay forma segura sin root.",
                )
                TvAdb.disconnectQuietly()
                return
            }
            state.value = state.value.copy(
                phase = Phase.WORKING,
                busyId = record.id,
                message = "${record.displayName} sigue abriendo «$effective»; lo retiro (intento ${attempt + 1} de 3)…",
            )
            sh("pm disable-user --user 0 $effective")
            disabled += effective!!
            current = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
            TvAdbStore.upsert(context, current)
            delay(1_000)
        }

        val effective = probeEffectiveHome()
        val done = current.copy(applied = effective == TvHomeRules.OUR_PKG, updatedAt = System.currentTimeMillis())
        state.value = state.value.copy(
            phase = Phase.ERROR,
            busyId = "",
            tvs = TvAdbStore.upsert(context, done),
            message = "Casi: revisa la pantalla de ${record.displayName}. Algunas marcas vuelven a su Inicio al " +
                "reiniciar; puedes reaplicarlo sin código.",
        )
        TvAdb.disconnectQuietly()
    }

    /**
     * El paquete que abre la tele AHORA. `resolve-activity` puede contestar el que tiene más
     * prioridad y no el que usa el sistema, así que se pulsa Inicio y se lee quién quedó al
     * frente. Se filtra con `grep` en el propio televisor: el `dumpsys` completo son megabytes
     * y por ADB tarda decenas de segundos (era lo que hacía parecer que «no hacía nada»).
     */
    private suspend fun probeEffectiveHome(): String? {
        sh("input keyevent KEYCODE_HOME", 10_000)
        delay(1_200)
        val dumpsys = sh(
            "dumpsys activity activities | grep -m1 -E '(topResumedActivity|mResumedActivity|ResumedActivity).*ActivityRecord'",
            15_000,
        )
        val resumed = TvHomeRules.parseResumedPackage(dumpsys)
        if (resumed != null) return resumed
        val resolve =
            sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        return TvHomeRules.packageOf(TvHomeRules.parseResolveActivity(resolve))
    }
}
