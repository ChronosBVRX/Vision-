package com.chronosbvrx.visionplus.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * «Perfiles» en la tele: cambiar de persona, crear una nueva (con nombre automático: el
 * teclado está en el teléfono) y borrar la activa. Al cambiar se recrea la pantalla para que
 * «Seguir viendo», Mi lista e historial lean el perfil nuevo.
 */
@Composable
internal fun TvProfilesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(ProfileStore.profiles(context)) }
    var activeId by remember { mutableStateOf(ProfileStore.activeId(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = rememberDialogFocus()
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    TvDialog("Perfiles", onDismiss = onDismiss) {
        Text(
            "Cada perfil guarda su «Seguir viendo», su historial y Mi lista. " +
                "Los ajustes, las descargas y el modo niños son del equipo.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        Text(
            "En uso: ${active.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        profiles.forEachIndexed { index, profile ->
            val inUse = profile.id == activeId
            TvActionOption(
                label = if (inUse) "${profile.name} · En uso" else profile.name,
                modifier = if (index == 0) Modifier.dialogFocus(focus) else Modifier,
            ) {
                if (inUse) {
                    onDismiss()
                } else if (ProfileStore.switchTo(context, profile.id)) {
                    Toast.makeText(context, "Perfil: ${profile.name}", Toast.LENGTH_SHORT).show()
                    com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext.syncAsync(context, force = true)
                    onDismiss()
                    context.findActivity()?.recreate()
                }
            }
        }
        if (profiles.size < ProfileStore.MAX_PROFILES) {
            TvActionOption("Crear perfil") {
                val created = ProfileStore.create(context, "Perfil ${profiles.size + 1}")
                profiles = ProfileStore.profiles(context)
                Toast.makeText(
                    context,
                    if (created != null) "Perfil «${created.name}» creado" else "No se pudo crear el perfil",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        if (active.id != ProfileStore.DEFAULT_ID) {
            TvActionOption(if (confirmDelete) "Sí, borrar «${active.name}»" else "Borrar «${active.name}»") {
                if (!confirmDelete) {
                    confirmDelete = true
                } else {
                    val name = active.name
                    if (ProfileStore.delete(context, active.id)) {
                        Toast.makeText(context, "Perfil «$name» borrado", Toast.LENGTH_SHORT).show()
                        onDismiss()
                        context.findActivity()?.recreate()
                    } else {
                        confirmDelete = false
                    }
                }
            }
            Text(
                if (confirmDelete) "Se borran su historial y su lista. No se puede deshacer."
                else "Al borrarlo se pierden su historial y su lista.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        } else {
            Text(
                "El perfil Principal conserva lo que ya tenías y no se puede borrar.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = VisionTextSecondary) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
