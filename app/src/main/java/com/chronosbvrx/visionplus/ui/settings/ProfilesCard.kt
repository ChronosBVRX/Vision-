package com.chronosbvrx.visionplus.ui.settings

import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.profile.Profile
import com.chronosbvrx.visionplus.data.profile.ProfileAvatarCategory
import com.chronosbvrx.visionplus.data.profile.ProfileAvatars
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.profile.ProfileNameField
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
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
    var renameTarget by remember { mutableStateOf<Profile?>(null) }
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
                        ProfileAvatar(name = profile.name, size = 56.dp, ring = inUse, avatar = profile.avatar)
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
                        IconButton(
                            onClick = { renameTarget = profile },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Editar ${profile.name}",
                                tint = VisionTextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
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
        SettingsNavRow("Editar «${active.name}»", onClick = { renameTarget = active })
        if (active.id != ProfileStore.DEFAULT_ID) {
            SettingsDestructiveRow(
                title = "Borrar «${active.name}»",
                supporting = "Borra su historial, Mi lista y sus series seguidas",
                onClick = { confirmDelete = true },
            )
        }
    }

    if (showCreate) {
        ProfileEditorDialog(
            title = "Nuevo perfil",
            initial = "Perfil ${profiles.size + 1}",
            initialAvatar = "",
            confirm = "Crear",
            onDismiss = { showCreate = false },
            onAccept = { name, avatar ->
                val created = ProfileStore.create(context, name, avatar)
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

    renameTarget?.let { target ->
        ProfileEditorDialog(
            title = "Editar perfil",
            initial = target.name,
            initialAvatar = target.avatar,
            confirm = "Guardar",
            onDismiss = { renameTarget = null },
            onAccept = { name, avatar ->
                ProfileStore.rename(context, target.id, name)
                ProfileStore.setAvatar(context, target.id, avatar)
                renameTarget = null
                reload()
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.dismissOnBack { confirmDelete = false },
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

/**
 * Diálogo de perfil: nombre (con el teclado predeterminado del sistema) e imagen de cine,
 * series o anime. «Inicial» deja el avatar dibujado de siempre.
 */
@Composable
private fun ProfileEditorDialog(
    title: String,
    initial: String,
    initialAvatar: String,
    confirm: String,
    onDismiss: () -> Unit,
    onAccept: (String, String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    var avatar by remember(initialAvatar) { mutableStateOf(initialAvatar) }
    var category by remember { mutableStateOf(ProfileAvatarCategory.CINE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dismissOnBack { onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProfileAvatar(name = name, size = 54.dp, avatar = avatar)
                    Column(Modifier.weight(1f)) {
                        Text("Nombre", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        ProfileNameField(
                            value = name,
                            onValueChange = { if (it.length <= 24) name = it },
                            autoFocus = true,
                            textSizeSp = 18f,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Imagen · cine, series y anime",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProfileAvatarCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AvatarChoice(
                        label = "Inicial",
                        selected = avatar.isBlank(),
                        onClick = { avatar = "" },
                    ) {
                        ProfileAvatar(name = name, size = 66.dp, avatar = "")
                    }
                    ProfileAvatars.of(category).forEach { option ->
                        AvatarChoice(
                            label = option.label,
                            selected = avatar == option.id,
                            onClick = { avatar = option.id },
                        ) {
                            Image(
                                painter = painterResource(option.drawable),
                                contentDescription = option.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(66.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sin imagen se usa la inicial del nombre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { if (name.isNotBlank()) onAccept(name.trim(), avatar) }) {
                    Text(confirm)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AvatarChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, VisionSurfaceHigh, RoundedCornerShape(12.dp)),
            )
            .padding(5.dp),
    ) {
        content()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
