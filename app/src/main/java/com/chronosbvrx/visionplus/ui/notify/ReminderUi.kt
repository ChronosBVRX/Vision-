package com.chronosbvrx.visionplus.ui.notify

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chronosbvrx.visionplus.data.notify.ReminderKit
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction

/** Pide el permiso de avisos solo cuando hace falta (en Android <13 siempre hay). */
@Composable
fun rememberNotifyGate(): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val run = pending
        pending = null
        if (granted) run?.invoke()
        else Toast.makeText(context, "Activa los avisos para recibir el recordatorio", Toast.LENGTH_SHORT).show()
    }
    return remember(context) {
        { onGranted: () -> Unit ->
            if (Build.VERSION.SDK_INT < 33 || ReminderKit.hasPermission(context)) {
                onGranted()
            } else {
                pending = onGranted
                runCatching {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }.onFailure { pending = null }
            }
        }
    }
}

/** Campana de aviso para un partido (móvil): programa 30 min antes o cancela. */
@Composable
fun MobileRemindBell(
    matchId: String,
    kickoffMs: Long,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gate = rememberNotifyGate()
    var tick by remember { mutableStateOf(0) }
    remember(tick) { Unit }
    val key = "match:$matchId"
    val active = ReminderKit.scheduled(context).containsKey(key)
    IconButton(
        modifier = modifier,
        onClick = {
            gate {
                if (active) {
                    ReminderKit.cancel(context, key)
                    tick++
                } else {
                    val at = ReminderKit.remindAtMs(kickoffMs, 30)
                    if (at <= System.currentTimeMillis()) {
                        Toast.makeText(context, "El partido ya empezó", Toast.LENGTH_SHORT).show()
                        return@gate
                    }
                    if (ReminderKit.schedule(context, key, at, title, "Empieza en 30 minutos")) {
                        Toast.makeText(context, "Te avisaremos 30 min antes", Toast.LENGTH_SHORT).show()
                        tick++
                    }
                }
            }
        },
    ) {
        Icon(
            if (active) Icons.Default.NotificationsOff else Icons.Default.Notifications,
            contentDescription = if (active) "Quitar recordatorio" else "Avisarme",
            tint = if (active) Color.White else com.chronosbvrx.visionplus.ui.theme.VisionBlue,
        )
    }
}

/** Acción de aviso para un partido en TV (dentro del modal, con D-pad). */
@Composable
fun TvRemindAction(
    matchId: String,
    kickoffMs: Long,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gate = rememberNotifyGate()
    var tick by remember { mutableStateOf(0) }
    remember(tick) { Unit }
    val key = "match:$matchId"
    val active = ReminderKit.scheduled(context).containsKey(key)
    TvCompactAction(
        label = if (active) "Aviso activo" else "Avisarme",
        icon = if (active) VisionIcons.Following else VisionIcons.Follow,
        modifier = modifier,
        onClick = {
            gate {
                if (active) {
                    ReminderKit.cancel(context, key)
                    tick++
                } else {
                    val at = ReminderKit.remindAtMs(kickoffMs, 30)
                    if (at <= System.currentTimeMillis()) {
                        Toast.makeText(context, "El partido ya empezó", Toast.LENGTH_SHORT).show()
                        return@gate
                    }
                    if (ReminderKit.schedule(context, key, at, title, "Empieza en 30 minutos")) {
                        Toast.makeText(context, "Te avisaremos 30 min antes", Toast.LENGTH_SHORT).show()
                        tick++
                    }
                }
            }
        },
    )
}
