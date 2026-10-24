package com.chronosbvrx.visionplus.home.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El cambio de Inicio debe sentirse como un botón, no como una tarea de Ajustes:
 * Android 10+ pide el rol HOME (ventana del sistema); Android viejo cae a Ajustes.
 */
class HomeRoleTest {

    @Test
    fun androidTenAndNewerRequestsTheHomeRoleDirectly() {
        assertEquals(HomeRoleAction.REQUEST_ROLE, homeRoleAction(29, roleAvailable = true, roleHeld = false))
        assertEquals(HomeRoleAction.REQUEST_ROLE, homeRoleAction(34, roleAvailable = true, roleHeld = false))
    }

    @Test
    fun olderAndroidUsesTheSettingsSelector() {
        assertEquals(HomeRoleAction.OPEN_SETTINGS, homeRoleAction(28, roleAvailable = true, roleHeld = false))
    }

    @Test
    fun anUnavailableRoleFallsBackToSettings() {
        assertEquals(HomeRoleAction.OPEN_SETTINGS, homeRoleAction(34, roleAvailable = false, roleHeld = false))
    }

    @Test
    fun alreadyBeingTheHomeScreenStillLeavesTheManualDoor() {
        assertEquals(HomeRoleAction.ALREADY_DEFAULT, homeRoleAction(34, roleAvailable = true, roleHeld = true))
    }
}
