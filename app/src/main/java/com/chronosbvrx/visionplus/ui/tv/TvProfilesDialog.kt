package com.chronosbvrx.visionplus.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.data.profile.Profile
import com.chronosbvrx.visionplus.data.profile.ProfileAvatarCategory
import com.chronosbvrx.visionplus.data.profile.ProfileAvatars
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.profile.ProfileNameField
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import kotlinx.coroutines.delay

/**
 * Paleta del selector de perfiles. Es la versión «cristal oscuro» de Vision+: el azul plano de
 * antes se cambia por un degradado carbón/medianoche y el cian queda reservado para el foco del
 * mando y la acción principal.
 */
private val GlassTop = Color(0xFF161B26)
private val GlassBottom = Color(0xFF0B0E14)
private val GlassBorder = Color(0x1AFFFFFF)
private val ProfileAccent = Color(0xFF38BDF8)
private val ProfileMuted = Color(0xFF94A3B8)
private val ProfileField = Color(0xFF090C10)
private val ProfileKey = Color(0xFF1E293B)
private val ProfileInk = Color(0xFF0F172A)
private val ProfileOnline = Color(0xFF34D399)
private val ProfileOnUse = Color(0xFFA7F3D0)

/**
 * «Perfiles» en la tele: elegir quién eres, crear uno nuevo o editar avatar y nombre. Todo vive
 * dentro de **una sola ventana de cristal**: la vista cambia con un fade (lista → editor →
 * teclado) en vez de abrir un modal encima de otro.
 */
@Composable
internal fun TvProfilesDialog(
    onDismiss: () -> Unit,
    onProfileChanged: () -> Unit = {},
    title: String = "Perfiles",
) {
    TvDialog(title = "", onDismiss = onDismiss, glass = true) {
        ProfilesPanel(
            title = title,
            onDismiss = onDismiss,
            onProfileChanged = onProfileChanged,
        )
    }
}

/**
 * El mismo selector pero dibujado en la misma composición (sin ventana aparte). Se usa al abrir
 * la tele: el splash se desliza hacia arriba y deja ver el Inicio ya cargado con los perfiles
 * encima, sin saltar a otra pantalla.
 */
@Composable
internal fun TvProfilesOverlay(
    onDismiss: () -> Unit,
    onProfileChanged: () -> Unit = {},
    title: String = "Perfiles",
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    BackHandler(enabled = true, onBack = onDismiss)
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(320),
        label = "profiles-gate-in",
    )
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = entrance }
            .background(Color.Black.copy(alpha = 0.75f))
            .modalFocusTrap()
            .dismissOnBack(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        ProfilesPanel(
            title = title,
            onDismiss = onDismiss,
            onProfileChanged = onProfileChanged,
            modifier = Modifier.graphicsLayer { translationY = (1f - entrance) * 30f * density },
        )
    }
}

/** Lo que se está creando/editando; [id] null significa «perfil nuevo». */
private data class ProfileDraft(
    val id: String?,
    val name: String,
    val avatar: String = "",
    /** Crear abre el teclado de una vez; el lápiz va directo a la imagen y el nombre. */
    val startWithName: Boolean = false,
)

/** Las tres vistas del mismo panel, jamás dos ventanas a la vez. */
private enum class EditorPage { Details, Keyboard, SystemKeyboard }

@Composable
private fun ProfilesPanel(
    title: String,
    onDismiss: () -> Unit,
    onProfileChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(ProfileStore.profiles(context)) }
    var activeId by remember { mutableStateOf(ProfileStore.activeId(context)) }
    var editing by remember { mutableStateOf<ProfileDraft?>(null) }
    val focus = rememberDialogFocus()

    fun reload() {
        profiles = ProfileStore.profiles(context)
        activeId = ProfileStore.activeId(context)
    }

    fun switchTo(profile: Profile) {
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

    // Al volver de la edición, el foco regresa a la lista (nunca queda huérfano detrás).
    LaunchedEffect(editing) {
        if (editing == null) {
            delay(160)
            requestFocusSafely(focus.requester)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .widthIn(min = 420.dp, max = 900.dp)
            .shadow(
                elevation = 30.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassTop, GlassBottom),
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 34.dp, vertical = 24.dp)
            .modalFocusTrap()
            .dismissOnBack(onDismiss),
    ) {
        Crossfade(
            targetState = editing,
            animationSpec = tween(220),
            label = "profiles-screen",
        ) { target ->
            if (target == null) {
                ProfileSelection(
                    title = title,
                    profiles = profiles,
                    activeId = activeId,
                    dialogFocus = focus,
                    onSelect = { switchTo(it) },
                    onCreate = {
                        editing = ProfileDraft(null, "Perfil ${profiles.size + 1}", "", startWithName = true)
                    },
                    onEdit = { profile ->
                        editing = ProfileDraft(profile.id, profile.name, profile.avatar, startWithName = false)
                    },
                )
            } else {
                val deletableId = target.id?.takeIf { ProfileStore.canDelete(it, profiles) }
                ProfileEditor(
                    target = target,
                    onBack = { editing = null },
                    onSave = { name, avatar ->
                        val ok = if (target.id == null) {
                            ProfileStore.create(context, name, avatar) != null
                        } else {
                            ProfileStore.rename(context, target.id, name) &&
                                ProfileStore.setAvatar(context, target.id, avatar)
                        }
                        if (ok) {
                            reload()
                            editing = null
                            Toast.makeText(
                                context,
                                if (target.id == null) "Perfil «$name» creado" else "Perfil actualizado: «$name»",
                                Toast.LENGTH_SHORT,
                            ).show()
                            onProfileChanged()
                        } else {
                            Toast.makeText(context, "Escribe un nombre para el perfil", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = deletableId?.let { id ->
                        {
                            val wasActive = id == activeId
                            if (ProfileStore.delete(context, id)) {
                                Toast.makeText(context, "Perfil «${target.name}» borrado", Toast.LENGTH_SHORT).show()
                                if (wasActive) {
                                    onDismiss()
                                    context.findActivity()?.recreate()
                                } else {
                                    reload()
                                    editing = null
                                    onProfileChanged()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Pantalla 1: los perfiles grandes, centrados y sin barras falsas. */
@Composable
private fun ProfileSelection(
    title: String,
    profiles: List<Profile>,
    activeId: String,
    dialogFocus: DialogFocus,
    onSelect: (Profile) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Profile) -> Unit,
) {
    val subtitle = if (title.startsWith("¿Quién")) {
        "Selecciona tu perfil para continuar"
    } else {
        "¿Quién está viendo ahora?"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = ProfileMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(22.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = 136.dp
            val gap = 20.dp
            val count = profiles.size + if (profiles.size < ProfileStore.MAX_PROFILES) 1 else 0
            val totalWidth = itemWidth * count.toFloat() + gap * (count - 1).coerceAtLeast(0).toFloat()
            // Centrado cuando cabe; si la tele es angosta, el riel se recorre con el mando.
            val side = ((maxWidth - totalWidth) / 2).coerceAtLeast(0.dp)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    ProfileItem(
                        profile = profile,
                        inUse = profile.id == activeId,
                        first = index == 0,
                        dialogFocus = dialogFocus,
                        onSelect = { onSelect(profile) },
                        onEdit = { onEdit(profile) },
                    )
                }
                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    item {
                        ProfileCard(
                            name = "Crear perfil",
                            inUse = false,
                            isAdd = true,
                            onClick = onCreate,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un perfil: la foto es la protagonista (110 dp). Al enfocar, solo la imagen crece y se enciende
 * con un aro cian y un resplandor suave; debajo aparece la píldora «Editar».
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
    val cardFocus = remember { FocusRequester() }
    val upRequester = if (first) dialogFocus.requester else cardFocus
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(136.dp),
    ) {
        ProfileCard(
            name = profile.name,
            inUse = inUse,
            avatar = profile.avatar,
            modifier = (if (first) Modifier.dialogFocus(dialogFocus) else Modifier.focusRequester(cardFocus))
                .onFocusChanged { cardFocused = it.isFocused }
                .softFocusDown(editFocus),
            onClick = onSelect,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = cardFocused || editFocused,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                Surface(
                    onClick = onEdit,
                    modifier = Modifier
                        .height(32.dp)
                        .focusRequester(editFocus)
                        .onFocusChanged { editFocused = it.isFocused }
                        .softFocusUp(upRequester)
                        .animateContentSize(),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                        focusedContainerColor = ProfileAccent,
                        focusedContentColor = ProfileInk,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = CircleShape),
                        focusedBorder = Border.None,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar ${profile.name}",
                            tint = if (editFocused) ProfileInk else Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        if (editFocused) {
                            Text(
                                "Editar",
                                color = ProfileInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de un perfil: solo la foto y el nombre. El aro y el resplandor viven en la imagen, no
 * en un rectángulo alrededor de toda la tarjeta; «En uso» es una píldora con punto verde.
 */
@Composable
private fun ProfileCard(
    name: String,
    inUse: Boolean,
    modifier: Modifier = Modifier,
    isAdd: Boolean = false,
    avatar: String = "",
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "profile-avatar-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(136.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (focused) {
                    Box(
                        Modifier
                            .requiredSize(154.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(ProfileAccent.copy(alpha = 0.30f), Color.Transparent),
                                ),
                                CircleShape,
                            ),
                    )
                }
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                    if (isAdd) {
                        Box(
                            Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.045f))
                                .border(
                                    width = if (focused) 2.5.dp else 1.5.dp,
                                    color = if (focused) ProfileAccent else Color.White.copy(alpha = 0.30f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Crear perfil",
                                tint = if (focused) ProfileAccent else Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    } else {
                        ProfileAvatar(
                            name = name,
                            size = 110.dp,
                            ring = focused || inUse,
                            avatar = avatar,
                            ringColor = if (focused) ProfileAccent else Color.White.copy(alpha = 0.72f),
                            ringWidth = if (focused) 2.5.dp else 1.5.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = name,
                color = Color.White,
                fontWeight = if (inUse || focused) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            if (!isAdd && inUse) {
                InUseBadge()
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Píldora discreta de «En uso»: punto verde y texto suave, sin gritar. */
@Composable
private fun InUseBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(ProfileOnline),
        )
        Text(
            "En uso",
            color = ProfileOnUse,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Pantalla 2: editar el perfil. La cabecera une avatar y nombre en un solo bloque oscuro; las
 * categorías son píldoras completas y la fila de avatares ya no encierra cada foto en un cuadro.
 * El teclado es una vista del mismo panel, no otro modal.
 */
@Composable
private fun ProfileEditor(
    target: ProfileDraft,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isNew = target.id == null
    var name by remember(target) { mutableStateOf(target.name) }
    var avatar by remember(target) { mutableStateOf(target.avatar) }
    var category by remember(target) {
        mutableStateOf(ProfileAvatars.find(target.avatar)?.category ?: ProfileAvatarCategory.CINE)
    }
    var page by remember(target) {
        mutableStateOf(if (target.startWithName) EditorPage.Keyboard else EditorPage.Details)
    }
    val firstKeyFocus = remember { FocusRequester() }
    val nameRowFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val firstAvatarFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    LaunchedEffect(page) {
        when (page) {
            EditorPage.Details -> {
                delay(140)
                requestFocusSafely(nameRowFocus)
            }
            EditorPage.Keyboard -> {
                delay(160)
                requestFocusSafely(firstKeyFocus)
            }
            EditorPage.SystemKeyboard -> Unit // ProfileNameField abre el IME solo.
        }
    }

    Crossfade(
        targetState = page,
        animationSpec = tween(190),
        label = "profile-editor-page",
    ) { current ->
        when (current) {
            EditorPage.Details -> ProfileDetailsPage(
                isNew = isNew,
                name = name,
                avatar = avatar,
                category = category,
                onAvatar = { avatar = it },
                onCategory = { category = it },
                onOpenKeyboard = { page = EditorPage.Keyboard },
                onBack = onBack,
                onSave = { onSave(name.trim(), avatar) },
                onDelete = onDelete,
                nameRowFocus = nameRowFocus,
                categoryFocus = categoryFocus,
                firstAvatarFocus = firstAvatarFocus,
                saveFocus = saveFocus,
            )
            EditorPage.Keyboard -> ProfileKeyboardPage(
                name = name,
                onName = {
                    if (it.length <= 24) name = it
                },
                onDone = { if (name.isNotBlank()) page = EditorPage.Details },
                onBack = { page = EditorPage.Details },
                onSystemKeyboard = { page = EditorPage.SystemKeyboard },
                firstKeyFocus = firstKeyFocus,
            )
            EditorPage.SystemKeyboard -> ProfileSystemKeyboardPage(
                name = name,
                onName = {
                    if (it.length <= 24) name = it
                },
                onDone = { if (name.isNotBlank()) page = EditorPage.Details },
                onBack = { page = EditorPage.Keyboard },
            )
        }
    }
}

@Composable
private fun ProfileDetailsPage(
    isNew: Boolean,
    name: String,
    avatar: String,
    category: ProfileAvatarCategory,
    onAvatar: (String) -> Unit,
    onCategory: (ProfileAvatarCategory) -> Unit,
    onOpenKeyboard: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    nameRowFocus: FocusRequester,
    categoryFocus: FocusRequester,
    firstAvatarFocus: FocusRequester,
    saveFocus: FocusRequester,
) {
    var nameFocused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4_000)
            confirmDelete = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorHeader(
            title = if (isNew) "Nuevo perfil" else "Editar perfil",
            onBack = onBack,
        )
        // Cabecera unificada: avatar + nombre en un campo oscuro con borde que se enciende.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ProfileAvatar(name = name, size = 76.dp, avatar = avatar)
            Surface(
                onClick = onOpenKeyboard,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(nameRowFocus)
                    .onFocusChanged { nameFocused = it.isFocused }
                    .softFocusDown(categoryFocus),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = ProfileField,
                    focusedContainerColor = ProfileField,
                    pressedContainerColor = ProfileField,
                    contentColor = Color.White,
                    focusedContentColor = Color.White,
                    pressedContentColor = Color.White,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), shape = RoundedCornerShape(14.dp)),
                    focusedBorder = Border(BorderStroke(1.5.dp, ProfileAccent), shape = RoundedCornerShape(14.dp)),
                    pressedBorder = Border(BorderStroke(1.5.dp, ProfileAccent), shape = RoundedCornerShape(14.dp)),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                ) {
                    Text(
                        text = name.ifBlank { "Nombre del perfil" },
                        color = if (name.isBlank()) ProfileMuted else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Escribir nombre",
                        tint = if (nameFocused) ProfileAccent else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Text(
            "Imagen",
            color = ProfileMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileAvatarCategory.entries.forEach { option ->
                ProfileCategoryPill(
                    label = option.label,
                    selected = category == option,
                    modifier = (if (option == ProfileAvatarCategory.CINE) {
                        Modifier.focusRequester(categoryFocus)
                    } else {
                        Modifier
                    })
                        .softFocusUp(nameRowFocus)
                        .softFocusDown(firstAvatarFocus),
                    onClick = { onCategory(option) },
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "inicial") {
                AvatarOptionCard(
                    label = "Inicial",
                    selected = avatar.isBlank(),
                    onClick = { onAvatar("") },
                    modifier = Modifier.focusRequester(firstAvatarFocus),
                    focusUp = categoryFocus,
                    focusDown = saveFocus,
                ) {
                    ProfileAvatar(name = name, size = 76.dp, avatar = "")
                }
            }
            itemsIndexed(ProfileAvatars.of(category), key = { _, option -> option.id }) { _, option ->
                AvatarOptionCard(
                    label = option.label,
                    selected = avatar == option.id,
                    onClick = { onAvatar(option.id) },
                    focusUp = categoryFocus,
                    focusDown = saveFocus,
                ) {
                    Image(
                        painter = painterResource(option.drawable),
                        contentDescription = option.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onDelete != null) {
                GhostAction(
                    label = if (confirmDelete) "Confirmar borrado" else "Borrar perfil",
                    icon = Icons.Rounded.DeleteOutline,
                    danger = true,
                    onClick = {
                        if (confirmDelete) onDelete() else confirmDelete = true
                    },
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostAction(
                    label = "Cancelar",
                    modifier = Modifier.softFocusUp(firstAvatarFocus),
                    onClick = onBack,
                )
                ProfilePrimaryAction(
                    label = "Guardar",
                    modifier = Modifier
                        .focusRequester(saveFocus)
                        .softFocusUp(firstAvatarFocus),
                    onClick = onSave,
                )
            }
        }
    }
}

/** Pantalla 3: el teclado del mando, con campo oscuro y cursor cian parpadeante. */
@Composable
private fun ProfileKeyboardPage(
    name: String,
    onName: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onSystemKeyboard: () -> Unit,
    firstKeyFocus: FocusRequester,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EditorHeader(title = "Nombre del perfil", onBack = onBack)
        Spacer(Modifier.height(10.dp))
        NameEditorField(name = name)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            TvKeyboard(
                text = name,
                onTextChange = onName,
                firstKey = Modifier.focusRequester(firstKeyFocus),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            GhostAction(label = "Teclado del sistema", onClick = onSystemKeyboard)
            ProfilePrimaryAction(
                label = "Listo",
                onClick = onDone,
            )
        }
    }
}

/** El campo de escritura con el cursor vivo, como un editor de texto real. */
@Composable
private fun NameEditorField(name: String) {
    val blink by rememberInfiniteTransition(label = "profile-caret").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "profile-caret-alpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProfileField)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = name.ifBlank { "Nombre del perfil" },
            color = if (name.isBlank()) ProfileMuted else Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            Modifier
                .padding(start = 2.dp)
                .width(2.dp)
                .height(28.dp)
                .background(ProfileAccent.copy(alpha = blink), RoundedCornerShape(1.dp)),
        )
    }
}

/** El teclado del sistema como opción secundaria (teles que sí lo traen). */
@Composable
private fun ProfileSystemKeyboardPage(
    name: String,
    onName: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EditorHeader(title = "Nombre del perfil", onBack = onBack)
        Spacer(Modifier.height(14.dp))
        ProfileNameField(
            value = name,
            onValueChange = onName,
            autoFocus = true,
            textSizeSp = 20f,
            backgroundColor = ProfileField,
            accentColor = ProfileAccent,
            onDone = onDone,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            GhostAction(label = "Teclado en pantalla", onClick = onBack)
            ProfilePrimaryAction(
                label = "Listo",
                onClick = onDone,
            )
        }
    }
}

/** Cabecera de la vista de edición: regreso discreto + título. */
@Composable
private fun EditorHeader(title: String, onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.06f),
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
                pressedContentColor = Color.Black,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = CircleShape),
                focusedBorder = Border.None,
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Atrás",
                    tint = if (focused) Color.Black else Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Píldora de categoría: activa con degradado azul; inactiva en gris/azulado sin borde duro. */
@Composable
private fun ProfileCategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
            pressedBorder = Border.None,
        ),
    ) {
        Box(
            Modifier
                .background(
                    brush = if (selected) {
                        Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))
                    } else {
                        Brush.linearGradient(listOf(ProfileKey, ProfileKey))
                    },
                    shape = CircleShape,
                )
                .padding(horizontal = 22.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * Una foto del catálogo sin recuadro exterior: la imagen es la tarjeta. Al enfocar crece un poco,
 * se ilumina el borde y el texto pasa de gris a blanco.
 */
@Composable
private fun AvatarOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(160),
        label = "avatar-option-scale",
    )
    val shape = if (label == "Inicial") CircleShape else RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(94.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (focusUp != null) runCatching { focusUp.requestFocus() }
                        focusUp != null
                    }
                    Key.DirectionDown -> {
                        if (focusDown != null) runCatching { focusDown.requestFocus() }
                        focusDown != null
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (focused) {
                    Box(
                        Modifier
                            .requiredSize(100.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(ProfileAccent.copy(alpha = 0.26f), Color.Transparent),
                                ),
                                CircleShape,
                            ),
                    )
                }
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                    Box(
                        Modifier
                            .then(
                                if (focused || selected) {
                                    Modifier.border(
                                        width = if (focused) 2.5.dp else 1.5.dp,
                                        color = if (focused) ProfileAccent else Color.White.copy(alpha = 0.75f),
                                        shape = shape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(3.dp),
                    ) {
                        content()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                color = if (focused) Color.White else ProfileMuted,
                fontSize = 10.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Acción principal del editor: blanca, texto oscuro y aro cian al recibir el foco. */
@Composable
private fun ProfilePrimaryAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(140),
        label = "profile-primary-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
            contentColor = ProfileInk,
            focusedContentColor = ProfileInk,
            pressedContentColor = ProfileInk,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, ProfileAccent), shape = CircleShape),
            pressedBorder = Border.None,
        ),
    ) {
        Box(
            Modifier.padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = ProfileInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Acción secundaria discreta: sin fondo en reposo y un velo gris solo al enfocar. */
@Composable
private fun GhostAction(
    label: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = if (danger) Color(0xFFF87171) else ProfileMuted,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (focused) Color.White else if (danger) Color(0xFFF87171) else ProfileMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                label,
                color = if (focused) Color.White else if (danger) Color(0xFFF87171) else ProfileMuted,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
