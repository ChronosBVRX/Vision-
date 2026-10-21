package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del reloj (lo que no se puede componer sin la app entera):
 * - el móvil lo pinta DENTRO de la barra superior, no flotando encima;
 * - el overlay flotante de `MainActivity` pasa por `showsFloatingClock`, que lo apaga
 *   en el teléfono (si alguien vuelve a soltar la píldora sobre el menú móvil, esto falla).
 */
class ClockWiringTest {

    @Test
    fun thePhoneClockLivesInsideTheTopBar() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        val title = phone.substringAfter("title = {").substringBefore("actions = {")
        assertTrue(
            "la barra superior del móvil debe pintar el reloj discreto",
            title.contains("VisionClockText("),
        )
    }

    @Test
    fun theFloatingPillIsDecidedByTheSharedPolicy() {
        val main = requireSource("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue(
            "el overlay flotante debe pasar por showsFloatingClock (móvil apagado)",
            main.contains("showsFloatingClock(isTv"),
        )
        assertTrue(
            "la píldora de la tele sigue cableada",
            main.contains("VisionClockBadge("),
        )
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
