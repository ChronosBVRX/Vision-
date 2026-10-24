package com.chronosbvrx.visionplus.home.data

/**
 * Cómo se ofrece el cambio de Inicio según el sistema:
 *
 * - **Android 10+ (API 29)** tiene `RoleManager.ROLE_HOME`: su intent abre directamente la
 *   ventana del sistema para elegir el launcher (sin navegar a Ajustes a mano).
 * - Si el rol no existe o el equipo es anterior, se cae al selector/ajustes de Inicio.
 * - Si Vision+ Home ya es el Inicio, la acción principal se convierte en la puerta manual para
 *   poder cambiar al otro launcher.
 */
internal enum class HomeRoleAction {
    REQUEST_ROLE,
    OPEN_SETTINGS,
    ALREADY_DEFAULT,
}

internal fun homeRoleAction(sdk: Int, roleAvailable: Boolean, roleHeld: Boolean): HomeRoleAction =
    when {
        roleHeld -> HomeRoleAction.ALREADY_DEFAULT
        sdk >= 29 && roleAvailable -> HomeRoleAction.REQUEST_ROLE
        else -> HomeRoleAction.OPEN_SETTINGS
    }
