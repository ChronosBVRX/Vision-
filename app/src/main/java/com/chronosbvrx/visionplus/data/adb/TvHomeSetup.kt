package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.mutableStateOf
import com.chronosbvrx.visionplus.data.debloat.CleanupGroup
import com.chronosbvrx.visionplus.data.debloat.CleanupOutcome
import com.chronosbvrx.visionplus.data.debloat.CleanupPlan
import com.chronosbvrx.visionplus.data.debloat.CleanupSource
import com.chronosbvrx.visionplus.data.debloat.DebloatCatalog
import com.chronosbvrx.visionplus.data.debloat.DebloatCommands
import com.chronosbvrx.visionplus.data.debloat.DebloatParse
import com.chronosbvrx.visionplus.data.debloat.DebloatRules
import com.chronosbvrx.visionplus.data.debloat.Launchers
import com.chronosbvrx.visionplus.data.debloat.ListMatch
import com.chronosbvrx.visionplus.data.debloat.ModelMatcher
import com.chronosbvrx.visionplus.data.debloat.TvBackupStore
import com.chronosbvrx.visionplus.data.debloat.TvDebloater
import com.chronosbvrx.visionplus.data.debloat.TvSnapshot
import com.chronosbvrx.visionplus.data.debloat.TvStorage
import com.chronosbvrx.visionplus.home.contract.HomeContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
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
        Regex("(?:topResumedActivity|mResumedActivity|mFocusedActivity|ResumedActivity)\\s*[:=]\\s*ActivityRecord\\{[^ ]+ u\\d+ ([^/\\s]+)/")
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

    /**
     * Paquetes que Android usa como Inicio de RESPALDO mientras el elegido arranca (sobre todo
     * en teles Android 8/9). No son el Inicio del fabricante que se puede retirar: si aparecen
     * hay que esperar, no desactivarlos (dejarían la tele sin pantalla).
     */
    private val TRANSIENT_HOMES = setOf(
        "android",
        "com.android.tv.settings",
        "com.android.tv.fallbackhome",
        "com.android.settings",
        "com.android.provision",
        "com.google.android.setupwizard",
    )

    fun isTransientHome(pkg: String?): Boolean = pkg?.trim() in TRANSIENT_HOMES

    /** El verificador de paquetes de Android bloqueó una instalación por ADB. */
    fun looksLikeVerificationBlocked(output: String?): Boolean {
        val text = output.orEmpty().lowercase()
        return "verification" in text || "verifier" in text
    }

    /** La línea de `pm install` que explica el resultado (Failure/Error/Success), no el progreso. */
    fun installReason(output: String?): String {
        val lines = output.orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
        return (lines.firstOrNull {
            it.contains("Failure", ignoreCase = true) ||
                it.contains("Error", ignoreCase = true) ||
                it.contains("Success", ignoreCase = true)
        } ?: lines.lastOrNull()).orEmpty().take(160)
    }
}

internal data class TvCandidate(
    val host: String,
    val name: String = "",
    val model: String = "",
    val legacy: Boolean = false,
    val tlsPort: Int = 0,
    val pairingPort: Int = 0,
) {
    val hasAdb: Boolean get() = legacy || tlsPort > 0 || pairingPort > 0
    val needsCode: Boolean get() = pairingPort > 0 && tlsPort == 0 && !legacy
    val title: String
        get() = listOf(name, model).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("adb-", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .joinToString(" · ")
            .ifBlank { "Tele en $host" }
    val subtitle: String
        get() = listOf(
            host,
            when {
                needsCode -> "Pide código"
                hasAdb -> "Lista para conectar"
                else -> "Activa la depuración"
            },
        ).joinToString(" · ")
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

    enum class Phase { IDLE, SEARCHING, READY, REVIEW, WORKING, DONE, ERROR }

    enum class ToolKind { SPACE, CHECK, ADS }

    data class ToolState(
        val kind: ToolKind,
        val tvId: String,
        val loading: Boolean = true,
        val message: String = "",
        val storage: TvStorage? = null,
        val check: TvCheckReport? = null,
        val dnsOn: Boolean = false,
        val dnsHost: String = "",
    )

    data class TvCheckReport(
        val backOn: List<String>,
        val homeChanged: Boolean,
        val stillOff: Int,
        val cleanedTotal: Int,
    )

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val host: String = "",
        val pairingPort: Int = 0,
        val tvs: List<TvRecord> = emptyList(),
        /** Última dirección Android 8/9 intentada, para ofrecer «Reintentar conexión». */
        val legacyHost: String = "",
        /** Teles encontradas por el descubrimiento (5555 + mDNS de ADB + Google Cast). */
        val candidates: List<TvCandidate> = emptyList(),
        val busyId: String = "",
        val log: List<String> = emptyList(),
        /** Momento del último paso, para que la interfaz pueda decir si se quedó callada. */
        val lastLogAt: Long = 0L,
        /** Limpieza por lista activada en Ajustes (se recuerda entre teles). */
        val cleanupEnabled: Boolean = true,
        /** Grupos marcados en la revisión actual. */
        val cleanupGroups: Set<CleanupGroup> = emptySet(),
        /** Apps marcadas ahora mismo en la revisión (individuales o por grupo). */
        val cleanupSelected: Set<String> = emptySet(),
        /** Plan que se está revisando antes de tocar la tele. */
        val plan: CleanupPlan? = null,
        /** Herramienta abierta para una tele guardada: espacio o revisión. */
        val tool: ToolState? = null,
    )

    /** Estado observable (mismo patrón que SyncStore/RemoteClient): la UI lo lee directo. */
    val state = mutableStateOf(UiState())

    private const val PREFS = "vision_tv_adb"
    private const val KEY_CLEANUP = "cleanup_enabled"
    private const val DNS_ADS = "dns.adguard-dns.com"
    private const val DNS_FAMILY = "family.adguard-dns.com"
    private val DNS_MODES = setOf("off", "opportunistic", "hostname")
    private val LOG_TIME = SimpleDateFormat("HH:mm:ss", Locale.US)

    private data class PendingApply(
        val record: TvRecord,
        val updateLauncher: Boolean,
        val updateApp: Boolean,
        val fireOs: Boolean,
    )

    private var pending: PendingApply? = null
    private var pendingSnapshot: TvSnapshot? = null
    private var pendingPlan: CleanupPlan? = null

    /** Evita que un doble toque en «Limpiar e instalar» lance dos limpiezas a la vez. */
    @Volatile
    private var confirming = false

    fun load(context: Context) {
        if (state.value.phase == Phase.REVIEW || state.value.phase == Phase.WORKING || state.value.tool != null) {
            TvAdb.disconnectQuietly()
        }
        pending = null
        pendingSnapshot = null
        pendingPlan = null
        state.value = state.value.copy(
            tvs = TvAdbStore.load(context),
            phase = Phase.IDLE,
            message = "",
            busyId = "",
            legacyHost = "",
            log = emptyList(),
            lastLogAt = 0L,
            candidates = emptyList(),
            cleanupEnabled = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CLEANUP, true),
            cleanupGroups = emptySet(),
            cleanupSelected = emptySet(),
            plan = null,
            tool = null,
        )
    }

    fun resetMessage() {
        pending = null
        pendingSnapshot = null
        pendingPlan = null
        state.value = state.value.copy(
            phase = Phase.IDLE,
            message = "",
            busyId = "",
            legacyHost = "",
            candidates = emptyList(),
            log = emptyList(),
            lastLogAt = 0L,
            cleanupGroups = emptySet(),
            cleanupSelected = emptySet(),
            plan = null,
            tool = null,
        )
    }

    /** Cancela la revisión y suelta la conexión sin tocar el televisor. */
    fun cancel() {
        pending = null
        pendingSnapshot = null
        pendingPlan = null
        TvAdb.disconnectQuietly()
        state.value = state.value.copy(
            phase = Phase.IDLE,
            message = "",
            busyId = "",
            legacyHost = "",
            candidates = emptyList(),
            log = emptyList(),
            lastLogAt = 0L,
            cleanupGroups = emptySet(),
            cleanupSelected = emptySet(),
            plan = null,
            tool = null,
        )
    }

    /** Vuelve de la pantalla del código a la lista de teles sin cerrar el diálogo. */
    fun backToCandidates() {
        state.value = state.value.copy(
            phase = Phase.READY,
            host = "",
            pairingPort = 0,
            message = "Elige tu tele.",
        )
    }

    /** Preferencia global: limpiar o no al instalar. */
    fun setCleanupEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CLEANUP, enabled).apply()
        state.value = state.value.copy(cleanupEnabled = enabled)
    }

    /**
     * Marca o desmarca un grupo en la revisión. El plan se recalcula en el acto: la interfaz
     * siempre enseña exactamente lo que se va a apagar.
     */
    fun toggleGroup(context: Context, group: CleanupGroup, enabled: Boolean) {
        val current = pendingPlan ?: return
        val selected = state.value.cleanupSelected.toMutableSet()
        val groupPkgs = current.candidates
            .filter { it.group == group && it.source == CleanupSource.MATCHED }
            .map { it.pkg }
        if (enabled) selected += groupPkgs else selected -= groupPkgs
        applySelection(selected, current)
    }

    /** Marca o desmarca UNA app, venga de la lista del modelo o de otra lista. */
    fun toggleApp(context: Context, pkg: String) {
        val current = pendingPlan ?: return
        val selected = state.value.cleanupSelected.toMutableSet()
        if (pkg in selected) selected -= pkg else selected += pkg
        applySelection(selected, current)
    }

    private fun applySelection(selected: MutableSet<String>, current: CleanupPlan) {
        val groups = current.candidates
            .filter { it.source == CleanupSource.MATCHED }
            .groupBy { it.group }
            .filter { (_, apps) -> apps.isNotEmpty() && apps.all { it.pkg in selected } }
            .keys
        val plan = current.copy(selected = selected.toList(), groups = groups)
        pendingPlan = plan
        state.value = state.value.copy(cleanupGroups = groups, cleanupSelected = selected, plan = plan)
    }

    /** Añade una línea al registro visible (una por paso; se queda con las últimas 16). */
    private fun log(line: String) {
        val clean = line.replace('\n', ' ').trim().take(240)
        if (clean.isBlank()) return
        val stamp = synchronized(LOG_TIME) { LOG_TIME.format(Date()) }
        state.value = state.value.copy(
            log = (state.value.log + "$stamp  $clean").takeLast(200),
            lastLogAt = System.currentTimeMillis(),
        )
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
     * El motor de limpieza habla por el mismo ADB que el resto del flujo. Cada comando tiene
     * tope: un firmware mudo no puede colgar la revisión ni la instalación.
     */
    private fun debloater(): TvDebloater = TvDebloater(
        shell = { command ->
            withTimeoutOrNull(30_000) {
                withContext(Dispatchers.IO) {
                    runCatching { TvAdb.shell(command) }
                        .getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                }
            } ?: "ERROR timeout"
        },
        log = { log(it) },
        sleep = { delay(it) },
    )

    /**
     * Conecta, lee la tele y calcula la limpieza. Si la limpieza está activada, deja el plan en
     * [Phase.REVIEW] para que la persona lo confirme; si no, aplica Vision+ directamente.
     *
     * La conexión NO se cierra al entrar en REVIEW: la tele ya está autorizada y el usuario
     * solo tiene que confirmar. Si se cancela, [cancel] la suelta sin tocar nada.
     */
    private suspend fun reviewOrApply(
        context: Context,
        record: TvRecord,
        updateLauncher: Boolean,
        updateApp: Boolean,
        allowCleanup: Boolean = true,
    ) {
        if (!allowCleanup || !state.value.cleanupEnabled) {
            pending = null
            pendingSnapshot = null
            pendingPlan = null
            state.value = state.value.copy(plan = null, cleanupGroups = emptySet(), cleanupSelected = emptySet(), tool = null)
            applyInternal(context, record, updateLauncher, updateApp)
            return
        }
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = record.id,
            message = "Leyendo ${record.displayName} para elegir su limpieza…",
            log = emptyList(),
            plan = null,
            cleanupGroups = emptySet(),
            cleanupSelected = emptySet(),
            tool = null,
        )
        pending = null
        pendingSnapshot = null
        pendingPlan = null
        val snapshot = try {
            debloater().snapshot()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            log("no pude leer la tele: ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "La tele se conectó, pero no pude leer su lista de apps. Puedes instalar Vision+ " +
                    "sin limpieza o reintentar. (${t.message.orEmpty().take(120)})",
            )
            TvAdb.disconnectQuietly()
            return
        }
        val lists = runCatching { DebloatCatalog.load(context) }.getOrDefault(emptyList())
        val rejected = DebloatCatalog.rejected()
        if (rejected.isNotEmpty()) log("listas rechazadas: ${rejected.size} (${rejected.first().id})")
        val match = ModelMatcher.match(snapshot.info, lists, limit = 3, installed = snapshot.installed).firstOrNull()
        val exact = match?.exact == true
        val groups = DebloatRules.defaultGroups(exact)
        val plan = DebloatRules.plan(match, snapshot, groups, exact, catalog = lists)
        val betterLabel = snapshot.info.displayName.ifBlank { record.label }
        val next = record.copy(label = betterLabel)
        pending = PendingApply(next, updateLauncher, updateApp, snapshot.info.isFireOs || match?.list?.isFireOs == true)
        pendingSnapshot = snapshot
        pendingPlan = plan
        state.value = state.value.copy(
            phase = Phase.REVIEW,
            busyId = next.id,
            host = next.host,
            cleanupGroups = groups,
            cleanupSelected = plan.selected.toSet(),
            plan = plan,
            message = if (match == null) {
                "No hay una lista probada para este modelo. Puedes instalar Vision+ igual; no se apagará nada."
            } else if (!exact) {
                "Hay una lista de un modelo parecido (${match.list.title.take(80)}), pero no está probada en esta tele. " +
                    "No se apagará nada salvo que marques un grupo."
            } else {
                "Revisa la limpieza de $betterLabel antes de instalar Vision+."
            },
        )
        log("modelo: ${snapshot.info.displayName.ifBlank { "(sin modelo)" }} · Android ${snapshot.info.release} " +
            "(SDK ${snapshot.info.sdk})")
        log(
            if (match == null) {
                "sin lista para este modelo; limpieza vacía"
            } else {
                "lista ${match.list.id}: ${if (exact) "probada en este modelo" else "modelo parecido, no probada"} · " +
                    "${plan.candidates.size} apps candidatas"
            },
        )
        log("apps marcadas al empezar: ${plan.selected.size}")
    }    /**
     * Barrido explícito de la red para las teles Android 8/9. No se lanza solo: la persona lo
     * pide desde el diálogo y, si hay varias, elige la dirección. Las Android 11+ ya se buscan
     * por el servicio de emparejamiento de arriba.
     */
    /**
     * «Mi tele está lista»: busca en la red las teles que ya tienen depuración. Junta tres
     * fuentes para que aparezcan todas: el puerto clásico 5555 (Android 8/9), los servicios
     * `_adb-tls-connect` / `_adb-tls-pairing` (Android 11+) y el nombre que publica Google Cast.
     */
    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        state.value = state.value.copy(
            phase = Phase.SEARCHING,
            message = "Buscando teles en tu red…",
            host = "",
            pairingPort = 0,
            legacyHost = "",
            candidates = emptyList(),
            log = emptyList(),
        )
        val ip = TvLanScan.phoneIpv4(context)
        log("red del teléfono: ${ip ?: "sin IPv4"}")
        // Sin el MulticastLock muchos teléfonos descartan el mDNS y las teles Android 11+ no aparecen.
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicast = wifi?.createMulticastLock("VisionTV")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val legacy = async {
            if (ip == null) emptyList() else TvLanScan.scan(ip)
        }
        val connect = async {
            AdbDiscovery.findAllServices(context, AdbDiscovery.SERVICE_CONNECT, 6_000, 10)
        }
        val pairing = async {
            AdbDiscovery.findAllServices(context, AdbDiscovery.SERVICE_PAIRING, 6_000, 10)
        }
        val cast = async {
            AdbDiscovery.findAllServices(context, "googlecast", 4_500, 10)
        }
        var legacyHosts: List<String> = emptyList()
        var connectServices: List<AdbDiscovery.AdbService> = emptyList()
        var pairingServices: List<AdbDiscovery.AdbService> = emptyList()
        var castServices: List<AdbDiscovery.AdbService> = emptyList()
        try {
            legacyHosts = legacy.await()
            connectServices = connect.await()
            pairingServices = pairing.await()
            castServices = cast.await()
        } finally {
            runCatching { multicast?.release() }
        }
        if (!isActive) return@withContext

        val byHost = linkedMapOf<String, TvCandidate>()
        fun merge(
            host: String,
            name: String = "",
            model: String = "",
            legacy: Boolean = false,
            tlsPort: Int = 0,
            pairingPort: Int = 0,
        ) {
            val old = byHost[host]
            byHost[host] = TvCandidate(
                host = host,
                name = old?.name.orEmpty().ifBlank { name },
                model = old?.model.orEmpty().ifBlank { model },
                legacy = old?.legacy == true || legacy,
                tlsPort = old?.tlsPort?.takeIf { it > 0 } ?: tlsPort,
                pairingPort = old?.pairingPort?.takeIf { it > 0 } ?: pairingPort,
            )
        }
        legacyHosts.forEach { merge(it, legacy = true) }
        connectServices.forEach { merge(it.host, it.name, it.model, tlsPort = it.port) }
        pairingServices.forEach { merge(it.host, it.name, it.model, pairingPort = it.port) }
        castServices.forEach { merge(it.host, it.name, it.model) }

        val found = byHost.values.sortedWith(
            compareByDescending<TvCandidate> { it.hasAdb }
                .thenByDescending { it.needsCode }
                .thenBy { it.title.lowercase() },
        )
        log(
            "encontradas: ${found.size} (5555=${legacyHosts.size}, tls=${connectServices.size}, " +
                "pairing=${pairingServices.size}, cast=${castServices.size})",
        )
        found.forEach { candidate ->
            log("  ${candidate.title} · ${candidate.subtitle}")
        }
        state.value = if (found.isEmpty()) {
            state.value.copy(
                phase = Phase.ERROR,
                message = "No encontré teles. Revisa que estén en la misma Wi-Fi y con la depuración encendida.",
            )
        } else {
            state.value.copy(
                phase = Phase.READY,
                candidates = found,
                message = "Elige tu tele.",
            )
        }
    }

    /**
     * La persona tocó una tarjeta. Si la tele pide código, se queda en READY para escribirlo;
     * si ya está autorizada, conecta y sigue al resumen de limpieza.
     */
    suspend fun connectCandidate(context: Context, host: String) = withContext(Dispatchers.IO) {
        val candidate = state.value.candidates.firstOrNull { it.host == host } ?: return@withContext
        log("elegida: ${candidate.title} (${candidate.host})")
        if (!candidate.hasAdb) {
            state.value = state.value.copy(
                message = "${candidate.title}: activa «Depuración inalámbrica» en la tele y vuelve a buscar.",
            )
            return@withContext
        }
        if (candidate.needsCode) {
            state.value = state.value.copy(
                phase = Phase.READY,
                host = candidate.host,
                pairingPort = candidate.pairingPort,
                message = "Escribe el código que muestra la tele.",
            )
            return@withContext
        }
        if (candidate.tlsPort > 0) {
            state.value = state.value.copy(
                phase = Phase.WORKING,
                busyId = "",
                host = candidate.host,
                message = "Conectando con ${candidate.title}…",
                log = emptyList(),
            )
            TvAdb.init(context)
            if (TvAdb.isConnected()) TvAdb.disconnectQuietly()
            log("conectando TLS ${candidate.host}:${candidate.tlsPort}…")
            val connected = runCatching { TvAdb.connect(candidate.host, candidate.tlsPort) }.getOrDefault(false)
            log(if (connected) "TLS conectado" else "TLS sin respuesta")
            if (!connected) {
                if (candidate.legacy) {
                    connectByAddress(context, candidate.host)
                    return@withContext
                }
                if (candidate.pairingPort > 0) {
                    state.value = state.value.copy(
                        phase = Phase.READY,
                        host = candidate.host,
                        pairingPort = candidate.pairingPort,
                        message = "La tele pide emparejamiento. Escribe el código que muestra.",
                    )
                } else {
                    state.value = state.value.copy(
                        phase = Phase.ERROR,
                        busyId = "",
                        message = "No pude conectar con ${candidate.title}. Vuelve a buscar o escribe su IP.",
                    )
                }
                return@withContext
            }
            val (identity, model) = identify()
            val record = TvRecord(
                id = identity.ifBlank { "host:${candidate.host}" },
                label = model.ifBlank { candidate.title },
                host = candidate.host,
                paired = true,
                updatedAt = System.currentTimeMillis(),
            )
            state.value = state.value.copy(tvs = TvAdbStore.upsert(context, record))
            reviewOrApply(context, record, updateLauncher = true, updateApp = true)
            return@withContext
        }
        connectByAddress(context, candidate.host)
    }

    /**
     * Busca una tele NUEVA en modo emparejamiento. El servicio solo existe mientras el
     * televisor tiene abierta la pantalla de «Emparejar dispositivo con código».
     */
    suspend fun search(context: Context) = withContext(Dispatchers.IO) {

        state.value = state.value.copy(
            phase = Phase.SEARCHING,
            message = "Buscando una tele Android 11+ esperando el código…",
            log = emptyList(),
        )
        val pairing = AdbDiscovery.find(context, AdbDiscovery.SERVICE_PAIRING, 20_000)
        if (!isActive) return@withContext
        if (pairing == null) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No encontré ninguna tele esperando el código. En el televisor: Ajustes → Sistema → " +
                    "Desarrollador → Depuración inalámbrica → «Emparejar dispositivo con código».\n\n" +
                    "En teles Android 8/9 no hay código: enciende «Depuración USB» y «ADB por red», y escribe su " +
                    "dirección abajo.",
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
                message = "Escribe una IP válida. Ejemplo: 192.168.1.50.",
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
            if (TvAdb.isConnected()) TvAdb.disconnectQuietly()
            log("comprobando $host:5555")
            if (!isPortOpen(host, 5555)) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "No pude conectar. Enciende «ADB por red» en la tele y revisa la IP.",
                )
                return@withContext
            }
            log("puerto abierto; esperando autorización")
            state.value = state.value.copy(
                message = "Conectando con $host… Acepta el aviso en la tele.",
            )
            // Aviso proactivo mientras se espera: en Android 8/9 el aviso del sistema solo sale
            // cuando la app envía su llave pública, y la persona debe aceptarlo en el televisor.
            val connected = coroutineScope {
                val ticker = launch {
                    delay(12_000)
                    if (isActive) {
                        log("esperando «Permitir depuración» en la tele…")
                        state.value = state.value.copy(
                            message = "Acepta «Permitir depuración» en la tele (marca «Permitir siempre»).",
                        )
                    }
                    delay(20_000)
                    if (isActive) {
                        log("sigo esperando la autorización de la tele…")
                        state.value = state.value.copy(
                            message = "Sigo esperando. Acepta el aviso en la tele o pulsa Cancelar.",
                        )
                    }
                }
                try {
                    // `runInterruptible`: cerrar el diálogo cancela la espera de 90 s en vez de
                    // dejar el socket ocupado hasta que venza.
                    runInterruptible(Dispatchers.IO) {
                        TvAdb.connectAllowingAuthorization(host, 5555)
                    }
                } finally {
                    ticker.cancel()
                }
            }
            log(if (connected) "conectado" else "la tele no autorizó la conexión")
            if (!isActive) return@withContext
            if (!connected) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "La tele no autorizó. Acepta «Permitir depuración» y reintenta.",
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
                message = "Conectado con ${record.displayName}. Revisando la limpieza…",
            )
            reviewOrApply(context, record, updateLauncher = true, updateApp = true)
        } catch (t: Throwable) {
            // También al cancelar: el socket no puede quedar esperando la autorización.
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No pude conectar. Revisa la IP y que la tele esté despierta.",
            )
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
            TvAdb.init(context)
            if (TvAdb.isConnected()) TvAdb.disconnectQuietly()
            val knownPort = state.value.candidates.firstOrNull { it.host == current.host }?.tlsPort ?: 0
            var connected = knownPort > 0 &&
                runCatching { TvAdb.connect(current.host, knownPort) }.getOrDefault(false)
            if (!connected) connected = connectTo(context, host = current.host, id = "")
            if (!connected) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = if (paired) {
                        "Emparejado, pero no pude conectar. Revisa que la depuración inalámbrica siga encendida."
                    } else {
                        "No pude emparejar. Abre «Emparejar dispositivo con código» y reintenta."
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
            reviewOrApply(context, record, updateLauncher = false, updateApp = false)
        } catch (t: Throwable) {
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "No se pudo emparejar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
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
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo conectar: ${t.javaClass.simpleName}. Enciende la depuración inalámbrica e intenta otra vez.",
            )
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
            reviewOrApply(context, record, updateLauncher = true, updateApp = true)
        } catch (t: Throwable) {
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo instalar: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
            )
        }
    }

    /**
     * Confirma la revisión: guarda la copia obligatoria, apaga los paquetes elegidos y, con la
     * misma conexión, instala Vision+ y su Inicio. Si la copia no se puede guardar, no se toca
     * nada. Si la limpieza se corta (la tele deja de contestar), tampoco se instala a medias.
     */
    suspend fun confirm(context: Context) = withContext(Dispatchers.IO) {
        if (confirming) return@withContext
        confirming = true
        try {
        val job = pending
        if (job == null) {
            state.value = state.value.copy(
                phase = Phase.ERROR,
                message = "La revisión ya no está disponible. Vuelve a conectar la tele.",
            )
            return@withContext
        }
        val plan = pendingPlan
        val snapshot = pendingSnapshot
        val selected = state.value.cleanupSelected.toList()
        log("confirmado: ${selected.size} app(s) para apagar")
        var record = job.record
        if (plan != null && snapshot != null && selected.isNotEmpty()) {
            state.value = state.value.copy(
                phase = Phase.WORKING,
                busyId = record.id,
                message = "Guardando la copia de ${record.displayName} antes de limpiar…",
            )
            val debloater = debloater()
            val backup = try {
                debloater.backup(snapshot, plan.list?.id.orEmpty())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            }
            if (backup == null) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "No pude leer los ajustes de la tele para la copia. No se apagó nada.",
                )
                TvAdb.disconnectQuietly()
                return@withContext
            }
            val file = runCatching { TvBackupStore.save(context, record.id, backup) }.getOrNull()
            if (file == null) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    message = "No pude guardar la copia en el teléfono. No se apagó nada.",
                )
                TvAdb.disconnectQuietly()
                return@withContext
            }
            log("copia guardada: ${file.name} (${snapshot.installed.size} apps, ${snapshot.info.displayName})")
            val now = System.currentTimeMillis()
            record = record.copy(
                cleanupList = plan.list?.id.orEmpty(),
                backupFile = file.name,
                cleanupAt = now,
                updatedAt = now,
            )
            TvAdbStore.upsert(context, record)

            state.value = state.value.copy(
                message = "Apagando ${selected.size} app(s) en ${record.displayName}…",
            )
            val outcome = try {
                debloater.run(selected)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                CleanupOutcome.Stopped(
                    emptyList(),
                    emptyMap(),
                    emptyMap(),
                    "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                )
            }
            val cleaned = when (outcome) {
                is CleanupOutcome.Done -> outcome.cleaned
                is CleanupOutcome.Stopped -> outcome.cleaned
            }
            val skipped = when (outcome) {
                is CleanupOutcome.Done -> outcome.skipped
                is CleanupOutcome.Stopped -> outcome.skipped
            }
            val failed = when (outcome) {
                is CleanupOutcome.Done -> outcome.failed
                is CleanupOutcome.Stopped -> outcome.failed
            }
            if (cleaned.isNotEmpty()) {
                record = record.copy(
                    cleaned = (record.cleaned + cleaned).distinct(),
                    updatedAt = System.currentTimeMillis(),
                )
                TvAdbStore.upsert(context, record)
                state.value = state.value.copy(tvs = TvAdbStore.load(context))
            }
            if (skipped.isNotEmpty()) log("la tele conservó ${skipped.size} app(s): ${skipped.keys.take(4).joinToString()}")
            if (failed.isNotEmpty()) log("no se pudieron apagar ${failed.size} app(s): ${failed.keys.take(4).joinToString()}")
            when (outcome) {
                is CleanupOutcome.Stopped -> {
                    state.value = state.value.copy(
                        phase = Phase.ERROR,
                        busyId = "",
                        tvs = TvAdbStore.load(context),
                        message = "La limpieza se detuvo: ${outcome.reason}. " +
                            if (cleaned.isEmpty()) {
                                "No se apagó nada; Vision+ no se instaló."
                            } else {
                                "Se apagaron ${cleaned.size} app(s) y quedan en «Restaurar». Vision+ no se instaló."
                            },
                    )
                    TvAdb.disconnectQuietly()
                    pending = null
                    pendingSnapshot = null
                    pendingPlan = null
                    return@withContext
                }
                is CleanupOutcome.Done -> {
                    log("limpieza: ${cleaned.size} apagada(s), ${skipped.size} conservada(s), ${failed.size} fallida(s)")
                }
            }
            pending = job.copy(record = record)
        }
        applyInternal(context, record, job.updateLauncher, job.updateApp, job.fireOs)
        pending = null
        pendingSnapshot = null
        pendingPlan = null
        } catch (t: Throwable) {
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            pending = null
            pendingSnapshot = null
            pendingPlan = null
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo terminar la limpieza: ${t.javaClass.simpleName}: ${t.message.orEmpty()}. " +
                    "Si se apagó alguna app, aparece en «Restaurar».",
            )
        } finally {
            confirming = false
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
            val debloater = debloater()
            val mirror = runCatching { debloater.readMirror() }.getOrDefault(emptySet())
            val targets = (record.disabled + record.cleaned + mirror).distinct()
            state.value = state.value.copy(message = "Reactivando ${targets.size} app(s) en ${record.displayName}…")
            var stillOff = emptyList<String>()
            for (pkg in targets) sh("pm enable $pkg")
            if (targets.isNotEmpty()) {
                stillOff = runCatching {
                    val disabled = DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED))
                    targets.filter { it in disabled }
                }.getOrDefault(emptyList())
            }
            runCatching { debloater.clearMirror() }
            runCatching { restoreDns(record) }
            if (record.originalHome.isNotBlank()) {
                sh("pm set-home-activity ${record.originalHome.removePrefix("package:")}")
            }
            val clean = record.copy(
                applied = false,
                disabled = emptyList(),
                cleaned = emptyList(),
                cleanupList = "",
                backupFile = "",
                cleanupAt = 0L,
                dnsMode = "",
                dnsSpecifier = "",
                dnsRecorded = false,
                originalHome = "",
                updatedAt = System.currentTimeMillis(),
            )
            state.value = state.value.copy(
                phase = if (stillOff.isEmpty()) Phase.DONE else Phase.ERROR,
                busyId = "",
                tvs = TvAdbStore.upsert(context, clean),
                message = when {
                    stillOff.isNotEmpty() -> "Reactivé ${targets.size - stillOff.size} app(s), pero " +
                        "${stillOff.size} siguen apagadas: ${stillOff.take(5).joinToString()}. Vuelve a pulsar «Restaurar»."
                    targets.isEmpty() -> "${record.displayName} vuelve a su Inicio de siempre."
                    else -> "Listo: reactivé ${targets.size} app(s) en ${record.displayName} y vuelve a su Inicio."
                },
            )
            TvAdb.disconnectQuietly()
        } catch (t: Throwable) {
            TvAdb.disconnectQuietly()
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                phase = Phase.ERROR,
                busyId = "",
                message = "No se pudo restaurar: ${t.message.orEmpty()}",
            )
        }
    }

    /** Cierra la herramienta abierta y suelta la conexión. */
    fun closeTool() {
        state.value = state.value.copy(tool = null)
        TvAdb.disconnectQuietly()
    }

    /** «Espacio»: lee el disco del televisor y deja la conexión lista para liberar caché. */
    suspend fun openSpace(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) return@withContext
        state.value = state.value.copy(
            tool = ToolState(ToolKind.SPACE, tvId, message = "Leyendo el espacio de ${record.displayName}…"),
        )
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    tool = ToolState(ToolKind.SPACE, tvId, loading = false, message = "No encontré la tele."),
                )
                return@withContext
            }
            log("leyendo espacio de ${record.displayName}…")
            val storage = debloater().storage()
            if (state.value.tool?.tvId != tvId) return@withContext
            state.value = state.value.copy(
                tool = ToolState(
                    ToolKind.SPACE,
                    tvId,
                    loading = false,
                    storage = storage,
                    message = if (storage == null) "No pude leer el espacio." else "",
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                tool = ToolState(ToolKind.SPACE, tvId, loading = false, message = "No pude leer el espacio."),
            )
        }
    }

    /** Libera la caché de las apps con `pm trim-caches` en segundo plano y refresca el espacio. */
    suspend fun clearSpace(context: Context) = withContext(Dispatchers.IO) {
        val tool = state.value.tool ?: return@withContext
        if (tool.kind != ToolKind.SPACE || tool.loading) return@withContext
        state.value = state.value.copy(tool = tool.copy(loading = true, message = "Liberando caché…"))
        val debloater = debloater()
        val ok = debloater.trimCaches { tick ->
            state.value = state.value.copy(
                tool = state.value.tool?.copy(message = "Liberando caché… ${tick * 2}s"),
            )
        }
        val storage = debloater.storage()
        state.value = state.value.copy(
            tool = state.value.tool?.copy(
                loading = false,
                storage = storage,
                message = if (ok) "Caché liberada" else "No pude liberar la caché. Inténtalo otra vez.",
            ),
        )
    }

    /** «Revisar»: compara lo que apagamos con lo que sigue apagado y con el Inicio actual. */
    suspend fun checkTv(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) return@withContext
        state.value = state.value.copy(
            tool = ToolState(ToolKind.CHECK, tvId, message = "Revisando ${record.displayName}…"),
        )
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    tool = ToolState(ToolKind.CHECK, tvId, loading = false, message = "No encontré la tele."),
                )
                return@withContext
            }
            log("revisando ${record.displayName}…")
            val snapshot = debloater().snapshot()
            if (state.value.tool?.tvId != tvId) return@withContext
            val backOn = record.cleaned.filter { it in snapshot.installed && it !in snapshot.disabled }
            val stillOff = record.cleaned.count { it in snapshot.disabled }
            val homeChanged = record.applied && snapshot.home?.substringBefore('/') != TvHomeRules.OUR_PKG
            state.value = state.value.copy(
                tool = ToolState(
                    ToolKind.CHECK,
                    tvId,
                    loading = false,
                    check = TvCheckReport(
                        backOn = backOn,
                        homeChanged = homeChanged,
                        stillOff = stillOff,
                        cleanedTotal = record.cleaned.size,
                    ),
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                tool = ToolState(ToolKind.CHECK, tvId, loading = false, message = "No pude revisar la tele."),
            )
        }
    }

    /** «Anuncios»: Private DNS con AdGuard, recordando lo que había antes para poder restaurarlo. */
    suspend fun openAds(context: Context, tvId: String) = withContext(Dispatchers.IO) {
        val record = TvAdbStore.find(context, tvId)
        if (record == null) return@withContext
        state.value = state.value.copy(
            tool = ToolState(ToolKind.ADS, tvId, message = "Leyendo los anuncios de ${record.displayName}…"),
        )
        try {
            if (!connectTo(context, record.host, record.id)) {
                state.value = state.value.copy(
                    tool = ToolState(ToolKind.ADS, tvId, loading = false, message = "No encontré la tele."),
                )
                return@withContext
            }
            log("leyendo Private DNS…")
            val (mode, host) = readDns()
            val sdk = sh("getprop ro.build.version.sdk", 8_000).trim().toIntOrNull() ?: 0
            val on = mode == "hostname" && host in setOf(DNS_ADS, DNS_FAMILY)
            if (state.value.tool?.tvId != tvId) return@withContext
            state.value = state.value.copy(
                tool = ToolState(
                    ToolKind.ADS,
                    tvId,
                    loading = false,
                    dnsOn = on,
                    dnsHost = host,
                    message = if (sdk in 1 until 28) "Esta tele es demasiado vieja para Private DNS." else "",
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            state.value = state.value.copy(
                tool = ToolState(ToolKind.ADS, tvId, loading = false, message = "No pude leer los anuncios."),
            )
        }
    }

    suspend fun enableAds(context: Context, family: Boolean) = withContext(Dispatchers.IO) {
        val tool = state.value.tool ?: return@withContext
        if (tool.kind != ToolKind.ADS || tool.loading) return@withContext
        val record = TvAdbStore.find(context, tool.tvId) ?: return@withContext
        state.value = state.value.copy(tool = tool.copy(loading = true, message = "Activando el bloqueo…"))
        val (modeBefore, hostBefore) = readDns()
        val saved = if (record.dnsRecorded) record else record.copy(
            dnsMode = modeBefore,
            dnsSpecifier = hostBefore,
            dnsRecorded = true,
            updatedAt = System.currentTimeMillis(),
        )
        TvAdbStore.upsert(context, saved)
        val host = if (family) DNS_FAMILY else DNS_ADS
        applyDns("hostname", host)
        val (mode, read) = readDns()
        val on = mode == "hostname" && read == host
        state.value = state.value.copy(
            tool = state.value.tool?.copy(
                loading = false,
                dnsOn = on,
                dnsHost = read,
                message = if (on) "Bloqueo activo" else "La tele no aceptó el cambio.",
            ),
        )
    }

    suspend fun disableAds(context: Context) = withContext(Dispatchers.IO) {
        val tool = state.value.tool ?: return@withContext
        if (tool.kind != ToolKind.ADS || tool.loading) return@withContext
        val record = TvAdbStore.find(context, tool.tvId) ?: return@withContext
        state.value = state.value.copy(tool = tool.copy(loading = true, message = "Desactivando el bloqueo…"))
        if (record.dnsRecorded) {
            applyDns(record.dnsMode, record.dnsSpecifier)
            TvAdbStore.upsert(
                context,
                record.copy(dnsMode = "", dnsSpecifier = "", dnsRecorded = false, updatedAt = System.currentTimeMillis()),
            )
        } else {
            // El DNS ya era de AdGuard (o de otro): guarda lo que había para que «Restaurar» lo devuelva.
            val (modeBefore, hostBefore) = readDns()
            TvAdbStore.upsert(
                context,
                record.copy(
                    dnsMode = modeBefore,
                    dnsSpecifier = hostBefore,
                    dnsRecorded = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            applyDns("off", "")
        }
        val (mode, host) = readDns()
        state.value = state.value.copy(
            tool = state.value.tool?.copy(
                loading = false,
                dnsOn = mode == "hostname" && host in setOf(DNS_ADS, DNS_FAMILY),
                dnsHost = host,
                message = "Bloqueo desactivado",
            ),
        )
    }

    private suspend fun readDns(): Pair<String, String> {
        fun clean(value: String) = value.trim().takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
        val mode = clean(sh("settings get global private_dns_mode", 8_000))
        val host = clean(sh("settings get global private_dns_specifier", 8_000))
        return (mode.takeIf { it in DNS_MODES }.orEmpty()) to host
    }

    private suspend fun applyDns(mode: String, host: String) {
        val safeMode = mode.takeIf { it in DNS_MODES } ?: "off"
        val safeHost = host.takeIf { it.matches(Regex("[A-Za-z0-9.-]{1,253}")) }.orEmpty()
        if (safeHost.isBlank()) sh("settings delete global private_dns_specifier", 8_000)
        else sh("settings put global private_dns_specifier $safeHost", 8_000)
        sh("settings put global private_dns_mode $safeMode", 8_000)
    }

    /** Devuelve el Private DNS original si Vision+ lo cambió. */
    private suspend fun restoreDns(record: TvRecord) {
        if (!record.dnsRecorded) return
        applyDns(record.dnsMode, record.dnsSpecifier)
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
        val identity = TvAdbStore.identityFrom(androidId, serial)
        log("identificada: ${model.trim().ifBlank { "(sin modelo)" }} · id=${identity.ifBlank { "(sin id)" }}")
        return identity to model.trim()
    }

    /**
     * Conecta con UNA tele: prueba primero el host conocido (el del emparejamiento), luego el
     * resto de puertos visibles y, al final, el ADB clásico del 5555 de las teles viejas.
     * Antes de tocar nada compara la identidad: si hay dos teles y ninguna coincide, no se
     * aplica nada en la equivocada.
     */
    private suspend fun connectTo(context: Context, host: String, id: String): Boolean {
        TvAdb.init(context)
        if (TvAdb.isConnected()) TvAdb.disconnectQuietly()

        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicast = wifi?.createMulticastLock("VisionTV")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val endpoints = try {
            AdbDiscovery.findAll(context, AdbDiscovery.SERVICE_CONNECT, 10_000, 4)
        } finally {
            runCatching { multicast?.release() }
        }.sortedByDescending { it.host == host }
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
            // El ADB clásico de Android 8/9 no tiene emparejamiento: la llave se autoriza con el
            // aviso «¿Permitir depuración?» del televisor. Kadb envía la llave pública y hay
            // que dar tiempo a aceptarlo (si no, el aviso nunca aparece).
            val ok = runCatching { TvAdb.connectAllowingAuthorization(host, 5555) }.getOrDefault(false)
            log("  $host:5555 → ${if (ok) "conectado" else "sin conexión"}")
            if (ok) {
                val identity = identify().first
                if (id.isBlank() || identity.isBlank() || identity == id || id.startsWith("host:")) return true
                TvAdb.disconnectQuietly()
            }
            // Aunque falle, no se deja el socket abierto esperando autorización.
            TvAdb.disconnectQuietly()
        }
        return false
    }

    /** La versión de un paquete en la tele AHORA ("" si no está instalado). */
    private suspend fun packageVersionOnTv(pkg: String): String {
        val out = sh("dumpsys package $pkg | grep -m1 versionName", 12_000)
        return TvHomeRules.parseVersionName(out).orEmpty()
    }

    /** Fire TV: se puede limpiar e instalar, pero no cambiar el Inicio. */
    private suspend fun detectFireOs(): Boolean {
        val brand = sh("getprop ro.product.brand", 8_000).trim()
        if (brand.equals("Amazon", ignoreCase = true)) return true
        val model = sh("getprop ro.product.model", 8_000).trim()
        return model.startsWith("AFT", ignoreCase = true)
    }

    private suspend fun launcherVersionOnTv(): String = packageVersionOnTv(TvHomeRules.OUR_PKG)

    /**
     * Deja la tele despierta y con el verificador de instalaciones por ADB apagado. En Android
     * 8/9 «Verificar apps por USB» puede rechazar el APK con `INSTALL_FAILED_VERIFICATION_FAILURE`
     * sin que la persona pueda hacer nada desde el televisor.
     */
    private suspend fun prepareInstall() {
        sh("input keyevent KEYCODE_WAKEUP", 8_000)
        val verifier = sh("settings get global verifier_verify_adb_installs", 8_000).trim()
        if (verifier != "0") {
            val was = verifier.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "1"
            log("desactivo la verificación de instalaciones por ADB (estaba en $was)")
            sh("settings put global verifier_verify_adb_installs 0", 8_000)
        }
    }

    /**
     * Instala y, si la tele contesta que el verificador bloqueó el paquete, apaga el verificador
     * y reintenta una vez. El bloqueo se detecta por el texto de `pm`, no por adivinar.
     */
    private suspend fun installWithVerificationRetry(
        label: String,
        timeoutMs: Long,
        install: suspend () -> String?,
    ): String? = coroutineScope {
        var seconds = 0
        val heartbeat = launch {
            while (isActive) {
                delay(10_000)
                seconds += 10
                log("$label: instalando… ${seconds}s")
            }
        }
        try {
            var output = withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.IO) { runCatching { install() }.getOrNull() }
            }
            if (TvHomeRules.looksLikeVerificationBlocked(output)) {
                log("la tele bloqueó $label por verificación; apago el verificador y reintento")
                sh("settings put global verifier_verify_adb_installs 0", 8_000)
                sh("settings put global package_verifier_enable 0", 8_000)
                output = withTimeoutOrNull(timeoutMs) {
                    withContext(Dispatchers.IO) { runCatching { install() }.getOrNull() }
                }
            }
            output
        } finally {
            heartbeat.cancel()
        }
    }

    /**
     * Deja la última Vision+ en la tele. Si no está, la instala; con [update], la sube a la
     * versión publicada. Devuelve la versión que quedó (`""` si no hay ninguna y no se pudo
     * instalar: en ese caso el flujo NO toca el Inicio, para no dejar la tele sin app que abrir).
     */
    private suspend fun ensureVisionPlus(context: Context, update: Boolean): String {
        val pkg = HomeContract.APP_PACKAGE
        val onTv = packageVersionOnTv(pkg)
        log(if (onTv.isBlank()) "Vision+ no está en la tele" else "la tele tiene Vision+ $onTv")
        if (onTv.isNotBlank() && !update) return onTv

        // La fuente de verdad es el release nuevo. Se consulta SIEMPRE antes de decidir
        // entre la copia del teléfono y la descarga, para no instalar una copia vieja.
        val latest = runCatching { TvAppPackage.latestVersion(context) }.getOrNull()
        if (onTv.isNotBlank() && !latest.isNullOrBlank() && !TvLauncherPackage.isNewer(latest, onTv)) {
            log("Vision+ ya está al día ($onTv)")
            return onTv
        }
        if (onTv.isNotBlank() && latest.isNullOrBlank()) {
            log("no pude comprobar la última versión de Vision+; pruebo la copia del teléfono")
        } else if (!latest.isNullOrBlank()) {
            log("hay una Vision+ más nueva: $latest")
        }

        // La copia que ya está instalada en este teléfono es la misma que se va a usar
        // solo si no es más vieja que el release. Si el release es más nuevo, se baja.
        val local = runCatching { TvAppPackage.local(context) }.getOrNull()
        val downloadRelease = TvAppPackage.shouldDownloadRelease(latest, local?.versionName)
        if (!downloadRelease && local != null &&
            (onTv.isBlank() || TvLauncherPackage.isNewer(local.versionName, onTv))
        ) {
            TvAppPackage.announce(local)
            prepareInstall()
            state.value = state.value.copy(
                message = if (onTv.isBlank()) {
                    "Instalando Vision+ ${local.versionName} desde este teléfono…"
                } else {
                    "Actualizando Vision+ $onTv a ${local.versionName} desde este teléfono…"
                },
            )
            log(
                "usando Vision+ ${local.versionName} del teléfono " +
                    "(${local.file.length() / 1_048_576} MB, sin descargar del release)",
            )
            val localOutput = installWithVerificationRetry("Vision+ (teléfono)", 15L * 60_000) {
                TvAdb.installApkFileOutput(local.file)
            }
            if (localOutput.orEmpty().contains("Success", ignoreCase = true)) {
                log("Vision+ ${local.versionName} instalado desde el teléfono")
                val after = packageVersionOnTv(pkg)
                return after.ifBlank { local.versionName }
            }
            val localReason = TvHomeRules.installReason(localOutput)
            log(
                "no se pudo instalar la copia del teléfono" +
                    (if (localReason.isBlank()) "" else ": $localReason") + "; pruebo el release",
            )
        } else if (local != null && downloadRelease) {
            log(
                "el release ($latest) es más nuevo que la copia del teléfono " +
                    "(${local.versionName}); se descarga del canal nuevo",
            )
        }

        state.value = state.value.copy(message = "Buscando la última Vision+ en el release…")
        val apk = TvAppPackage.resolve(context)
        if (apk == null) {
            log("no pude descargar Vision+")
            state.value = state.value.copy(message = "No pude descargar Vision+. Revisa la conexión del teléfono.")
            return onTv
        }
        if (onTv.isNotBlank() && !TvLauncherPackage.isNewer(apk.versionName, onTv)) {
            log("la tele ya tiene Vision+ $onTv")
            return onTv
        }
        prepareInstall()
        state.value = state.value.copy(
            message = if (onTv.isBlank()) {
                "Instalando Vision+ ${apk.versionName} en la tele… (puede tardar unos minutos)"
            } else {
                "Actualizando Vision+ $onTv a ${apk.versionName}… (puede tardar unos minutos)"
            },
        )
        log("instalando Vision+ ${apk.versionName} (${apk.file.length() / 1_048_576} MB)…")
        val output = installWithVerificationRetry("Vision+", 15L * 60_000) {
            TvAdb.installApkFileOutput(apk.file)
        }
        val ok = output.orEmpty().contains("Success", ignoreCase = true)
        if (ok) {
            log("Vision+ ${apk.versionName} instalado")
        } else {
            val reason = TvHomeRules.installReason(output)
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
        state.value = state.value.copy(message = "Buscando Vision+ Home en el release…")
        val apk = TvLauncherPackage.resolve(context)
        if (apk == null) {
            log("no pude obtener el APK de Vision+ Home")
            state.value = state.value.copy(message = "No pude obtener Vision+ Home. Revisa «Detalles».")
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
        prepareInstall()
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
        val output = installWithVerificationRetry("Vision+ Home", 120_000) {
            TvAdb.installApkOutput(bytes)
        }
        val ok = output.orEmpty().contains("Success", ignoreCase = true)
        if (ok) {
            log("Vision+ Home ${apk.versionName} instalado")
        } else {
            val reason = TvHomeRules.installReason(output)
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
        fireOs: Boolean? = null,
    ) {
        state.value = state.value.copy(
            phase = Phase.WORKING,
            busyId = record.id,
            message = "Conectado con ${record.displayName}. Preparando Vision+…",
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

        // Fire TV: Amazon no permite que otra app sea el Inicio. Se instala Vision+ y su Home,
        // pero no se toca el launcher de Amazon; intentarlo dejaría la tele inservible.
        val onFireOs = fireOs ?: detectFireOs()
        if (onFireOs) {
            val done = current.copy(applied = false, updatedAt = System.currentTimeMillis())
            state.value = state.value.copy(
                phase = Phase.DONE,
                busyId = "",
                tvs = TvAdbStore.upsert(context, done),
                message = "Vision+ $appVersion y Vision+ Home $version quedaron instalados en ${done.displayName}. " +
                    "Fire TV no permite cambiar el Inicio: se abre desde «Ver todas las apps». " +
                    if (done.cleaned.isEmpty()) "" else "Apagué ${done.cleaned.size} app(s) de su lista.",
            )
            TvAdb.disconnectQuietly()
            return
        }

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

        setHomeActivity()
        val disabled = current.disabled.toMutableSet()

        repeat(3) { attempt ->
            var effective = probeEffectiveHome()
            // En Android 8/9 el sistema enseña un Inicio de respaldo mientras arranca el elegido.
            // No se retira (dejaría la tele sin pantalla): se espera y se vuelve a mirar.
            if (TvHomeRules.isTransientHome(effective)) {
                log("  la tele está en su pantalla de respaldo ($effective); abro la nuestra y espero")
                launchOurHome()
                delay(2_500)
                effective = probeEffectiveHome()
            }
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
                        "Listo: ${done.displayName} abre Vision+ Home con Vision+ $appVersion." +
                            if (done.cleaned.isEmpty()) "" else " Apagué ${done.cleaned.size} app(s) de su lista."
                    } else {
                        "Listo: ${done.displayName} abre Vision+ Home con Vision+ $appVersion. Retiré " +
                            "${disabled.size} Inicio(s) que lo tapaban (puedes devolverlos con «Restaurar»)." +
                            if (done.cleaned.isEmpty()) "" else " Apagué ${done.cleaned.size} app(s) de su lista."
                    },
                )
                TvAdb.disconnectQuietly()
                return
            }
            if (effective == null) {
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, current.copy(updatedAt = System.currentTimeMillis())),
                    message = "No pude leer qué abre ${record.displayName}. Revisa que la tele esté despierta y " +
                        "pulsa «Aplicar» otra vez.",
                )
                TvAdb.disconnectQuietly()
                return
            }
            if (TvHomeRules.isTransientHome(effective)) {
                val kept = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, kept),
                    message = "${record.displayName} sigue en su pantalla de respaldo («$effective»). " +
                        "Reinicia el televisor y pulsa «Aplicar»: no toco ese Inicio porque dejaría la tele " +
                        "sin pantalla.",
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
            if (isPersistent(effective)) {
                val kept = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, kept),
                    message = "${record.displayName} sigue abriendo «$effective», pero es una app persistente " +
                        "del sistema: no la retiro para no dejarla sin arranque. Reinicia la tele y vuelve a " +
                        "abrir Vision+ Home desde sus apps.",
                )
                TvAdb.disconnectQuietly()
                return
            }
            if (effective == Launchers.SETUPWRAITH && !setupWraithSafe()) {
                val kept = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, kept),
                    message = "El Inicio cayó en la app de configuración de Google TV y no la retiro: puede ser " +
                        "dueña del Wi-Fi o la configuración inicial no ha terminado. Reinicia la tele y vuelve a " +
                        "intentar, o fija Vision+ Home desde Ajustes → Aplicaciones → Inicio.",
                )
                TvAdb.disconnectQuietly()
                return
            }
            state.value = state.value.copy(
                phase = Phase.WORKING,
                busyId = record.id,
                message = "${record.displayName} sigue abriendo «$effective»; lo retiro (intento ${attempt + 1} de 3)…",
            )
            // La lista de deshacer viaja también en la tele: si se pierde el teléfono, el Inicio
            // de fábrica se puede recuperar desde el propio televisor.
            val mirror = sh(DebloatCommands.mirror(listOf(effective!!)), 10_000)
            if (!DebloatParse.lines(mirror).contains("ok")) {
                val kept = current.copy(disabled = disabled.toList(), updatedAt = System.currentTimeMillis())
                state.value = state.value.copy(
                    phase = Phase.ERROR,
                    busyId = "",
                    tvs = TvAdbStore.upsert(context, kept),
                    message = "No pude guardar en la tele la lista para devolver su Inicio. No retiro «$effective» " +
                        "para no perder la forma de volver.",
                )
                TvAdb.disconnectQuietly()
                return
            }
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

    /** Una app PERSISTENT es parte del arranque: apagarla puede dejar la tele sin interfaz. */
    private suspend fun isPersistent(pkg: String): Boolean {
        val flags = sh("dumpsys package $pkg | grep -m1 pkgFlags", 12_000).trim()
        // Sin respuesta se falla cerrado: mejor dejar el Inicio de fábrica que arriesgar el arranque.
        return flags.isBlank() || flags.contains("PERSISTENT", ignoreCase = true)
    }

    /**
     * Google TV puede tener `setupwraith` como Inicio de respaldo. Apagarlo sin comprobar puede
     * borrar la Wi-Fi guardada (si es su dueño) o romper una configuración inicial a medias.
     */
    private suspend fun setupWraithSafe(): Boolean {
        val provisioned = sh("settings get global device_provisioned", 8_000).trim()
        val complete = sh("settings get secure user_setup_complete", 8_000).trim()
        if (provisioned != "1" || complete != "1") {
            log("setupwraith: configuración inicial sin terminar; no se apaga")
            return false
        }
        val owners = sh("dumpsys wifi | grep -o 'cname=[^ ]*'", 12_000)
        if (owners.contains(Launchers.SETUPWRAITH, ignoreCase = true)) {
            log("setupwraith: es dueño del Wi-Fi guardado; no se apaga")
            return false
        }
        return true
    }

    /** Fija el Inicio y, si el `pm` del firmware no trae el comando, prueba la forma `cmd package`. */
    private suspend fun setHomeActivity() {
        val output = sh("pm set-home-activity ${TvHomeRules.OUR_HOME}", 15_000)
        val failed = listOf("Error", "Exception", "Unknown", "not found")
            .any { output.contains(it, ignoreCase = true) }
        if (failed) {
            log("«pm set-home-activity» no funcionó; pruebo «cmd package set-home-activity»")
            sh("cmd package set-home-activity ${TvHomeRules.OUR_HOME}", 15_000)
        }
    }

    /**
     * Abre Vision+ Home a mano. En teles viejas el sistema puede quedarse en su pantalla de
     * respaldo varios segundos después de retirar el Inicio de fábrica; lanzar la actividad
     * correcta lo saca de ahí sin esperar a un reinicio.
     */
    private suspend fun launchOurHome() {
        sh(
            "am start --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME " +
                "-n ${TvHomeRules.OUR_HOME}",
            15_000,
        )
    }

    /**
     * El paquete que abre la tele AHORA. `resolve-activity` puede contestar el que tiene más
     * prioridad y no el que usa el sistema, así que se pulsa Inicio y se lee quién quedó al
     * frente. En Android 8/9 el Inicio elegido tarda un poco en relevar al de fábrica: se mira
     * varias veces antes de darlo por perdido. Se filtra con `grep` en el propio televisor: el
     * `dumpsys` completo son megabytes y por ADB tarda decenas de segundos.
     */
    private suspend fun probeEffectiveHome(): String? {
        sh("input keyevent KEYCODE_HOME", 10_000)
        var last: String? = null
        repeat(4) { index ->
            delay(if (index == 0) 1_200 else 900)
            val dumpsys = sh(
                "dumpsys activity activities | grep -m1 -E " +
                    "'(topResumedActivity|mResumedActivity|mFocusedActivity|ResumedActivity).*ActivityRecord'",
                15_000,
            )
            val resumed = TvHomeRules.parseResumedPackage(dumpsys)
            if (resumed != null) {
                last = resumed
                if (resumed == TvHomeRules.OUR_PKG) return resumed
            }
        }
        if (last != null) return last
        val resolve =
            sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        return TvHomeRules.packageOf(TvHomeRules.parseResolveActivity(resolve))
    }
}
