package com.chronosbvrx.visionplus.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chronosbvrx.visionplus.DeviceKind
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.ui.settings.VisionSettings

/**
 * Política pura de Picture-in-Picture: se puede probar sin Android.
 *
 * Reglas del producto:
 *  - solo teléfonos/tablets, nunca la tele;
 *  - Android 8.0 (API 26) o superior;
 *  - las descargas del búnker solo entran si el usuario habilitó el ajuste explícito
 *    (por seguridad, ese modo desactiva `FLAG_SECURE` mientras el cuadro está visible).
 */
internal object PipRules {
    fun isPipSupported(sdkInt: Int, isTv: Boolean): Boolean = sdkInt >= 26 && !isTv

    fun canUsePipFor(isVault: Boolean, offlinePipAllowed: Boolean): Boolean =
        !isVault || offlinePipAllowed

    fun toggleTitle(playing: Boolean): String = if (playing) "Pausar" else "Reproducir"

    const val ASPECT_WIDTH = 16
    const val ASPECT_HEIGHT = 9
}

/**
 * Fachada de Picture-in-Picture. Todas las clases de API 26 viven en [PipApi26], que solo
 * se carga cuando el sistema la soporta: los equipos Android 7 no tocan esos tipos.
 */
object PipMode {
    const val ACTION_TOGGLE = "com.chronosbvrx.visionplus.action.PIP_TOGGLE"
    internal const val REQUEST_TOGGLE = 8101

    fun isSupported(context: Context): Boolean =
        PipRules.isPipSupported(Build.VERSION.SDK_INT, DeviceKind.isTelevision(context)) &&
            RemoteConfig.flag(context, "pip_enabled", true)

    /** `isInPictureInPictureMode` solo existe desde API 26; en equipos viejos siempre es false. */
    fun isActivityInPip(activity: Activity?): Boolean =
        if (Build.VERSION.SDK_INT >= 26) PipApi26.isActivityInPip(activity) else false

    fun register(context: Context, receiver: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT < 26 || !isSupported(context)) return
        PipApi26.register(context, receiver)
    }

    fun unregister(context: Context, receiver: BroadcastReceiver?) {
        if (receiver == null) return
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** Actualiza las acciones y la entrada automática. Devuelve false si el equipo no soporta PiP. */
    fun update(activity: Activity, playing: Boolean, autoEnter: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !isSupported(activity)) return false
        return PipApi26.update(activity, playing, autoEnter)
    }

    /** Entra al PiP desde un botón o desde `onUserLeaveHint` (Android 8–11). */
    fun enter(activity: Activity, playing: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !isSupported(activity)) return false
        if (isActivityInPip(activity)) return false
        return PipApi26.enter(activity, playing)
    }
}

@RequiresApi(26)
private object PipApi26 {
    fun register(context: Context, receiver: BroadcastReceiver) {
        val filter = IntentFilter(PipMode.ACTION_TOGGLE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun isActivityInPip(activity: Activity?): Boolean =
        activity?.isInPictureInPictureMode == true

    fun update(activity: Activity, playing: Boolean, autoEnter: Boolean): Boolean = runCatching {
        activity.setPictureInPictureParams(params(activity, playing, autoEnter))
        true
    }.getOrDefault(false)

    fun enter(activity: Activity, playing: Boolean): Boolean = runCatching {
        activity.enterPictureInPictureMode(params(activity, playing, autoEnter = false))
    }.getOrDefault(false)

    private fun params(activity: Activity, playing: Boolean, autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(PipRules.ASPECT_WIDTH, PipRules.ASPECT_HEIGHT))
            .setActions(listOf(toggleAction(activity, playing)))
        // Android 12+ usa el rectángulo de origen para animar la transición; el reproductor
        // ocupa toda la ventana. También es la recomendación de lint para targetSdk 31+.
        runCatching {
            val rect = Rect()
            activity.window?.decorView?.getGlobalVisibleRect(rect)
            if (!rect.isEmpty) builder.setSourceRectHint(rect)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(autoEnter)
            // El motor nativo pinta sobre una superficie propia: el resize "seamless" puede
            // dejar un cuadro negro al entrar. La transición normal es más segura.
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun toggleAction(context: Context, playing: Boolean): RemoteAction {
        val intent = Intent(PipMode.ACTION_TOGGLE).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            PipMode.REQUEST_TOGGLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(
            context,
            if (playing) R.drawable.ic_vp_pause else R.drawable.ic_vp_play,
        )
        val title = PipRules.toggleTitle(playing)
        return RemoteAction(icon, title, title, pending)
    }
}

/**
 * Sesión de reproducción que la actividad principal (ExoPlayer) expone al PiP.
 *
 * Importante: [PlayerCore] NO cambia su firma gigante para esto. Se registra aquí, como
 * `GeeZapSignals`, y la actividad lee el estado para entrar en PiP y actualizar las acciones.
 */
object PipSession {
    class Active(
        val isVault: Boolean,
        val isLive: Boolean,
        val title: String,
        val playing: () -> Boolean,
        val toggle: () -> Unit,
        val close: () -> Unit,
    )

    @Volatile
    private var active: Active? = null

    /** Se incrementa al montar/desmontar la sesión o al cambiar play/pausa. */
    var revision by mutableIntStateOf(0)
        private set

    /** Lo lee Compose para ocultar los controles dentro del cuadro. */
    var inPip by mutableStateOf(false)
        private set

    fun attach(session: Active) {
        active = session
        revision++
    }

    fun detach(session: Active) {
        if (active === session) {
            active = null
            inPip = false
            revision++
        }
    }

    fun current(): Active? = active

    fun bump() {
        revision++
    }

    fun markInPip(value: Boolean) {
        if (inPip != value) inPip = value
    }

    fun canEnter(context: Context): Boolean {
        val session = active ?: return false
        if (!PipMode.isSupported(context)) return false
        return PipRules.canUsePipFor(
            isVault = session.isVault,
            offlinePipAllowed = VisionSettings.pipDownloadsAllowed(context),
        )
    }

    fun toggle() {
        runCatching { active?.toggle() }
    }

    fun close() {
        runCatching { active?.close() }
    }

    fun clear() {
        active = null
        inPip = false
        revision++
    }
}
