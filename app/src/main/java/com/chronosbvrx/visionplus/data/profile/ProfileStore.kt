package com.chronosbvrx.visionplus.data.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Un perfil es una persona del equipo. Cada una tiene su propio «Seguir viendo», historial,
 * Mi lista, series seguidas y código de continuidad. Los ajustes, las descargas, el modo niños
 * y el emparejamiento del control remoto son del equipo, no del perfil.
 *
 * El perfil [ProfileStore.DEFAULT_ID] conserva los nombres de archivo de siempre
 * (`vision_user`, `playback_history`…), así que actualizar la app NO pierde nada: lo que ya
 * había pasa a ser el perfil «Principal». Los demás viven en archivos con sufijo
 * (`vision_user__p1`).
 */
data class Profile(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
)

object ProfileStore {

    /** El perfil que existe siempre y dueño de los datos heredados. */
    const val DEFAULT_ID = "principal"
    const val DEFAULT_NAME = "Principal"

    /** Tope de perfiles por equipo (4 personas + el principal se siente suficiente). */
    const val MAX_PROFILES = 5

    private const val PREFS = "vision_profiles"
    private const val K_LIST = "profiles"
    private const val ACTIVE_FILE = "active_profile"
    private const val ACTIVE_TMP = "active_profile.part"

    /**
     * Preferencias que son de una persona. Si mañana se agrega otra (p. ej. favoritos de
     * canales), se suma aquí y [delete] la limpia sola. `vision_live` (caché de canales y
     * favoritos de la casa), ajustes, descargas y niños quedan fuera a propósito.
     */
    internal val PROFILE_PREFS = listOf(
        "vision_user",
        "playback_history",
        "sync_state",
        "vision_follow",
    )

    // ----- piezas puras (con guardián) -----

    /** ¿Es un id que nosotros generamos? Evita que un archivo corrupto apunte a cualquier cosa. */
    internal fun isValidId(id: String): Boolean =
        id == DEFAULT_ID ||
            (id.length in 2..6 && id[0] == 'p' && id.drop(1).all { it in '0'..'9' })

    /** Nombre real de las preferencias: el principal mantiene el nombre legado. */
    internal fun prefsNameFor(base: String, activeId: String): String =
        if (activeId == DEFAULT_ID || !isValidId(activeId)) base else "${base}__$activeId"

    /** Archivo XML de esas preferencias (el reproductor lo lee directo del disco). */
    internal fun xmlFileNameFor(base: String, activeId: String): String =
        "${prefsNameFor(base, activeId)}.xml"

    /** Nombre visible: sin NUL, espacios colapsados y 24 caracteres como tope. */
    internal fun sanitizeName(raw: String): String = raw
        .replace('\u0000', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .take(24)

    /** Siguiente id libre: `p1`, `p2`… (el `principal` no cuenta). */
    internal fun nextId(existing: List<Profile>): String {
        val used = existing.map { it.id }.toSet()
        var n = 1
        while ("p$n" in used) n++
        return "p$n"
    }

    /** Lista guardada; siempre saneada, con el principal primero y sin duplicados. */
    internal fun parseProfiles(raw: String?): List<Profile> {
        val found = LinkedHashMap<String, Profile>()
        runCatching {
            val arr = JSONArray(raw ?: "[]")
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (!isValidId(id)) continue
                val name = sanitizeName(obj.optString("name"))
                if (id in found) continue
                found[id] = Profile(
                    id = id,
                    name = name.ifBlank { if (id == DEFAULT_ID) DEFAULT_NAME else "Perfil" },
                    createdAt = obj.optLong("createdAt"),
                )
            }
        }
        val base = found.remove(DEFAULT_ID) ?: Profile(DEFAULT_ID, DEFAULT_NAME)
        return listOf(base) + found.values.take(MAX_PROFILES - 1)
    }

    internal fun encodeProfiles(profiles: List<Profile>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("createdAt", p.createdAt)
                }
            )
        }
        return arr.toString()
    }

    /** ¿Se puede borrar? El principal no (es donde viven los datos heredados). */
    internal fun canDelete(id: String, profiles: List<Profile>): Boolean =
        id != DEFAULT_ID && profiles.any { it.id == id }

    // ----- almacenamiento -----

    private fun globalPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profiles(context: Context): List<Profile> =
        parseProfiles(globalPrefs(context).getString(K_LIST, null))

    /** Id activo. Se lee del archivo en cada llamada: `:x4k` y el proceso principal deben ver lo mismo. */
    fun activeId(context: Context): String {
        val raw = runCatching { activeFile(context).readText().trim() }.getOrNull().orEmpty()
        return if (isValidId(raw)) raw else DEFAULT_ID
    }

    fun active(context: Context): Profile {
        val id = activeId(context)
        return profiles(context).firstOrNull { it.id == id }
            ?: Profile(DEFAULT_ID, DEFAULT_NAME)
    }

    /** Nombre de las preferencias de [base] para el perfil activo (lo usan los almacenes). */
    fun prefsName(context: Context, base: String): String = prefsNameFor(base, activeId(context))

    fun switchTo(context: Context, id: String): Boolean {
        if (profiles(context).none { it.id == id }) return false
        writeActive(context, id)
        return true
    }

    /** Crea un perfil nuevo con nombre visible. Devuelve null si el nombre está vacío o ya no caben. */
    fun create(context: Context, name: String): Profile? {
        val clean = sanitizeName(name)
        if (clean.isBlank()) return null
        val current = profiles(context)
        if (current.size >= MAX_PROFILES) return null
        val profile = Profile(nextId(current), clean, System.currentTimeMillis())
        save(context, current + profile)
        return profile
    }

    fun rename(context: Context, id: String, name: String): Boolean {
        val clean = sanitizeName(name)
        if (clean.isBlank()) return false
        val current = profiles(context)
        if (current.none { it.id == id }) return false
        save(context, current.map { if (it.id == id) it.copy(name = clean) else it })
        return true
    }

    /**
     * Borra el perfil y sus datos. Si era el activo, se vuelve al principal (nunca se queda
     * la app sin perfil válido).
     */
    fun delete(context: Context, id: String): Boolean {
        val current = profiles(context)
        if (!canDelete(id, current)) return false
        clearProfileData(context, id)
        save(context, current.filterNot { it.id == id })
        if (activeId(context) == id) writeActive(context, DEFAULT_ID)
        return true
    }

    /** Datos de un perfil: se vacían y se borra su XML (el principal no se toca por accidente). */
    internal fun clearProfileData(context: Context, id: String) {
        if (id == DEFAULT_ID) return
        val app = context.applicationContext
        PROFILE_PREFS.forEach { base ->
            val name = prefsNameFor(base, id)
            runCatching { app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
            runCatching { File(File(app.dataDir, "shared_prefs"), "$name.xml").delete() }
        }
    }

    private fun save(context: Context, profiles: List<Profile>) {
        // commit (no apply): crear/borrar un perfil es raro y no puede perderse si el
        // sistema mata la app justo después (la lista es la fuente de verdad del equipo).
        globalPrefs(context).edit().putString(K_LIST, encodeProfiles(profiles)).commit()
    }

    private fun activeFile(context: Context): File =
        File(context.applicationContext.filesDir, ACTIVE_FILE)

    /**
     * Escribe el perfil activo de forma atómica: un archivo a medias haría que el proceso
     * `:x4k` escribiera el historial en el perfil equivocado.
     */
    private fun writeActive(context: Context, id: String) {
        val dir = context.applicationContext.filesDir.apply { mkdirs() }
        val target = File(dir, ACTIVE_FILE)
        val tmp = File(dir, ACTIVE_TMP)
        val ok = runCatching {
            tmp.writeText(id)
            target.delete()
            tmp.renameTo(target)
        }.getOrDefault(false)
        if (!ok) {
            runCatching { target.writeText(id) }
            runCatching { tmp.delete() }
        }
    }
}
