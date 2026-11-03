package com.chronosbvrx.visionplus.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Modo persistente del Inicio.
 *
 * Google TV (y algunos firmware) pueden dejar el rol HOME concedido a Vision+ Home y aun así
 * abrir su launcher de fábrica: el rol es una preferencia, no una garantía. Este servicio
 * escucha únicamente cambios de ventana y, cuando comprueba que el paquete al frente es el
 * Inicio real del sistema y no Vision+ Home, vuelve a traer nuestro launcher al frente.
 *
 * No lee el contenido de la pantalla, no usa capturas y no actúa sobre apps normales: solo
 * cuando el paquete al frente coincide con el Inicio resuelto por Android.
 */
class GuardianAccessibilityService : AccessibilityService() {

    private var lastPullAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString().orEmpty()
        val home = currentHomePackage()
        if (!shouldRescue(foreground, home, packageName)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPullAt < PULL_COOLDOWN_MS) return
        lastPullAt = now
        val intent = Intent(this, LauncherActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    private fun currentHomePackage(): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()

    companion object {
        private const val PULL_COOLDOWN_MS = 1_200L

        /**
         * Solo se recupera el Inicio si la ventana al frente ES el Inicio resuelto del
         * sistema y ese Inicio no es Vision+ Home. Si el usuario abre Google TV como una app
         * cualquiera (o Vision+ Home ya es el Inicio), no se interviene.
         */
        internal fun shouldRescue(
            foregroundPackage: String?,
            homePackage: String?,
            ownPackage: String,
        ): Boolean {
            if (foregroundPackage.isNullOrBlank() || homePackage.isNullOrBlank()) return false
            return foregroundPackage == homePackage && foregroundPackage != ownPackage
        }

        /** ¿El usuario activó el guardián en Accesibilidad? */
        fun isEnabled(context: Context): Boolean = runCatching {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return@runCatching false
            val own = GuardianAccessibilityService::class.java.name
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo?.serviceInfo ?: return@any false
                    service.packageName == context.packageName && service.name == own
                }
        }.getOrDefault(false)

        /** Abre la pantalla de Accesibilidad para activar/desactivar el guardián. */
        fun openSettings(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }
}
