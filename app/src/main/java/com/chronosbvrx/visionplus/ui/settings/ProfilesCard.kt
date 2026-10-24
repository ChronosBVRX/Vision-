package com.chronosbvrx.visionplus.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.profile.Profile
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * «Perfiles» en Ajustes (móvil): una persona por perfil, cada una con su «Seguir viendo»,
 * su historial, Mi lista y las series que sigue. Los ajustes, las descargas y el modo niños
 * son del equipo.
 *
 * Al cambiar de perfil se recrea la pantalla: es la forma barata y sin estados zombis de que
 * TODAS las filas (Inicio, Mi lista, ficha, launcher) vuelvan a leer el perfil nuevo.
 */
@Composable
internal fun ProfilesSettingsSection() {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(ProfileStore.profiles(context)) }
    var activeId by remember { mutableStateOf(ProfileStore.activeId(context)) }
    var showCreate by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    fun reload() {
        profiles = ProfileStore.profiles(context)
        activeId = ProfileStore.activeId(context)
    }

    SettingsSection("Perfiles") {
        SettingsBlock {
            Text("Una persona por perfil", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Cada perfil guarda su «Seguir viendo», su historial, Mi lista y las series que " +
                    "sigue. Los ajustes, las descargas y el modo niños son del equipo.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            // Fila de avatares: se elige tocando el círculo, como en la tele.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                profiles.forEach { profile ->
                    val inUse = profile.id == activeId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable(enabled = !inUse) {
                                useProfile(context, profile)
                                reload()
                            },
                    ) {
                        ProfileAvatar(name = profile.name, size = 56.dp, ring = inUse)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (inUse) {
                            Text("En uso", color = VisionBlue, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { showCreate = true },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .border(2.dp, VisionTextSecondary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Crear",
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (profiles.size < ProfileStore.MAX_PROFILES) {
            SettingsNavRow(
                "Crear perfil",
                onClick = { showCreate = true },
                supporting = "Hasta ${ProfileStore.MAX_PROFILES} personas por equipo",
            )
        }
        SettingsNavRow("Renombrar «${active.name}»", onClick = { showRename = true })
        if (active.id != ProfileStore.DEFAULT_ID) {
            SettingsDestructiveRow(
                title = "Borrar «${active.name}»",
                supporting = "Borra su historial, Mi lista y sus series seguidas",
                onClick = { confirmDelete = true },
            )
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Nuevo perfil",
            initial = "Perfil ${profiles.size + 1}",
            confirm = "Crear",
            onDismiss = { showCreate = false },
            onAccept = { name ->
                val created = ProfileStore.create(context, name)
                showCreate = false
                if (created == null) {
                    Toast.makeText(context, "No se pudo crear el perfil", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Perfil «${created.name}» creado", Toast.LENGTH_SHORT).show()
                    reload()
                }
            },
        )
    }

    if (showRename) {
        NameDialog(
            title = "Renombrar perfil",
            initial = active.name,
            confirm = "Guardar",
            onDismiss = { showRename = false },
            onAccept = { name ->
                ProfileStore.rename(context, active.id, name)
                showRename = false
                reload()
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «${active.name}»?") },
            text = {
                Text(
                    "Se borran su «Seguir viendo», su historial, Mi lista y las series que sigue. " +
                        "No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = active.name
                    if (ProfileStore.delete(context, active.id)) {
                        Toast.makeText(context, "Perfil «$name» borrado", Toast.LENGTH_SHORT).show()
                        confirmDelete = false
                        reload()
                        context.findActivity()?.recreate()
                    } else {
                        confirmDelete = false
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Diálogo de nombre: sirve para crear y para renombrar. */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                singleLine = true,
                label = { Text("Nombre") },
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAccept(name) }) { Text(confirm) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Cambia de perfil y recrea la pantalla para que ninguna fila se quede con los datos viejos. */
private fun useProfile(context: Context, profile: Profile) {
    if (!ProfileStore.switchTo(context, profile.id)) return
    Toast.makeText(context, "Perfil: ${profile.name}", Toast.LENGTH_SHORT).show()
    // En la tele, el canal «Continuar viendo» del launcher debe espejar el perfil nuevo.
    com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext.syncAsync(context, force = true)
    context.findActivity()?.recreate()
    // Sin actividad (p. ej. una vista embebida), al menos las preferencias ya apuntan al nuevo.
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
