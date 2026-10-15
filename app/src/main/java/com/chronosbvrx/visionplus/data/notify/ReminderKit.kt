package com.chronosbvrx.visionplus.data.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Avisos locales (capítulos nuevos + partidos): alarma inexacta del sistema (sin permiso
 * especial) que dispara una notificación. Todo vive en el equipo: nada se sube.
 */
object ReminderKit {
    const val CHANNEL_ID = "vision_reminders"
    const val ACTION_REMIND = "com.chronosbvrx.visionplus.REMIND"

    private const val PREFS = "vision_reminders"
    private const val KEY = "reminders_json"
    private const val MAX_REMINDERS = 100

    /** Minutos antes del evento para avisar (fútbol 30, series 60). Puro, testeable. */
    fun remindAtMs(eventMs: Long, beforeMin: Int): Long =
        eventMs - beforeMin.coerceIn(0, 24 * 60) * 60_000L

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Programa el aviso; devuelve false si la hora ya pasó. */
    fun schedule(context: Context, key: String, atMs: Long, title: String, text: String): Boolean {
        if (key.isBlank() || atMs <= System.currentTimeMillis()) return false
        val app = context.applicationContext
        val intent = Intent(app, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra("key", key)
            putExtra("title", title.take(120))
            putExtra("text", text.take(200))
        }
        val pending = PendingIntent.getBroadcast(
            app, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending) }
        save(app, key, atMs)
        return true
    }

    fun cancel(context: Context, key: String) {
        val app = context.applicationContext
        val intent = Intent(app, ReminderReceiver::class.java).apply { action = ACTION_REMIND }
        val pending = PendingIntent.getBroadcast(
            app, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        val current = load(app).toMutableMap()
        if (current.remove(key) != null) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, JSONObject(current as Map<*, *>).toString()).apply()
        }
    }

    fun scheduled(context: Context): Map<String, Long> = load(context.applicationContext)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Avisos de Vision+", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun load(context: Context): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            LinkedHashMap<String, Long>().also { out ->
                o.keys().forEach { k -> o.optLong(k).takeIf { it > 0 }?.let { out[k] = it } }
            }
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, key: String, atMs: Long) {
        val current = load(context).toMutableMap()
        if (current.size >= MAX_REMINDERS) current.remove(current.keys.firstOrNull())
        current[key] = atMs
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONObject(current as Map<*, *>).toString()).apply()
    }
}

/** Recibe la alarma y muestra el aviso (proceso principal). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderKit.ACTION_REMIND) return
        val title = intent.getStringExtra("title").orEmpty().ifBlank { return }
        val text = intent.getStringExtra("text").orEmpty()
        ReminderKit.ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderKit.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(title.hashCode(), notification) }
    }
}
