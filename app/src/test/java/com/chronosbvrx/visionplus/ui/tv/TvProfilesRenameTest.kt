package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editar el perfil desde la tele y el móvil: el nombre se escribe con el **teclado
 * predeterminado del sistema** y se elige una imagen de cine, series o anime. El móvil ya lo
 * tenía a medias; estos guardianes evitan que cualquiera de las dos interfaces se quede atrás.
 */
class TvProfilesRenameTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theTvDialogOffersNameAndImageEditingWithTheSystemKeyboard() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("falta el editor de perfil", tv.contains("ProfileEditor("))
        assertTrue("no usa el campo con teclado del sistema", tv.contains("ProfileNameField("))
        assertTrue("no guarda el nombre", tv.contains("ProfileStore.rename(context, target.id, name)"))
        assertTrue("no guarda la imagen", tv.contains("ProfileStore.setAvatar(context, target.id, avatar)"))
        assertTrue("no ofrece las imágenes de cine/series/anime", tv.contains("ProfileAvatars.of(category)"))
        assertTrue("no se puede crear un perfil escribiendo el nombre", tv.contains("ProfileStore.create(context, name, avatar)"))
    }

    @Test
    fun theNameFieldReallyAsksTheSystemIme() {
        val field = source("ui/profile/ProfileNameField.kt")
        assertTrue("no usa un EditText real", field.contains("EditText("))
        assertTrue("no usa AndroidView", field.contains("AndroidView("))
        assertTrue("no pide el IME del sistema", field.contains("InputMethodManager"))
        assertTrue("no abre el teclado", field.contains("showSoftInput("))
    }

    @Test
    fun theTvEditIconAppearsOnFocusWithoutSelectingTheProfile() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("falta el lápiz por perfil", tv.contains("Icons.Rounded.Edit"))
        assertTrue("el lápiz no aparece al enfocar", tv.contains("cardFocused || editFocused"))
        assertTrue("el lápiz no se puede alcanzar con Abajo", tv.contains("softFocusDown(editFocus)"))
        assertTrue("el lápiz no abre la edición", tv.contains("editing = ProfileDraft(profile.id"))
    }

    @Test
    fun thePhoneEditIconIsInEveryProfileCard() {
        val phone = source("ui/settings/ProfilesCard.kt")
        assertTrue("el móvil no tiene lápiz por perfil", phone.contains("Icons.Rounded.Edit"))
        assertTrue("el lápiz no edita ese perfil", phone.contains("renameTarget = profile"))
        assertTrue("el diálogo no renombra el perfil elegido", phone.contains("ProfileStore.rename(context, target.id, name)"))
        assertTrue("el diálogo no guarda la imagen", phone.contains("ProfileStore.setAvatar(context, target.id, avatar)"))
        assertTrue("la fila no pinta la imagen elegida", phone.contains("avatar = profile.avatar"))
    }

    @Test
    fun thePhoneStillRenamesProfiles() {
        val phone = source("ui/settings/ProfilesCard.kt")
        assertTrue("el móvil perdió Editar", phone.contains("Editar «"))
        assertTrue("el móvil no guarda el nombre elegido", phone.contains("ProfileStore.rename(context, target.id, name)"))
        assertTrue("el móvil no ofrece imágenes", phone.contains("ProfileAvatars.of(category)"))
    }

    @Test
    fun theTvAvatarPickerNeverTrapsTheRemote() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("falta el foco de la fila de nombre", tv.contains("focusRequester(nameRowFocus)"))
        assertTrue("las pestañas deben bajar a la primera imagen", tv.contains("softFocusDown(firstAvatarFocus)"))
        assertTrue("la selección debe subir a las pestañas", tv.contains("focusUp = categoryFocus"))
        assertTrue("la selección debe bajar a Guardar", tv.contains("focusDown = saveFocus"))
        assertTrue("Guardar debe volver arriba", tv.contains("softFocusUp(firstAvatarFocus)"))
        assertFalse(
            "no puede quedar focusProperties con destinos que se desmontan",
            tv.contains("focusProperties"),
        )
        assertTrue("el contenido alto debe poder desplazarse", tv.contains("verticalScroll(rememberScrollState())"))
    }

    @Test
    fun theTvProfilesScreenIsDarkGlassWithoutFakeActionBars() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        // Degradado diagonal, borde de cristal y esquinas de 24 dp.
        assertTrue("falta el azul medianoche del cristal", tv.contains("Color(0xFF161B26)"))
        assertTrue("falta el casi negro del cristal", tv.contains("Color(0xFF0B0E14)"))
        assertTrue("falta el borde de cristal", tv.contains("Color(0x1AFFFFFF)"))
        assertTrue("falta el scrim del 75 %", tv.contains("Color.Black.copy(alpha = 0.75f)"))
        assertTrue("el avatar debe ser protagonista", tv.contains("size = 110.dp"))
        assertTrue("falta la escala suave del foco", tv.contains("1.08f"))
        assertTrue("las vistas deben reemplazarse con un fade", tv.contains("Crossfade("))
        // Se retiraron las dos barras que parecían botones y el texto largo de ayuda.
        assertFalse(tv.contains("Renombrar «"))
        assertFalse(tv.contains("Cambiar imagen de «"))
        assertFalse(tv.contains("Pulsa el nombre"))
        assertFalse(tv.contains("Cada perfil guarda su"))
    }

    @Test
    fun theOnScreenKeyboardIsDarkWithCyanFocusAndUniversalIcons() {
        val keyboard = source("ui/tv/TvSearchScreen.kt")
        assertTrue("teclas en reposo demasiado claras", keyboard.contains("Color(0xFF1E293B)"))
        assertTrue("el foco no es cian", keyboard.contains("Color(0xFF38BDF8)"))
        assertTrue("la letra enfocada no es oscura", keyboard.contains("Color(0xFF0F172A)"))
        assertTrue("la tecla enfocada no crece", keyboard.contains("1.1f"))
        assertTrue("falta el icono de espacio", keyboard.contains("Icons.Rounded.SpaceBar"))
        assertTrue("falta el icono de borrar", keyboard.contains("KeyboardBackspace"))
    }

    @Test
    fun theAvatarCatalogHasCharacterImagesAndVariety() {
        val catalog = source("data/profile/ProfileAvatars.kt")
        assertTrue("faltan personajes de cine", catalog.contains("ProfileAvatarCategory.CINE"))
        assertTrue("faltan personajes de series", catalog.contains("ProfileAvatarCategory.SERIES"))
        assertTrue("faltan personajes de anime", catalog.contains("ProfileAvatarCategory.ANIME"))
        val cine = Regex("""ProfileAvatarOption\("[^"]+", "[^"]+", ProfileAvatarCategory\.CINE""").findAll(catalog).count()
        val series = Regex("""ProfileAvatarOption\("[^"]+", "[^"]+", ProfileAvatarCategory\.SERIES""").findAll(catalog).count()
        val anime = Regex("""ProfileAvatarOption\("[^"]+", "[^"]+", ProfileAvatarCategory\.ANIME""").findAll(catalog).count()
        assertEquals("deben ser 20 personajes de cine", 20, cine)
        assertEquals("deben ser 20 personajes de series", 20, series)
        assertEquals("deben ser 20 personajes de anime", 20, anime)
        assertTrue("falta un personaje de anime", catalog.contains("anime_ninja_novato"))
        assertTrue("falta un personaje infantil de series", catalog.contains("series_alien"))
    }
}
