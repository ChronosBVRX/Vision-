package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the activation session lives: an encrypted prefs file with the credentials and the
 * captured cookies. Created once per app; the first open migrates the legacy plaintext file
 * ("xuper_config") into the encrypted one and wipes it, so no secret survives in clear.
 */
object SecretKeystore {

    private const val FILE = "xuper_secure"
    private const val LEGACY_FILE = "xuper_config"
    private const val KEY_CONFIG = "config"

    @Volatile
    private var cache: SharedPreferences? = null

    /** The encrypted store, ready (and migrated). Use it like any other SharedPreferences. */
    fun get(context: Context): SharedPreferences {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val app = context.applicationContext
            val prefs = EncryptedSharedPreferences.create(
                app,
                FILE,
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            migrateLegacy(app, prefs)
            cache = prefs
            return prefs
        }
    }

    /** The gate's question: has this install ever been activated? */
    fun hasConfig(context: Context): Boolean =
        get(context).getString(KEY_CONFIG, null)?.isNotBlank() == true

    private fun migrateLegacy(app: Context, secure: SharedPreferences) {
        val current = secure.getString(KEY_CONFIG, null)
        if (!current.isNullOrBlank()) return
        val legacy = app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val entries = legacy.all
        if (entries.isEmpty()) return
        var moved = 0
        for ((key, value) in entries) {
            if (value is String) {
                secure.edit().putString(key, value).apply()
                moved++
            }
        }
        if (moved > 0) legacy.edit().clear().apply()
    }
}
