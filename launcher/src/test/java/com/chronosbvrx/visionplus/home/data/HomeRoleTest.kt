package com.chronosbvrx.visionplus.home.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La puerta del Inicio: se elige por lo que el equipo REALMENTE tiene (pantalla de Inicio,
 * apps predeterminadas o ninguna de las dos) y el estado se mide resolviendo el intent HOME.
 */
class HomeRoleTest {

    @Test
    fun asksTheSystemRoleOnAndroid10WhenNobodyHoldsIt() {
        assertEquals(
            HomeRoleAction.REQUEST_ROLE,
            homeRoleAction(29, roleAvailable = true, roleHeld = false, isDefault = false, handlesHomeSettings = false, handlesDefaultApps = false),
        )
        assertEquals(
            HomeRoleAction.REQUEST_ROLE,
            homeRoleAction(34, roleAvailable = true, roleHeld = false, isDefault = false, handlesHomeSettings = true, handlesDefaultApps = false),
        )
    }

    @Test
    fun withTheRoleAlreadyGrantedGoesToTheSettingsScreen() {
        // Google TV da el rol por bueno aunque el Inicio sea el suyo: pedirlo otra vez no hace nada.
        assertEquals(
            HomeRoleAction.OPEN_SETTINGS,
            homeRoleAction(34, roleAvailable = true, roleHeld = true, isDefault = false, handlesHomeSettings = true, handlesDefaultApps = false),
        )
    }

    @Test
    fun android9WithoutRoleFallsToTheSettingsScreen() {
        assertEquals(
            HomeRoleAction.OPEN_SETTINGS,
            homeRoleAction(28, roleAvailable = true, roleHeld = false, isDefault = false, handlesHomeSettings = true, handlesDefaultApps = false),
        )
        assertEquals(
            HomeRoleAction.OPEN_SETTINGS,
            homeRoleAction(28, roleAvailable = false, roleHeld = false, isDefault = false, handlesHomeSettings = false, handlesDefaultApps = true),
        )
    }

    @Test
    fun aFirmwareWithoutAnyHomeScreenGoesToTheCurrentLauncherDetails() {
        // El caso que reportó el usuario: la tele no tiene selector y el botón no hacía nada.
        assertEquals(
            HomeRoleAction.OPEN_CURRENT_LAUNCHER,
            homeRoleAction(34, roleAvailable = false, roleHeld = false, isDefault = false, handlesHomeSettings = false, handlesDefaultApps = false),
        )
        assertEquals(
            HomeRoleAction.OPEN_CURRENT_LAUNCHER,
            homeRoleAction(28, roleAvailable = false, roleHeld = false, isDefault = false, handlesHomeSettings = false, handlesDefaultApps = false),
        )
    }

    @Test
    fun whenVisionHomeIsTheOneThatOpensTheTvThereIsNothingToFix() {
        assertEquals(
            HomeRoleAction.ALREADY_DEFAULT,
            homeRoleAction(34, roleAvailable = true, roleHeld = true, isDefault = true, handlesHomeSettings = true, handlesDefaultApps = true),
        )
    }

    @Test
    fun theStateComesFromWhoResolvesTheHomeIntentAndNotFromTheRole() {
        assertTrue(isOwnHome("com.chronosbvrx.visionplus.home", "com.chronosbvrx.visionplus.home"))
        assertFalse(isOwnHome("com.google.android.apps.tv.launcherx", "com.chronosbvrx.visionplus.home"))
        assertFalse(isOwnHome(null, "com.chronosbvrx.visionplus.home"))
        assertFalse(isOwnHome("   ", "com.chronosbvrx.visionplus.home"))
        assertFalse(isOwnHome("", "com.chronosbvrx.visionplus.home"))
    }
}
