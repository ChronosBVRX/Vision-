package com.chronosbvrx.visionplus.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * «Perfiles» en la tele: se elige **quién eres** con avatares grandes (inicial y color por
 * nombre, dibujados), se crea uno nuevo (nombre automático: el teclado está en el teléfono) y se
 * borra el activo. Al cambiar se recrea la pantalla para que «Seguir viendo», Mi lista e
 * historial lean el perfil nuevo.
 */
@Composable
internal fun TvProfilesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(ProfileStore.profiles(context)) }
    var activeId by remember { mutableStateOf(ProfileStore.activeId(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = rememberDialogFocus()
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    fun switchTo(profile: com.chronosbvrx.visionplus.data.profile.Profile) {
        if (profile.id == activeId) {
            onDismiss()
            return
        }
        if (ProfileStore.switchTo(context, profile.id)) {
            Toast.makeText(context, "Perfil: ${profile.name}", Toast.LENGTH_SHORT).show()
            com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext.syncAsync(context, force = true)
            onDismiss()
            context.findActivity()?.recreate()
        }
    }

    TvDialog("Perfiles", onDismiss = onDismiss) {
        Text(
            "Cada perfil guarda su «Seguir viendo», su historial y Mi lista. Los ajustes, las " +
                "descargas y el modo niños son del equipo.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(profiles) { index, profile ->
                ProfileCard(
                    name = profile.name,
                    inUse = profile.id == activeId,
                    modifier = if (index == 0) Modifier.dialogFocus(focus) else Modifier,
                    onClick = { switchTo(profile) },
                )
            }
            if (profiles.size < ProfileStore.MAX_PROFILES) {
                item {
                    ProfileCard(
                        name = "Crear perfil",
                        inUse = false,
                        isAdd = true,
                        onClick = {
                            val created = ProfileStore.create(context, "Perfil ${profiles.size + 1}")
                            profiles = ProfileStore.profiles(context)
                            Toast.makeText(
                                context,
                                if (created != null) "Perfil «${created.name}» creado" else "No se pudo crear el perfil",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }

        if (active.id != ProfileStore.DEFAULT_ID) {
            TvActionOption(
                if (confirmDelete) "Sí, borrar «${active.name}»" else "Borrar «${active.name}»",
            ) {
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

/**
 * Tarjeta de perfil: avatar + nombre, con aro blanco cuando está en uso. La de «Crear perfil» usa
 * un círculo con «+» (nada de imágenes: la tele no gasta memoria).
 */
@Composable
private fun ProfileCard(
    name: String,
    inUse: Boolean,
    modifier: Modifier = Modifier,
    isAdd: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(132.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (inUse) Color(0xFF1B2538) else VisionSurface,
            focusedContainerColor = if (inUse) Color(0xFF1B2538) else VisionSurface,
            pressedContainerColor = Color(0xFF1B2538),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (inUse) Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(14.dp))
            else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(14.dp)),
            pressedBorder = Border.None,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
        ) {
            if (isAdd) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(2.dp, VisionTextSecondary, CircleShape)
                        .background(Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                ProfileAvatar(name = name, size = 76.dp, ring = inUse)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = name,
                color = Color.White,
                fontWeight = if (inUse) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (inUse) {
                Text(
                    "En uso",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
