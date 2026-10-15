package com.chronosbvrx.visionplus.data.kids

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.data.CatalogRow
import java.security.MessageDigest

/**
 * Modo niños con PIN: cuando está bloqueado, el catálogo visible es solo infantil
 * (las pantallas leen las hileras ya filtradas; en vivo/deportes/descargas piden el PIN).
 * El PIN se guarda como hash (nunca en claro).
 */
object KidsMode {
    private const val PREFS = "vision_kids"
    private const val KEY_PIN = "pin_hash"
    private const val KEY_LOCKED = "locked"
    private const val KEY_PASS_UNTIL = "pass_until"

    var locked by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        locked = p.getBoolean(KEY_LOCKED, false) && p.getString(KEY_PIN, "").isNullOrBlank().not()
    }

    fun hasPin(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN, "").isNullOrBlank().not()

    /** Crea o cambia el PIN (4–8 dígitos). */
    fun setPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIN, sha256(pin)).apply()
        return true
    }

    fun verify(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN, "")
        return !stored.isNullOrBlank() && sha256(pin) == stored
    }

    fun lock(context: Context) {
        if (!hasPin(context)) return
        locked = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCKED, true).apply()
        com.chronosbvrx.visionplus.data.XuperCatalog.lastHomeCurated = null
    }

    /** Desbloquea solo con el PIN correcto. */
    fun unlock(context: Context, pin: String): Boolean {
        if (!verify(context, pin)) return false
        locked = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCKED, false).apply()
        com.chronosbvrx.visionplus.data.XuperCatalog.lastHomeCurated = null
        return true
    }

    fun isValidPin(pin: String): Boolean =
        pin.length in 4..8 && pin.all { it.isDigit() }

    /**
     * Pase temporal (15 min): entrar con PIN a una sección no desbloquea todo.
     * El bloqueo sigue activo; el pase solo deja pasar las puertas.
     */
    fun pass(context: Context, minutes: Int = 15) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PASS_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun hasPass(context: Context): Boolean =
        System.currentTimeMillis() < context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PASS_UNTIL, 0L)

    /** Puerta cerrada = bloqueado y sin pase vigente. Puro el criterio, testeable aparte. */
    fun gateClosed(lockedNow: Boolean, hasPassNow: Boolean): Boolean = lockedNow && !hasPassNow

    internal fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Hileras solo-infantiles (puro, testeable): lo demás no existe en modo niños. */
    fun kidsRows(rows: List<CatalogRow>): List<CatalogRow> =
        rows.mapNotNull { row ->
            val films = row.films.filter { it.kind == "kids" }
            if (films.isEmpty()) null else row.copy(films = films)
        }
}
