package com.chronosbvrx.visionplus.data.debloat

import android.content.Context
import java.io.File
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Motor de limpieza por ADB. Portado del núcleo de TidyTV (MIT) y simplificado para Vision+:
 * - solo apaga (nunca desinstala), con `pm disable-user --user 0`;
 * - escribe la lista de deshacer en el propio televisor antes de tocar nada;
 * - verifica cada lote con `pm list packages -d`;
 * - respeta los paquetes protegidos, los Inicios y lo que la lista manda conservar.
 */

internal class TvCommandError(message: String) : Exception(message)

internal data class TvSnapshot(
    val info: TvInfo,
    val props: Map<String, String>,
    val installed: Set<String>,
    val disabled: Set<String>,
    val home: String?,
    /** IMEs, servicios de accesibilidad y entradas de TV en uso: nunca se apagan. */
    val liveProtected: Set<String>,
)

/** Plan que se enseña antes de tocar la tele. */
internal data class CleanupPlan(
    val tv: TvInfo,
    /** Lista elegida; null cuando ningún modelo coincide. */
    val match: ListMatch?,
    val candidates: List<CleanupCandidate>,
    /** Paquetes que se apagarán con los grupos elegidos. */
    val selected: List<String>,
    val groups: Set<CleanupGroup>,
    val tested: Boolean,
) {
    val list: DebloatList? get() = match?.list
    val selectedCount: Int get() = selected.size
    fun countIn(group: CleanupGroup): Int =
        candidates.count { it.group == group && !it.optional }
}

internal sealed interface CleanupOutcome {
    data class Done(
        val cleaned: List<String>,
        val skipped: Map<String, String>,
        val failed: Map<String, String>,
    ) : CleanupOutcome

    /** La tele dejó de contestar; [cleaned] ya está apagado y queda registrado. */
    data class Stopped(
        val cleaned: List<String>,
        val skipped: Map<String, String>,
        val failed: Map<String, String>,
        val reason: String,
    ) : CleanupOutcome
}

internal object DebloatCommands {
    const val MIRROR_DIR = "/data/local/tmp/visionplus"
    const val MIRROR_FILE = "$MIRROR_DIR/disabled.txt"
    private const val HOME_ARGS = "-a android.intent.action.MAIN -c android.intent.category.HOME"

    const val GETPROP = "getprop"
    const val LIST_PACKAGES = "pm list packages"
    const val LIST_DISABLED = "pm list packages -d"
    const val RESOLVE_HOME = "cmd package resolve-activity --brief $HOME_ARGS"
    const val TV_INPUTS = "dumpsys tv_input"
    const val ENABLED_IMES = "settings get secure enabled_input_methods"
    const val DEFAULT_IME = "settings get secure default_input_method"
    const val A11Y_SERVICES = "settings get secure enabled_accessibility_services"
    const val READ_MIRROR = "if [ -e $MIRROR_FILE ]; then cat $MIRROR_FILE; fi; echo END"
    const val CLEAR_MIRROR = "rm -f $MIRROR_FILE; echo cleared"
    const val TRIM_DONE = "$MIRROR_DIR/trim.done"
    const val TRIM_BACKGROUND =
        "mkdir -p $MIRROR_DIR && rm -f $TRIM_DONE && " +
            "(nohup sh -c 'pm trim-caches 999G >/dev/null 2>&1; echo done > $TRIM_DONE' >/dev/null 2>&1 &); echo started"
    const val TRIM_STATUS = "if [ -e $TRIM_DONE ]; then echo done; else echo running; fi"

    fun disable(pkg: String) = "pm disable-user --user 0 ${PackageName.require(pkg)}"
    fun enable(pkg: String) = "pm enable ${PackageName.require(pkg)}"

    /** Añade cada paquete a la lista de deshacer del televisor, sin duplicados. */
    fun mirror(pkgs: Collection<String>): String {
        pkgs.forEach { PackageName.require(it) }
        return "mkdir -p $MIRROR_DIR && for p in ${pkgs.joinToString(" ")}; do " +
            "grep -qxF \$p $MIRROR_FILE 2>/dev/null || printf '%s\\n' \$p >> $MIRROR_FILE; done && echo ok"
    }

    /** Una ida y vuelta para varios paquetes: imprime "@pkg" y su línea pkgFlags (o nada). */
    fun flags(pkgs: Collection<String>): String {
        pkgs.forEach { PackageName.require(it) }
        return "for p in ${pkgs.joinToString(" ")}; do echo \"@\$p\"; " +
            "dumpsys package \$p | grep -m1 pkgFlags; done"
    }

    fun settingsList(namespace: String): String {
        require(namespace in setOf("global", "secure", "system"))
        return "settings list $namespace"
    }

    fun isInstalled(pkg: String) = "pm list packages ${PackageName.require(pkg)}"
}

internal object DebloatParse {
    private val PROP = Regex("^\\[([^]]+)]: \\[(.*)]$")
    private val COMPONENT = Regex("([A-Za-z_][A-Za-z0-9_.]*)/([A-Za-z0-9_.$]+)")

    fun lines(out: String) = out.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun props(out: String): Map<String, String> =
        lines(out).mapNotNull { PROP.find(it)?.let { m -> m.groupValues[1] to m.groupValues[2] } }.toMap()

    fun tvInfo(props: Map<String, String>): TvInfo = TvInfo(
        model = props["ro.product.model"].orEmpty(),
        device = props["ro.product.device"].orEmpty(),
        product = props["ro.product.name"].orEmpty(),
        brand = props["ro.product.brand"].orEmpty(),
        manufacturer = props["ro.product.manufacturer"].orEmpty(),
        sdk = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0,
        release = props["ro.build.version.release"].orEmpty(),
        serial = listOf("ro.serialno", "ro.boot.serialno")
            .firstNotNullOfOrNull { key -> props[key]?.takeIf { it.isNotBlank() && it != "unknown" } }
            ?: "${props["ro.product.brand"]}-${props["ro.product.model"]}-${props["ro.product.device"]}",
    )

    /**
     * `pm list packages` tolera líneas sueltas (algunos firmwares imprimen avisos), pero exige
     * que haya al menos un paquete válido: una respuesta vacía no se toma como «no hay apps».
     */
    fun packages(out: String, command: String = DebloatCommands.LIST_PACKAGES): Set<String> {
        val names = lines(out)
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringAfterLast('=') }
            .filter { PackageName.isValid(it) }
            .toSet()
        if (names.isEmpty() && lines(out).isNotEmpty()) {
            throw TvCommandError("respuesta inesperada de $command: ${out.take(160)}")
        }
        return names
    }

    /** Última línea de `resolve-activity --brief`: "pkg/.Activity", o null. */
    fun component(out: String): String? {
        val last = lines(out).lastOrNull() ?: return null
        return COMPONENT.matchEntire(last)?.value
    }

    /** Espacio y cachés de `dumpsys diskstats`; null si el firmware no lo publica. */
    fun diskStats(out: String): TvStorage? {
        val free = Regex("Data-Free: (\\d+)K / (\\d+)K total").find(out) ?: return null
        fun array(name: String): List<String> =
            Regex("$name: \\[(.*?)]").find(out)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim().trim('"') }.orEmpty()
        val names = array("Package Names")
        val caches = array("Cache Sizes").map { it.toLongOrNull() ?: 0L }
        val byPackage = names.indices.mapNotNull { index ->
            val mb = (caches.getOrElse(index) { 0L } / 1024L).toInt()
            if (mb > 0 && PackageName.isValid(names[index])) names[index] to mb else null
        }.sortedByDescending { it.second }
        return TvStorage(
            freeKb = free.groupValues[1].toLongOrNull(),
            totalKb = free.groupValues[2].toLongOrNull(),
            cacheMb = byPackage.sumOf { it.second },
            top = byPackage.take(6),
        )
    }

    fun mirror(out: String): Set<String>? {
        val lines = lines(out)
        if (lines.lastOrNull() != "END") return null
        return lines.dropLast(1).filter { PackageName.isValid(it) }.toSet()
    }

    /** Paquetes que son dueños de algo vivo: IMEs, accesibilidad y entradas de TV. */
    fun ownerPackages(vararg outs: String): Set<String> =
        outs.flatMap { out ->
            Regex("([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/")
                .findAll(out)
                .map { it.groupValues[1] }
                .toList()
        }.filter { PackageName.isValid(it) }.toSet()

    fun flags(out: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var current: String? = null
        for (line in lines(out)) {
            if (line.startsWith("@")) {
                current = line.removePrefix("@").also { result[it] = "" }
            } else if (current != null && line.contains("pkgFlags")) {
                result[current] = line
            }
        }
        return result
    }
}

/** Una instantánea del televisor antes de cambiarlo. Es obligatoria antes de apagar algo. */
internal data class TvStorage(
    val freeKb: Long?,
    val totalKb: Long?,
    val cacheMb: Int,
    /** Paquetes con más caché, de mayor a menor. */
    val top: List<Pair<String, Int>>,
)

internal data class TvBackup(
    val takenAt: Long,
    val serial: String,
    val model: String,
    val sdk: Int,
    val props: Map<String, String>,
    val packages: List<String>,
    val disabled: List<String>,
    val home: String?,
    val settings: Map<String, String>,
    val listId: String,
) {
    fun toJson(): String {
        val root = JSONObject()
            .put("takenAt", takenAt)
            .put("serial", serial)
            .put("model", model)
            .put("sdk", sdk)
            .put("props", JSONObject(props as Map<*, *>))
            .put("packages", JSONArray(packages))
            .put("disabled", JSONArray(disabled))
            .put("home", home ?: JSONObject.NULL)
            .put("settings", JSONObject(settings as Map<*, *>))
            .put("listId", listId)
        return root.toString()
    }

    companion object {
        fun fromJson(text: String): TvBackup {
            val root = JSONObject(text)
            fun objects(key: String): Map<String, String> {
                val obj = root.optJSONObject(key) ?: return emptyMap()
                return obj.keys().asSequence().associateWith { obj.optString(it) }
            }
            fun array(key: String): List<String> {
                val arr = root.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            }
            return TvBackup(
                takenAt = root.optLong("takenAt"),
                serial = root.optString("serial"),
                model = root.optString("model"),
                sdk = root.optInt("sdk"),
                props = objects("props"),
                packages = array("packages"),
                disabled = array("disabled"),
                home = root.optString("home").takeIf { it.isNotBlank() && it != "null" },
                settings = objects("settings"),
                listId = root.optString("listId"),
            )
        }
    }
}

/** Guarda la copia en el teléfono: `filesDir/tv_backups/<id>.json`. */
internal object TvBackupStore {
    fun save(context: Context, tvId: String, backup: TvBackup): File {
        val dir = File(context.filesDir, "tv_backups").apply { mkdirs() }
        val safe = tvId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80).ifBlank { "tele" }
        val file = File(dir, "$safe.json")
        val part = File(dir, "$safe.json.part")
        part.writeText(backup.toJson())
        if (!part.renameTo(file)) {
            file.writeText(backup.toJson())
            part.delete()
        }
        return file
    }

    fun file(context: Context, tvId: String): File? {
        val safe = tvId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80).ifBlank { "tele" }
        return File(File(context.filesDir, "tv_backups"), "$safe.json").takeIf { it.isFile }
    }
}

/**
 * Habla con la tele a través de una función que devuelve stdout+stderr. `TvHomeSetup` le pasa
 * `TvAdb.shell`; los tests le pasan un televisor falso.
 */
internal class TvDebloater(
    private val shell: suspend (String) -> String,
    private val log: (String) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val batchSize: Int = 5,
) {
    companion object {
        /** Android guarda el estado de los paquetes con retraso; reiniciar antes puede deshacerlo. */
        const val SAVE_WAIT_MS = 15_000L
    }

    private suspend fun sh(command: String): String {
        log("$ $command")
        val out = shell(command)
        if (out.startsWith("ERROR ", ignoreCase = true)) {
            log("  !! ${out.take(160)}")
            throw TvCommandError(out.take(220))
        }
        val first = out.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160)
        if (first.isNotBlank()) log("  $first")
        return out
    }

    /** Lee todo lo necesario para planificar sin cambiar nada. */
    suspend fun snapshot(): TvSnapshot {
        val props = DebloatParse.props(sh(DebloatCommands.GETPROP))
        if (props.isEmpty()) throw TvCommandError("la tele no devolvió getprop")
        val info = DebloatParse.tvInfo(props)
        val installed = DebloatParse.packages(sh(DebloatCommands.LIST_PACKAGES))
        val disabled = runCatching { DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED)) }.getOrDefault(emptySet())
        val home = DebloatParse.component(sh(DebloatCommands.RESOLVE_HOME))
        val live = runCatching {
            DebloatParse.ownerPackages(
                sh(DebloatCommands.ENABLED_IMES),
                sh(DebloatCommands.DEFAULT_IME),
                sh(DebloatCommands.A11Y_SERVICES),
                sh(DebloatCommands.TV_INPUTS),
            )
        }.getOrDefault(emptySet())
        return TvSnapshot(info, props, installed, disabled, home, live)
    }

    /** Lee la copia completa: props, paquetes, apagados, Inicio y ajustes. */
    suspend fun backup(snapshot: TvSnapshot, listId: String, now: Long = System.currentTimeMillis()): TvBackup {
        val settings = linkedMapOf<String, String>()
        for (namespace in listOf("global", "secure", "system")) {
            runCatching { settings[namespace] = sh(DebloatCommands.settingsList(namespace)) }
        }
        return TvBackup(
            takenAt = now,
            serial = snapshot.info.serial,
            model = snapshot.info.displayName,
            sdk = snapshot.info.sdk,
            props = snapshot.props,
            packages = snapshot.installed.sorted(),
            disabled = snapshot.disabled.sorted(),
            home = snapshot.home,
            settings = settings,
            listId = listId,
        )
    }

    /**
     * Apaga [pkgs] en lotes. Antes de cada lote deja la lista de deshacer EN la tele; después
     * comprueba con `pm list packages -d` que de verdad quedaron apagados.
     *
     * Si un paquete protegido por el firmware se resiste, se anota y se sigue: la limpieza no
     * puede impedir que Vision+ se instale. Si la tele deja de contestar, se corta.
     */
    suspend fun run(pkgs: List<String>): CleanupOutcome {
        val requested = pkgs.distinct().filter { PackageName.isValid(it) }
        if (requested.isEmpty()) return CleanupOutcome.Done(emptyList(), emptyMap(), emptyMap())

        val cleaned = mutableListOf<String>()
        val skipped = linkedMapOf<String, String>()
        val failed = linkedMapOf<String, String>()
        try {
            val installed = DebloatParse.packages(sh(DebloatCommands.LIST_PACKAGES))
            var disabled = runCatching {
                DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED))
            }.getOrDefault(emptySet())
            val flags = runCatching {
                DebloatParse.flags(sh(DebloatCommands.flags(requested.filter { it in installed })))
            }.getOrDefault(emptyMap())

            val work = mutableListOf<String>()
            for (pkg in requested) {
                when {
                    Launchers.isHomeApp(pkg) || Protected.reason(pkg) != null ->
                        skipped[pkg] = "protegida por Vision+"
                    pkg !in installed -> skipped[pkg] = "no está instalada"
                    pkg in disabled -> skipped[pkg] = "ya estaba apagada"
                    flags[pkg].orEmpty().contains("PERSISTENT") -> skipped[pkg] = "app persistente del sistema"
                    else -> work += pkg
                }
            }

            for (batch in work.chunked(batchSize)) {
                if (!DebloatParse.lines(sh(DebloatCommands.mirror(batch))).contains("ok")) {
                    return CleanupOutcome.Stopped(
                        cleaned,
                        skipped,
                        failed,
                        "No pude guardar la lista de deshacer en la tele; no se cambió nada más.",
                    )
                }
                for (pkg in batch) {
                    log("apagando $pkg")
                    val out = sh(DebloatCommands.disable(pkg))
                    when {
                        out.contains("new state: disabled", ignoreCase = true) ||
                            out.contains("new state: disabled-user", ignoreCase = true) -> Unit
                        out.contains("Unknown package", ignoreCase = true) ||
                            out.contains("not installed", ignoreCase = true) ||
                            out.contains("not found", ignoreCase = true) -> skipped[pkg] = "no está instalada"
                        out.contains("protected", ignoreCase = true) ||
                            out.contains("Cannot disable", ignoreCase = true) ||
                            out.contains("Permission denial", ignoreCase = true) ->
                            skipped[pkg] = "el firmware la protege"
                        else -> failed[pkg] = out.trim().take(160).ifBlank { "sin respuesta" }
                    }
                }
                disabled = runCatching {
                    DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED))
                }.getOrDefault(disabled)
                for (pkg in batch) {
                    when {
                        pkg in disabled -> {
                            if (pkg !in cleaned) cleaned += pkg
                            failed.remove(pkg)
                            skipped.remove(pkg)
                            log("  ok")
                        }
                        pkg !in failed && pkg !in skipped -> failed[pkg] = "siguió encendida"
                    }
                }
            }
            if (cleaned.isNotEmpty()) {
                log("espero ${SAVE_WAIT_MS / 1000}s para que Android guarde el estado de los paquetes")
                sleep(SAVE_WAIT_MS)
            }
            return CleanupOutcome.Done(cleaned, skipped, failed)
        } catch (t: TvCommandError) {
            return CleanupOutcome.Stopped(cleaned, skipped, failed, t.message.orEmpty())
        }
    }

    /** Reactiva paquetes, verificando cada lote. Devuelve los que no volvieron. */
    suspend fun enableAll(pkgs: Collection<String>): List<String> {
        val targets = pkgs.distinct().filter { PackageName.isValid(it) }
        if (targets.isEmpty()) return emptyList()
        val disabled = runCatching {
            DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED))
        }.getOrDefault(emptySet())
        val todo = targets.filter { it in disabled }
        for (batch in todo.chunked(batchSize)) {
            for (pkg in batch) sh(DebloatCommands.enable(pkg))
        }
        if (todo.isEmpty()) return emptyList()
        val after = runCatching {
            DebloatParse.packages(sh(DebloatCommands.LIST_DISABLED))
        }.getOrDefault(disabled)
        return todo.filter { it in after }
    }

    suspend fun readMirror(): Set<String> =
        DebloatParse.mirror(runCatching { sh(DebloatCommands.READ_MIRROR) }.getOrDefault("")) ?: emptySet()

    /** Espacio libre y cachés que Android tiene contabilizadas. */
    suspend fun storage(): TvStorage? =
        runCatching { DebloatParse.diskStats(sh("dumpsys diskstats")) }.getOrNull()

    /**
     * `pm trim-caches` puede tardar; se lanza en segundo plano en la tele y se consulta el
     * archivo de fin. Así una caída de Wi-Fi no corta la limpieza.
     */
    suspend fun trimCaches(onTick: (Int) -> Unit = {}): Boolean {
        log("iniciando limpieza de caché en la tele…")
        val started = runCatching { sh(DebloatCommands.TRIM_BACKGROUND) }.getOrDefault("")
        if (!started.contains("started", ignoreCase = true)) return false
        repeat(45) { tick ->
            onTick(tick)
            sleep(2_000)
            val status = runCatching { sh(DebloatCommands.TRIM_STATUS) }.getOrDefault("")
            if (status.contains("done", ignoreCase = true)) {
                log("caché liberada")
                return true
            }
        }
        return false
    }

    suspend fun clearMirror() {
        runCatching { sh(DebloatCommands.CLEAR_MIRROR) }
    }
}

/**
 * Elige qué se apaga de una lista. La decisión es pura para poder probarla sin una tele.
 *
 * - Solo las listas **probadas en el modelo** vienen con grupos marcados.
 * - Nunca se marcan solas las apps opcionales (líneas comentadas), el asistente de voz, el
 *   casting, las actualizaciones ni el streaming.
 * - Los paquetes protegidos y los que la lista manda conservar se descartan antes.
 */
internal object DebloatRules {
    fun plan(
        match: ListMatch?,
        snapshot: TvSnapshot,
        groups: Set<CleanupGroup>,
        exact: Boolean = match?.exact == true,
        catalog: List<DebloatList> = emptyList(),
    ): CleanupPlan {
        val matched = match?.let {
            Grouping.candidates(it.list, snapshot.installed, snapshot.disabled, snapshot.liveProtected)
        }.orEmpty()
        val matchedPkgs = matched.map { it.pkg }.toSet()
        val allKeep = match?.list?.keep.orEmpty() + catalog.flatMap { it.keep }
        val extras = catalog.asSequence()
            .filter { it.id != match?.list?.id }
            .flatMap { list -> (list.entries + list.optional).asSequence() }
            .filter { it.pkg in snapshot.installed && it.pkg !in snapshot.disabled && it.pkg !in matchedPkgs }
            .filterNot { Launchers.isHomeApp(it.pkg) }
            .filterNot { Protected.isProtected(it.pkg, snapshot.liveProtected, allKeep) }
            .distinctBy { it.pkg }
            .map { entry ->
                CleanupCandidate(
                    pkg = entry.pkg,
                    label = AppLabels.label(entry.pkg, entry.note),
                    note = entry.note,
                    group = Grouping.groupOf(entry),
                    optional = true,
                    source = CleanupSource.OTHER,
                )
            }
            .toList()
        val candidates = (matched + extras)
            .sortedWith(compareBy({ it.group.ordinal }, { it.label.lowercase() }))
        val selected = matched
            .filter { it.source == CleanupSource.MATCHED && it.group in groups }
            .map { it.pkg }
            .distinct()
        return CleanupPlan(
            tv = snapshot.info,
            match = match,
            candidates = candidates,
            selected = selected,
            groups = groups,
            tested = exact,
        )
    }

    /** El grupo por defecto para una lista: probada sí, parecida no. */
    fun defaultGroups(exact: Boolean): Set<CleanupGroup> =
        if (exact) CleanupGroup.DEFAULTS else emptySet()
}
