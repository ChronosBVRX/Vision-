package com.chronosbvrx.visionplus.home.data

/**
 * Cómo se ofrece el cambio de Inicio según el sistema.
 *
 * - **Android 10+ (API 29)** tiene `RoleManager.ROLE_HOME`: su intent abre la ventana del
 *   sistema para elegir el launcher sin navegar a Ajustes a mano.
 * - Si el rol no existe, ya lo tiene el equipo o el aparato es anterior, se cae al
 *   selector/ajustes de Inicio… siempre que el firmware TENGA esa pantalla: muchas teles
 *   ignoran `ACTION_HOME_SETTINGS` sin decir nada (el intent «funciona» y no abre nada).
 * - Si el firmware no trae selector, la única puerta es la ficha de la app del launcher que
 *   manda: ahí está «Borrar predeterminados», y después Android pregunta con qué abrir el Inicio.
 */
enum class HomeRoleAction {
    REQUEST_ROLE,
    OPEN_SETTINGS,
    OPEN_CURRENT_LAUNCHER,
    ALREADY_DEFAULT,
}

/**
 * El Inicio REAL se mide resolviendo el intent HOME, no preguntando por el rol: en Google TV
 * `RoleManager` da el rol por bueno aunque el Inicio siga siendo el de Google. Creerle al rol
 * hacía que el botón se rindiera («ya eres el Inicio») en equipos donde no lo era.
 */
internal fun isOwnHome(resolvedHomePackage: String?, ownPackage: String): Boolean =
    !resolvedHomePackage.isNullOrBlank() && resolvedHomePackage == ownPackage

internal fun homeRoleAction(
    sdk: Int,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    isDefault: Boolean,
    handlesHomeSettings: Boolean,
    handlesDefaultApps: Boolean,
): HomeRoleAction = when {
    isDefault -> HomeRoleAction.ALREADY_DEFAULT
    // Con el rol «ya concedido» el sistema no vuelve a preguntar: hay que ir a Ajustes.
    sdk >= 29 && roleAvailable && !roleHeld -> HomeRoleAction.REQUEST_ROLE
    handlesHomeSettings || handlesDefaultApps -> HomeRoleAction.OPEN_SETTINGS
    else -> HomeRoleAction.OPEN_CURRENT_LAUNCHER
}
