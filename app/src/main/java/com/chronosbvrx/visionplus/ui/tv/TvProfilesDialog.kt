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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.data.profile.Profile
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

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
    var editing by remember { mutableStateOf<Profile?>(null) }
    val focus = rememberDialogFocus()
    // Al cerrar el modal de escritura, el foco vuelve a la lista de perfiles (nunca queda
    // huérfano detrás de la ventana que se acaba de cerrar).
    LaunchedEffect(editing) {
        if (editing == null) {
            delay(150)
            requestFocusSafely(focus.requester)
        }
    }
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
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                ProfileItem(
                    profile = profile,
                    inUse = profile.id == activeId,
                    first = index == 0,
                    dialogFocus = focus,
                    onSelect = { switchTo(profile) },
                    onEdit = {
                        confirmDelete = false
                        editing = profile
                    },
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

        TvActionOption("Renombrar «${active.name}»") {
            confirmDelete = false
            editing = active
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

    // El lápiz abre un modal de escritura PROPIO (no edita dentro de esta lista) y con el
    // teclado nativo de la tele: el mismo que usa cualquier campo de texto del sistema.
    editing?.let { target ->
        RenameProfileDialog(
            initial = target.name,
            onDismiss = { editing = null },
            onSave = { name ->
                if (ProfileStore.rename(context, target.id, name)) {
                    profiles = ProfileStore.profiles(context)
                    activeId = ProfileStore.activeId(context)
                    editing = null
                    Toast.makeText(context, "Perfil renombrado a «$name»", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Escribe un nombre para el perfil", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/**
 * Modal de escritura para renombrar un perfil en la tele. Usa el teclado NATIVO del televisor
 * (el mismo que cualquier campo del sistema), no el teclado dibujado por la app: al abrirse
 * enfoca el campo y pide el teclado; «Listo» en el teclado guarda y los botones quedan también
 * disponibles para el mando.
 */
@Composable
private fun RenameProfileDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(initial) {
        // La ventana del diálogo tarda unos frames en aceptar el foco; después se pide el IME.
        delay(250)
        requestFocusSafely(fieldFocus)
        keyboard?.show()
    }
    TvDialog("Renombrar perfil", onDismiss = onDismiss) {
        Text(
            "Nuevo nombre",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )
        BasicTextField(
            value = name,
            onValueChange = { if (it.length <= 24) name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White),
            cursorBrush = SolidColor(VisionBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (name.isNotBlank()) onSave(name.trim()) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .focusRequester(fieldFocus)
                .background(VisionSurfaceHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Text(
            "Escribe con el teclado de tu tele. Cuando termines, elige Guardar (o «Listo» en el teclado).",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) {
                Text("Guardar", color = Color.White)
            }
            TextButton(onClick = onDismiss) { Text("Cancelar", color = VisionTextSecondary) }
        }
    }
}

/**
 * Un perfil en el riel: al enfocarlo aparece debajo el botón de editar (lápiz). Así se puede
 * renombrar CUALQUIER perfil sin cambiarse a él; el clic en la tarjeta sigue siendo el cambio.
 */
@Composable
private fun ProfileItem(
    profile: Profile,
    inUse: Boolean,
    first: Boolean,
    dialogFocus: DialogFocus,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    var cardFocused by remember { mutableStateOf(false) }
    var editFocused by remember { mutableStateOf(false) }
    val editFocus = remember { FocusRequester() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(132.dp),
    ) {
        ProfileCard(
            name = profile.name,
            inUse = inUse,
            modifier = (if (first) Modifier.dialogFocus(dialogFocus) else Modifier)
                .onFocusChanged { cardFocused = it.isFocused }
                .softFocusDown(editFocus),
            onClick = onSelect,
        )
        if (cardFocused || editFocused) {
            Spacer(Modifier.height(6.dp))
            Surface(
                onClick = onEdit,
                modifier = Modifier
                    .size(38.dp)
                    .focusRequester(editFocus)
                    .onFocusChanged { editFocused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = VisionSurfaceHigh,
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), shape = CircleShape),
                    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape),
                ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Renombrar ${profile.name}",
                        tint = if (editFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
